"""
Fine-Tuning Engine with QLoRA
==============================

Core training logic that handles:
- 4-bit model loading with bitsandbytes quantization
- LoRA adapter injection
- Memory-optimized training with gradient checkpointing
- Automatic GPU memory management
- Checkpoint saving and resumption
"""

import gc
import logging
import os
from pathlib import Path
from typing import Optional

import torch
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
    Trainer,
    DataCollatorForSeq2Seq,
)
from peft import (
    LoraConfig,
    get_peft_model,
    prepare_model_for_kbit_training,
    TaskType,
    PeftModel,
)
from datasets import Dataset

from .config import PipelineConfig, PEFTMethod

logger = logging.getLogger(__name__)


# ═══════════════════════════════════════════════════════════════════════════════
# GPU MEMORY UTILITIES
# ═══════════════════════════════════════════════════════════════════════════════

class GPUMemoryManager:
    """
    Monitors and manages GPU memory throughout training.

    Strategies employed:
    1. 4-bit quantization (QLoRA): Reduces model memory from 14GB → 3.5GB for 7B
    2. Gradient checkpointing: Trades 30% extra compute for ~60% activation memory savings
    3. Paged optimizers (8-bit AdamW): Reduces optimizer states by ~50%
    4. Gradient accumulation: Simulates large batches without proportional memory cost
    5. Mixed precision (bf16/fp16): Halves activation memory
    """

    @staticmethod
    def get_gpu_info() -> dict:
        """Get current GPU memory statistics."""
        if not torch.cuda.is_available():
            return {"available": False, "device": "cpu"}

        device = torch.cuda.current_device()
        total = torch.cuda.get_device_properties(device).total_mem / 1024**3
        allocated = torch.cuda.memory_allocated(device) / 1024**3
        reserved = torch.cuda.memory_reserved(device) / 1024**3
        free = total - reserved

        return {
            "available": True,
            "device": torch.cuda.get_device_name(device),
            "total_gb": round(total, 2),
            "allocated_gb": round(allocated, 2),
            "reserved_gb": round(reserved, 2),
            "free_gb": round(free, 2),
        }

    @staticmethod
    def estimate_memory_requirements(
        model_params_billions: float,
        lora_rank: int,
        batch_size: int,
        seq_length: int,
        gradient_checkpointing: bool = True,
    ) -> dict:
        """
        Estimate GPU memory requirements for fine-tuning.

        Formula breakdown:
        - Base model (4-bit): params * 0.5 bytes ≈ params_B * 0.5 GB
        - LoRA adapters (fp16): ~2 * rank * hidden_dim * num_layers * 2 bytes
        - Optimizer (8-bit Adam): ~2 * lora_params (momentum + variance)
        - Activations: batch_size * seq_len * hidden_dim * num_layers * 2 bytes
        - Gradient checkpointing reduces activations by sqrt(num_layers)/num_layers

        Returns dict with memory breakdown in GB.
        """
        # Rough estimates based on typical transformer architectures
        hidden_dim = int(model_params_billions * 512)  # Approximate
        num_layers = int(model_params_billions * 4.5)  # Approximate

        # Base model in 4-bit
        model_mem = model_params_billions * 0.5  # GB

        # LoRA parameters (fp16)
        lora_params = 2 * lora_rank * hidden_dim * num_layers * 2  # bytes (in + out projections)
        lora_mem = lora_params / 1024**3  # GB

        # Optimizer states (8-bit: 1 byte per param instead of 8)
        optimizer_mem = lora_params * 2 / 1024**3  # momentum + variance

        # Activations
        activation_mem = batch_size * seq_length * hidden_dim * num_layers * 2 / 1024**3
        if gradient_checkpointing:
            activation_mem *= 0.4  # ~60% savings

        total = model_mem + lora_mem + optimizer_mem + activation_mem

        return {
            "model_4bit_gb": round(model_mem, 2),
            "lora_adapters_gb": round(lora_mem, 3),
            "optimizer_states_gb": round(optimizer_mem, 3),
            "activations_gb": round(activation_mem, 2),
            "total_estimated_gb": round(total, 2),
            "recommended_gpu_gb": round(total * 1.3, 0),  # 30% headroom
        }

    @staticmethod
    def clear_memory():
        """Aggressively free GPU memory."""
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.synchronize()


