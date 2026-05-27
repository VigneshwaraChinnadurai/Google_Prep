"""
CPU-Only Inference Example
===========================
Run large language models on CPU when no GPU is available.
Useful for machines without CUDA-capable GPUs.

Note: CPU inference is significantly slower than GPU but allows
running models on any machine with sufficient RAM.
"""

import time
from airllm import AutoModel

# ============================================================
# Configuration
# ============================================================
# Use a smaller model for CPU inference (still impressive!)
MODEL_ID = "meta-llama/Llama-2-7b-chat-hf"
MAX_LENGTH = 128
MAX_NEW_TOKENS = 50

# ============================================================
# Load model (AirLLM handles CPU inference automatically)
# ============================================================
print(f"Loading model for CPU inference: {MODEL_ID}")
print("Note: CPU inference is slower but uses no GPU memory\n")

model = AutoModel.from_pretrained(MODEL_ID)

# ============================================================
# Inference on CPU
# ============================================================
prompt = "What are the three laws of thermodynamics?"

input_tokens = model.tokenizer(
    [prompt],
    return_tensors="pt",
    return_attention_mask=False,
    truncation=True,
    max_length=MAX_LENGTH,
    padding=False,
)

print(f"Prompt: {prompt}")
print("Generating (this will take a moment on CPU)...")

start = time.time()

# Key difference: use .cpu() instead of .cuda()
generation_output = model.generate(
    input_tokens["input_ids"].cpu(),  # <-- CPU inference
    max_new_tokens=MAX_NEW_TOKENS,
    use_cache=True,
    return_dict_in_generate=True,
)

elapsed = time.time() - start

output = model.tokenizer.decode(
    generation_output.sequences[0], skip_special_tokens=True
)

tokens_generated = len(generation_output.sequences[0]) - len(input_tokens["input_ids"][0])

print(f"\nResponse: {output}")
print(f"\n--- Stats ---")
print(f"Time: {elapsed:.2f}s")
print(f"Tokens generated: {tokens_generated}")
print(f"Speed: {tokens_generated/elapsed:.2f} tok/s")
