"""
AirLLM Performance Benchmark
==============================
Comprehensive benchmarking of inference performance across different
configurations: compression levels, model sizes, and sequence lengths.

Usage:
    python benchmark.py --model llama3-8b-instruct
    python benchmark.py --model-id Qwen/Qwen2.5-7B-Instruct --runs 5
    python benchmark.py --full  (benchmarks multiple configurations)
"""

import argparse
import json
import time
import sys
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List, Optional

import psutil
from rich.console import Console
from rich.table import Table
from rich.panel import Panel

from config import ModelConfig, MODELS, get_model_config
from utils.memory_tracker import MemoryTracker

console = Console()

try:
    import torch
    HAS_CUDA = torch.cuda.is_available()
    if HAS_CUDA:
        GPU_NAME = torch.cuda.get_device_name(0)
        GPU_MEM = torch.cuda.get_device_properties(0).total_mem / (1024**3)
    else:
        GPU_NAME = "N/A"
        GPU_MEM = 0
except ImportError:
    HAS_CUDA = False
    GPU_NAME = "N/A"
    GPU_MEM = 0


@dataclass
class BenchmarkResult:
    """Single benchmark run result."""
    model_id: str
    compression: Optional[str]
    input_length: int
    output_tokens: int
    time_seconds: float
    tokens_per_second: float
    peak_gpu_mb: float
    peak_ram_mb: float


def get_system_info() -> dict:
    """Collect system information for the benchmark report."""
    ram = psutil.virtual_memory()
    return {
        "cpu": f"{psutil.cpu_count(logical=False)} cores ({psutil.cpu_count()} threads)",
        "ram_total_gb": round(ram.total / (1024**3), 1),
        "ram_available_gb": round(ram.available / (1024**3), 1),
        "gpu": GPU_NAME,
        "gpu_memory_gb": round(GPU_MEM, 1),
        "cuda_available": HAS_CUDA,
    }


def benchmark_single(
    model_id: str,
    compression: Optional[str],
    input_lengths: List[int],
    max_new_tokens: int,
    num_runs: int,
) -> List[BenchmarkResult]:
    """Run benchmark for a single model configuration."""
    from airllm import AutoModel

    console.print(f"\n[bold]Benchmarking:[/bold] {model_id} (compression={compression})")

    kwargs = {}
    if compression:
        kwargs["compression"] = compression

    model = AutoModel.from_pretrained(model_id, **kwargs)
    results = []

    test_prompt = "Explain the concept of " + "artificial intelligence " * 50  # Long prompt

    for input_len in input_lengths:
        times = []
        output_tokens_list = []

        for run in range(num_runs):
            input_tokens = model.tokenizer(
                [test_prompt],
                return_tensors="pt",
                return_attention_mask=False,
                truncation=True,
                max_length=input_len,
                padding=False,
            )

            actual_input_len = len(input_tokens["input_ids"][0])

            if HAS_CUDA:
                torch.cuda.reset_peak_memory_stats()

            device = "cuda" if HAS_CUDA else "cpu"

            start = time.time()
            generation_output = model.generate(
                input_tokens["input_ids"].to(device),
                max_new_tokens=max_new_tokens,
                use_cache=True,
                return_dict_in_generate=True,
            )
            elapsed = time.time() - start

            output_len = len(generation_output.sequences[0]) - actual_input_len
            times.append(elapsed)
            output_tokens_list.append(output_len)

        avg_time = sum(times) / len(times)
        avg_tokens = sum(output_tokens_list) / len(output_tokens_list)
        tokens_per_sec = avg_tokens / avg_time if avg_time > 0 else 0

        peak_gpu = 0
        if HAS_CUDA:
            peak_gpu = torch.cuda.max_memory_allocated() / (1024**2)

        peak_ram = psutil.Process().memory_info().rss / (1024**2)

        result = BenchmarkResult(
            model_id=model_id,
            compression=compression,
            input_length=actual_input_len,
            output_tokens=int(avg_tokens),
            time_seconds=round(avg_time, 3),
            tokens_per_second=round(tokens_per_sec, 2),
            peak_gpu_mb=round(peak_gpu, 1),
            peak_ram_mb=round(peak_ram, 1),
        )
        results.append(result)

        console.print(
            f"  input={actual_input_len:>4} tokens | "
            f"output={int(avg_tokens):>3} tokens | "
            f"time={avg_time:.2f}s | "
            f"{tokens_per_sec:.2f} tok/s | "
            f"GPU={peak_gpu:.0f}MB"
        )

    return results


