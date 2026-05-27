# Enterprise LLM Fine-Tuning with PEFT (QLoRA)

## Overview

A production-ready Python module for fine-tuning open-source LLMs on enterprise-specific data using **QLoRA** (Quantized Low-Rank Adaptation) — the most memory-efficient fine-tuning method available.

**Fine-tune a 7B parameter model on a single 8GB GPU.**

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Memory Comparison: Full Fine-Tuning vs QLoRA                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Full Fine-Tuning (7B model):        QLoRA Fine-Tuning (7B model):     │
│  ┌─────────────────────────────┐     ┌─────────────────────────────┐   │
│  │ Model (FP16):     14.0 GB   │     │ Model (4-bit):     3.5 GB   │   │
│  │ Optimizer:        28.0 GB   │     │ LoRA adapters:     0.06 GB  │   │
│  │ Activations:      12.0 GB   │     │ Optimizer (8-bit): 0.12 GB  │   │
│  │ Gradients:        14.0 GB   │     │ Activations:       1.9 GB   │   │
│  │                             │     │ (w/ grad checkpoint)         │   │
│  ├─────────────────────────────┤     ├─────────────────────────────┤   │
│  │ TOTAL:            ~70 GB    │     │ TOTAL:             ~6.6 GB  │   │
│  │ Needs: A100 80GB ($15K)     │     │ Needs: RTX 3060 8GB ($300)  │   │
│  └─────────────────────────────┘     └─────────────────────────────┘   │
│                                                                         │
│  Trainable params: 7B (100%)         Trainable params: 33M (0.5%)      │
│  Quality loss: None                  Quality loss: ~1-2% (negligible)  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Module Structure

```
peft_finetuning/
├── __init__.py              # Package docs and architecture diagram
├── __main__.py              # Entry point (python -m peft_finetuning)
├── config.py                # All hyperparameters and configuration
├── data_preprocessing.py    # Enterprise data → tokenized dataset
├── finetuning_engine.py     # QLoRA model loading + training loop
├── evaluation.py            # Perplexity, ROUGE, generation quality
├── memory_strategies.py     # Detailed GPU memory optimization reference
├── run.py                   # CLI orchestrator
└── requirements.txt         # Dependencies
```

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Run demo (generates sample data, shows full pipeline)
python -m peft_finetuning --demo

# Fine-tune on your enterprise data
python -m peft_finetuning --train-file data/enterprise.jsonl --format instruction

# Memory-constrained (8GB GPU)
python -m peft_finetuning --train-file data/train.jsonl --batch-size 1 --grad-accum 16 --max-seq-length 1024
```

## Supported Data Formats

| Format | Fields | Use Case |
|--------|--------|----------|
| `instruction` | `instruction`, `input`, `output` | Task completion, following commands |
| `conversation` | `messages: [{role, content}]` | Multi-turn chat, customer service |
| `qa` | `question`, `answer` | Knowledge bases, FAQs |
| `completion` | `prompt`, `completion` | Document generation, templates |

### Example Data (JSONL)

```json
{"instruction": "Summarize the Q3 earnings report", "input": "Revenue: $4.2M...", "output": "Q3 revenue reached $4.2M, a 15% YoY increase..."}
{"instruction": "Draft a response to the customer complaint", "input": "Order #12345 delayed", "output": "Dear Customer, We apologize..."}
```

## GPU Memory Strategy by Hardware

| GPU | VRAM | Config | Effective Batch |
|-----|------|--------|-----------------|
| RTX 3060 / 4060 | 8 GB | `--batch-size 1 --grad-accum 16 --max-seq-length 1024` | 16 |
| RTX 3080 / 4070 | 12-16 GB | `--batch-size 2 --grad-accum 8 --max-seq-length 2048` | 16 |
| RTX 3090 / 4090 | 24 GB | `--batch-size 4 --grad-accum 4 --max-seq-length 2048` | 16 |
| A100 / H100 | 40-80 GB | `--batch-size 8 --grad-accum 2 --max-seq-length 4096` | 16 |

## Key Memory Optimizations Explained

1. **4-bit Quantization (NF4)**: Model weights stored in 4 bits → 14GB → 3.5GB
2. **LoRA Adapters**: Only train 0.5% of parameters → optimizer memory: 28GB → 0.12GB
3. **Gradient Checkpointing**: Recompute activations in backward → 60% activation savings
4. **Paged 8-bit Adam**: Optimizer in 8-bit with CPU paging → halves optimizer memory
5. **Gradient Accumulation**: Large effective batch without proportional memory
6. **Sequence Packing**: Eliminate padding waste → process more data per batch
7. **Mixed Precision (BF16)**: All computation in 16-bit → halves activation memory

## Programmatic Usage

```python
from peft_finetuning.config import PipelineConfig, ModelConfig, DataConfig, TrainingConfig
from peft_finetuning.data_preprocessing import DataPreprocessor
from peft_finetuning.finetuning_engine import FineTuningEngine
from peft_finetuning.evaluation import ModelEvaluator

