"""
Basic Inference Example
========================
Simplest possible AirLLM usage - load a model and generate text.
This is the "Hello World" of AirLLM.
"""

from airllm import AutoModel

# ============================================================
# Configuration
# ============================================================
MODEL_ID = "garage-bAInd/Platypus2-70B-instruct"  # or any HF model
MAX_LENGTH = 128
MAX_NEW_TOKENS = 50

# ============================================================
# Step 1: Load the model
# AirLLM will automatically:
#   - Download the model from HuggingFace (first time only)
#   - Split it into layer-wise shards on disk
#   - Load only one layer at a time during inference
# ============================================================
print(f"Loading model: {MODEL_ID}")
print("(First run downloads and splits the model - this takes time)")

model = AutoModel.from_pretrained(MODEL_ID)

# ============================================================
# Step 2: Tokenize your input
# ============================================================
input_text = ["What is the capital of United States?"]

input_tokens = model.tokenizer(
    input_text,
    return_tensors="pt",
    return_attention_mask=False,
    truncation=True,
    max_length=MAX_LENGTH,
    padding=False,  # Set False if tokenizer has no pad token
)

# ============================================================
# Step 3: Generate output
# The model processes layer-by-layer from disk, using minimal GPU memory
# ============================================================
print("Generating response...")

generation_output = model.generate(
    input_tokens["input_ids"].cuda(),  # Use .cpu() for CPU-only inference
    max_new_tokens=MAX_NEW_TOKENS,
    use_cache=True,
    return_dict_in_generate=True,
)

# ============================================================
# Step 4: Decode and display
# ============================================================
output = model.tokenizer.decode(
    generation_output.sequences[0],
    skip_special_tokens=True,
)

print(f"\nInput: {input_text[0]}")
print(f"Output: {output}")
