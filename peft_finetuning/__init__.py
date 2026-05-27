"""
Enterprise LLM Fine-Tuning with PEFT (Parameter-Efficient Fine-Tuning)
======================================================================

This module provides a production-ready pipeline for fine-tuning large language
models on enterprise-specific data using QLoRA (Quantized Low-Rank Adaptation),
the most memory-efficient PEFT method available.

Key Features:
- QLoRA 4-bit quantization: Fine-tune 7B models on a single 16GB GPU
- Streaming data preprocessing for large enterprise datasets
- Gradient checkpointing + accumulation for memory-constrained environments
- Comprehensive evaluation with perplexity, ROUGE, and custom metrics
- Support for multiple data formats (JSON, CSV, Parquet, HuggingFace datasets)

Architecture:
    ┌─────────────────────────────────────────────────────────────┐
    │                    Fine-Tuning Pipeline                       │
    ├─────────────────────────────────────────────────────────────┤
    │                                                             │
    │  ┌──────────┐   ┌──────────────┐   ┌──────────────────┐   │
    │  │Enterprise│──▶│    Data       │──▶│  Tokenized       │   │
    │  │  Data    │   │Preprocessing │   │  Dataset         │   │
    │  └──────────┘   └──────────────┘   └────────┬─────────┘   │
    │                                              │             │
    │                                              ▼             │
    │  ┌──────────┐   ┌──────────────┐   ┌──────────────────┐   │
    │  │  QLoRA   │◀──│  4-bit       │◀──│  Base Model      │   │
    │  │ Adapters │   │  Quantized   │   │  (Llama/Mistral) │   │
    │  └────┬─────┘   └──────────────┘   └──────────────────┘   │
    │       │                                                     │
    │       ▼                                                     │
    │  ┌──────────────────────────────────────────────────────┐   │
    │  │  Training Loop (gradient accumulation + checkpointing)│   │
    │  └────────────────────────┬─────────────────────────────┘   │
    │                           │                                 │
    │                           ▼                                 │
    │  ┌──────────────┐   ┌──────────────┐                       │
    │  │  Evaluation  │   │  Merged      │                       │
    │  │  (ROUGE/PPL) │   │  Model       │                       │
    │  └──────────────┘   └──────────────┘                       │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘

GPU Memory Budget (7B model, 4-bit QLoRA):
    - Base model (4-bit):     ~3.5 GB
    - LoRA adapters (fp16):   ~0.1 GB
    - Optimizer states:       ~0.4 GB
    - Activations (bs=1):     ~2.0 GB
    - Gradient checkpointing: saves ~60% activation memory
    ─────────────────────────────────────
    Total:                    ~6-8 GB (fits on 8GB GPU!)
"""

__version__ = "1.0.0"