# Configure
config = PipelineConfig(
    model=ModelConfig(model_name_or_path="meta-llama/Llama-2-7b-hf"),
    data=DataConfig(train_file="data/train.jsonl"),
    training=TrainingConfig(
        per_device_train_batch_size=2,
        gradient_accumulation_steps=8,
        gradient_checkpointing=True,
        num_train_epochs=3,
    ),
)

# Preprocess
preprocessor = DataPreprocessor(config.model, config.data)
train_dataset, eval_dataset = preprocessor.prepare_datasets()

# Train
engine = FineTuningEngine(config)
engine.load_model()
engine.train(train_dataset, eval_dataset)
engine.save_model()

# Merge adapters into standalone model (optional)
engine.merge_and_export("./final_model")

# Evaluate
evaluator = ModelEvaluator(config)
evaluator.load_finetuned_model()
results = evaluator.evaluate_all(eval_dataset)
```

## Evaluation Metrics

| Metric | What It Measures | Good Score |
|--------|-----------------|------------|
| **Perplexity** | How well model predicts text | Lower is better (< 5 for domain-specific) |
| **ROUGE-1** | Unigram overlap with reference | > 0.4 for summarization tasks |
| **ROUGE-L** | Longest common subsequence | > 0.35 for generation tasks |
| **Base vs FT Comparison** | Qualitative improvement | Should show domain knowledge |

## End-to-End Demo (Runs on CPU — No GPU Required)

A complete working demo that runs the full pipeline on CPU using GPT-2 (124M params) with enterprise mock data:

```bash
python -m peft_finetuning.run_e2e_demo
```

### What It Does

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    END-TO-END PIPELINE FLOW (CPU Demo)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────────┐  │
│  │  Mock Data   │───▶│  Tokenize &  │───▶│  GPT-2 + LoRA Injection     │  │
│  │  (21 train,  │    │  Format      │    │  (124M params, 0.65% train) │  │
│  │   5 eval)    │    │  (256 tokens)│    │  Target: c_attn, c_proj     │  │
│  └──────────────┘    └──────────────┘    └──────────────┬───────────────┘  │
│                                                          │                  │
│                                                          ▼                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────────┐  │
│  │  Save LoRA   │◀───│  Evaluate    │◀───│  Train (2 epochs, ~72s)     │  │
│  │  Adapter     │    │  Perplexity  │    │  Cosine LR, warmup 10%     │  │
│  │  (~8 MB)     │    │  + Generate  │    │  Batch: 2 × 2 accum = 4    │  │
│  └──────────────┘    │  + Compare   │    └──────────────────────────────┘  │
│                      └──────────────┘                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Demo Output Summary

| Metric | Value |
|--------|-------|
| Model | GPT-2 (124M params) |
| LoRA Config | rank=8, alpha=16, target: c_attn + c_proj |
| Trainable params | 811,008 / 125M (0.65%) |
| Train loss | 4.17 → 3.89 (improving) |
| Eval perplexity | ~49 |
| Adapter size | ~8 MB (1.5% of base model) |
| Total time (CPU) | ~150 seconds |

### Mock Enterprise Data

The demo includes 26 rich enterprise training samples in `mock_data/` covering:

| Category | Sample Topics |
|----------|--------------|
| **Executive Communication** | Quarterly reports, competitive analysis, release notes |
| **Technical Documentation** | API docs, design RFCs, Terraform modules, architecture proposals |
| **Engineering Operations** | Incident post-mortems, runbooks, CI/CD FAQs, K8s troubleshooting |
| **People & Process** | Performance reviews, sprint retros, onboarding checklists, interview rubrics |
| **Security & Compliance** | GDPR policies, incident response playbooks, data models |
| **Planning** | Capacity planning, tech debt assessment, migration plans |

### File Structure

```
peft_finetuning/
├── run_e2e_demo.py              # Complete pipeline runner (CPU-compatible)
├── mock_data/
│   ├── enterprise_train.jsonl   # 21 training samples (instruction format)
│   └── enterprise_eval.jsonl    # 5 evaluation samples
└── demo_output/                 # Generated after running
    ├── final_adapter/
    │   ├── adapter_config.json
    │   ├── adapter_model.safetensors
    │   └── tokenizer files...
    └── checkpoint-*/            # Training checkpoints