# ═══════════════════════════════════════════════════════════════════════════════
# FINE-TUNING ENGINE
# ═══════════════════════════════════════════════════════════════════════════════

class FineTuningEngine:
    """
    Main fine-tuning engine using QLoRA.

    Usage:
        engine = FineTuningEngine(config)
        engine.load_model()
        engine.train(train_dataset, eval_dataset)
        engine.save_model()
        engine.merge_and_export()  # Optional: merge LoRA into base model
    """

    def __init__(self, config: PipelineConfig):
        self.config = config
        self.model = None
        self.tokenizer = None
        self.trainer = None
        self.memory_manager = GPUMemoryManager()

    def load_model(self):
        """
        Load the base model with 4-bit quantization and inject LoRA adapters.

        Memory optimization flow:
        1. Load in 4-bit with double quantization (nf4)
        2. Prepare for k-bit training (cast LayerNorm to fp32)
        3. Inject LoRA adapters into target modules
        4. Enable gradient checkpointing
        """
        logger.info(f"Loading model: {self.config.model.model_name_or_path}")
        logger.info(f"PEFT method: {self.config.peft_method.value}")

        # Print GPU info
        gpu_info = self.memory_manager.get_gpu_info()
        logger.info(f"GPU: {gpu_info}")

        # Print memory estimate
        model_size_b = self._estimate_model_size()
        mem_estimate = self.memory_manager.estimate_memory_requirements(
            model_params_billions=model_size_b,
            lora_rank=self.config.lora.r,
            batch_size=self.config.training.per_device_train_batch_size,
            seq_length=self.config.data.max_seq_length,
            gradient_checkpointing=self.config.training.gradient_checkpointing,
        )
        logger.info(f"Estimated memory: {mem_estimate}")

        # ── Step 1: Configure quantization ────────────────────────────────────
        quantization_config = None
        if self.config.peft_method == PEFTMethod.QLORA:
            compute_dtype = getattr(torch, self.config.model.bnb_4bit_compute_dtype)
            quantization_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_compute_dtype=compute_dtype,
                bnb_4bit_quant_type=self.config.model.bnb_4bit_quant_type,
                bnb_4bit_use_double_quant=self.config.model.bnb_4bit_use_double_quant,
            )
            logger.info(
                f"Quantization: 4-bit NF4, double_quant={self.config.model.bnb_4bit_use_double_quant}"
            )

        # ── Step 2: Load base model ───────────────────────────────────────────
        model_kwargs = {
            "pretrained_model_name_or_path": self.config.model.model_name_or_path,
            "quantization_config": quantization_config,
            "torch_dtype": getattr(torch, self.config.model.torch_dtype),
            "trust_remote_code": self.config.model.trust_remote_code,
            "attn_implementation": self.config.model.attn_implementation,
            "device_map": "auto",  # Automatically distribute across available GPUs
        }
        if self.config.model.hf_token:
            model_kwargs["token"] = self.config.model.hf_token

        self.model = AutoModelForCausalLM.from_pretrained(**model_kwargs)
        logger.info(f"Base model loaded. Parameters: {self.model.num_parameters():,}")

        # ── Step 3: Prepare for k-bit training ────────────────────────────────
        if self.config.peft_method == PEFTMethod.QLORA:
            self.model = prepare_model_for_kbit_training(
                self.model,
                use_gradient_checkpointing=self.config.training.gradient_checkpointing,
            )

        # ── Step 4: Inject LoRA adapters ──────────────────────────────────────
        lora_config = LoraConfig(
            r=self.config.lora.r,
            lora_alpha=self.config.lora.lora_alpha,
            lora_dropout=self.config.lora.lora_dropout,
            bias=self.config.lora.bias,
            task_type=TaskType.CAUSAL_LM,
            target_modules=self.config.lora.target_modules,
            modules_to_save=self.config.lora.modules_to_save,
        )

        self.model = get_peft_model(self.model, lora_config)

        # Print trainable parameters
        trainable, total = self.model.get_nb_trainable_parameters()
        logger.info(
            f"Trainable parameters: {trainable:,} / {total:,} "
            f"({100 * trainable / total:.2f}%)"
        )

        # ── Step 5: Load tokenizer ────────────────────────────────────────────
        self.tokenizer = AutoTokenizer.from_pretrained(
            self.config.model.model_name_or_path,
            trust_remote_code=self.config.model.trust_remote_code,
            token=self.config.model.hf_token,
        )
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        # ── Step 6: Enable gradient checkpointing ─────────────────────────────
        if self.config.training.gradient_checkpointing:
            self.model.gradient_checkpointing_enable(
                gradient_checkpointing_kwargs={"use_reentrant": False}
            )
            logger.info("Gradient checkpointing enabled (saves ~60% activation memory)")

        # Log final memory state
        if torch.cuda.is_available():
            gpu_info = self.memory_manager.get_gpu_info()
            logger.info(f"After model load — GPU: {gpu_info['allocated_gb']:.2f}GB allocated")

    def train(self, train_dataset: Dataset, eval_dataset: Optional[Dataset] = None):
        """
        Run the training loop with all memory optimizations.

        Key memory optimizations in TrainingArguments:
        - gradient_accumulation_steps: Simulate large batch without memory cost
        - gradient_checkpointing: Recompute activations in backward pass
        - optim="paged_adamw_8bit": 8-bit optimizer with CPU offloading
        - bf16/fp16: Mixed precision training
        - dataloader_pin_memory: Faster CPU→GPU transfer
        - group_by_length: Minimize padding within batches
        """
        logger.info("Setting up training...")

        # Configure training arguments
        training_args = TrainingArguments(
            output_dir=self.config.training.output_dir,
            num_train_epochs=self.config.training.num_train_epochs,
            max_steps=self.config.training.max_steps,
            per_device_train_batch_size=self.config.training.per_device_train_batch_size,
            per_device_eval_batch_size=self.config.training.per_device_eval_batch_size,
            gradient_accumulation_steps=self.config.training.gradient_accumulation_steps,
            gradient_checkpointing=self.config.training.gradient_checkpointing,
            learning_rate=self.config.training.learning_rate,
            lr_scheduler_type=self.config.training.lr_scheduler_type,
            warmup_ratio=self.config.training.warmup_ratio,
            optim=self.config.training.optim,
            weight_decay=self.config.training.weight_decay,
            max_grad_norm=self.config.training.max_grad_norm,
            fp16=self.config.training.fp16,
            bf16=self.config.training.bf16,
            logging_steps=self.config.training.logging_steps,
            save_strategy=self.config.training.save_strategy,
            save_steps=self.config.training.save_steps,
            save_total_limit=self.config.training.save_total_limit,
            eval_strategy=self.config.training.eval_strategy if eval_dataset else "no",
            eval_steps=self.config.training.eval_steps if eval_dataset else None,
            load_best_model_at_end=self.config.training.load_best_model_at_end if eval_dataset else False,
            metric_for_best_model=self.config.training.metric_for_best_model,
            dataloader_pin_memory=self.config.training.dataloader_pin_memory,
            remove_unused_columns=self.config.training.remove_unused_columns,
            group_by_length=self.config.training.group_by_length,
            seed=self.config.training.seed,
            report_to="none",  # Set to "wandb" or "tensorboard" for experiment tracking
            gradient_checkpointing_kwargs={"use_reentrant": False},
        )

        # Data collator handles dynamic padding (more memory-efficient than static)
        data_collator = DataCollatorForSeq2Seq(
            tokenizer=self.tokenizer,
            padding=True,
            pad_to_multiple_of=8,  # Tensor core alignment for efficiency
        )

        # Initialize Trainer
        self.trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_dataset,
            eval_dataset=eval_dataset,
            data_collator=data_collator,
            tokenizer=self.tokenizer,
        )

        # ── Training ──────────────────────────────────────────────────────────
        logger.info("Starting training...")
        logger.info(
            f"  Effective batch size: "
            f"{self.config.training.per_device_train_batch_size * self.config.training.gradient_accumulation_steps}"
        )
        logger.info(f"  Learning rate: {self.config.training.learning_rate}")
        logger.info(f"  Epochs: {self.config.training.num_train_epochs}")
        logger.info(f"  Optimizer: {self.config.training.optim}")

        # Check for existing checkpoint to resume from
        checkpoint_dir = Path(self.config.training.output_dir)
        last_checkpoint = None
        if checkpoint_dir.exists():
            checkpoints = sorted(checkpoint_dir.glob("checkpoint-*"))
            if checkpoints:
                last_checkpoint = str(checkpoints[-1])
                logger.info(f"Resuming from checkpoint: {last_checkpoint}")

        train_result = self.trainer.train(resume_from_checkpoint=last_checkpoint)

        # Log results
        metrics = train_result.metrics
        logger.info(f"Training complete. Metrics: {metrics}")

        # Save final model
        self.trainer.save_model()
        self.trainer.save_state()

        # Log peak memory
        if torch.cuda.is_available():
            peak_mem = torch.cuda.max_memory_allocated() / 1024**3
            logger.info(f"Peak GPU memory: {peak_mem:.2f} GB")

        return metrics

    def save_model(self, output_path: Optional[str] = None):
        """Save the LoRA adapters (small, ~50-100MB typically)."""
        save_path = output_path or self.config.training.output_dir
        self.model.save_pretrained(save_path)
        self.tokenizer.save_pretrained(save_path)
        logger.info(f"LoRA adapters saved to: {save_path}")

    def merge_and_export(self, output_path: Optional[str] = None):
        """
        Merge LoRA weights back into the base model and export.

        This creates a standalone model that doesn't require PEFT at inference.
        Note: Requires loading the full model in fp16 (~14GB for 7B), so
        needs more memory than training. Do this on a machine with sufficient RAM.
        """
        export_path = output_path or f"{self.config.training.output_dir}/merged"
        logger.info("Merging LoRA adapters into base model...")

        # Clear memory first
        self.memory_manager.clear_memory()

        # Load base model in fp16 (no quantization for merging)
        base_model = AutoModelForCausalLM.from_pretrained(
            self.config.model.model_name_or_path,
            torch_dtype=torch.float16,
            device_map="auto",
            trust_remote_code=self.config.model.trust_remote_code,
            token=self.config.model.hf_token,
        )

        # Load and merge LoRA
        model = PeftModel.from_pretrained(base_model, self.config.training.output_dir)
        merged_model = model.merge_and_unload()

        # Save
        merged_model.save_pretrained(export_path)
        self.tokenizer.save_pretrained(export_path)
        logger.info(f"Merged model exported to: {export_path}")

        # Cleanup
        del base_model, model, merged_model
        self.memory_manager.clear_memory()

    def _estimate_model_size(self) -> float:
        """Estimate model size in billions of parameters from model name."""
        name = self.config.model.model_name_or_path.lower()
        for size in ["405b", "72b", "70b", "34b", "13b", "8b", "7b", "3b", "1.5b", "1b"]:
            if size in name:
                return float(size.rstrip("b"))
        return 7.0  # Default assumption
