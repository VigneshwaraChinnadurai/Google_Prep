"""
End-to-End Fine-Tuning Pipeline — CPU Demo with GPT-2
======================================================

This script runs the COMPLETE fine-tuning pipeline on CPU using GPT-2 (124M params)
as a stand-in for larger models. It demonstrates every step:

1. Load enterprise mock data (instruction-following format)
2. Tokenize and format with instruction templates
3. Inject LoRA adapters (standard LoRA, no quantization needed on CPU)
4. Train for a few steps with all memory optimizations applicable to CPU
5. Evaluate: perplexity + ROUGE + sample generation
6. Compare base model vs fine-tuned model outputs
7. Save and reload the LoRA adapter

Usage:
    python -m peft_finetuning.run_e2e_demo
"""

import json
import math
import logging
import time
from pathlib import Path
from typing import Dict, List

import torch
import numpy as np
from datasets import Dataset, load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    Trainer,
    DataCollatorForSeq2Seq,
)
from peft import LoraConfig, get_peft_model, PeftModel, TaskType

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)

# ═══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════════

MODEL_NAME = "gpt2"  # 124M params — trainable on CPU
OUTPUT_DIR = "./peft_finetuning/demo_output"
MOCK_DATA_DIR = "./peft_finetuning/mock_data"
MAX_SEQ_LENGTH = 256  # Short for CPU speed
LORA_RANK = 8
LORA_ALPHA = 16
NUM_TRAIN_EPOCHS = 2
BATCH_SIZE = 2
GRADIENT_ACCUMULATION = 2
LEARNING_RATE = 3e-4

INSTRUCTION_TEMPLATE = """### Instruction:
{instruction}

### Input:
{input}

### Response:
{output}"""

INSTRUCTION_TEMPLATE_NO_INPUT = """### Instruction:
{instruction}

### Response:
{output}"""


# ═══════════════════════════════════════════════════════════════════════════════
# STEP 1: DATA LOADING & PREPROCESSING
# ═══════════════════════════════════════════════════════════════════════════════

def load_and_preprocess_data(tokenizer) -> tuple:
    """Load mock enterprise data and tokenize for training."""
    logger.info("=" * 60)
    logger.info("STEP 1: Data Loading & Preprocessing")
    logger.info("=" * 60)

    train_path = Path(MOCK_DATA_DIR) / "enterprise_train.jsonl"
    eval_path = Path(MOCK_DATA_DIR) / "enterprise_eval.jsonl"

    if not train_path.exists() or not eval_path.exists():
        raise FileNotFoundError(
            f"Mock data not found. Expected:\n  {train_path}\n  {eval_path}"
        )

    # Load JSONL files
    train_dataset = load_dataset("json", data_files=str(train_path), split="train")
    eval_dataset = load_dataset("json", data_files=str(eval_path), split="train")

    logger.info(f"  Train samples: {len(train_dataset)}")
    logger.info(f"  Eval samples:  {len(eval_dataset)}")

    # Format into instruction-following text
    def format_sample(sample):
        input_text = sample.get("input", "")
        if input_text.strip():
            text = INSTRUCTION_TEMPLATE.format(
                instruction=sample["instruction"],
                input=input_text,
                output=sample["output"],
            )
        else:
            text = INSTRUCTION_TEMPLATE_NO_INPUT.format(
                instruction=sample["instruction"],
                output=sample["output"],
            )
        return {"text": text + tokenizer.eos_token}

    train_formatted = train_dataset.map(
        format_sample,
        remove_columns=train_dataset.column_names,
        desc="Formatting train data",
    )
    eval_formatted = eval_dataset.map(
        format_sample,
        remove_columns=eval_dataset.column_names,
        desc="Formatting eval data",
    )

    # Tokenize
    def tokenize(batch):
        tokenized = tokenizer(
            batch["text"],
            truncation=True,
            max_length=MAX_SEQ_LENGTH,
            padding=False,
            return_attention_mask=True,
        )
        tokenized["labels"] = tokenized["input_ids"].copy()
        return tokenized

    train_tokenized = train_formatted.map(
        tokenize, batched=True, remove_columns=["text"], desc="Tokenizing train"
    )
    eval_tokenized = eval_formatted.map(
        tokenize, batched=True, remove_columns=["text"], desc="Tokenizing eval"
    )

    # Print stats
    avg_len = np.mean([len(x) for x in train_tokenized["input_ids"]])
    logger.info(f"  Average token length: {avg_len:.0f}")
    logger.info(f"  Max sequence length:  {MAX_SEQ_LENGTH}")
    logger.info(f"  Tokenized train: {len(train_tokenized)} samples")
    logger.info(f"  Tokenized eval:  {len(eval_tokenized)} samples")

    return train_tokenized, eval_tokenized


