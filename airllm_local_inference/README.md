# AirLLM Local Inference Engine

> Run 70B+ parameter LLMs on a single 4GB GPU — no quantization degradation, no cloud dependency.

## What is AirLLM?

AirLLM is a Python library that enables running extremely large language models (up to **405B parameters**) on consumer-grade hardware by loading model weights **layer-by-layer** from disk during inference. Unlike traditional approaches that require the entire model to fit in GPU/RAM, AirLLM streams each transformer layer sequentially, requiring only enough memory for a single layer at a time.

### Key Innovation

```
Traditional Inference:          AirLLM Inference:
┌─────────────────────┐        ┌─────────────────────┐
│  Load ALL 80 layers │        │  Load Layer 1 → GPU │ ← Process → Store KV
│  into GPU memory    │        │  Load Layer 2 → GPU │ ← Process → Store KV
│  (140GB+ VRAM!)     │        │  ...                │
│                     │        │  Load Layer 80→ GPU │ ← Process → Output
└─────────────────────┘        └─────────────────────┘
     Needs: 140GB VRAM              Needs: ~4GB VRAM!
```

### What This Means

| Model | Traditional Requirement | With AirLLM |
|-------|------------------------|-------------|
| Llama-2 7B | 14GB VRAM | **4GB VRAM** |
| Llama-2 70B | 140GB VRAM | **4GB VRAM** |
| Llama-3 8B | 16GB VRAM | **4GB VRAM** |
| Llama-3.1 405B | 800GB+ VRAM | **8GB VRAM** |
| Qwen2.5 72B | 144GB VRAM | **4GB VRAM** |

---

## Project Structure

```
airllm_local_inference/
├── config.py                    # Centralized configuration & model profiles
├── setup_model.py               # Download & prepare models
├── inference.py                 # Main inference engine (CLI)
├── chat.py                      # Interactive chat interface
├── benchmark.py                 # Performance benchmarking tool
├── apply_patches.py             # Compatibility patches for transformers 4.57+
├── requirements.txt             # Dependencies
├── _demo.py                     # Quick demo to verify setup
├── utils/
│   ├── __init__.py
│   ├── memory_tracker.py        # GPU/RAM usage monitoring
│   └── prompt_templates.py      # Chat templates for all model families
└── examples/
    ├── basic_inference.py       # Simplest possible usage
    ├── compressed_inference.py  # 4-bit/8-bit compression comparison
    ├── batch_inference.py       # Process multiple prompts
    ├── cpu_inference.py         # CPU-only inference
    └── model_comparison.py      # Compare different models
```

---

## Detailed Setup Instructions

### Prerequisites

Before you begin, ensure you have:

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Python** | 3.10+ | 3.11 or 3.12 |
| **RAM** | 8 GB | 16 GB |
| **Disk Space** | 10 GB (for 1.5B model) | 30+ GB (for 7B model) |
| **GPU** | None (CPU works) | NVIDIA GPU with 4+ GB VRAM |
| **Internet** | Required for first-time model download | — |
| **OS** | Windows 10+, macOS 12+, Linux | — |

> **Note**: You do NOT need a GPU. AirLLM works on CPU-only machines (just slower: ~25s per token for a 1.5B model). With an NVIDIA GPU, expect 1-5 tokens/second.

---

### Windows Setup (Step-by-Step)

#### Step 1: Install Python

1. Download Python from https://www.python.org/downloads/ (version 3.11 or 3.12 recommended)
2. **IMPORTANT**: During installation, check ✅ "Add Python to PATH"
3. Verify installation by opening **PowerShell** or **Command Prompt**:
   ```powershell
   python --version
   # Should show: Python 3.11.x or 3.12.x
   ```

#### Step 2: Install Git (Optional but Recommended)

1. Download Git from https://git-scm.com/download/win
2. Install with default options
3. This allows you to clone the repository directly

#### Step 3: Get the Project

**Option A — Clone with Git:**
```powershell
cd C:\Users\YourName\Projects
git clone <repository-url> airllm_local_inference
cd airllm_local_inference
```

**Option B — Download manually:**
- Download and extract the `airllm_local_inference` folder to your preferred location
- Open PowerShell and navigate to it:
```powershell
cd "C:\Users\YourName\Projects\airllm_local_inference"
```

#### Step 4: Create a Virtual Environment

