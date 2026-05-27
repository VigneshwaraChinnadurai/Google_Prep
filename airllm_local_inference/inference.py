"""
AirLLM Inference Engine
========================
Main inference script with full control over generation parameters.
Supports single prompts, batch prompts from file, and custom templates.

Usage:
    python inference.py --model llama3-8b-instruct --prompt "Explain quantum computing"
    python inference.py --model qwen2.5-7b --prompt-file prompts.txt
    python inference.py --model-id garage-bAInd/Platypus2-70B-instruct --compression 4bit --prompt "Hello"
"""

import argparse
import json
import sys
import time
from pathlib import Path
from typing import List

from rich.console import Console
from rich.panel import Panel
from rich.markdown import Markdown

from config import ModelConfig, GenerationConfig, MODELS, get_model_config
from utils.memory_tracker import MemoryTracker
from utils.prompt_templates import get_template

console = Console()


class AirLLMInference:
    """High-level inference wrapper around AirLLM."""

    def __init__(self, model_config: ModelConfig, gen_config: GenerationConfig = None):
        self.model_config = model_config
        self.gen_config = gen_config or GenerationConfig()
        self.model = None
        self.template = get_template(model_config.model_id)
        self.tracker = MemoryTracker()

    def load_model(self):
        """Load the model using AirLLM's layer-wise approach."""
        from airllm import AutoModel
        import torch

        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.tracker.snapshot("Before model load")
        console.print(f"[dim]Loading model: {self.model_config.model_id} (device: {self.device})[/dim]")

        kwargs = {
            "profiling_mode": self.model_config.profiling_mode,
            "prefetching": self.model_config.prefetching if self.device.startswith("cuda") else False,
            "device": self.device,
        }
        if self.model_config.compression:
            kwargs["compression"] = self.model_config.compression
        if self.model_config.hf_token:
            kwargs["hf_token"] = self.model_config.hf_token
        if self.model_config.layer_shards_saving_path:
            kwargs["layer_shards_saving_path"] = self.model_config.layer_shards_saving_path

        self.model = AutoModel.from_pretrained(self.model_config.model_id, **kwargs)
        self.tracker.snapshot("After model load")
        console.print("[green]Model loaded successfully.[/green]\n")

    def generate(self, prompt: str, system_prompt: str = None) -> str:
        """Generate a response for a single prompt."""
        if self.model is None:
            self.load_model()

        # Format the prompt using the appropriate template
        formatted = self.template.format_single(prompt, system_prompt)

        # Tokenize
        input_tokens = self.model.tokenizer(
            [formatted],
            return_tensors="pt",
            return_attention_mask=False,
            truncation=True,
            max_length=self.gen_config.max_input_length,
            padding=self.gen_config.padding,
        )

        # Determine device
        device = self.device

        self.tracker.snapshot("Before generation")
        start = time.time()

        # Generate
        generation_output = self.model.generate(
            input_tokens["input_ids"].to(device),
            max_new_tokens=self.gen_config.max_new_tokens,
            use_cache=self.gen_config.use_cache,
            return_dict_in_generate=True,
        )

        elapsed = time.time() - start
        self.tracker.snapshot("After generation")

        # Decode output
        output_text = self.model.tokenizer.decode(
            generation_output.sequences[0], skip_special_tokens=True
        )

        # Strip the input prompt from the output if present
        if output_text.startswith(formatted):
            output_text = output_text[len(formatted):]

        tokens_generated = len(generation_output.sequences[0]) - len(input_tokens["input_ids"][0])
        tokens_per_sec = tokens_generated / elapsed if elapsed > 0 else 0

        console.print(f"[dim]Generated {tokens_generated} tokens in {elapsed:.2f}s ({tokens_per_sec:.1f} tok/s)[/dim]")

        return output_text.strip()

    def generate_batch(self, prompts: List[str], system_prompt: str = None) -> List[str]:
        """Generate responses for multiple prompts sequentially."""
        results = []
        for i, prompt in enumerate(prompts):
            console.print(f"\n[bold]Prompt {i+1}/{len(prompts)}:[/bold] {prompt[:80]}...")
            result = self.generate(prompt, system_prompt)
            results.append(result)
        return results


def main():
    parser = argparse.ArgumentParser(description="AirLLM Inference Engine")

    # Model selection
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--model", choices=list(MODELS.keys()), help="Pre-configured model profile")
    group.add_argument("--model-id", type=str, help="HuggingFace model repo ID")

    # Input
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument("--prompt", type=str, help="Single prompt text")
    input_group.add_argument("--prompt-file", type=str, help="File with one prompt per line")

    # Generation parameters
    parser.add_argument("--compression", choices=["4bit", "8bit"], default=None)
    parser.add_argument("--hf-token", type=str, default=None)
    parser.add_argument("--max-new-tokens", type=int, default=256)
    parser.add_argument("--max-input-length", type=int, default=512)
    parser.add_argument("--system-prompt", type=str, default=None)
    parser.add_argument("--output-file", type=str, default=None, help="Save output to file")
    parser.add_argument("--show-memory", action="store_true", help="Show memory usage report")

    args = parser.parse_args()

    # Build configs
    if args.model:
        model_config = get_model_config(args.model)
        if args.compression:
            model_config.compression = args.compression
    else:
        model_config = ModelConfig(model_id=args.model_id, compression=args.compression)

    if args.hf_token:
        model_config.hf_token = args.hf_token

    gen_config = GenerationConfig(
        max_new_tokens=args.max_new_tokens,
        max_input_length=args.max_input_length,
    )

    # Initialize engine
    engine = AirLLMInference(model_config, gen_config)

    # Get prompts
    if args.prompt:
        prompts = [args.prompt]
    else:
        prompt_path = Path(args.prompt_file)
        if not prompt_path.exists():
            console.print(f"[red]Error: File not found: {args.prompt_file}[/red]")
            sys.exit(1)
        prompts = [line.strip() for line in prompt_path.read_text().splitlines() if line.strip()]

    # Run inference
    console.print(Panel.fit(
        f"[cyan]Model:[/cyan] {model_config.model_id}\n"
        f"[cyan]Compression:[/cyan] {model_config.compression or 'None'}\n"
        f"[cyan]Max New Tokens:[/cyan] {gen_config.max_new_tokens}\n"
        f"[cyan]Prompts:[/cyan] {len(prompts)}",
        title="AirLLM Inference"
    ))

    results = engine.generate_batch(prompts, args.system_prompt)

    # Display results
    for i, (prompt, result) in enumerate(zip(prompts, results)):
        console.print(Panel(
            f"[bold cyan]Prompt:[/bold cyan] {prompt}\n\n[bold green]Response:[/bold green]\n{result}",
            title=f"Result {i+1}",
            border_style="green"
        ))

    # Save to file if requested
    if args.output_file:
        output_path = Path(args.output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_data = [{"prompt": p, "response": r} for p, r in zip(prompts, results)]
        output_path.write_text(json.dumps(output_data, indent=2))
        console.print(f"\n[green]Results saved to {args.output_file}[/green]")

    # Memory report
    if args.show_memory:
        console.print("\n")
        engine.tracker.print_report()


if __name__ == "__main__":
    main()
