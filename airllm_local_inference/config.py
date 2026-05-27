"""
AirLLM Configuration Module
============================
Centralized configuration for model selection, inference parameters,
and system resource management.
"""

from dataclasses import dataclass, field
from typing import Optional, Literal
from pathlib import Path


@dataclass
class ModelConfig:
    """Configuration for model loading and inference."""

    # Model identifier (HuggingFace repo ID or local path)
    model_id: str = "meta-llama/Llama-2-7b-chat-hf"

    # Compression: None, '4bit', or '8bit' block-wise quantization
    compression: Optional[Literal["4bit", "8bit"]] = None

    # HuggingFace token for gated models
    hf_token: Optional[str] = None

    # Path to save split layer shards (None = default HF cache)
    layer_shards_saving_path: Optional[str] = None

    # Delete original model after splitting to save disk space
    delete_original: bool = False

    # Enable prefetching (overlaps model loading with compute)
    prefetching: bool = True

    # Enable profiling mode to output time consumptions
    profiling_mode: bool = False


@dataclass
class GenerationConfig:
    """Configuration for text generation parameters."""

    # Maximum input sequence length
    max_input_length: int = 512

    # Maximum new tokens to generate
    max_new_tokens: int = 256

    # Use KV-cache for faster generation
    use_cache: bool = True

    # Temperature for sampling (lower = more deterministic)
    temperature: float = 0.7

    # Top-p nucleus sampling
    top_p: float = 0.9

    # Top-k sampling (0 = disabled)
    top_k: int = 50

    # Repetition penalty
    repetition_penalty: float = 1.1

    # Whether to use padding
    padding: bool = False


@dataclass
class SystemConfig:
    """System-level configuration."""

    # Device to use: 'cuda', 'cpu', or 'mps'
    device: str = "cuda"

    # Seed for reproducibility
    seed: int = 42

    # Log level
    log_level: str = "INFO"

    # Output directory for generated texts
    output_dir: str = "./outputs"


# ============================================================
# Pre-configured model profiles for common use cases
# ============================================================

MODELS = {
    "llama2-7b-chat": ModelConfig(
        model_id="meta-llama/Llama-2-7b-chat-hf",
        compression=None,
    ),
    "llama2-13b-chat": ModelConfig(
        model_id="meta-llama/Llama-2-13b-chat-hf",
        compression="4bit",
    ),
    "llama2-70b": ModelConfig(
        model_id="meta-llama/Llama-2-70b-hf",
        compression="4bit",
    ),
    "llama3-8b-instruct": ModelConfig(
        model_id="meta-llama/Meta-Llama-3-8B-Instruct",
        compression=None,
    ),
    "llama3-70b-instruct": ModelConfig(
        model_id="meta-llama/Meta-Llama-3-70B-Instruct",
        compression="4bit",
    ),
    "llama3.1-405b": ModelConfig(
        model_id="meta-llama/Llama-3.1-405B",
        compression="4bit",
    ),
    "qwen2.5-7b": ModelConfig(
        model_id="Qwen/Qwen2.5-7B-Instruct",
        compression=None,
    ),
    "qwen2.5-72b": ModelConfig(
        model_id="Qwen/Qwen2.5-72B-Instruct",
        compression="4bit",
    ),
    "mistral-7b": ModelConfig(
        model_id="mistralai/Mistral-7B-Instruct-v0.2",
        compression=None,
    ),
    "mixtral-8x7b": ModelConfig(
        model_id="mistralai/Mixtral-8x7B-Instruct-v0.1",
        compression="4bit",
    ),
    "platypus2-70b": ModelConfig(
        model_id="garage-bAInd/Platypus2-70B-instruct",
        compression="4bit",
    ),
}


def get_model_config(model_name: str) -> ModelConfig:
    """Get a pre-configured model profile by name."""
    if model_name not in MODELS:
        available = ", ".join(MODELS.keys())
        raise ValueError(
            f"Unknown model '{model_name}'. Available: {available}"
        )
    return MODELS[model_name]