```powershell
# Create the virtual environment
python -m venv .venv

# Activate it (you'll see (.venv) in your prompt)
.venv\Scripts\Activate.ps1
```

> **If you get a "running scripts is disabled" error**, run this first:
> ```powershell
> Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
> ```
> Then retry the activation command.

#### Step 5: Install Dependencies

```powershell
# Upgrade pip first
python -m pip install --upgrade pip

# Install all required packages
pip install -r requirements.txt
```

This installs: `airllm`, `torch`, `transformers`, `accelerate`, `safetensors`, `rich`, `psutil`, and more.

> **GPU Users (NVIDIA)**: PyTorch will automatically install with CUDA support if you have a compatible GPU. To verify:
> ```powershell
> python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}')"
> ```

#### Step 6: Apply Compatibility Patches

AirLLM v2.11.0 has compatibility issues with modern `transformers` (4.57+). This script automatically patches them:

```powershell
python apply_patches.py
```

You should see output like:
```
AirLLM directory: C:\Users\...\site-packages\airllm
Applying patches for transformers 4.57+ compatibility...

[1/7] BetterTransformer import (airllm_base.py)
  [OK]   Wrap BetterTransformer import in try/except
[2/7] Add _is_stateful attribute (airllm_base.py)
  [OK]   Add _is_stateful and main_input_name class attributes
...
==================================================
All patches applied successfully!
==================================================
```

#### Step 7: Run the Demo

```powershell
python _demo.py
```

This will:
1. Download the Qwen2.5-1.5B-Instruct model (~3 GB, first time only)
2. Split it into layer shards (~30 seconds)
3. Generate text from a prompt

**Expected output** (first run takes longer due to download):
```
============================================================
AirLLM Local Inference - End-to-End Demo
============================================================

[1/3] Loading model: Qwen/Qwen2.5-1.5B-Instruct
      Model loaded in 15.2s

[2/3] Prompt: "What is the capital of France? Answer in one sentence."

[3/3] Generating (max_new_tokens=30)...
running layers(cpu): 100%|████████████████████| 31/31 [00:22<00:00]
      Generation time: 22.4s

============================================================
OUTPUT: What is the capital of France? Answer in one sentence. The capital of France is Paris.
============================================================
```

#### Step 8: Start Using It!

```powershell
# Run inference with any prompt
python inference.py --model-id "Qwen/Qwen2.5-1.5B-Instruct" --prompt "Explain Python decorators in simple terms"

# Or start an interactive chat
python chat.py --model-id "Qwen/Qwen2.5-1.5B-Instruct"
```

---

### macOS Setup (Step-by-Step)

#### Step 1: Install Python

**Option A — Using Homebrew (Recommended):**
```bash
# Install Homebrew if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Python
brew install python@3.12

# Verify
python3 --version
# Should show: Python 3.12.x
```

**Option B — Official Installer:**
1. Download from https://www.python.org/downloads/macos/
2. Run the `.pkg` installer
3. Verify: `python3 --version`

#### Step 2: Install Git

Git comes pre-installed on macOS. Verify:
```bash
git --version
```
If not installed, you'll be prompted to install Xcode Command Line Tools — accept the prompt.

#### Step 3: Get the Project

```bash
cd ~/Projects  # or your preferred directory
git clone <repository-url> airllm_local_inference
cd airllm_local_inference
```

Or download manually and navigate to the folder:
```bash
cd ~/Downloads/airllm_local_inference
```

#### Step 4: Create a Virtual Environment

```bash
# Create the virtual environment
python3 -m venv .venv

# Activate it (you'll see (.venv) in your prompt)
source .venv/bin/activate
```

> **Verify activation**: Your terminal prompt should now start with `(.venv)`. You can also check:
> ```bash
> which python
> # Should show: /path/to/airllm_local_inference/.venv/bin/python
> ```

#### Step 5: Install Dependencies

```bash
# Upgrade pip
pip install --upgrade pip

# Install all required packages
pip install -r requirements.txt
```

> **Apple Silicon (M1/M2/M3) Note**: PyTorch supports Apple's Metal Performance Shaders (MPS) for GPU acceleration. To verify:
> ```bash
> python -c "import torch; print(f'MPS available: {torch.backends.mps.is_available()}')"
> ```
> If MPS is available, AirLLM will use it for acceleration (though CPU mode is the most tested path).