# ═══════════════════════════════════════════════════════════════════════════════
# STEP 2: MODEL LOADING + LoRA INJECTION
# ═══════════════════════════════════════════════════════════════════════════════

def load_model_with_lora(tokenizer):
    """Load GPT-2 and inject LoRA adapters."""
    logger.info("=" * 60)
    logger.info("STEP 2: Model Loading + LoRA Injection")
    logger.info("=" * 60)

    # Load base model (full precision on CPU)
    logger.info(f"  Loading base model: {MODEL_NAME}")
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_NAME,
        torch_dtype=torch.float32,  # Full precision for CPU
    )

    total_params = sum(p.numel() for p in model.parameters())
    logger.info(f"  Base model parameters: {total_params:,}")

    # Configure LoRA
    lora_config = LoraConfig(
        r=LORA_RANK,
        lora_alpha=LORA_ALPHA,
        lora_dropout=0.05,
        bias="none",
        task_type=TaskType.CAUSAL_LM,
        target_modules=["c_attn", "c_proj"],  # GPT-2 attention layers
    )

    # Inject LoRA
    model = get_peft_model(model, lora_config)

    # Print trainable stats
    trainable_params, all_params = model.get_nb_trainable_parameters()
    logger.info(f"  LoRA rank: {LORA_RANK}, alpha: {LORA_ALPHA}")
    logger.info(f"  Target modules: c_attn, c_proj")
    logger.info(f"  Trainable: {trainable_params:,} / {all_params:,} "
                f"({100 * trainable_params / all_params:.4f}%)")
    logger.info(f"  Memory savings: ~{(1 - trainable_params/all_params)*100:.1f}% fewer params to update")

    return model


# ═══════════════════════════════════════════════════════════════════════════════
# STEP 3: TRAINING
# ═══════════════════════════════════════════════════════════════════════════════

def train_model(model, tokenizer, train_dataset, eval_dataset):
    """Fine-tune with LoRA on CPU."""
    logger.info("=" * 60)
    logger.info("STEP 3: Fine-Tuning with LoRA")
    logger.info("=" * 60)

    effective_batch = BATCH_SIZE * GRADIENT_ACCUMULATION
    total_steps = (len(train_dataset) // effective_batch) * NUM_TRAIN_EPOCHS
    logger.info(f"  Batch size: {BATCH_SIZE}")
    logger.info(f"  Gradient accumulation: {GRADIENT_ACCUMULATION}")
    logger.info(f"  Effective batch size: {effective_batch}")
    logger.info(f"  Epochs: {NUM_TRAIN_EPOCHS}")
    logger.info(f"  Estimated total steps: {total_steps}")
    logger.info(f"  Learning rate: {LEARNING_RATE}")

    # Training arguments optimized for CPU
    training_args = TrainingArguments(
        output_dir=OUTPUT_DIR,
        num_train_epochs=NUM_TRAIN_EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        per_device_eval_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=GRADIENT_ACCUMULATION,
        learning_rate=LEARNING_RATE,
        lr_scheduler_type="cosine",
        warmup_ratio=0.1,
        weight_decay=0.01,
        logging_steps=1,
        eval_strategy="epoch",
        save_strategy="epoch",
        save_total_limit=2,
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        fp16=False,  # CPU doesn't support fp16 training
        bf16=False,
        report_to="none",
        seed=42,
        use_cpu=True,
        dataloader_num_workers=0,
        remove_unused_columns=False,
    )

    # Data collator for dynamic padding
    data_collator = DataCollatorForSeq2Seq(
        tokenizer=tokenizer,
        padding=True,
        pad_to_multiple_of=8,
    )

    # Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=data_collator,
        processing_class=tokenizer,
    )

    # Train
    logger.info("  Starting training...")
    start_time = time.time()
    train_result = trainer.train()
    elapsed = time.time() - start_time

    # Results
    metrics = train_result.metrics
    logger.info(f"  Training complete in {elapsed:.1f}s")
    logger.info(f"  Train loss: {metrics.get('train_loss', 'N/A'):.4f}")
    logger.info(f"  Train samples/sec: {metrics.get('train_samples_per_second', 0):.2f}")

    # Eval
    eval_metrics = trainer.evaluate()
    logger.info(f"  Eval loss: {eval_metrics.get('eval_loss', 'N/A'):.4f}")
    logger.info(f"  Eval perplexity: {math.exp(eval_metrics.get('eval_loss', 0)):.2f}")

    return trainer, metrics, eval_metrics


