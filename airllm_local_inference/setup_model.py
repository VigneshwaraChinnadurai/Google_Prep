"""
AirLLM Model Setup & Download
==============================
Downloads, splits, and prepares models for layer-wise inference.
Run this first before using inference scripts.

Usage:
    python setup_model.py --model llama3-8b-instruct
    python setup_model.py --model-id Qwen/Qwen2.5-7B-Instruct --compression 4bit
    python setup_model.py --model platypus2-70b --delete-original
"""

import argparse
import sys
import time
from pathlib import Path

from rich.console import Console
from rich.panel import Panel
from rich.progress import Progress, SpinnerColumn, TextColumn

from config import ModelConfig, GenerationConfig, MODELS, get_model_config
from utils.memory_tracker import MemoryTracker

console = Console()


def setup_model(model_config: ModelConfig) -> None:
    """Download and prepare a model for AirLLM inference."""
    
    console.print(Panel.fit(
        f"[bold cyan]AirLLM Model Setup[/bold cyan]\n"
        f"Model: [green]{model_config.model_id}[/green]\n"
        f"Compression: [yellow]{model_config.compression or 'None (full precision)'}[/yellow]\n"
        f"Delete Original: {'Yes' if model_config.delete_original else 'No'}",
        title="Configuration"
    ))

    tracker = MemoryTracker()
    tracker.snapshot("Before model load")

    try:
        from airllm import AutoModel
        import torch

        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        console.print(f"\n[bold]Device:[/bold] {device}")
        console.print("\n[bold]Step 1:[/bold] Downloading and splitting model into layer shards...")
        console.print("[dim]This may take a while on first run (downloads model + splits layers to disk)[/dim]\n")

        start_time = time.time()

        kwargs = {
            "profiling_mode": model_config.profiling_mode,
            "prefetching": model_config.prefetching if device.startswith("cuda") else False,
            "delete_original": model_config.delete_original,
            "device": device,
        }

        if model_config.compression:
            kwargs["compression"] = model_config.compression

        if model_config.hf_token:
            kwargs["hf_token"] = model_config.hf_token

        if model_config.layer_shards_saving_path:
            kwargs["layer_shards_saving_path"] = model_config.layer_shards_saving_path

        model = AutoModel.from_pretrained(model_config.model_id, **kwargs)

        elapsed = time.time() - start_time
        tracker.snapshot("After model load")

        console.print(f"\n[bold green]✓ Model ready![/bold green] Setup completed in {elapsed:.1f}s")
        console.print(f"  Tokenizer vocab size: {len(model.tokenizer)}")

        # Quick validation: run a tiny inference
        console.print("\n[bold]Step 2:[/bold] Validating with a test inference...")
        
        test_input = model.tokenizer(
            ["Hello"],
            return_tensors="pt",
            return_attention_mask=False,
            truncation=True,
            max_length=32,
            padding=False,
        )

        output = model.generate(
            test_input["input_ids"].to(device),
            max_new_tokens=5,
            use_cache=True,
            return_dict_in_generate=True,
        )
        
        decoded = model.tokenizer.decode(output.sequences[0], skip_special_tokens=True)
        tracker.snapshot("After test inference")

        console.print(f"[bold green]✓ Validation passed![/bold green] Test output: '{decoded[:50]}...'")
        console.print("\n")
        tracker.print_report()

    except ImportError:
        console.print("[bold red]Error:[/bold red] airllm not installed. Run: pip install airllm")
        sys.exit(1)
    except Exception as e:
        console.print(f"[bold red]Error:[/bold red] {e}")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="AirLLM Model Setup & Download")
    
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--model", choices=list(MODELS.keys()),
        help="Use a pre-configured model profile"
    )
    group.add_argument(
        "--model-id", type=str,
        help="HuggingFace model repo ID (e.g., meta-llama/Llama-2-7b-hf)"
    )

    parser.add_argument("--compression", choices=["4bit", "8bit"], default=None)
    parser.add_argument("--hf-token", type=str, default=None, help="HuggingFace API token for gated models")
    parser.add_argument("--save-path", type=str, default=None, help="Custom path for layer shards")
    parser.add_argument("--delete-original", action="store_true", help="Delete original model after splitting")

    args = parser.parse_args()

    if args.model:
        config = get_model_config(args.model)
        # Override with CLI args if provided
        if args.compression:
            config.compression = args.compression
        if args.hf_token:
            config.hf_token = args.hf_token
        if args.save_path:
            config.layer_shards_saving_path = args.save_path
        if args.delete_original:
            config.delete_original = True
    else:
        config = ModelConfig(
            model_id=args.model_id,
            compression=args.compression,
            hf_token=args.hf_token,
            layer_shards_saving_path=args.save_path,
            delete_original=args.delete_original,
        )

    setup_model(config)


if __name__ == "__main__":
    main()