```

### Scaling from Demo to Production

| Aspect | Demo (CPU) | Production (GPU) |
|--------|-----------|-----------------|
| Model | GPT-2 (124M) | Llama-2-7B / Mistral-7B |
| Quantization | None (float32) | 4-bit NF4 (QLoRA) |
| Data | 21 samples | 1,000–100,000 samples |
| Sequence length | 256 tokens | 2048–4096 tokens |
| Training time | ~150s | 30min–4hrs |
| Hardware | Any CPU | 8GB+ NVIDIA GPU |
| Optimizer | AdamW | Paged AdamW 8-bit |
| Grad checkpoint | Off | On (saves 60% VRAM) |

To switch to production mode:
```python
# In run_e2e_demo.py, change:
MODEL_NAME = "meta-llama/Llama-2-7b-hf"  # instead of "gpt2"
MAX_SEQ_LENGTH = 2048                     # instead of 256
# And enable quantization + gradient checkpointing in FineTuningEngine
```

---

## Supported Base Models

Any HuggingFace causal LM works. Tested with:
- `meta-llama/Llama-2-7b-hf` / `13b` / `70b`
- `meta-llama/Meta-Llama-3-8B`
- `mistralai/Mistral-7B-v0.1`
- `Qwen/Qwen2.5-7B`
- `microsoft/phi-2`
- `google/gemma-7b`

---

## Project Structure (Complete)

```
peft_finetuning/
├── __init__.py              # Package docs and architecture diagram
├── __main__.py              # Entry point (python -m peft_finetuning)
├── config.py                # All hyperparameters and configuration dataclasses
├── data_preprocessing.py    # Enterprise data → tokenized datasets + sample generator
├── finetuning_engine.py     # QLoRA model loading + training loop + memory management
├── evaluation.py            # Perplexity, ROUGE, generation, base vs fine-tuned comparison
├── memory_strategies.py     # Detailed GPU memory optimization reference document
├── run.py                   # CLI orchestrator (--demo, --train-file, etc.)
├── run_e2e_demo.py          # Self-contained end-to-end demo (CPU, GPT-2 + LoRA)
├── requirements.txt         # Dependencies
├── README.md                # This file
├── mock_data/
│   ├── enterprise_train.jsonl   # 21 enterprise instruction-following samples
│   └── enterprise_eval.jsonl    # 5 evaluation samples
└── demo_output/                 # Generated artifacts (gitignored)
    └── final_adapter/           # Saved LoRA adapter (~8 MB)
```

## Requirements

```
torch>=2.0
transformers>=4.40
accelerate>=0.25
datasets>=2.16
peft>=0.8
bitsandbytes>=0.41       # GPU only (QLoRA quantization)
trl>=0.7                 # Optional: SFTTrainer alternative
rouge-score>=0.1.2       # Evaluation
numpy
```

Install:
```bash
pip install torch transformers accelerate datasets peft bitsandbytes trl rouge-score
```

## How LoRA Works (Visual)

```
┌─────────────────────────────────────────────────────────────────┐
│                    Standard Transformer Layer                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Input ──▶ ┌──────────────────┐                                 │
│            │  Frozen Weights  │ (Original model, 4-bit)         │
│            │  W (d × d)       │─────────────────┐               │
│            └──────────────────┘                 │               │
│                                                  ▼               │
│  Input ──▶ ┌────────┐    ┌────────┐       ┌─────────┐          │
│            │  A      │───▶│  B     │──────▶│  ADD    │──▶ Output│
│            │(d × r)  │    │(r × d) │       └─────────┘          │
│            └────────┘    └────────┘                             │
│            LoRA Down      LoRA Up                                │
│            (trainable)    (trainable)                            │
│                                                                  │
│  Where: d = hidden dimension (4096 for 7B model)                │
│         r = LoRA rank (8-64, typically 16)                      │
│         Trainable params: 2 × d × r = 2 × 4096 × 16 = 131K    │
│         Per layer × 32 layers = ~4.2M total (vs 7B frozen)     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## License

MIT