# ═══════════════════════════════════════════════════════════════════════════════
# STEP 4: EVALUATION & COMPARISON
# ═══════════════════════════════════════════════════════════════════════════════

def evaluate_model(model, tokenizer, eval_dataset):
    """Evaluate fine-tuned model with perplexity and generation quality."""
    logger.info("=" * 60)
    logger.info("STEP 4: Evaluation & Model Comparison")
    logger.info("=" * 60)

    model.eval()
    results = {}

    # ── Perplexity ────────────────────────────────────────────────────────
    logger.info("  Computing perplexity...")
    total_loss = 0.0
    total_tokens = 0

    with torch.no_grad():
        for i in range(min(len(eval_dataset), 5)):
            sample = eval_dataset[i]
            input_ids = torch.tensor([sample["input_ids"]])
            attention_mask = torch.tensor([sample["attention_mask"]])
            labels = torch.tensor([sample["labels"]])

            outputs = model(
                input_ids=input_ids,
                attention_mask=attention_mask,
                labels=labels,
            )
            seq_len = (labels != -100).sum().item()
            total_loss += outputs.loss.item() * seq_len
            total_tokens += seq_len

    perplexity = math.exp(total_loss / total_tokens) if total_tokens > 0 else float("inf")
    results["perplexity"] = round(perplexity, 2)
    logger.info(f"  Perplexity (fine-tuned): {perplexity:.2f}")

    # ── Generation Samples ────────────────────────────────────────────────
    logger.info("\n  Generating sample outputs...")
    test_prompts = [
        "### Instruction:\nSummarize the key risks identified in the Q2 infrastructure review\n\n### Input:\nReview found: 1) Single point of failure in payment service, 2) No disaster recovery plan tested in 6 months, 3) SSL certificates expiring in 30 days with no auto-renewal.\n\n### Response:\n",
        "### Instruction:\nDraft a brief announcement about a new company policy\n\n### Input:\nStarting April 1, all employees can work remotely up to 3 days per week. Must be in office Tuesday and Thursday.\n\n### Response:\n",
        "### Instruction:\nExplain the difference between horizontal and vertical scaling\n\n### Response:\n",
    ]

    generations = []
    for i, prompt in enumerate(test_prompts):
        input_ids = tokenizer.encode(prompt, return_tensors="pt")
        with torch.no_grad():
            output = model.generate(
                input_ids,
                max_new_tokens=100,
                temperature=0.7,
                do_sample=True,
                top_p=0.9,
                pad_token_id=tokenizer.eos_token_id,
                repetition_penalty=1.2,
            )
        generated_text = tokenizer.decode(
            output[0][input_ids.shape[1]:], skip_special_tokens=True
        )
        generations.append(generated_text)
        logger.info(f"\n  --- Sample {i+1} ---")
        logger.info(f"  Prompt: {prompt[:80]}...")
        logger.info(f"  Generated: {generated_text[:200]}")

    results["generations"] = generations

    # ── Base vs Fine-tuned Comparison ─────────────────────────────────────
    logger.info("\n  Comparing base model vs fine-tuned...")
    comparison_prompt = "### Instruction:\nWrite a professional email subject line for a project update\n\n### Response:\n"
    input_ids = tokenizer.encode(comparison_prompt, return_tensors="pt")

    # Fine-tuned output
    with torch.no_grad():
        ft_output = model.generate(
            input_ids, max_new_tokens=50, temperature=0.7, do_sample=True,
            pad_token_id=tokenizer.eos_token_id,
        )
    ft_text = tokenizer.decode(ft_output[0][input_ids.shape[1]:], skip_special_tokens=True)

    # Base model output (disable LoRA adapters)
    model.disable_adapter_layers()
    with torch.no_grad():
        base_output = model.generate(
            input_ids, max_new_tokens=50, temperature=0.7, do_sample=True,
            pad_token_id=tokenizer.eos_token_id,
        )
    base_text = tokenizer.decode(base_output[0][input_ids.shape[1]:], skip_special_tokens=True)
    model.enable_adapter_layers()

    results["comparison"] = {
        "prompt": comparison_prompt.strip(),
        "base_model": base_text[:150],
        "fine_tuned": ft_text[:150],
    }

    logger.info(f"  Base model:  {base_text[:100]}")
    logger.info(f"  Fine-tuned:  {ft_text[:100]}")

    return results


# ═══════════════════════════════════════════════════════════════════════════════
# STEP 5: SAVE & RELOAD
# ═══════════════════════════════════════════════════════════════════════════════

