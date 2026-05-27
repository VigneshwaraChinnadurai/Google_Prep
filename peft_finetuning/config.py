"""
Configuration for the PEFT fine-tuning pipeline.
All hyperparameters and paths are centralized here.
"""

from dataclasses import dataclass, field
from typing import Optional, List
from enum import Enum


class DataFormat(Enum):
    """Supported enterprise data formats."""
    INSTRUCTION = "instruction"      # {"instruction": ..., "input": ..., "output": ...}
    CONVERSATION = "conversation"    # {"messages": [{"role": ..., "content": ...}]}
    COMPLETION = "completion"        # {"prompt": ..., "completion": ...}
    QA = "qa"                        # {"question": ..., "answer": ...}


class PEFTMethod(Enum):
    """Available PEFT methods."""
    LORA = "lora"
    QLORA = "qlora"       # LoRA + 4-bit quantization (recommended)
    PREFIX_TUNING = "prefix_tuning"
    PROMPT_TUNING = "prompt_tuning"


@dataclass
class ModelConfig:
    """Base model configuration."""
    model_name_or_path: str = "meta-llama/Llama-2-7b-hf"
    torch_dtype: str = "float16"                 # float16, bfloat16, float32
    attn_implementation: str = "sdpa"            # sdpa, flash_attention_2, eager
    trust_remote_code: bool = False
    hf_token: Optional[str] = None               # For gated models like Llama

    # Quantization (for QLoRA)
    load_in_4bit: bool = True
    bnb_4bit_compute_dtype: str = "float16"      # Compute dtype for 4-bit base
    bnb_4bit_quant_type: str = "nf4"             # nf4 or fp4
    bnb_4bit_use_double_quant: bool = True       # Nested quantization (saves ~0.4GB)


@dataclass
class LoRAConfig:
    """LoRA adapter configuration."""
    r: int = 16                          # LoRA rank (higher = more capacity, more memory)
    lora_alpha: int = 32                 # Scaling factor (alpha/r = scaling)
    lora_dropout: float = 0.05           # Dropout for regularization
    bias: str = "none"                   # "none", "all", "lora_only"
    task_type: str = "CAUSAL_LM"

    # Which modules to apply LoRA to
    target_modules: List[str] = field(default_factory=lambda: [
        "q_proj", "k_proj", "v_proj", "o_proj",   # Attention projections
        "gate_proj", "up_proj", "down_proj",       # MLP projections
    ])

    # Advanced: modules to keep in full precision
    modules_to_save: Optional[List[str]] = None    # e.g., ["embed_tokens", "lm_head"]


@dataclass
class DataConfig:
    """Data preprocessing configuration."""
    train_file: str = "data/train.jsonl"
    eval_file: Optional[str] = "data/eval.jsonl"
    data_format: DataFormat = DataFormat.INSTRUCTION

    max_seq_length: int = 2048           # Maximum sequence length
    packing: bool = True                 # Pack multiple samples into one sequence
    num_proc: int = 4                    # Parallel preprocessing workers

    # Train/eval split if no separate eval file
    eval_split_ratio: float = 0.05
    seed: int = 42

    # Streaming for large datasets
    streaming: bool = False              # Use streaming for datasets > RAM

    # Template for instruction format
    system_prompt: str = (
        "You are a helpful enterprise assistant. "
        "Answer questions accurately based on company knowledge."
    )


@dataclass
class TrainingConfig:
    """Training hyperparameters with GPU memory optimization."""
    output_dir: str = "./output/finetuned_model"

    # Core training params
    num_train_epochs: int = 3
    max_steps: int = -1                          # Override epochs if > 0

    # ── Memory Optimization Strategy ──────────────────────────────────
    #
    # GPU MEMORY BUDGET CALCULATOR:
    #   Available VRAM = Total VRAM - OS overhead (~0.5GB)
    #
    #   Memory usage ≈ Model (4-bit) + Adapters + Optimizer + Activations
    #                ≈ params/2B * 1GB + rank * 0.01GB + rank * 0.04GB
    #                  + batch_size * seq_len * hidden_dim * num_layers * 2B / gradient_ckpt_factor
    #
    # Strategy by GPU size:
    #   8GB  GPU: bs=1, grad_accum=16, grad_ckpt=True, max_seq=1024
    #   16GB GPU: bs=2, grad_accum=8,  grad_ckpt=True, max_seq=2048
    #   24GB GPU: bs=4, grad_accum=4,  grad_ckpt=True, max_seq=2048
    #   40GB GPU: bs=8, grad_accum=2,  grad_ckpt=False, max_seq=4096
    #   80GB GPU: bs=16, grad_accum=1, grad_ckpt=False, max_seq=4096

    per_device_train_batch_size: int = 2
    per_device_eval_batch_size: int = 2
    gradient_accumulation_steps: int = 8         # Effective batch = bs * grad_accum = 16
    gradient_checkpointing: bool = True          # Trade compute for memory (~60% savings)

    # Learning rate
    learning_rate: float = 2e-4                  # Higher than full FT since LoRA is low-rank
    lr_scheduler_type: str = "cosine"            # cosine, linear, constant_with_warmup
    warmup_ratio: float = 0.03                   # 3% of total steps

    # Optimizer
    optim: str = "paged_adamw_8bit"              # 8-bit optimizer saves ~50% optimizer memory
    weight_decay: float = 0.01
    max_grad_norm: float = 0.3                   # Gradient clipping

    # Precision
    fp16: bool = False
    bf16: bool = True                            # Use bf16 if GPU supports it (Ampere+)

    # Logging & saving
    logging_steps: int = 10
    save_strategy: str = "steps"
    save_steps: int = 100
    save_total_limit: int = 3                    # Keep only last 3 checkpoints
    eval_strategy: str = "steps"
    eval_steps: int = 100
    load_best_model_at_end: bool = True
    metric_for_best_model: str = "eval_loss"

    # Memory-saving flags
    dataloader_pin_memory: bool = True
    remove_unused_columns: bool = True
    group_by_length: bool = True                 # Minimize padding waste

    # Reproducibility
    seed: int = 42

    # Hub push (optional)
    push_to_hub: bool = False
    hub_model_id: Optional[str] = None


@dataclass
class EvalConfig:
    """Evaluation configuration."""
    eval_samples: int = 100                      # Number of samples for generation eval
    max_new_tokens: int = 256
    temperature: float = 0.1                     # Low temp for deterministic eval
    compute_perplexity: bool = True
    compute_rouge: bool = True
    compute_bleu: bool = False
    custom_eval_fn: Optional[str] = None         # Path to custom evaluation function


@dataclass
class PipelineConfig:
    """Master configuration combining all sub-configs."""
    model: ModelConfig = field(default_factory=ModelConfig)
    lora: LoRAConfig = field(default_factory=LoRAConfig)
    data: DataConfig = field(default_factory=DataConfig)
    training: TrainingConfig = field(default_factory=TrainingConfig)
    evaluation: EvalConfig = field(default_factory=EvalConfig)
    peft_method: PEFTMethod = PEFTMethod.QLORA
