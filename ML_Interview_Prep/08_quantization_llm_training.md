# Quantization & LLM Training - Comprehensive Guide

## Table of Contents
1. [LLM Training Fundamentals](#training-fundamentals)
2. [Pre-training Pipeline](#pre-training)
3. [Fine-tuning Techniques](#fine-tuning)
4. [RLHF & Alignment](#rlhf)
5. [Distributed Training](#distributed-training)
6. [Quantization Fundamentals](#quantization-fundamentals)
7. [Quantization Methods](#quantization-methods)
8. [Post-Training Quantization (PTQ)](#ptq)
9. [Quantization-Aware Training (QAT)](#qat)
10. [Inference Optimization](#inference-optimization)
11. [Interview Questions with Answers](#interview-questions)
12. [Comparisons & Alternatives](#comparisons)

---

## LLM Training Fundamentals

### What is LLM Training?
Training a Large Language Model involves teaching a neural network to predict text by exposing it to massive amounts of text data.

**Layman Example:** Imagine reading millions of books, articles, and websites until you can predict the next word in any sentence. That's essentially what an LLM does during training — it becomes incredibly good at "autocomplete."

### Training Stages

```
Stage 1: Pre-training (Learn language/knowledge)
    → Massive data, massive compute, self-supervised
    
Stage 2: Supervised Fine-tuning (SFT) (Learn to follow instructions)
    → Curated instruction-response pairs
    
Stage 3: Alignment (Learn human preferences)
    → RLHF / DPO / Constitutional AI
    
Stage 4: Task-specific adaptation (Optional)
    → Domain fine-tuning, LoRA, etc.
```

### Key Training Concepts

| Concept | Description |
|---------|-------------|
| Loss Function | Cross-entropy loss (next token prediction) |
| Optimizer | AdamW (Adam with weight decay) |
| Learning Rate | Warmup → cosine decay schedule |
| Batch Size | Large (millions of tokens per batch) |
| Context Length | Max sequence length during training |
| Tokenizer | BPE/SentencePiece (subword tokenization) |

---

## Pre-training Pipeline

### Data Pipeline
```
Web Crawl → Deduplication → Quality Filtering → Tokenization → Shuffling → Training
```

#### Data Sources
- Web crawl (Common Crawl, 100TB+ raw)
- Books (Books3, Gutenberg)
- Code (GitHub, Stack Overflow)
- Scientific papers (ArXiv, PubMed)
- Wikipedia, Reddit
- Curated high-quality sources

#### Data Cleaning
- Deduplication (exact and near-duplicate removal)
- Quality filtering (perplexity-based, heuristic rules)
- Toxicity removal
- PII (Personally Identifiable Information) removal
- Language identification
- Boilerplate removal

### Scaling Laws (Chinchilla)
**Key Finding:** For compute-optimal training:
- Model size and data should scale proportionally
- Token count ≈ 20× parameter count
- Example: 7B param model needs ~140B tokens

| Model Size | Optimal Tokens | Compute (FLOPs) |
|-----------|---------------|-----------------|
| 1B | 20B | ~10^20 |
| 7B | 140B | ~10^21 |
| 70B | 1.4T | ~10^23 |
| 175B | 3.5T | ~10^24 |

### Training Objective
**Causal Language Modeling (CLM):**
```
Given: "The cat sat on the"
Predict: "mat" (next token)

Loss = -Σ log P(x_t | x_{<t})  for all positions t
```

### Training Infrastructure
- Thousands of GPUs (A100, H100, H200)
- Weeks to months of training
- Cost: $1M - $100M+
- Custom hardware clusters (AWS Trainium, Google TPU)
- High-bandwidth interconnects (NVLink, InfiniBand)

---

## Fine-tuning Techniques

### Full Fine-tuning
- Update ALL model parameters
- Requires significant GPU memory (full model + optimizer states + gradients)
- Memory: ~16-20× model size in FP32
- Best quality but most expensive

### Parameter-Efficient Fine-tuning (PEFT)

#### LoRA (Low-Rank Adaptation)
**Concept:** Instead of updating full weight matrices, add small trainable low-rank matrices.

```
Original: Y = WX
LoRA:     Y = WX + BAX
where W is frozen (original weights)
      B is d×r matrix (trainable)
      A is r×d matrix (trainable)
      r << d (rank, typically 4-64)
```

**Layman Example:** Instead of rewriting an entire encyclopedia, you add small sticky notes with corrections/additions.

**Key Parameters:**
- **r (rank):** Higher = more expressive, more parameters. Typical: 8-64
- **α (scaling):** Scales the LoRA update. ΔW = (α/r) × BA
- **Target modules:** Which layers to apply LoRA (attention Q,K,V,O; MLP)

**Advantages:**
- 0.1-1% of original parameters
- Multiple LoRA adapters for different tasks (hot-swap)
- Merges back into base model (no inference overhead)
- Trains much faster, less memory

#### QLoRA
- Quantize base model to 4-bit (NF4)
- Apply LoRA adapters in FP16/BF16
- Enables fine-tuning 65B models on single 48GB GPU
- Uses double quantization + paged optimizers

**Memory Savings:**
```
Full Fine-tune 7B model: ~120GB GPU RAM
LoRA 7B model: ~30GB GPU RAM
QLoRA 7B model: ~12GB GPU RAM
```

#### Adapters
- Small bottleneck layers inserted between transformer layers
- Down-project → ReLU → Up-project
- Only adapter parameters trained
- Slight inference overhead (extra layers)

#### Prefix Tuning
- Learn continuous "virtual tokens" prepended to each layer's input
- Base model completely frozen
- Very parameter efficient (<0.1%)

#### IA3 (Infused Adapter by Inhibiting and Amplifying)
- Learn scaling vectors for key, value, and FFN activations
- Even fewer parameters than LoRA
- Simple element-wise multiplication

### Comparison of PEFT Methods

| Method | % Params | Memory | Quality | Inference Cost |
|--------|----------|--------|---------|----------------|
| Full FT | 100% | Very High | Best | None |
| LoRA | 0.1-1% | Low | Near-full | None (merged) |
| QLoRA | 0.1-1% | Very Low | Near-full | None (merged) |
| Adapters | 1-5% | Medium | Good | Slight overhead |
| Prefix Tuning | <0.1% | Very Low | Good | Slight overhead |

---

## RLHF & Alignment

### Why Alignment?
Pre-trained models predict likely text — but likely ≠ helpful/safe/honest.

**Problem Examples:**
- Model generates toxic content (learned from internet)
- Model makes up facts (hallucination)
- Model doesn't follow instructions well
- Model gives harmful advice

### RLHF Pipeline (Reinforcement Learning from Human Feedback)

```
Step 1: Supervised Fine-Tuning (SFT)
  - Fine-tune on (instruction, response) pairs
  - Teaches model to follow instructions

Step 2: Reward Model Training
  - Humans rank multiple model responses
  - Train reward model to predict human preferences
  - Input: (prompt, response) → Score

Step 3: RL Optimization (PPO)
  - Generate response for prompts
  - Score with reward model
  - Update policy (LLM) to maximize reward
  - KL penalty from reference model (prevent deviation)
```

### DPO (Direct Preference Optimization)
**Key Innovation:** Skip reward model + RL, directly optimize from preferences.

```
Loss = -log σ(β × (log π(y_w|x)/π_ref(y_w|x) - log π(y_l|x)/π_ref(y_l|x)))
where y_w = preferred response, y_l = rejected response
```

**Advantages over RLHF:**
- Simpler (no reward model, no RL)
- More stable training
- Theoretically equivalent to RLHF
- Fewer hyperparameters

### Other Alignment Methods

| Method | Approach | Key Feature |
|--------|----------|-------------|
| RLHF (PPO) | RL with reward model | Original, proven at scale |
| DPO | Direct preference pairs | Simpler, no RL needed |
| ORPO | Odds ratio preference | Single-stage alignment |
| KTO | Kahneman-Tversky optimization | Only needs good/bad labels |
| Constitutional AI | Self-critique with principles | No human labels needed |
| SPIN | Self-play fine-tuning | Model plays against itself |

### Constitutional AI (Anthropic)
1. Define principles (constitution): "Be helpful, harmless, honest"
2. Model generates response
3. Model critiques own response against principles
4. Model revises response
5. Train on revised responses
- Reduces need for human labelers
- Used by Claude

---

## Distributed Training

### Why Distributed?
- Models don't fit in single GPU memory (70B params = 140GB in FP16)
- Training data is massive (trillions of tokens)
- Need to train in reasonable time

### Parallelism Strategies

#### 1. Data Parallelism (DP)
- Same model on each GPU, different data batches
- Aggregate gradients across GPUs
- Scales throughput linearly
- **Limitation:** Each GPU must hold full model

#### 2. Fully Sharded Data Parallelism (FSDP / ZeRO)
- Shard model parameters, gradients, and optimizer states across GPUs
- Each GPU holds only a fraction of the model
- Gather parameters just-in-time for computation
- **ZeRO Stages:**
  - Stage 1: Shard optimizer states (4× memory reduction)
  - Stage 2: + Shard gradients (8× reduction)
  - Stage 3: + Shard parameters (linear scaling)

#### 3. Tensor Parallelism (TP)
- Split individual layers across GPUs
- Example: Split attention heads across 8 GPUs
- Requires high-bandwidth connection (intra-node NVLink)
- Low-latency communication critical

#### 4. Pipeline Parallelism (PP)
- Different layers on different GPUs
- GPU 1: Layers 1-8, GPU 2: Layers 9-16, etc.
- Micro-batching to reduce bubble time
- **Bubble:** Time when GPUs are idle waiting for dependencies

#### 5. Expert Parallelism
- For Mixture-of-Experts models
- Different experts on different GPUs
- Only activate relevant experts per token

### Typical Setup for Large Models

| Model Size | Parallelism Strategy |
|-----------|---------------------|
| < 7B | FSDP or single GPU + DeepSpeed |
| 7-13B | FSDP ZeRO-3 across 4-8 GPUs |
| 13-70B | FSDP + TP (hybrid parallelism) |
| 70B+ | TP + PP + DP (3D parallelism) |

### Key Frameworks
- **DeepSpeed (Microsoft):** ZeRO, pipeline, tensor parallelism
- **FSDP (PyTorch):** Native distributed training
- **Megatron-LM (NVIDIA):** Highly optimized for NVIDIA hardware
- **ColossalAI:** Easy-to-use distributed training
- **AWS SageMaker:** Managed training with built-in parallelism

### Mixed Precision Training
- **FP32 (32-bit):** Full precision, baseline
- **FP16 (16-bit):** Half memory, 2× speed, but overflow risk
- **BF16 (Brain Float 16):** Same dynamic range as FP32, half memory
- **TF32:** NVIDIA Ampere+ tensor core format

**Best Practice:** BF16 for training (if supported), FP16 with loss scaling otherwise.

---

## Quantization Fundamentals

### What is Quantization?
Reducing the precision of model weights (and/or activations) from high-bit (FP32/FP16) to lower-bit (INT8/INT4) representations.

**Layman Example:** Like converting a high-resolution photo to a smaller file size. You lose some quality, but it takes much less storage space and loads faster. Quantization does this for model weights — less memory, faster inference, with minimal quality loss.

### Why Quantize?

| Benefit | Impact |
|---------|--------|
| Memory reduction | 4× less (FP16→INT4) |
| Faster inference | 2-4× speedup |
| Lower cost | Smaller GPUs, less hardware |
| Edge deployment | Run on phones, laptops |
| Larger batch sizes | More requests per GPU |

### Number Representations

```
FP32: 1 sign + 8 exponent + 23 mantissa = 32 bits
FP16: 1 sign + 5 exponent + 10 mantissa = 16 bits
BF16: 1 sign + 8 exponent + 7 mantissa = 16 bits
INT8: 8-bit integer (-128 to 127 or 0 to 255)
INT4: 4-bit integer (-8 to 7 or 0 to 15)
NF4:  4-bit Normal Float (optimally distributed for neural network weights)
```

### Memory Requirements

| Precision | 7B Model | 13B Model | 70B Model |
|-----------|----------|-----------|-----------|
| FP32 | 28 GB | 52 GB | 280 GB |
| FP16/BF16 | 14 GB | 26 GB | 140 GB |
| INT8 | 7 GB | 13 GB | 70 GB |
| INT4 | 3.5 GB | 6.5 GB | 35 GB |

### Key Quantization Concepts

#### Symmetric vs Asymmetric
- **Symmetric:** Zero point is 0. Range: [-α, α] → [-127, 127]
  - q = round(x / scale)
  - Simpler, faster
- **Asymmetric:** Zero point can be offset. Range: [β, α] → [0, 255]
  - q = round(x / scale) + zero_point
  - Better for activations (often non-symmetric)

#### Granularity
- **Per-tensor:** One scale factor for entire tensor (fastest, least accurate)
- **Per-channel:** One scale per output channel (good balance)
- **Per-group:** One scale per group of weights (e.g., 128 weights) — most accurate
- **Per-token:** Scale per token for activations

#### Calibration
- Process of determining optimal quantization parameters (scale, zero point)
- Run representative data through model
- Collect activation statistics (min, max, distribution)
- Choose parameters that minimize quantization error

---

## Quantization Methods

### Overview of Methods

| Method | Type | Bits | Key Innovation |
|--------|------|------|----------------|
| LLM.int8() | PTQ | 8-bit | Mixed precision for outliers |
| GPTQ | PTQ | 4-bit | Layer-wise optimal quantization |
| AWQ | PTQ | 4-bit | Activation-aware weight quantization |
| GGUF/GGML | PTQ | 2-8 bit | CPU-optimized, multiple quant levels |
| SqueezeLLM | PTQ | 3-4 bit | Non-uniform quantization |
| QuIP# | PTQ | 2-4 bit | Incoherence processing |
| QAT | Training | Any | Train with quantization-aware |
| NF4 (QLoRA) | PTQ | 4-bit | Information-theoretically optimal for normal dist |

### LLM.int8() (bitsandbytes)
- Most weights quantized to INT8
- **Key insight:** Some activations have extreme outliers (>6σ)
- Outlier features (~0.1% of dims) kept in FP16
- Mixed-precision decomposition: INT8 for normal, FP16 for outliers
- No quality loss on most models

### GPTQ (Generalized Post-Training Quantization)
- Layer-by-layer quantization
- Minimizes layer reconstruction error
- Uses Hessian information (second-order) to determine quantization order
- Quantize most impactful weights more carefully
- **Process:**
  1. For each layer: find optimal quantization that minimizes output error
  2. Compensate quantization error in remaining weights
  3. Move to next layer
- Very fast inference with custom CUDA kernels

### AWQ (Activation-Aware Weight Quantization)
- **Key insight:** Not all weights are equally important
- Weights connected to large activations are more important
- Protect important weights: scale them up before quantization
- 4-bit with minimal quality loss
- Doesn't need calibration data (uses activation magnitudes)
- Faster inference than GPTQ

### GGUF/GGML (llama.cpp)
- CPU-optimized quantization format
- Multiple quantization levels: Q2_K, Q3_K, Q4_K, Q5_K, Q6_K, Q8_0
- K-quant: Per-group quantization with different bit-widths for different groups
- Runs on consumer hardware (laptops, phones)
- Used by: llama.cpp, Ollama, LM Studio

### Comparison of PTQ Methods

| Method | Quality (4-bit) | Speed | Memory | Ease of Use |
|--------|-----------------|-------|--------|-------------|
| GPTQ | Good | Fast (GPU) | Low | Medium |
| AWQ | Very Good | Very Fast (GPU) | Low | Easy |
| GGUF Q4_K_M | Good | Medium (CPU/GPU) | Low | Easy |
| LLM.int8() | Excellent (8-bit) | Medium | Medium | Easy |
| NF4 (QLoRA) | Good | Medium | Very Low | Easy |

---

## Post-Training Quantization (PTQ)

### Process
```
Trained Model (FP16) → Calibration Data → Quantization Algorithm → 
Quantized Model (INT4/INT8) → Evaluate Quality
```

### Calibration Strategies
1. **Min-Max:** Use min/max values of weights/activations
   - Simple but sensitive to outliers
2. **Percentile:** Use 99.9th percentile (clip outliers)
   - More robust
3. **MSE minimization:** Find scale that minimizes mean squared error
   - Best quality
4. **Entropy-based:** Minimize KL divergence between original and quantized distributions

### Weight-Only vs Weight+Activation Quantization

| Approach | Quantize | Speed Gain | Quality | Complexity |
|----------|----------|-----------|---------|------------|
| Weight-only | Weights only | 2-4× memory, 1.5-2× speed | Higher | Lower |
| W8A8 | Weights + Activations (8-bit) | 2× | Good | Medium |
| W4A16 | Weights 4-bit, Acts 16-bit | 2-4× memory | Good | Low |
| W4A8 | Weights 4-bit, Acts 8-bit | Best speed | Lower | High |

### Key Insight: Why weight-only quantization works
- Weights are STATIC (known after training) → can carefully quantize
- Activations are DYNAMIC (change per input) → harder to quantize
- Modern trend: Quantize weights aggressively (4-bit), keep activations in higher precision

---

## Quantization-Aware Training (QAT)

### Concept
Simulate quantization during training so the model learns to be robust to quantization effects.

```
Forward pass: Fake quantize weights → compute → fake quantize activations
Backward pass: Straight-Through Estimator (STE) for gradients
```

### Straight-Through Estimator (STE)
- Quantization is non-differentiable (step function)
- STE: Pretend the gradient through quantization is 1 (identity)
- Allows backpropagation through quantized operations
- Works surprisingly well in practice

### QAT vs PTQ

| Aspect | PTQ | QAT |
|--------|-----|-----|
| Training cost | None (post-hoc) | Significant (re-train) |
| Quality at low bits | Degrades at 4-bit | Maintains at 4-bit |
| Data needed | Small calibration set | Full training data |
| Time | Minutes to hours | Hours to days |
| Best for | 8-bit, large models | <4-bit, quality-critical |

### When to Use QAT
- Target precision ≤ 4 bits
- PTQ quality is insufficient
- Have compute budget for training
- Quality is critical (edge deployment where you can't use larger model)
- Specific hardware constraints (INT4/INT2 only)

---

## Inference Optimization

### Beyond Quantization

#### 1. KV-Cache
- Cache key-value tensors from previous tokens
- Avoid recomputing attention for past tokens
- Memory: O(batch × layers × seq_len × dim)
- **Challenge:** KV-cache grows linearly with sequence length

#### 2. KV-Cache Quantization
- Quantize cached KV tensors to INT8/INT4
- Reduces memory for long sequences
- Minimal quality impact

#### 3. Flash Attention
- IO-aware attention algorithm
- Reduces memory from O(n²) to O(n)
- 2-4× faster attention computation
- Exact (no approximation)

#### 4. Speculative Decoding
- Small "draft" model generates candidate tokens quickly
- Large model verifies multiple tokens in one forward pass
- 2-3× speedup with no quality loss
- Works best when draft model is highly accurate

```
Draft model: Generates 5 candidate tokens fast
Target model: Verifies all 5 in one pass
Accept: First 3 correct → emit them, regenerate from position 4
```

#### 5. Continuous Batching
- Dynamic batching of requests
- Don't wait for longest sequence to finish
- New requests can start as slots free up
- Better GPU utilization

#### 6. PagedAttention (vLLM)
- Manages KV-cache like virtual memory pages
- Reduces memory fragmentation
- Enables larger batch sizes
- Foundation of vLLM's efficiency

#### 7. Tensor Parallelism for Inference
- Split model across GPUs for large models
- Lower latency than pipeline parallelism
- NVLink bandwidth critical

### Inference Frameworks

| Framework | Key Feature | Best For |
|-----------|-------------|----------|
| vLLM | PagedAttention, continuous batching | High-throughput serving |
| TensorRT-LLM | NVIDIA-optimized, best perf | NVIDIA GPUs |
| llama.cpp (GGUF) | CPU/edge, quantization | Consumer hardware |
| TGI (HuggingFace) | Easy deployment, batching | HF model serving |
| SGLang | Structured generation, fast | Constrained output |
| Ollama | Simple local deployment | Local development |
| Triton (NVIDIA) | Multi-model serving | Production ML systems |

### Serving Architecture
```
Load Balancer → Request Queue → Inference Engine
                                    ├── Model (quantized, tensor-parallel)
                                    ├── KV-Cache (paged)
                                    ├── Continuous Batcher
                                    └── Token Streaming
```

---

## Interview Questions with Answers

### Q1: Explain the difference between FP16, BF16, and FP32 training
**Answer:**
- **FP32 (32-bit):** Full precision. Exact gradients. Slow, high memory. Rarely used alone now.
- **FP16 (16-bit):** Half precision. 2× speed, half memory. Risk of overflow/underflow due to limited range (max ~65K). Requires loss scaling.
- **BF16 (Brain Float 16):** Same range as FP32 (8 exponent bits) but less precision (7 mantissa). No overflow risk. Ideal for training. Requires Ampere+ GPUs.
- **Mixed Precision:** Forward pass in FP16/BF16, master weights in FP32. Best of both worlds.
- **Best practice:** BF16 for training (Ampere+ GPUs), FP16 with loss scaling for older GPUs.

### Q2: What is the difference between GPTQ, AWQ, and GGUF?
**Answer:**
- **GPTQ:** Layer-wise optimal quantization using Hessian info. Best for GPU inference. Custom CUDA kernels. Good quality at 4-bit.
- **AWQ:** Activation-aware scaling before quantization. Protects important weights. Faster inference than GPTQ. Slightly better quality.
- **GGUF:** CPU-optimized format with multiple quantization levels. Runs on consumer hardware. Used by llama.cpp/Ollama. Various quality levels (Q4_K_M, Q5_K, etc.).
- **Choice:** GPU + speed → AWQ; GPU + compatibility → GPTQ; CPU/laptop → GGUF

### Q3: How does LoRA work and why is rank important?
**Answer:**
- LoRA decomposes weight update: ΔW = B×A (where B is d×r, A is r×d)
- Rank r controls expressiveness:
  - Low rank (4-8): Few parameters, fast, may underfit complex tasks
  - Medium rank (16-32): Good balance for most tasks
  - High rank (64-128): More expressive, closer to full fine-tune
- Total trainable params: 2 × d × r per adapted layer
- Applied to attention weights (Q, K, V, O) and sometimes MLP layers
- **Key insight:** Most weight updates during fine-tuning are low-rank in nature (empirically shown)

### Q4: Explain the training instabilities in LLMs and solutions
**Answer:**
- **Loss spikes:** Sudden loss increases during training
  - Solution: Lower LR, gradient clipping, skip bad batches
- **Divergence:** Loss goes to infinity
  - Solution: Warmup, smaller LR, check data quality
- **NaN gradients:** Numerical overflow
  - Solution: Mixed precision with loss scaling, gradient clipping, BF16
- **Slow convergence:**
  - Solution: Learning rate schedule (warmup + cosine decay), better initialization
- **Best practices:**
  - Gradient clipping (max_norm=1.0)
  - LR warmup (1-5% of steps)
  - AdamW optimizer (β1=0.9, β2=0.95)
  - Weight decay (0.1)
  - BF16 training

### Q5: What is ZeRO and how does it save memory?
**Answer:**
ZeRO (Zero Redundancy Optimizer) eliminates memory redundancy in data parallelism:

Without ZeRO (N GPUs): Each GPU holds FULL copy of:
- Model params (Ψ)
- Gradients (Ψ)  
- Optimizer states (12Ψ for Adam in FP32): momentum + variance + FP32 copy

**ZeRO Stages:**
- **Stage 1:** Shard optimizer states → 4× savings
- **Stage 2:** + Shard gradients → 8× savings
- **Stage 3:** + Shard parameters → Linear scaling with # GPUs

Example: 7B model, 8 GPUs
- Without ZeRO: Each GPU needs ~56GB (full copy)
- ZeRO-3: Each GPU needs ~7GB (1/8 of everything)

### Q6: What is the difference between PTQ and QAT?
**Answer:**
| Aspect | PTQ (Post-Training) | QAT (Quantization-Aware) |
|--------|---------------------|--------------------------|
| When | After training | During training |
| Cost | Cheap (minutes-hours) | Expensive (needs GPU training) |
| Quality (8-bit) | Excellent | Slightly better |
| Quality (4-bit) | Good (with GPTQ/AWQ) | Better |
| Quality (2-bit) | Poor | Usable |
| Data needed | Small calibration set | Full training data |
| Use case | Most deployments | Ultra-low bit, quality-critical |

### Q7: How does speculative decoding work?
**Answer:**
1. **Draft model** (small, fast) generates K candidate tokens autoregressively
2. **Target model** (large, accurate) verifies all K tokens in ONE forward pass
   - Compute probability of each draft token under target model
   - Accept tokens sequentially until one is rejected
3. **Acceptance criteria:** If target_prob ≥ draft_prob, accept
   - Otherwise accept with probability target_prob/draft_prob
4. Regenerate from rejection point
- **Result:** 2-3× tokens per second with zero quality loss
- **Key:** Draft model must be fast AND accurate enough (high acceptance rate)

### Q8: Explain the KV-cache and why it's a bottleneck
**Answer:**
- **What:** During autoregressive generation, cache key/value tensors from all previous tokens
- **Why:** Avoid recomputing attention over the entire sequence at each step
- **Size per token:** 2 × n_layers × d_model × sizeof(dtype)
  - For 7B model in FP16: 2 × 32 × 4096 × 2 = 512KB per token
  - For 1024 tokens: 512MB per sequence
- **Bottleneck because:**
  - Grows linearly with sequence length
  - Limits batch size (memory competition)
  - Long sequences (128K+) require tens of GB
- **Solutions:**
  - KV-cache quantization (INT8/INT4)
  - PagedAttention (avoid fragmentation)
  - Multi-Query Attention (shared KV heads)
  - Grouped-Query Attention (fewer KV heads)
  - Sliding window attention (fixed cache size)

### Q9: What are Mixture of Experts (MoE) and how do they relate to efficiency?
**Answer:**
- **Concept:** Multiple "expert" FFN layers, only activate subset per token
- **Router:** Small network decides which experts process each token
- **Example:** Mixtral 8x7B: 8 experts, 2 active per token
  - Total params: ~46B, Active params per token: ~13B
  - Quality of ~70B model with cost of ~13B inference
- **Advantages:**
  - More parameters without proportional compute increase
  - Experts specialize in different types of content
  - Better scaling efficiency
- **Challenges:**
  - Load balancing (some experts used more)
  - Expert parallelism needed for training
  - Larger memory footprint (all experts loaded)
  - Communication overhead in distributed setting

### Q10: How would you deploy a 70B parameter model for production?
**Answer:**
```
Strategy:
1. Quantization: AWQ 4-bit → reduces to ~35GB (fits on 2× A100 40GB or 1× A100 80GB)
2. Tensor Parallelism: Split across 2 GPUs if needed
3. Serving Framework: vLLM with PagedAttention
4. Optimizations:
   - Continuous batching
   - KV-cache quantization (INT8)
   - Flash Attention
   - Speculative decoding (if latency-critical)
5. Infrastructure:
   - Auto-scaling based on queue length
   - Request routing and load balancing
   - Monitoring: latency p50/p99, throughput, GPU utilization
6. Cost optimization:
   - Spot instances for batch workloads
   - Right-size GPU selection
   - Consider smaller model + RAG if quality sufficient
```

---

## Comparisons & Alternatives

### Training Compute Estimation
```
FLOPs ≈ 6 × N × D
where N = number of parameters
      D = number of training tokens

Training time ≈ FLOPs / (num_gpus × gpu_flops × utilization)
```

Example: 7B model, 140B tokens, 8× A100 GPUs:
- FLOPs = 6 × 7B × 140B ≈ 5.9 × 10²¹
- A100 BF16: 312 TFLOPS, utilization ~40%
- Time ≈ 5.9×10²¹ / (8 × 312×10¹² × 0.4) ≈ 6 × 10⁶ seconds ≈ 70 days

### Model Size vs Quality Trade-off

| Model Size | Quantization | GPU Needed | Quality |
|-----------|-------------|-----------|---------|
| 7B FP16 | None | 1× A100 40GB | Good |
| 7B INT4 | GPTQ/AWQ | 1× RTX 4090 | Good (95%+) |
| 13B INT4 | GPTQ/AWQ | 1× RTX 4090 | Better |
| 70B INT4 | AWQ | 2× A100 40GB | Excellent |
| 70B INT8 | LLM.int8() | 2× A100 80GB | Excellent+ |

### Hardware Landscape (2024-2025)

| GPU | VRAM | BF16 TFLOPS | Best For |
|-----|------|-------------|----------|
| RTX 4090 | 24GB | 83 | Local dev, small models |
| A100 40GB | 40GB | 312 | Training, inference |
| A100 80GB | 80GB | 312 | Large model training |
| H100 | 80GB | 990 | Cutting-edge training |
| H200 | 141GB | 990 | Very large models |
| AWS Trainium2 | 96GB | Custom | Cost-effective training |
| Google TPU v5 | 96GB | Custom | Large-scale training |

### Emerging Trends
1. **1-bit LLMs (BitNet):** Binary/ternary weights with minimal quality loss
2. **Mixture of Depths:** Skip layers for easy tokens (adaptive compute)
3. **Ring Attention:** Distributed attention across GPUs for very long context
4. **Knowledge Distillation:** Train smaller models to match larger ones
5. **Synthetic data training:** Use large models to generate training data for smaller ones
6. **Unsloth/TRL:** Efficient fine-tuning frameworks
7. **FP8 training:** Native on H100, between BF16 and INT8

### Quick Decision Guide

**"I want to run LLMs locally"** → GGUF quantization + Ollama/llama.cpp

**"I want to fine-tune on my data"** → QLoRA (4-bit base + LoRA adapters)

**"I want fastest production inference"** → AWQ + vLLM or TensorRT-LLM

**"I want to train from scratch"** → Megatron-LM/DeepSpeed + BF16 + ZeRO-3

**"I want best quality with size constraint"** → Larger model + aggressive quantization beats smaller model + full precision
