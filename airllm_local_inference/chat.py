"""
AirLLM Interactive Chat
========================
Terminal-based interactive chat with any supported model.
Maintains conversation history within a session.

Usage:
    python chat.py --model llama3-8b-instruct
    python chat.py --model-id Qwen/Qwen2.5-7B-Instruct --compression 4bit
    python chat.py --model mistral-7b --system-prompt "You are a Python expert"
"""

import argparse
import sys
import time
from typing import List, Dict

from rich.console import Console
from rich.panel import Panel
from rich.markdown import Markdown

from config import ModelConfig, GenerationConfig, MODELS, get_model_config
from utils.prompt_templates import get_template

console = Console()


class ChatSession:
    """Interactive chat session with conversation memory."""

    def __init__(self, model_config: ModelConfig, gen_config: GenerationConfig, system_prompt: str = None):
        self.model_config = model_config
        self.gen_config = gen_config
        self.model = None
        self.template = get_template(model_config.model_id)
        self.history: List[Dict[str, str]] = []
        self.system_prompt = system_prompt or self.template.default_system_prompt

    def load_model(self):
        """Load model with AirLLM."""
        from airllm import AutoModel
        import torch

        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        console.print(f"[dim]Loading {self.model_config.model_id} (device: {self.device})...[/dim]")
        
        kwargs = {
            "prefetching": self.model_config.prefetching if self.device.startswith("cuda") else False,
            "device": self.device,
        }
        if self.model_config.compression:
            kwargs["compression"] = self.model_config.compression
        if self.model_config.hf_token:
            kwargs["hf_token"] = self.model_config.hf_token

        self.model = AutoModel.from_pretrained(self.model_config.model_id, **kwargs)
        console.print("[green]Model ready! Start chatting (type 'quit' to exit, 'reset' to clear history)[/green]\n")

    def chat(self, user_message: str) -> str:
        """Send a message and get a response."""
        if self.model is None:
            self.load_model()

        # Add user message to history
        self.history.append({"role": "user", "content": user_message})

        # Build conversation with system prompt
        messages = [{"role": "system", "content": self.system_prompt}] + self.history

        # Format using template
        formatted = self.template.format_chat(messages)

        # Tokenize (truncate to fit within context window)
        input_tokens = self.model.tokenizer(
            [formatted],
            return_tensors="pt",
            return_attention_mask=False,
            truncation=True,
            max_length=self.gen_config.max_input_length,
            padding=False,
        )

        import torch
        device = self.device

        start = time.time()

        generation_output = self.model.generate(
            input_tokens["input_ids"].to(device),
            max_new_tokens=self.gen_config.max_new_tokens,
            use_cache=self.gen_config.use_cache,
            return_dict_in_generate=True,
        )

        elapsed = time.time() - start

        # Decode
        full_output = self.model.tokenizer.decode(
            generation_output.sequences[0], skip_special_tokens=True
        )

        # Extract only the assistant's response (after the formatted prompt)
        assistant_response = full_output
        if full_output.startswith(formatted.replace(self.template.assistant_prefix, "").strip()):
            # Try to extract just the new content
            input_decoded = self.model.tokenizer.decode(
                input_tokens["input_ids"][0], skip_special_tokens=True
            )
            if full_output.startswith(input_decoded):
                assistant_response = full_output[len(input_decoded):].strip()

        # Add assistant response to history
        self.history.append({"role": "assistant", "content": assistant_response})

        tokens_generated = len(generation_output.sequences[0]) - len(input_tokens["input_ids"][0])
        console.print(f"[dim]({tokens_generated} tokens, {elapsed:.1f}s)[/dim]")

        return assistant_response

    def reset(self):
        """Clear conversation history."""
        self.history = []
        console.print("[yellow]Conversation history cleared.[/yellow]\n")


def main():
    parser = argparse.ArgumentParser(description="AirLLM Interactive Chat")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--model", choices=list(MODELS.keys()))
    group.add_argument("--model-id", type=str)

    parser.add_argument("--compression", choices=["4bit", "8bit"], default=None)
    parser.add_argument("--hf-token", type=str, default=None)
    parser.add_argument("--max-new-tokens", type=int, default=512)
    parser.add_argument("--max-input-length", type=int, default=2048)
    parser.add_argument("--system-prompt", type=str, default=None)

    args = parser.parse_args()

    # Build config
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

    # Display header
    console.print(Panel.fit(
        f"[bold cyan]AirLLM Interactive Chat[/bold cyan]\n"
        f"Model: [green]{model_config.model_id}[/green]\n"
        f"Compression: [yellow]{model_config.compression or 'None'}[/yellow]\n"
        f"Commands: [dim]quit, reset, history[/dim]",
        title="Chat Session"
    ))

    # Start session
    session = ChatSession(model_config, gen_config, args.system_prompt)

    while True:
        try:
            user_input = console.input("\n[bold blue]You:[/bold blue] ").strip()

            if not user_input:
                continue
            elif user_input.lower() == "quit":
                console.print("[yellow]Goodbye![/yellow]")
                break
            elif user_input.lower() == "reset":
                session.reset()
                continue
            elif user_input.lower() == "history":
                for msg in session.history:
                    role_color = "blue" if msg["role"] == "user" else "green"
                    console.print(f"[{role_color}]{msg['role']}:[/{role_color}] {msg['content'][:100]}...")
                continue

            response = session.chat(user_input)
            console.print(f"\n[bold green]Assistant:[/bold green] {response}")

        except KeyboardInterrupt:
            console.print("\n[yellow]Interrupted. Type 'quit' to exit.[/yellow]")
        except Exception as e:
            console.print(f"[red]Error: {e}[/red]")


if __name__ == "__main__":
    main()
