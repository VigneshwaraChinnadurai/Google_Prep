"""
Main Runner: Enterprise LLM Fine-Tuning Pipeline
==================================================

End-to-end pipeline orchestrator that ties together:
1. Data preprocessing
2. Model loading + LoRA injection
3. Training with memory optimizations
4. Evaluation and reporting

Usage:
    # Full pipeline with default config (Llama-2-7B, QLoRA)
    python -m peft_finetuning.run

    # With custom data
    python -m peft_finetuning.run --train-file data/enterprise_qa.jsonl --format qa

    # With a different model
    python -m peft_finetuning.run --model mistralai/Mistral-7B-Instruct-v0.2

    # Memory-constrained (8GB GPU)
    python -m peft_finetuning.run --batch-size 1 --grad-accum 16 --max-seq-length 1024

    # Generate sample data and run demo
    python -m peft_finetuning.run --demo
"""

import argparse
import json
import logging
import sys
from pathlib import Path

from .config import (
    PipelineConfig,
    ModelConfig,
    LoRAConfig,
    DataConfig,
    TrainingConfig,
    EvalConfig,
    DataFormat,
    PEFTMethod,
)
from .data_preprocessing import DataPreprocessor, generate_sample_data
from .finetuning_engine import FineTuningEngine, GPUMemoryManager
from .evaluation import ModelEvaluator, print_evaluation_report

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def build_config_from_args(args) -> PipelineConfig:
    """Build pipeline configuration from CLI arguments."""
    config = PipelineConfig()

    # Model config
    if args.model:
        config.model.model_name_or_path = args.model
    if args.hf_token:
        config.model.hf_token = args.hf_token

    # Data config
    if args.train_file:
        config.data.train_file = args.train_file
    if args.eval_file:
        config.data.eval_file = args.eval_file
    if args.format:
        config.data.data_format = DataFormat(args.format)
    if args.max_seq_length:
        config.data.max_seq_length = args.max_seq_length

    # Training config (memory optimization)
    if args.batch_size:
        config.training.per_device_train_batch_size = args.batch_size
    if args.grad_accum:
        config.training.gradient_accumulation_steps = args.grad_accum
    if args.epochs:
        config.training.num_train_epochs = args.epochs
    if args.lr:
        config.training.learning_rate = args.lr
    if args.output_dir:
        config.training.output_dir = args.output_dir

    # LoRA config
    if args.lora_rank:
        config.lora.r = args.lora_rank
        config.lora.lora_alpha = args.lora_rank * 2  # Common heuristic

    # PEFT method
    if args.no_quantize:
        config.peft_method = PEFTMethod.LORA
        config.model.load_in_4bit = False

    return config


def run_demo():
    """
    Run a complete demo pipeline with synthetic data.

    This demonstrates the full workflow without requiring real enterprise data
    or large GPU resources. Uses a small sample to validate the pipeline.
    """
    print("\n" + "=" * 70)
    print("  ENTERPRISE LLM FINE-TUNING DEMO (QLoRA)")
    print("=" * 70)

    # Step 1: Generate sample enterprise data
    print("\n[1/5] Generating sample enterprise data...")
    data_dir = Path("./demo_data")
    data_dir.mkdir(exist_ok=True)

    train_path = generate_sample_data(
        str(data_dir / "train.jsonl"), num_samples=50, format=DataFormat.INSTRUCTION
    )
    eval_path = generate_sample_data(
        str(data_dir / "eval.jsonl"), num_samples=10, format=DataFormat.INSTRUCTION
    )
    print(f"   ✓ Train: {train_path} (50 samples)")
    print(f"   ✓ Eval:  {eval_path} (10 samples)")

    # Step 2: Configure for demo (small model, minimal resources)
    print("\n[2/5] Configuring pipeline...")
    config = PipelineConfig(
        model=ModelConfig(
            model_name_or_path="meta-llama/Llama-2-7b-hf",  # Change to available model
            load_in_4bit=True,
        ),
        lora=LoRAConfig(r=8, lora_alpha=16),  # Smaller rank for demo
        data=DataConfig(
            train_file=str(train_path),
            eval_file=str(eval_path),
            data_format=DataFormat.INSTRUCTION,
            max_seq_length=512,
            packing=False,
        ),
        training=TrainingConfig(
            output_dir="./demo_output",
            num_train_epochs=1,
            per_device_train_batch_size=1,
            gradient_accumulation_steps=4,
            gradient_checkpointing=True,
            logging_steps=5,
            save_steps=50,
            eval_steps=25,
        ),
    )

    # Print memory estimate
    mem = GPUMemoryManager.estimate_memory_requirements(
        model_params_billions=7.0,
        lora_rank=config.lora.r,
        batch_size=config.training.per_device_train_batch_size,
        seq_length=config.data.max_seq_length,
        gradient_checkpointing=True,
    )
    print(f"   Model: {config.model.model_name_or_path}")
    print(f"   LoRA rank: {config.lora.r}")
    print(f"   Estimated GPU memory: {mem['total_estimated_gb']} GB")
    print(f"   Recommended GPU: ≥ {mem['recommended_gpu_gb']} GB")

    # Step 3: Preprocess data
    print("\n[3/5] Preprocessing enterprise data...")
    preprocessor = DataPreprocessor(config.model, config.data)
    train_dataset, eval_dataset = preprocessor.prepare_datasets()
    print(f"   ✓ Train samples: {len(train_dataset)}")
    print(f"   ✓ Eval samples: {len(eval_dataset)}")

    # Step 4: Fine-tune
    print("\n[4/5] Fine-tuning with QLoRA...")
    print("   (This requires a GPU with sufficient VRAM)")
    print("   In production, this step takes 30min-4hrs depending on data size")

    gpu_info = GPUMemoryManager.get_gpu_info()
    if not gpu_info["available"]:
        print("\n   ⚠ No GPU detected. Skipping actual training.")
        print("   To run training, use a machine with an NVIDIA GPU (8GB+ VRAM).")
        print("   Alternatively, use cloud: Google Colab, RunPod, Lambda Labs, etc.")
    else:
        engine = FineTuningEngine(config)
        engine.load_model()
        metrics = engine.train(train_dataset, eval_dataset)
        engine.save_model()

        # Step 5: Evaluate
        print("\n[5/5] Evaluating fine-tuned model...")
        evaluator = ModelEvaluator(config)
        evaluator.load_finetuned_model()
        results = evaluator.evaluate_all(eval_dataset)
        comparisons = evaluator.compare_base_vs_finetuned(eval_dataset, num_samples=3)
        print_evaluation_report(results, comparisons)

    print("\n" + "=" * 70)
    print("  DEMO COMPLETE")
    print("=" * 70)
    print("\nNext steps:")
    print("  1. Replace demo data with your enterprise dataset")
    print("  2. Adjust config for your GPU (see config.py comments)")
    print("  3. Run full training: python -m peft_finetuning.run --train-file your_data.jsonl")
    print()