#### Step 6: Apply Compatibility Patches

```bash
python apply_patches.py
```

You should see all 7 patches applied successfully (same output as Windows section above).

#### Step 7: Run the Demo

```bash
python _demo.py
```

First run downloads ~3 GB model weights. Subsequent runs are instant (cached locally at `~/.cache/huggingface/`).

#### Step 8: Start Using It!

```bash
# Single prompt inference
python inference.py --model-id "Qwen/Qwen2.5-1.5B-Instruct" --prompt "What is machine learning?"

# Interactive chat mode
python chat.py --model-id "Qwen/Qwen2.5-1.5B-Instruct"

# Use a larger model (requires ~14GB disk space)
python inference.py --model-id "Qwen/Qwen2.5-7B-Instruct" --prompt "Write a haiku about coding"
```

---

### Quick Reference: Common Commands

| Task | Windows (PowerShell) | macOS/Linux (Terminal) |
|------|---------------------|----------------------|
| Activate venv | `.venv\Scripts\Activate.ps1` | `source .venv/bin/activate` |
| Deactivate venv | `deactivate` | `deactivate` |
| Run demo | `python _demo.py` | `python _demo.py` |
| Single inference | `python inference.py --model-id "Qwen/Qwen2.5-1.5B-Instruct" --prompt "Hello"` | Same |
| Interactive chat | `python chat.py --model-id "Qwen/Qwen2.5-1.5B-Instruct"` | Same |
| Apply patches | `python apply_patches.py` | Same |
| Check GPU | `python -c "import torch; print(torch.cuda.is_available())"` | Same (or check MPS) |

---

### Choosing a Model

| Model | Size on Disk | RAM Needed | Good For |
|-------|-------------|-----------|----------|
| `Qwen/Qwen2.5-1.5B-Instruct` | ~3 GB | 8 GB | Testing, quick setup, low-resource machines |
| `Qwen/Qwen2.5-7B-Instruct` | ~14 GB | 16 GB | General use, better quality |
| `mistralai/Mistral-7B-Instruct-v0.2` | ~14 GB | 16 GB | Strong reasoning, fast |
| `meta-llama/Meta-Llama-3-8B-Instruct` | ~16 GB | 16 GB | Best quality at 8B (requires HF token) |
| `Qwen/Qwen2.5-72B-Instruct` | ~144 GB | 16 GB | Near-GPT4 quality (slow, lots of disk) |

> **First-time users**: Start with `Qwen/Qwen2.5-1.5B-Instruct`. It's small (3 GB), doesn't require authentication, and works on any machine.

---

### Using Pre-configured Model Profiles

Instead of typing the full model ID, use shorthand profiles:

```bash
# Setup a model with a profile name
python setup_model.py --model qwen2.5-7b

# Then reference it in inference
python inference.py --model qwen2.5-7b --prompt "Explain gravity"

# With 4-bit compression for 3x speed
python setup_model.py --model llama3-8b-instruct --compression 4bit

# For gated models (Llama), you need a HuggingFace token
# Get one at: https://huggingface.co/settings/tokens
python setup_model.py --model llama3-8b-instruct --hf-token hf_YOUR_TOKEN_HERE
```

### Interactive Chat Mode

```bash
python chat.py --model qwen2.5-7b

# With a custom system prompt
python chat.py --model mistral-7b --system-prompt "You are a Python coding expert"

# With compression for faster responses
python chat.py --model llama3-8b-instruct --compression 4bit
```

### Benchmark Performance

```bash
# Benchmark a specific model
python benchmark.py --model qwen2.5-7b --runs 3

# Full benchmark suite (multiple compression levels)
python benchmark.py --full
```

---

## How AirLLM Works (Technical Deep-Dive)

### Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                        AirLLM Pipeline                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. MODEL PREPARATION (one-time)                               │
│     ┌──────────┐    ┌──────────────┐    ┌─────────────────┐   │
│     │ HF Model │───▶│ Split into   │───▶│ Layer Shards    │   │
│     │ (140GB)  │    │ Layer Files  │    │ on Disk (140GB) │   │
│     └──────────┘    └──────────────┘    └─────────────────┘   │
│                                                                │
│  2. INFERENCE (per token)                                      │
│     ┌─────────┐                                                │
│     │ Input   │                                                │
│     │ Tokens  │                                                │
│     └────┬────┘                                                │
│          │                                                     │
│          ▼                                                     │
│     ┌─────────────────────────────────────┐                    │
│     │  For each layer i = 1 to N:         │                    │
│     │    1. Load layer_i.pt from disk     │  ← Bottleneck     │
│     │    2. Move to GPU                    │                    │
│     │    3. Forward pass (compute)         │                    │
│     │    4. Store KV cache                 │                    │
│     │    5. Free GPU memory                │                    │
│     └─────────────────────────────────────┘                    │
│          │                                                     │
│          ▼                                                     │
│     ┌─────────┐                                                │
│     │ Output  │                                                │
│     │ Logits  │                                                │
│     └─────────┘                                                │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Key Mechanisms

1. **Layer-wise Loading**: The model is split into individual transformer layers saved as separate files. During inference, only one layer is loaded into GPU memory at a time.

2. **Prefetching**: While the GPU processes layer N, the CPU asynchronously loads layer N+1 from disk. This overlaps I/O with computation for ~10% speedup.

3. **Block-wise Compression**: Optional 4-bit/8-bit quantization of weights *on disk*. Since the bottleneck is disk I/O (not compute), compressing weights by 4x makes loading 3x faster without needing activation quantization.

4. **KV-Cache**: Key-value pairs from each layer's attention computation are cached to avoid recomputation during autoregressive generation.

### Performance Characteristics

| Aspect | Detail |
|--------|--------|
| **Bottleneck** | Disk I/O (SSD speed is critical) |
| **Speed** | ~1-5 tokens/second (model & hardware dependent) |
| **GPU VRAM** | 4GB for 70B models, 8GB for 405B |
| **Disk Space** | 2x model size (original + split shards) |
| **RAM** | Minimal (a few GB for tokenizer + KV cache) |

### When to Use AirLLM

| Use Case | AirLLM? | Alternative |
|----------|---------|-------------|
| Run 70B models on consumer GPU | ✅ Yes | — |
| Interactive chat (low latency) | ⚠️ Slow | Use smaller model or API |
| Batch processing (quality > speed) | ✅ Yes | — |
| Research/evaluation of large models | ✅ Yes | — |
| Production serving (high throughput) | ❌ No | vLLM, TGI |
| Edge deployment | ✅ Yes (CPU) | GGML/llama.cpp |

---

## Configuration Reference

### Model Profiles (config.py)

Pre-configured profiles available via `--model`:

| Profile | Model | Size | Default Compression |
|---------|-------|------|-------------------|
| `llama2-7b-chat` | meta-llama/Llama-2-7b-chat-hf | 7B | None |
| `llama2-13b-chat` | meta-llama/Llama-2-13b-chat-hf | 13B | 4bit |
| `llama2-70b` | meta-llama/Llama-2-70b-hf | 70B | 4bit |
| `llama3-8b-instruct` | meta-llama/Meta-Llama-3-8B-Instruct | 8B | None |
| `llama3-70b-instruct` | meta-llama/Meta-Llama-3-70B-Instruct | 70B | 4bit |
| `llama3.1-405b` | meta-llama/Llama-3.1-405B | 405B | 4bit |
| `qwen2.5-7b` | Qwen/Qwen2.5-7B-Instruct | 7B | None |
| `qwen2.5-72b` | Qwen/Qwen2.5-72B-Instruct | 72B | 4bit |
| `mistral-7b` | mistralai/Mistral-7B-Instruct-v0.2 | 7B | None |
| `mixtral-8x7b` | mistralai/Mixtral-8x7B-Instruct-v0.1 | 8x7B | 4bit |
| `platypus2-70b` | garage-bAInd/Platypus2-70B-instruct | 70B | 4bit |

### Generation Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `max_input_length` | 512 | Maximum tokens for input prompt |
| `max_new_tokens` | 256 | Maximum tokens to generate |
| `use_cache` | True | Enable KV-cache |
| `temperature` | 0.7 | Sampling temperature |
| `top_p` | 0.9 | Nucleus sampling threshold |
| `top_k` | 50 | Top-k sampling |
| `repetition_penalty` | 1.1 | Penalize repeated tokens |

### AirLLM Init Parameters

