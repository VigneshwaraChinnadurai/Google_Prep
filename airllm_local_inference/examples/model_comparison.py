"""
Multi-Model Comparison Example
===============================
Compare outputs from different models on the same prompts.
Useful for evaluating model quality and selecting the best model
for your use case.
"""

import time
import json
from pathlib import Path
from airllm import AutoModel

# ============================================================
# Models to compare (use smaller ones for faster comparison)
# ============================================================
MODELS_TO_COMPARE = [
    {"id": "mistralai/Mistral-7B-Instruct-v0.2", "name": "Mistral-7B", "compression": None},
    {"id": "Qwen/Qwen2.5-7B-Instruct", "name": "Qwen2.5-7B", "compression": None},
    {"id": "meta-llama/Meta-Llama-3-8B-Instruct", "name": "Llama3-8B", "compression": None},
]

# Test prompts
TEST_PROMPTS = [
    "What is the capital of France? Answer in one sentence.",
    "Write a haiku about programming.",
    "Explain what an API is to a 10-year-old.",
]

MAX_LENGTH = 128
MAX_NEW_TOKENS = 100


def run_single_model(model_info: dict, prompts: list) -> list:
    """Run all prompts through a single model."""
    print(f"\n{'='*60}")
    print(f"Loading: {model_info['name']} ({model_info['id']})")
    print(f"{'='*60}")

    kwargs = {}
    if model_info["compression"]:
        kwargs["compression"] = model_info["compression"]

    model = AutoModel.from_pretrained(model_info["id"], **kwargs)
    results = []

    for prompt in prompts:
        input_tokens = model.tokenizer(
            [prompt],
            return_tensors="pt",
            return_attention_mask=False,
            truncation=True,
            max_length=MAX_LENGTH,
            padding=False,
        )

        start = time.time()
        generation_output = model.generate(
            input_tokens["input_ids"].cuda(),
            max_new_tokens=MAX_NEW_TOKENS,
            use_cache=True,
            return_dict_in_generate=True,
        )
        elapsed = time.time() - start

        output = model.tokenizer.decode(
            generation_output.sequences[0], skip_special_tokens=True
        )

        results.append({
            "prompt": prompt,
            "response": output,
            "time": round(elapsed, 2),
        })
        print(f"  ✓ '{prompt[:40]}...' → {elapsed:.2f}s")

    return results


def main():
    all_results = {}

    for model_info in MODELS_TO_COMPARE:
        try:
            results = run_single_model(model_info, TEST_PROMPTS)
            all_results[model_info["name"]] = results
        except Exception as e:
            print(f"  ✗ Failed: {e}")
            all_results[model_info["name"]] = {"error": str(e)}

    # ============================================================
    # Display comparison
    # ============================================================
    print("\n" + "=" * 60)
    print("MODEL COMPARISON RESULTS")
    print("=" * 60)

    for i, prompt in enumerate(TEST_PROMPTS):
        print(f"\n{'─'*60}")
        print(f"Prompt: {prompt}")
        print(f"{'─'*60}")
        for model_name, results in all_results.items():
            if isinstance(results, dict) and "error" in results:
                print(f"  [{model_name}] ERROR: {results['error']}")
            else:
                r = results[i]
                print(f"  [{model_name}] ({r['time']}s): {r['response'][:150]}...")

    # Save comparison
    output_path = Path("outputs/model_comparison.json")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(all_results, indent=2))
    print(f"\nFull results saved to: {output_path}")


if __name__ == "__main__":
    main()
