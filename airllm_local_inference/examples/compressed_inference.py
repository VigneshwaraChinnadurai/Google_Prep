"""
Compressed Inference Example
=============================
Run models with 4-bit or 8-bit block-wise quantization.
This gives ~3x speed improvement with negligible accuracy loss.

Requirements:
    pip install bitsandbytes
"""

import time
from airllm import AutoModel

# ============================================================
# Compare: Full Precision vs 4-bit Compressed
# ============================================================

MODEL_ID = "garage-bAInd/Platypus2-70B-instruct"
PROMPT = "Explain the theory of relativity in simple terms."
MAX_LENGTH = 128
MAX_NEW_TOKENS = 100


def run_inference(model, prompt: str) -> tuple:
    """Run inference and return (output_text, time_taken)."""
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
    return output, elapsed


# ============================================================
# Method 1: 4-bit compression (fastest, minimal quality loss)
# ============================================================
print("=" * 60)
print("Loading model with 4-bit compression...")
print("=" * 60)

model_4bit = AutoModel.from_pretrained(
    MODEL_ID,
    compression="4bit",  # Block-wise 4-bit quantization
)

output_4bit, time_4bit = run_inference(model_4bit, PROMPT)
print(f"\n[4-bit] Time: {time_4bit:.2f}s")
print(f"[4-bit] Output: {output_4bit[:200]}...")

# ============================================================
# Method 2: 8-bit compression (balanced speed/quality)
# ============================================================
print("\n" + "=" * 60)
print("Loading model with 8-bit compression...")
print("=" * 60)

model_8bit = AutoModel.from_pretrained(
    MODEL_ID,
    compression="8bit",  # Block-wise 8-bit quantization
)

output_8bit, time_8bit = run_inference(model_8bit, PROMPT)
print(f"\n[8-bit] Time: {time_8bit:.2f}s")
print(f"[8-bit] Output: {output_8bit[:200]}...")

# ============================================================
# Summary
# ============================================================
print("\n" + "=" * 60)
print("COMPRESSION COMPARISON")
print("=" * 60)
print(f"4-bit: {time_4bit:.2f}s")
print(f"8-bit: {time_8bit:.2f}s")
print(f"Speedup (8-bit vs 4-bit): {time_8bit/time_4bit:.2f}x")