| Parameter | Options | Description |
|-----------|---------|-------------|
| `compression` | None, '4bit', '8bit' | Block-wise weight quantization |
| `profiling_mode` | True/False | Print layer-by-layer timing |
| `prefetching` | True/False | Overlap I/O with compute |
| `delete_original` | True/False | Delete HF cache after splitting |
| `hf_token` | string | Token for gated models |
| `layer_shards_saving_path` | path | Custom shard save location |

---

## Supported Models

AirLLM supports any HuggingFace transformer model. Explicitly tested:

- **Llama Family**: Llama-2 (7B/13B/70B), Llama-3 (8B/70B), Llama-3.1 (405B)
- **Qwen**: Qwen, Qwen2, Qwen2.5 (all sizes)
- **Mistral/Mixtral**: Mistral-7B, Mixtral-8x7B
- **Others**: ChatGLM, Baichuan, InternLM, Platypus

---

## Disk Space Requirements

First-time setup downloads the model and splits it into layer shards. You need:

| Model Size | Download | After Split | Total Needed |
|-----------|----------|-------------|--------------|
| 7B | ~14GB | ~14GB | ~28GB |
| 13B | ~26GB | ~26GB | ~52GB |
| 70B | ~140GB | ~140GB | ~280GB |
| 405B | ~800GB | ~800GB | ~1.6TB |

> **Tip**: Use `--delete-original` in `setup_model.py` to delete the original download after splitting, halving disk usage.

> **Tip**: Use `--compression 4bit` to reduce shard sizes by ~4x.

---

## Troubleshooting

### "MetadataIncompleteBuffer" Error
→ You ran out of disk space. Clear HuggingFace cache (`~/.cache/huggingface/`) and ensure sufficient free space.

### "401 Client Error... Repo model is gated"
→ The model requires authentication. Get a token from https://huggingface.co/settings/tokens and pass it via `--hf-token`.

### "ValueError: max() arg is an empty sequence"
→ You're using the wrong model class. Use `AutoModel.from_pretrained()` (this project does this by default).

### "Tokenizer does not have a padding token"
→ Already handled in this project (padding=False by default). If you modify code, set `padding=False` in tokenizer call.

### Very slow inference
→ This is expected. The bottleneck is disk I/O. Solutions:
1. Use an NVMe SSD (not HDD)
2. Enable compression (`--compression 4bit`) for 3x speedup
3. Reduce `--max-new-tokens`
4. Use a smaller model

---

## Performance Tips

1. **Use NVMe SSD**: Disk I/O is the bottleneck. NVMe gives 5-10x speedup over HDD.
2. **Enable 4-bit compression**: ~3x faster with negligible quality loss.
3. **Keep `prefetching=True`**: Overlaps disk reads with GPU compute (~10% faster).
4. **Minimize `max_new_tokens`**: Each token requires a full forward pass through all layers.
5. **Use `delete_original=True`**: Saves disk space by removing the original model files.

---

## License

This project uses the AirLLM library (Apache-2.0). Individual models have their own licenses — check the model card on HuggingFace before use.

---

## Compatibility Notes (transformers 4.57+)

AirLLM v2.11.0 was designed for older versions of `transformers`. When using **transformers 4.57+**, several patches are required (already applied in this project's venv):

| Issue | Cause | Fix Applied |
|-------|-------|-------------|
| `optimum.bettertransformer` import error | `optimum` removed BetterTransformer | Wrapped import in try/except |
| Non-sharded models fail to split | AirLLM expects model index JSON files | Added single safetensors file detection |
| Tied embeddings (lm_head) missing | Models with `tie_word_embeddings=True` skip lm_head | Load embed_tokens weights as fallback |
| `DynamicCache` object not iterable | transformers passes Cache objects instead of tuples | Added type check in `prepare_inputs_for_generation` |
| `position_embeddings` is None | Qwen2 layers now expect rotary embeddings as argument | Override `get_pos_emb_args` in AirLLMQWen2 |
| Layer returns tensor not tuple | `Qwen2DecoderLayer.forward` now returns plain tensor | Check `isinstance(result, tuple)` before indexing |
| Path type error for lm_head | `checkpoint_path` is str, persister expects Path | Wrap in `Path()` |

These patches are located in `.venv-1/Lib/site-packages/airllm/` and will be overwritten if you reinstall AirLLM.
