# NLP - Deep Learning Comprehensive Guide

## Table of Contents
1. [NLP Foundations](#foundations)
2. [Word Embeddings](#word-embeddings)
3. [Recurrent Neural Networks](#rnns)
4. [Attention Mechanism](#attention)
5. [Transformer Architecture](#transformers)
6. [Pre-trained Language Models](#pretrained-models)
7. [Key NLP Tasks & Architectures](#nlp-tasks)
8. [Tokenization Strategies](#tokenization)
9. [Fine-tuning Strategies](#fine-tuning)
10. [Interview Questions with Answers](#interview-questions)
11. [Comparisons & Alternatives](#comparisons)

---

## NLP Foundations

### What is NLP?
Natural Language Processing enables machines to understand, interpret, and generate human language.

**Layman Example:** Teaching a computer to read, write, and have conversations — like Siri understanding "What's the weather?" or Gmail auto-completing your emails.

### Key NLP Tasks

| Task | Description | Example |
|------|-------------|---------|
| Text Classification | Assign label to text | Sentiment analysis, spam detection |
| Named Entity Recognition | Identify entities | "Apple" → Organization |
| Machine Translation | Translate between languages | English → French |
| Question Answering | Answer questions from context | Reading comprehension |
| Text Summarization | Condense long text | News article → bullet points |
| Text Generation | Generate coherent text | ChatGPT, story writing |
| Relation Extraction | Find relationships between entities | "CEO of" between person & company |

### Traditional NLP Pipeline
```
Raw Text → Tokenization → Stopword Removal → Stemming/Lemmatization → 
Feature Extraction (BoW/TF-IDF) → ML Model
```

### Traditional Feature Representations
- **Bag of Words (BoW):** Word count vector (ignores order)
- **TF-IDF:** Term Frequency × Inverse Document Frequency (weighs rare words higher)
- **N-grams:** Sequences of N consecutive words

**Limitations:** High dimensionality, no semantic meaning, ignores word order, sparse representations

---

## Word Embeddings

### Word2Vec (2013)
**Concept:** Learn dense vector representations where similar words have similar vectors.

**Two Architectures:**
1. **CBOW (Continuous Bag of Words):** Predict center word from context
2. **Skip-gram:** Predict context words from center word

**Layman Example:** Words are like people — you can understand a person by the company they keep. "King" and "Queen" appear in similar contexts, so they get similar vectors.

**Key Properties:**
- Vector arithmetic: King - Man + Woman ≈ Queen
- Captures semantic relationships
- Typically 100-300 dimensions
- Trained on large text corpora

### GloVe (2014)
- Global Vectors: Combines global co-occurrence statistics with local context
- Factorizes word co-occurrence matrix
- Often better for analogy tasks than Word2Vec

### FastText (2016)
- Extension of Word2Vec using subword (character n-gram) information
- Can handle out-of-vocabulary (OOV) words
- Better for morphologically rich languages

### Limitations of Static Embeddings
- One vector per word regardless of context
- "Bank" (river) = "Bank" (financial) — same vector!
- Solved by contextual embeddings (ELMo, BERT)

---

## Recurrent Neural Networks (RNNs)

### Vanilla RNN
**Concept:** Process sequences by maintaining hidden state that carries information across time steps.

**Formula:** h_t = tanh(W_hh × h_{t-1} + W_xh × x_t + b)

**Layman Example:** Like reading a book — you remember what happened in previous pages to understand the current page.

**Problem:** Vanishing/exploding gradients for long sequences.

### LSTM (Long Short-Term Memory)
**Concept:** Adds gating mechanisms to control information flow.

**Gates:**
1. **Forget Gate:** What to discard from cell state → f_t = σ(W_f × [h_{t-1}, x_t] + b_f)
2. **Input Gate:** What new info to store → i_t = σ(W_i × [h_{t-1}, x_t] + b_i)
3. **Output Gate:** What to output → o_t = σ(W_o × [h_{t-1}, x_t] + b_o)
4. **Cell State Update:** C_t = f_t ⊙ C_{t-1} + i_t ⊙ tanh(W_c × [h_{t-1}, x_t] + b_c)

**Layman Example:** Like a conveyor belt (cell state) with workers who decide what to add, keep, or remove from the belt at each station.

**Why LSTM Solves Vanishing Gradients:**
- Cell state provides "highway" for gradients
- Additive updates (not multiplicative)
- Gates learn to preserve important information

### GRU (Gated Recurrent Unit)
- Simplified LSTM with 2 gates (reset, update) instead of 3
- Comparable performance, fewer parameters
- Faster to train

**Gates:**
1. **Reset Gate:** How much past info to forget
2. **Update Gate:** How much past info to keep

### Bidirectional RNNs
- Process sequence in both directions (forward + backward)
- Captures both past and future context
- Output = concatenation of forward and backward hidden states
- Essential for tasks like NER where right context matters

### Seq2Seq (Encoder-Decoder)
- **Encoder:** Processes input sequence → fixed-length context vector
- **Decoder:** Generates output sequence from context vector
- Used for: Translation, summarization, dialogue

**Problem:** Fixed-length bottleneck loses information for long sequences → Solved by attention

---

## Attention Mechanism

### Concept
Allow the decoder to "look back" at all encoder states, not just the final one.

**Layman Example:** When translating a sentence, instead of memorizing the whole sentence first, you look back at specific words as needed — like a human translator glancing at the source text.

### Types of Attention

#### Bahdanau Attention (Additive, 2014)
```
score(s_t, h_i) = v^T × tanh(W_1 × s_t + W_2 × h_i)
```
- Learnable alignment between decoder state and encoder states

#### Luong Attention (Multiplicative, 2015)
```
score(s_t, h_i) = s_t^T × W × h_i  (General)
score(s_t, h_i) = s_t^T × h_i        (Dot product)
```
- Simpler, faster computation

### Attention Computation Steps
1. Compute alignment scores between decoder state and all encoder states
2. Apply softmax to get attention weights (sum to 1)
3. Compute context vector as weighted sum of encoder states
4. Combine context vector with decoder state for prediction

### Self-Attention
- Each position attends to all positions in the same sequence
- Captures intra-sequence relationships
- Foundation of the Transformer architecture
- No sequential processing → fully parallelizable

---

## Transformer Architecture

### "Attention Is All You Need" (2017)
Revolutionary architecture that replaces RNNs entirely with self-attention.

### Architecture Overview
```
Input Embedding + Positional Encoding
        ↓
[Encoder Block × N]
  - Multi-Head Self-Attention
  - Add & Norm (Residual)
  - Feed-Forward Network
  - Add & Norm (Residual)
        ↓
[Decoder Block × N]
  - Masked Multi-Head Self-Attention
  - Add & Norm
  - Cross-Attention (attend to encoder output)
  - Add & Norm
  - Feed-Forward Network
  - Add & Norm
        ↓
Linear + Softmax → Output
```

### Multi-Head Self-Attention

**Scaled Dot-Product Attention:**
```
Attention(Q, K, V) = softmax(QK^T / √d_k) × V
```

**Why scale by √d_k?** Without scaling, dot products grow large in high dimensions, pushing softmax into regions with tiny gradients.

**Multi-Head:**
- Run attention multiple times in parallel (different learned projections)
- Each head can attend to different aspects (syntax, semantics, position)
- Concatenate heads and project

```
MultiHead(Q, K, V) = Concat(head_1, ..., head_h) × W_O
where head_i = Attention(QW_i^Q, KW_i^K, VW_i^V)
```

### Positional Encoding
**Why needed:** Self-attention is permutation-invariant (no notion of order).

**Sinusoidal (Original):**
```
PE(pos, 2i) = sin(pos / 10000^(2i/d_model))
PE(pos, 2i+1) = cos(pos / 10000^(2i/d_model))
```

**Alternatives:**
- Learned positional embeddings (BERT, GPT)
- Rotary Position Embeddings (RoPE) — used in LLaMA, modern LLMs
- ALiBi (Attention with Linear Biases)

### Feed-Forward Network
- Two linear layers with ReLU/GELU activation
- FFN(x) = max(0, xW_1 + b_1)W_2 + b_2
- Applied position-wise (same weights for each position)
- Typically d_ff = 4 × d_model

### Key Innovations
- **Parallelization:** No sequential dependency (unlike RNN)
- **Long-range dependencies:** Direct connections between any positions
- **Multi-head attention:** Multiple representation subspaces
- **Residual connections + Layer Norm:** Stable deep training

**Follow-up Q: Why is Transformer better than RNN?**
| Aspect | RNN/LSTM | Transformer |
|--------|----------|-------------|
| Parallelization | Sequential (slow) | Fully parallel |
| Long-range deps | Struggles (gradient) | Direct attention |
| Training speed | Slow | Fast (GPU-friendly) |
| Context window | Theoretically infinite | Fixed (but large) |
| Memory | O(1) per step | O(n²) for attention |

---

## Pre-trained Language Models

### ELMo (2018)
- Bidirectional LSTM language model
- Context-dependent embeddings (first contextual embeddings)
- Used as feature extractor (frozen)

### BERT (2019) — Bidirectional Encoder

**Pre-training Tasks:**
1. **Masked Language Modeling (MLM):** Mask 15% of tokens, predict them
2. **Next Sentence Prediction (NSP):** Predict if sentence B follows A

**Architecture:** Transformer Encoder only (bidirectional)

**Key Properties:**
- BERT-Base: 12 layers, 768 hidden, 12 heads, 110M params
- BERT-Large: 24 layers, 1024 hidden, 16 heads, 340M params
- Input: [CLS] + tokens + [SEP] (+ tokens + [SEP] for pairs)
- [CLS] token embedding used for classification tasks

**BERT Variants:**
- RoBERTa: Better training (no NSP, more data, dynamic masking)
- ALBERT: Parameter sharing (lighter)
- DistilBERT: Knowledge distillation (smaller, faster)
- DeBERTa: Disentangled attention (position + content separately)

### GPT Family — Autoregressive Decoder

**Pre-training Task:** Next token prediction (causal language modeling)

**Architecture:** Transformer Decoder only (left-to-right, causal mask)

**Evolution:**
- GPT-1 (2018): 117M params
- GPT-2 (2019): 1.5B params
- GPT-3 (2020): 175B params (few-shot learning)
- GPT-4 (2023): Multimodal, ~1.8T params (estimated)

**Key Insight:** Scale + autoregressive pretraining → emergent abilities

### T5 (Text-to-Text Transfer Transformer)
- Frames ALL NLP tasks as text-to-text
- "translate English to French: The house is wonderful" → "La maison est merveilleuse"
- "summarize: ..." → summary
- Encoder-Decoder architecture
- Unified framework for all tasks

### Modern LLMs
- **LLaMA/LLaMA 2/3 (Meta):** Open-source, efficient
- **Mistral/Mixtral:** Mixture of Experts, efficient
- **Claude (Anthropic):** Constitutional AI
- **Gemini (Google):** Multimodal
- **Command (Cohere):** Enterprise-focused

---

## Key NLP Tasks & Architectures

### Text Classification
- **BERT approach:** [CLS] token → linear layer → softmax
- **Fine-tune pretrained model** on labeled data
- Applications: sentiment, topic, intent classification

### Named Entity Recognition (NER)
- Token-level classification
- BIO/BIOES tagging scheme: B-PER, I-PER, O
- **BERT approach:** Token embeddings → linear layer → CRF (optional)

### Question Answering
- **Extractive QA:** Find answer span in context
  - BERT: Predict start and end positions
- **Generative QA:** Generate answer from context
  - T5, GPT: Generate text answer
- **Open-domain QA:** Retrieve relevant passages first, then extract/generate

### Machine Translation
- Seq2Seq with attention → Transformer (encoder-decoder)
- BLEU score for evaluation
- Modern: Large language models with prompting

### Text Summarization
- **Extractive:** Select important sentences
- **Abstractive:** Generate new summary text (encoder-decoder models)
- Metrics: ROUGE-1, ROUGE-2, ROUGE-L

### Sequence Labeling
- POS tagging, NER, chunking
- BiLSTM + CRF or BERT + CRF
- CRF ensures valid tag sequences (B-PER can't follow B-LOC without O)

---

## Tokenization Strategies

### Word-Level
- Simple but large vocabulary, OOV problem
- "unhappiness" → ["unhappiness"] (if in vocab) or [UNK]

### Character-Level
- Small vocabulary, handles any word
- Very long sequences, harder to learn semantics

### Subword Tokenization (Modern Standard)

#### BPE (Byte-Pair Encoding)
- Start with characters, iteratively merge most frequent pairs
- "unhappiness" → ["un", "happiness"] or ["un", "happi", "ness"]
- Used by GPT-2, RoBERTa

#### WordPiece
- Similar to BPE but maximizes likelihood instead of frequency
- Uses "##" prefix for continuation tokens
- "unhappiness" → ["un", "##happi", "##ness"]
- Used by BERT

#### SentencePiece/Unigram
- Treats input as raw stream (no pre-tokenization)
- Language-agnostic (works for any language)
- Used by T5, LLaMA, XLNet

### Tokenization Comparison

| Method | Vocabulary Size | OOV Handling | Example Models |
|--------|----------------|--------------|----------------|
| Word | 100K+ | Poor | Early models |
| BPE | 30K-50K | Good | GPT-2, LLaMA |
| WordPiece | 30K | Good | BERT |
| SentencePiece | 32K-64K | Excellent | T5, LLaMA |

---

## Fine-tuning Strategies

### Full Fine-tuning
- Update all parameters of pretrained model
- Requires significant compute and data
- Risk of catastrophic forgetting

### Parameter-Efficient Fine-tuning (PEFT)

#### LoRA (Low-Rank Adaptation)
- Add low-rank decomposition matrices to attention weights
- W_new = W_pretrained + BA (where B is d×r and A is r×d, r << d)
- Only train A and B (0.1-1% of parameters)
- Merges back into model at inference (no latency cost)

#### Adapters
- Small bottleneck layers inserted between transformer layers
- Only adapter parameters are trained
- Original model frozen

#### Prefix Tuning / Prompt Tuning
- Learn soft prompts (continuous embeddings) prepended to input
- Model parameters frozen
- Very parameter-efficient

### Comparison of Fine-tuning Methods

| Method | % Params Trained | Quality | Speed | Memory |
|--------|-----------------|---------|-------|--------|
| Full Fine-tune | 100% | Best | Slow | High |
| LoRA | 0.1-1% | Near-full | Fast | Low |
| Adapters | 1-5% | Good | Medium | Medium |
| Prompt Tuning | <0.01% | Good for similar tasks | Fast | Very Low |

### Instruction Tuning
- Fine-tune on instruction-response pairs
- Makes model follow human instructions
- FLAN-T5, InstructGPT, Alpaca

### RLHF (Reinforcement Learning from Human Feedback)
1. Supervised fine-tuning on demonstrations
2. Train reward model on human preferences
3. Optimize policy (LLM) using PPO against reward model
- Used by ChatGPT, Claude

---

## Interview Questions with Answers

### Q1: Explain the self-attention mechanism step by step
**Answer:**
1. For each token, compute Query (Q), Key (K), Value (V) via learned projections
2. Compute attention scores: score_ij = Q_i · K_j (how much position i attends to j)
3. Scale by √d_k to prevent large magnitudes
4. Apply softmax to get weights (sum to 1 per position)
5. Compute weighted sum of Values: output_i = Σ(weight_ij × V_j)
6. Each position gets a new representation that's a weighted mix of all positions

### Q2: Why does BERT use masked language modeling instead of standard LM?
**Answer:**
- Standard LM is left-to-right (can only attend to previous tokens)
- MLM allows bidirectional context (both left AND right)
- Bidirectional understanding is critical for tasks like NER, QA
- But can't be used for generation (GPT uses causal LM for that)
- Trade-off: BERT is better for understanding tasks, GPT for generation

### Q3: Explain the difference between encoder-only, decoder-only, and encoder-decoder
| Architecture | Attention | Best For | Examples |
|-------------|-----------|----------|----------|
| Encoder-only | Bidirectional | Understanding (classification, NER) | BERT, RoBERTa |
| Decoder-only | Causal (left-to-right) | Generation | GPT, LLaMA |
| Encoder-Decoder | Both | Seq2Seq (translation, summarization) | T5, BART |

### Q4: What is the computational complexity of self-attention?
**Answer:**
- O(n² × d) where n = sequence length, d = embedding dimension
- The n² comes from every token attending to every other token
- Problem for long sequences (e.g., documents with 10K+ tokens)
- **Solutions:**
  - Sparse attention (Longformer, BigBird)
  - Linear attention (Performer)
  - Sliding window (Mistral)
  - Flash Attention (hardware-aware, exact but efficient)

### Q5: How does teacher forcing work and what are its problems?
**Answer:**
- During training, feed ground truth tokens as decoder input (not model's own predictions)
- **Advantage:** Faster convergence, more stable training
- **Problem:** Exposure bias — at inference, model uses its own predictions (which may be wrong)
- **Solutions:** Scheduled sampling (gradually use model predictions), RAML, sequence-level training

### Q6: Explain knowledge distillation for NLP models
**Answer:**
- Train a smaller "student" model to mimic a larger "teacher" model
- Student learns from teacher's soft probability distributions (more info than hard labels)
- Loss = α × CE(student, hard_labels) + (1-α) × KL(student_soft, teacher_soft)
- Temperature T softens distributions: softmax(z/T)
- Examples: DistilBERT (6 layers from 12-layer BERT, 97% performance)

### Q7: What is the difference between autoregressive and autoencoding models?
**Answer:**
- **Autoregressive (GPT):** Predict next token given previous tokens. P(x) = ΠP(x_t|x_<t)
  - Good for generation
  - Can only see left context
- **Autoencoding (BERT):** Reconstruct corrupted input
  - Good for understanding
  - Sees full bidirectional context
  - Can't easily generate text

### Q8: How do positional embeddings work in modern LLMs?
**Answer:**
- **Absolute (BERT/GPT):** Learned embedding for each position (fixed max length)
- **Sinusoidal (original Transformer):** Mathematical function of position
- **RoPE (LLaMA, modern):** Rotates Q and K by position-dependent angles
  - Relative position naturally encoded in dot product
  - Can extrapolate to longer sequences
- **ALiBi:** Adds linear bias based on distance (no explicit PE, extrapolates well)

### Q9: What is Flash Attention and why is it important?
**Answer:**
- Hardware-aware attention algorithm (same result, different computation order)
- Reduces memory from O(n²) to O(n) by avoiding materialization of full attention matrix
- Fuses operations to minimize GPU memory reads/writes (IO-aware)
- 2-4x speedup, enables longer sequences
- Key insight: Tiling + recomputation is faster than storing intermediate results

### Q10: Explain chain-of-thought prompting
**Answer:**
- Add reasoning steps before the final answer in prompts
- "Let's think step by step" or provide few-shot examples with reasoning
- Dramatically improves performance on reasoning tasks (math, logic)
- Works best with large models (>100B parameters)
- Variants: Zero-shot CoT, Few-shot CoT, Tree-of-Thought, Self-consistency

---

## Comparisons & Alternatives

### BERT vs GPT

| Aspect | BERT | GPT |
|--------|------|-----|
| Architecture | Encoder | Decoder |
| Training | MLM + NSP | Causal LM |
| Context | Bidirectional | Left-to-right |
| Best for | Understanding tasks | Generation tasks |
| Fine-tuning | Task-specific heads | Prompting or fine-tune |
| Few-shot | Poor | Excellent (GPT-3+) |

### When to Use What
- **Classification/NER:** BERT-family (encoder)
- **Generation:** GPT-family (decoder)
- **Translation/Summarization:** T5/BART (encoder-decoder) or large decoder
- **Few-shot learning:** Large decoder models with prompting
- **Production (speed):** DistilBERT, TinyBERT, or quantized models

### Emerging Trends in NLP
1. **Mixture of Experts (MoE):** Activate subset of parameters per input (Mixtral)
2. **Long context:** Models handling 100K+ tokens (Claude, Gemini)
3. **Multimodal:** Text + images + audio in one model
4. **Retrieval-Augmented Generation (RAG):** Ground in external knowledge
5. **Constitutional AI:** Self-alignment without human labels
6. **Speculative decoding:** Speed up inference with draft models
7. **State Space Models (Mamba):** Alternative to attention for long sequences

### State Space Models vs Transformers

| Aspect | Transformer | SSM (Mamba) |
|--------|-------------|-------------|
| Complexity | O(n²) | O(n) |
| Long sequences | Expensive | Efficient |
| Parallelization | Full | Full |
| Quality (short) | Excellent | Very good |
| Quality (long) | Degrades | Maintains |
| Maturity | High | Growing |