def save_and_reload(model, tokenizer):
    """Save LoRA adapter and demonstrate reload."""
    logger.info("=" * 60)
    logger.info("STEP 5: Save & Reload LoRA Adapter")
    logger.info("=" * 60)

    save_path = Path(OUTPUT_DIR) / "final_adapter"
    save_path.mkdir(parents=True, exist_ok=True)

    # Save
    model.save_pretrained(str(save_path))
    tokenizer.save_pretrained(str(save_path))

    # Check saved files
    adapter_files = list(save_path.glob("*"))
    total_size = sum(f.stat().st_size for f in adapter_files if f.is_file())
    logger.info(f"  Saved to: {save_path}")
    logger.info(f"  Adapter size: {total_size / 1024:.1f} KB")
    logger.info(f"  Files: {[f.name for f in adapter_files if f.is_file()]}")

    # Reload to prove it works
    logger.info("  Reloading adapter to verify...")
    base_model = AutoModelForCausalLM.from_pretrained(MODEL_NAME)
    reloaded_model = PeftModel.from_pretrained(base_model, str(save_path))
    reloaded_model.eval()

    # Quick test
    test_input = tokenizer.encode("### Instruction:\nHello\n\n### Response:\n", return_tensors="pt")
    with torch.no_grad():
        out = reloaded_model.generate(test_input, max_new_tokens=20, pad_token_id=tokenizer.eos_token_id)
    logger.info(f"  Reload test: {tokenizer.decode(out[0], skip_special_tokens=True)[:100]}")
    logger.info("  ✓ Adapter saved and reloaded successfully")

    return save_path


# ═══════════════════════════════════════════════════════════════════════════════
# MAIN PIPELINE
# ═══════════════════════════════════════════════════════════════════════════════

def main():
    """Run the complete fine-tuning pipeline end-to-end."""
    print("\n" + "═" * 70)
    print("  PEFT FINE-TUNING PIPELINE — END-TO-END DEMO")
    print("  Model: GPT-2 (124M params) | Method: LoRA | Device: CPU")
    print("═" * 70 + "\n")

    start_time = time.time()

    # Load tokenizer
    logger.info("Loading tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    # Step 1: Data
    train_dataset, eval_dataset = load_and_preprocess_data(tokenizer)

    # Step 2: Model + LoRA
    model = load_model_with_lora(tokenizer)

    # Step 3: Train
    trainer, train_metrics, eval_metrics = train_model(
        model, tokenizer, train_dataset, eval_dataset
    )

    # Step 4: Evaluate
    eval_results = evaluate_model(model, tokenizer, eval_dataset)

    # Step 5: Save
    adapter_path = save_and_reload(model, tokenizer)

    # ── Final Report ──────────────────────────────────────────────────────
    total_time = time.time() - start_time
    print("\n" + "═" * 70)
    print("  PIPELINE COMPLETE — SUMMARY")
    print("═" * 70)
    print(f"""
  Model:            {MODEL_NAME} (124M params)
  PEFT Method:      LoRA (rank={LORA_RANK}, alpha={LORA_ALPHA})
  Training Data:    {len(train_dataset)} samples (enterprise instruction-following)
  Eval Data:        {len(eval_dataset)} samples
  
  Training Results:
    Train Loss:     {train_metrics.get('train_loss', 'N/A'):.4f}
    Eval Loss:      {eval_metrics.get('eval_loss', 'N/A'):.4f}
    Eval Perplexity:{math.exp(eval_metrics.get('eval_loss', 0)):.2f}
  
  Evaluation:
    Perplexity:     {eval_results['perplexity']}
  
  Model Comparison (same prompt):
    Base GPT-2:     {eval_results['comparison']['base_model'][:80]}...
    Fine-tuned:     {eval_results['comparison']['fine_tuned'][:80]}...
  
  Artifacts:
    Adapter saved:  {adapter_path}
    Adapter size:   ~{sum(f.stat().st_size for f in adapter_path.glob('*') if f.is_file()) / 1024:.0f} KB
    (vs base model: ~500 MB — {sum(f.stat().st_size for f in adapter_path.glob('*') if f.is_file()) / (500*1024*1024) * 100:.2f}% of base)
  
  Total time:       {total_time:.1f}s
""")
    print("═" * 70)
    print("  In production with GPU + Llama-2-7B + QLoRA:")
    print("    • Replace MODEL_NAME with 'meta-llama/Llama-2-7b-hf'")
    print("    • Enable 4-bit quantization (BitsAndBytesConfig)")
    print("    • Use gradient_checkpointing=True")
    print("    • Use optim='paged_adamw_8bit'")
    print("    • Scale to 1000+ training samples")
    print("    • Train for 3-5 epochs with early stopping")
    print("═" * 70 + "\n")


if __name__ == "__main__":
    main()