def display_results(results: List[BenchmarkResult], system_info: dict):
    """Display benchmark results in a rich table."""
    console.print("\n")
    console.print(Panel.fit(
        f"CPU: {system_info['cpu']}\n"
        f"RAM: {system_info['ram_available_gb']}/{system_info['ram_total_gb']} GB\n"
        f"GPU: {system_info['gpu']} ({system_info['gpu_memory_gb']} GB)",
        title="System Info"
    ))

    table = Table(title="Benchmark Results")
    table.add_column("Model", style="cyan")
    table.add_column("Compression", style="yellow")
    table.add_column("Input Len", justify="right")
    table.add_column("Output Tokens", justify="right")
    table.add_column("Time (s)", justify="right", style="green")
    table.add_column("Tok/s", justify="right", style="bold green")
    table.add_column("GPU (MB)", justify="right")
    table.add_column("RAM (MB)", justify="right")

    for r in results:
        model_short = r.model_id.split("/")[-1][:25]
        table.add_row(
            model_short,
            r.compression or "None",
            str(r.input_length),
            str(r.output_tokens),
            f"{r.time_seconds:.3f}",
            f"{r.tokens_per_second:.2f}",
            f"{r.peak_gpu_mb:.0f}",
            f"{r.peak_ram_mb:.0f}",
        )

    console.print(table)


def main():
    parser = argparse.ArgumentParser(description="AirLLM Performance Benchmark")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--model", choices=list(MODELS.keys()))
    group.add_argument("--model-id", type=str)
    group.add_argument("--full", action="store_true", help="Run full benchmark suite")

    parser.add_argument("--compression", choices=["4bit", "8bit", "none"], default=None)
    parser.add_argument("--runs", type=int, default=3, help="Number of runs per config")
    parser.add_argument("--max-new-tokens", type=int, default=50)
    parser.add_argument("--input-lengths", type=int, nargs="+", default=[32, 64, 128, 256])
    parser.add_argument("--output", type=str, default="outputs/benchmark.json")

    args = parser.parse_args()

    system_info = get_system_info()
    all_results = []

    if args.full:
        # Benchmark multiple configurations
        configs = [
            ("Qwen/Qwen2.5-7B-Instruct", None),
            ("Qwen/Qwen2.5-7B-Instruct", "4bit"),
            ("Qwen/Qwen2.5-7B-Instruct", "8bit"),
        ]
        for model_id, compression in configs:
            results = benchmark_single(
                model_id, compression, args.input_lengths, args.max_new_tokens, args.runs
            )
            all_results.extend(results)
    else:
        if args.model:
            config = get_model_config(args.model)
            model_id = config.model_id
            compression = args.compression if args.compression != "none" else None
            if compression is None:
                compression = config.compression
        else:
            model_id = args.model_id
            compression = args.compression if args.compression != "none" else None

        results = benchmark_single(
            model_id, compression, args.input_lengths, args.max_new_tokens, args.runs
        )
        all_results.extend(results)

    # Display results
    display_results(all_results, system_info)

    # Save results
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_data = {
        "system_info": system_info,
        "results": [asdict(r) for r in all_results],
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    output_path.write_text(json.dumps(output_data, indent=2))
    console.print(f"\n[green]Results saved to: {output_path}[/green]")


if __name__ == "__main__":
    main()
