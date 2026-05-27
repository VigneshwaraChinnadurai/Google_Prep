"""
Batch Inference Example
========================
Process multiple prompts efficiently with AirLLM.
Useful for evaluation, dataset processing, and bulk generation.
"""

import json
import time
from pathlib import Path
from airllm import AutoModel


# ============================================================
# Configuration
# ============================================================
MODEL_ID = "Qwen/Qwen2.5-7B-Instruct"  # Smaller model for faster demo
COMPRESSION = "4bit"
MAX_LENGTH = 256
MAX_NEW_TOKENS = 150

# Sample prompts for batch processing
PROMPTS = [
    "What is machine learning? Explain in 2 sentences.",
    "Write a Python function to calculate fibonacci numbers.",
    "What are the benefits of cloud computing?",
    "Explain the difference between SQL and NoSQL databases.",
    "What is Docker and why is it useful?",
    "Describe the SOLID principles in software engineering.",
    "What is the difference between TCP and UDP?",
    "Explain how a neural network learns.",
]


def main():
    print(f"Loading model: {MODEL_ID} (compression: {COMPRESSION})")
    model = AutoModel.from_pretrained(MODEL_ID, compression=COMPRESSION)

    results = []
    total_tokens = 0
    total_time = 0

    print(f"\nProcessing {len(PROMPTS)} prompts...\n")
    print("-" * 60)

    for i, prompt in enumerate(PROMPTS):
        print(f"\n[{i+1}/{len(PROMPTS)}] {prompt[:60]}...")

        # Tokenize
        input_tokens = model.tokenizer(
            [prompt],
            return_tensors="pt",
            return_attention_mask=False,
            truncation=True,
            max_length=MAX_LENGTH,
            padding=False,
        )

        input_len = len(input_tokens["input_ids"][0])

        # Generate
        start = time.time()
        generation_output = model.generate(
            input_tokens["input_ids"].cuda(),
            max_new_tokens=MAX_NEW_TOKENS,
            use_cache=True,
            return_dict_in_generate=True,
        )
        elapsed = time.time() - start

        # Decode
        output = model.tokenizer.decode(
            generation_output.sequences[0], skip_special_tokens=True
        )

        output_len = len(generation_output.sequences[0]) - input_len
        total_tokens += output_len
        total_time += elapsed

        results.append({
            "prompt": prompt,
            "response": output,
            "input_tokens": input_len,
            "output_tokens": output_len,
            "time_seconds": round(elapsed, 2),
        })

        print(f"   → {output_len} tokens in {elapsed:.2f}s ({output_len/elapsed:.1f} tok/s)")

    # ============================================================
    # Summary Statistics
    # ============================================================
    print("\n" + "=" * 60)
    print("BATCH INFERENCE SUMMARY")
    print("=" * 60)
    print(f"Total prompts:      {len(PROMPTS)}")
    print(f"Total tokens:       {total_tokens}")
    print(f"Total time:         {total_time:.2f}s")
    print(f"Avg tokens/prompt:  {total_tokens/len(PROMPTS):.1f}")
    print(f"Avg time/prompt:    {total_time/len(PROMPTS):.2f}s")
    print(f"Overall throughput: {total_tokens/total_time:.2f} tok/s")

    # Save results to JSON
    output_path = Path("outputs/batch_results.json")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(results, indent=2))
    print(f"\nResults saved to: {output_path}")


if __name__ == "__main__":
    main()