def run_pipeline(config: PipelineConfig):
    """Run the full fine-tuning pipeline."""

    # ── Phase 1: Data Preprocessing ───────────────────────────────────────────
    logger.info("=" * 50)
    logger.info("PHASE 1: Data Preprocessing")
    logger.info("=" * 50)

    preprocessor = DataPreprocessor(config.model, config.data)
    train_dataset, eval_dataset = preprocessor.prepare_datasets()

    # ── Phase 2: Fine-Tuning ──────────────────────────────────────────────────
    logger.info("=" * 50)
    logger.info("PHASE 2: Fine-Tuning")
    logger.info("=" * 50)

    engine = FineTuningEngine(config)
    engine.load_model()
    metrics = engine.train(train_dataset, eval_dataset)
    engine.save_model()

    # ── Phase 3: Evaluation ───────────────────────────────────────────────────
    logger.info("=" * 50)
    logger.info("PHASE 3: Evaluation")
    logger.info("=" * 50)

    evaluator = ModelEvaluator(config)
    evaluator.load_finetuned_model()
    results = evaluator.evaluate_all(eval_dataset)
    comparisons = evaluator.compare_base_vs_finetuned(eval_dataset, num_samples=5)

    # Print report
    print_evaluation_report(results, comparisons)

    # Save results
    output_path = Path(config.training.output_dir) / "eval_results.json"
    serializable = {k: v for k, v in results.items() if k != "sample_outputs"}
    serializable["sample_outputs"] = results.get("sample_outputs", [])
    with open(output_path, "w") as f:
        json.dump(serializable, f, indent=2, default=str)
    logger.info(f"Results saved to: {output_path}")

    return results


def main():
    parser = argparse.ArgumentParser(
        description="Enterprise LLM Fine-Tuning with QLoRA",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Run demo with synthetic data
  python -m peft_finetuning.run --demo

  # Fine-tune on enterprise data
  python -m peft_finetuning.run --train-file data/train.jsonl --format instruction

  # Memory-constrained setup (8GB GPU)
  python -m peft_finetuning.run --train-file data/train.jsonl --batch-size 1 --grad-accum 16 --max-seq-length 1024

  # Use Mistral instead of Llama
  python -m peft_finetuning.run --model mistralai/Mistral-7B-Instruct-v0.2 --train-file data/train.jsonl
        """,
    )

    # Mode
    parser.add_argument("--demo", action="store_true", help="Run demo with synthetic data")

    # Model
    parser.add_argument("--model", type=str, help="HuggingFace model ID")
    parser.add_argument("--hf-token", type=str, help="HuggingFace token for gated models")
    parser.add_argument("--no-quantize", action="store_true", help="Disable 4-bit quantization (uses more memory)")

    # Data
    parser.add_argument("--train-file", type=str, help="Path to training data (JSONL/JSON/CSV/Parquet)")
    parser.add_argument("--eval-file", type=str, help="Path to evaluation data")
    parser.add_argument("--format", choices=["instruction", "conversation", "qa", "completion"], help="Data format")
    parser.add_argument("--max-seq-length", type=int, help="Maximum sequence length")

    # Training (memory optimization levers)
    parser.add_argument("--batch-size", type=int, help="Per-device batch size")
    parser.add_argument("--grad-accum", type=int, help="Gradient accumulation steps")
    parser.add_argument("--epochs", type=int, help="Number of training epochs")
    parser.add_argument("--lr", type=float, help="Learning rate")
    parser.add_argument("--lora-rank", type=int, help="LoRA rank (higher = more capacity, more memory)")
    parser.add_argument("--output-dir", type=str, help="Output directory for checkpoints")

    args = parser.parse_args()

    if args.demo:
        run_demo()
    elif args.train_file:
        config = build_config_from_args(args)
        run_pipeline(config)
    else:
        parser.print_help()
        print("\n⚠ Specify --demo for a demo run or --train-file for real training.")
        sys.exit(1)


if __name__ == "__main__":
    main()
