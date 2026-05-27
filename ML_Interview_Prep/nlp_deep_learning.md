# Natural Language Processing & Deep Learning - Interview Concepts

---

## 1. Word Embeddings (Word2Vec, GloVe, FastText)

**Answer:**
Word embeddings map words to dense, low-dimensional vectors where semantic relationships are captured by vector arithmetic. Words with similar meanings have similar vectors. They replaced one-hot encoding (sparse, no semantics) as the foundation of NLP.

**Methods Comparison:**

| Method | Training | Key Idea | Handles OOV? |
|--------|----------|----------|--------------|
| Word2Vec (CBOW) | Predict center word from context | Local context window | No |
| Word2Vec (Skip-gram) | Predict context from center word | Local context window | No |
| GloVe | Factorize co-occurrence matrix | Global statistics + local context | No |
| FastText | Skip-gram on character n-grams | Subword information | Yes (compose from n-grams) |
| ELMo | Bidirectional LSTM language model | Contextual (different per sentence) | Partial (char-level) |

**Properties of Good Embeddings:**
- king - man + woman ≈ queen (analogies)
- Similar words cluster: {cat, dog, pet} vs. {car, truck, vehicle}
- Dimensions capture meaningful axes (gender, tense, plurality)

**Layman Example:**
Imagine a city map where:
- Restaurants are clustered in one area, parks in another, hospitals elsewhere
- Italian restaurants are near French restaurants (similar cuisines)
- The direction from "hospital" to "doctor" is the same as "school" to "teacher" (workplace → profession)

Word embeddings create this "map" for words — nearby words are semantically similar, and directions encode relationships.

**Follow-up Questions:**

**Q: What's the difference between Word2Vec and GloVe?**
A: Word2Vec is predictive (neural network trained to predict context/center words from local windows). GloVe is count-based (factorizes the global word co-occurrence matrix with a weighted least-squares objective). In practice, they produce similar quality embeddings. GloVe is faster to train (matrix factorization vs. SGD), Word2Vec is simpler to understand.

**Q: Why can't Word2Vec handle polysemy (multiple meanings)?**
A: Word2Vec assigns ONE vector per word regardless of context. "Bank" (financial) and "bank" (river) have the same vector — a compromise between meanings. ELMo and BERT solved this by producing context-dependent embeddings (different vector for "bank" in different sentences).

**Q: How does FastText handle out-of-vocabulary (OOV) words?**
A: FastText represents each word as a bag of character n-grams. "where" → {"<wh", "whe", "her", "ere", "re>", "<wher", "where", ...}. The word vector is the sum of its n-gram vectors. For OOV words, compose the vector from known n-grams. This also captures morphology: "unhappy" shares n-grams with "happy."

**Q: Are static embeddings still useful in the BERT/GPT era?**
A: Yes, for: (1) Resource-constrained environments (mobile, IoT), (2) Large-scale retrieval (pre-compute, fast lookup), (3) Simple tasks where context isn't critical, (4) As initialization for task-specific models, (5) Interpretability (fixed vectors are easier to analyze). But for accuracy-critical tasks, contextual embeddings (BERT) are strictly superior.

**Additional Info:**
- Embedding dimensions: typically 100-300 for Word2Vec/GloVe, 768-1024 for BERT
- Negative sampling (Word2Vec): instead of softmax over entire vocabulary, contrast positive pairs against random negatives — makes training tractable
- Subword tokenization (BPE, WordPiece) in modern models subsumes FastText's approach

---

## 2. Recurrent Neural Networks (RNN, LSTM, GRU)

**Answer:**
RNNs process sequential data by maintaining a hidden state that's updated at each time step. LSTMs and GRUs add gating mechanisms to handle long-range dependencies and mitigate vanishing gradients.

**Architecture Comparison:**

| Model | Gates | Hidden state | Long-range? | Parameters |
|-------|-------|-------------|-------------|------------|
| Vanilla RNN | None | h_t = tanh(W·[h_{t-1}, x_t]) | No (vanishing gradient) | Fewest |
| LSTM | Forget, Input, Output | Cell state + hidden state | Yes (cell state highway) | Most (4× RNN) |
| GRU | Reset, Update | Single hidden state | Yes (simpler than LSTM) | ~75% of LSTM |
| Bidirectional | — | Forward + backward passes | Both directions | 2× base model |

**LSTM Gates:**
```
Forget gate:  f_t = σ(W_f·[h_{t-1}, x_t] + b_f)     — what to forget from cell state
Input gate:   i_t = σ(W_i·[h_{t-1}, x_t] + b_i)     — what new info to store
Cell update:  C̃_t = tanh(W_C·[h_{t-1}, x_t] + b_C)  — candidate values
Cell state:   C_t = f_t ⊙ C_{t-1} + i_t ⊙ C̃_t       — update cell state
Output gate:  o_t = σ(W_o·[h_{t-1}, x_t] + b_o)     — what to output
Hidden state: h_t = o_t ⊙ tanh(C_t)                   — filtered cell state
```

**Layman Example:**
Reading a book chapter by chapter:
- **Vanilla RNN:** You can only remember the last few pages. By chapter 10, you've forgotten chapter 1 entirely. (Vanishing gradient = memory fades)
- **LSTM:** You have a notebook (cell state). You can:
  - **Forget gate:** Erase irrelevant notes ("villain's old plan no longer relevant")
  - **Input gate:** Write new important facts ("hero found a key!")
  - **Output gate:** Decide what's relevant for the current chapter ("I need to remember the key, not the weather from ch.2")
  - The notebook persists — information from chapter 1 can survive to chapter 100

**Follow-up Questions:**

**Q: Why do vanilla RNNs suffer from vanishing gradients?**
A: During backpropagation through time, gradients multiply by the weight matrix at each step: ∂h_t/∂h_1 = Π W_h. If W_h's spectral radius < 1, gradients shrink exponentially → early layers don't learn. If > 1, gradients explode. LSTM's cell state provides a gradient highway (multiplied only by forget gate ≈ 1, not full matrix).

**Q: When would you use GRU vs. LSTM?**
A: GRU: fewer parameters (faster training/inference), works equally well on many tasks, simpler. LSTM: slightly better for very long sequences where fine-grained memory control matters, more established. In practice: performance is similar; GRU is preferred for efficiency. Both are largely replaced by Transformers now.

**Q: Are RNNs still relevant in the Transformer era?**
A: Mostly no for NLP (Transformers dominate). Still relevant for: (1) Streaming/real-time processing (constant memory, no full sequence needed), (2) Very long sequences where O(n²) attention is prohibitive, (3) Edge/embedded devices (smaller models), (4) State-space models (Mamba, S4) are RNN-like alternatives gaining traction. (5) Some time-series tasks.

**Q: What is the difference between bidirectional and unidirectional RNNs?**
A: Unidirectional: processes sequence left-to-right only. At position t, only past context [1..t-1] is available. Used for generation (can't look ahead). Bidirectional: two passes (left→right, right←left), concatenates both hidden states. At position t, full context [1..T] is available. Used for understanding tasks (NER, classification) where future context helps.

**Additional Info:**
- Teacher forcing: during training, feed ground truth tokens (not predictions) as input at each step. Faster convergence but exposure bias (never sees own errors during training).
- Attention mechanism was first added to RNNs (Bahdanau 2014) before Transformers existed — allowing the decoder to "look back" at all encoder states instead of relying only on the final hidden state.

---

## 3. The Transformer Architecture

**Answer:**
The Transformer (Vaswani et al., 2017 — "Attention Is All You Need") replaced RNNs with self-attention, enabling parallel processing and better long-range dependencies. It's the foundation of BERT, GPT, T5, and all modern LLMs.

**Architecture:**
```
Encoder:                          Decoder:
Input Embedding                   Output Embedding (shifted right)
+ Positional Encoding             + Positional Encoding
┌──────────────────┐ ×N          ┌──────────────────┐ ×N
│ Multi-Head Self-Attention │     │ Masked Multi-Head Self-Attention │
│ Add & LayerNorm            │     │ Add & LayerNorm                    │
│ Feed-Forward Network       │     │ Multi-Head Cross-Attention         │
│ Add & LayerNorm            │     │ Add & LayerNorm                    │
└──────────────────┘             │ Feed-Forward Network               │
                                  │ Add & LayerNorm                    │
                                  └──────────────────┘
```

**Self-Attention:**
$$\text{Attention}(Q, K, V) = \text{softmax}\left(\frac{QK^T}{\sqrt{d_k}}\right)V$$

Each token attends to all other tokens. Q (query) = "what am I looking for?", K (key) = "what do I contain?", V (value) = "what information do I provide?"

**Multi-Head Attention:** Run H parallel attention heads with different learned projections, concatenate:
$$\text{MultiHead}(Q,K,V) = \text{Concat}(\text{head}_1,...,\text{head}_H)W^O$$

**Comparison: RNN vs. Transformer:**

| Aspect | RNN/LSTM | Transformer |
|--------|----------|-------------|
| Parallelization | Sequential (can't parallelize across time) | Fully parallel |
| Long-range dependencies | Struggles (info passes through many steps) | Direct (any token attends to any token) |
| Computational complexity | O(n·d²) per layer | O(n²·d) per layer |
| Memory | O(1) per step (fixed state) | O(n²) for attention matrix |
| Position awareness | Implicit (order of processing) | Explicit (positional encoding needed) |
| Training speed | Slow (sequential) | Fast (parallel) |
| Dominant for | Streaming, very long sequences | Everything else |

**Layman Example:**
A classroom discussion:
- **RNN:** Students pass notes one by one in a line. By the time the note reaches student #50, it's been rewritten 50 times and the original message is distorted.
- **Transformer (Self-Attention):** Every student can directly look at and talk to every other student simultaneously. Student #50 can directly reference what student #1 said without any intermediaries. The "attention" mechanism = each student decides which other students' comments are most relevant to their own response.

**Follow-up Questions:**

**Q: Why is positional encoding needed?**
A: Self-attention is permutation-invariant — it treats input as a set, not a sequence. "The cat sat on the mat" and "mat the on sat cat the" would produce identical attention weights without position info. Sinusoidal or learned positional embeddings inject order information.

**Q: What is the purpose of the √d_k scaling factor?**
A: Dot products grow in magnitude with dimension d_k (variance ≈ d_k). Large dot products push softmax into saturated regions with near-zero gradients. Dividing by √d_k normalizes the variance to 1, keeping gradients healthy regardless of embedding dimension.

**Q: What are the different types of attention in the Transformer?**
A: (1) **Self-attention (encoder):** Each token attends to all tokens — bidirectional context. (2) **Masked self-attention (decoder):** Each token attends only to previous tokens — prevents looking ahead for autoregressive generation. (3) **Cross-attention (decoder):** Decoder tokens attend to encoder outputs — connects input to output.

**Q: What is the computational bottleneck of Transformers?**
A: Self-attention is O(n²) in sequence length — attention matrix is n×n. For n=4096, that's 16M entries per head per layer. Solutions: sparse attention (attend to subset), linear attention (kernel approximation), sliding window (Longformer), flash attention (memory-efficient implementation), state-space models (Mamba — O(n)).

**Q: What is the Feed-Forward Network (FFN) doing?**
A: FFN processes each token independently (position-wise). It's a 2-layer MLP: FFN(x) = W₂·ReLU(W₁·x + b₁) + b₂, typically with inner dimension 4× the model dimension. It acts as a "memory" — recent research shows FFN layers store factual knowledge while attention layers do relational reasoning.

---

## 4. BERT and Encoder Models

**Answer:**
BERT (Bidirectional Encoder Representations from Transformers) is a pre-trained encoder-only Transformer that learns bidirectional context. It revolutionized NLU tasks (classification, NER, QA) by providing rich contextual representations fine-tunable for any task.

**Pre-training Objectives:**
- **Masked Language Model (MLM):** Mask 15% of tokens, predict them from context. Forces bidirectional understanding.
- **Next Sentence Prediction (NSP):** Given two sentences, predict if B follows A. (Later shown to be less important — removed in RoBERTa)

**BERT Variants:**

| Model | Parameters | Key Innovation | Year |
|-------|-----------|----------------|------|
| BERT-base | 110M | Original MLM + NSP | 2018 |
| BERT-large | 340M | Bigger model | 2018 |
| RoBERTa | 355M | Better training (no NSP, more data, longer) | 2019 |
| ALBERT | 12M-235M | Parameter sharing, factorized embedding | 2019 |
| DistilBERT | 66M | Knowledge distillation (6 layers vs. 12) | 2019 |
| DeBERTa v3 | 86M-304M | Disentangled attention + enhanced mask decoder | 2021 |
| ModernBERT | 150M-400M | Updated architecture with flash attention | 2024 |

**Architecture:**
```
Input: [CLS] token1 token2 ... tokenN [SEP] sentence2_token1 ... [SEP]
     + Token embeddings
     + Segment embeddings (sentence A vs B)
     + Position embeddings
     → 12/24 Transformer encoder layers
Output: Contextual embedding for each token (768/1024 dim)

Fine-tuning:
  Classification: [CLS] embedding → linear layer → class
  Token classification (NER): each token embedding → linear → tag
  QA: predict start/end positions in passage
```

**Layman Example:**
BERT is like a student who read the entire internet and then takes a reading comprehension test:
- **Pre-training (reading):** Given sentences with blanked-out words: "The __ chased the mouse." BERT learns to predict "cat" by understanding context from BOTH sides. After reading billions of sentences, it deeply understands language.
- **Fine-tuning (test):** For a specific task like sentiment analysis, you show BERT a few thousand labeled examples and it quickly adapts its understanding. It doesn't learn language from scratch — just how to apply its existing knowledge to the specific task.

**Follow-up Questions:**

**Q: Why is bidirectional context important?**
A: "The bank by the river" vs. "The bank approved the loan" — the meaning of "bank" depends on surrounding words in BOTH directions. GPT (left-to-right only) can't use "river" to disambiguate "bank" because "river" comes after. BERT sees both sides simultaneously, producing better representations for understanding tasks.

**Q: What's the difference between BERT and GPT for downstream tasks?**
A: BERT (encoder): bidirectional, best for understanding tasks (classification, NER, QA, similarity). Can't generate text. GPT (decoder): unidirectional (left-to-right), best for generation tasks (text completion, summarization, dialogue). Can do understanding tasks via prompting but less efficient than fine-tuned BERT.

**Q: How does fine-tuning BERT work?**
A: (1) Add task-specific head (linear layer) on top of BERT. (2) Initialize with pre-trained weights. (3) Train entire model (BERT + head) on labeled task data with small learning rate (2e-5 to 5e-5). (4) Typically 3-5 epochs suffice. The pre-trained representations adapt to the task. This works with as few as 1000 labeled examples.

**Q: What is RoBERTa and why is it better than BERT?**
A: RoBERTa = Robustly Optimized BERT. Changes: (1) Remove NSP (no benefit), (2) Train longer with larger batches, (3) More training data (160GB vs. 16GB), (4) Dynamic masking (different masks each epoch vs. static), (5) Larger byte-pair encoding vocabulary. Result: consistently 2-5% better across tasks with same architecture.

**Q: When should you use BERT vs. a generative LLM (GPT-4)?**
A: BERT: (1) Fixed classification tasks with labeled data, (2) Token-level tasks (NER, POS), (3) Efficient inference (smaller model), (4) When you need consistent, deterministic outputs, (5) Embedding/retrieval. GPT-4: (1) Zero/few-shot with no labeled data, (2) Complex reasoning, (3) Generation, (4) Multi-task with single model, (5) When accuracy matters more than cost.

---

## 5. GPT and Decoder Models (Autoregressive LMs)

**Answer:**
GPT (Generative Pre-trained Transformer) is a decoder-only Transformer trained autoregressively — predicting the next token given all previous tokens. This simple objective, scaled up, produces powerful language models capable of generation, reasoning, and in-context learning.

**GPT Evolution:**

| Model | Parameters | Training data | Context length | Key innovation |
|-------|-----------|---------------|----------------|----------------|
| GPT-1 | 117M | BookCorpus | 512 | Transformer decoder + fine-tune |
| GPT-2 | 1.5B | WebText (40GB) | 1024 | Zero-shot transfer, no fine-tuning needed |
| GPT-3 | 175B | 300B tokens | 2048 | In-context learning, few-shot prompting |
| GPT-4 | ~1.8T (MoE est.) | ~13T tokens | 8K-128K | Multi-modal, stronger reasoning |
| GPT-4o | ~1.8T | Multi-modal | 128K | Native multi-modal (text+image+audio) |

**Training Objective:**
$$L = -\sum_{t=1}^{T} \log P(x_t | x_1, ..., x_{t-1})$$
Simply: predict the next token. This forces the model to understand grammar, facts, reasoning, code, etc.

**Key Concepts:**

| Concept | Description |
|---------|-------------|
| Autoregressive | Generate one token at a time, left-to-right |
| Causal masking | Each position can only attend to previous positions |
| In-context learning | Learn from examples in the prompt without updating weights |
| Few-shot | Provide a few examples in the prompt |
| Zero-shot | No examples, just instruction |
| Chain-of-thought | Prompt model to reason step-by-step |
| RLHF | Fine-tune with human preference feedback |

**Comparison: Encoder (BERT) vs. Decoder (GPT) vs. Encoder-Decoder (T5):**

| Aspect | BERT (Encoder) | GPT (Decoder) | T5 (Enc-Dec) |
|--------|---------------|---------------|---------------|
| Attention | Bidirectional | Causal (left-to-right) | Encoder: bi, Decoder: causal |
| Pre-training | MLM (fill blanks) | Next token prediction | Text-to-text (span corruption) |
| Best for | Classification, NER, QA | Generation, reasoning | Seq2seq (translation, summarization) |
| Inference | Single forward pass | Autoregressive (token by token) | Mixed |
| Scaling behavior | Saturates ~1B | Improves with scale | Good scaling |
| Few-shot ability | Poor (needs fine-tuning) | Excellent (in-context) | Moderate |

**Layman Example:**
- **GPT pre-training:** Like predicting the next word in autocomplete, trained on the entire internet. "The cat sat on the ___" → "mat." After trillions of such predictions, it learns grammar, facts, reasoning, code, math — because predicting well requires understanding.
- **In-context learning:** Like showing a translator a few example translations at the top of a page, then asking them to translate a new sentence. They learn the pattern from the examples without any retraining. GPT does this with any task — classification, math, coding — just from examples in the prompt.

**Follow-up Questions:**

**Q: How does in-context learning work without updating weights?**
A: The attention mechanism can implement a form of gradient-free learning at inference time. The model learns during pre-training to "pattern match" — given examples of input→output, the attention layers implement an implicit algorithm that maps new inputs to outputs following the demonstrated pattern. Mechanistically, some attention heads perform induction (copy patterns from examples).

**Q: What is RLHF and why is it important?**
A: Reinforcement Learning from Human Feedback: (1) Train a reward model on human preference data (which response is better?), (2) Use PPO (or DPO) to fine-tune the LLM to maximize reward. This aligns the model's outputs with human preferences — helpful, harmless, honest. Without RLHF, models produce plausible but not necessarily useful/safe text.

**Q: What are the scaling laws for LLMs?**
A: Kaplan et al. (2020) / Chinchilla (2022): Performance scales predictably as power laws of model size (N), dataset size (D), and compute (C). Chinchilla optimal: train for ~20× tokens as parameters. A 70B model should see ~1.4T tokens. Under-training (too few tokens for model size) wastes compute.

**Q: What is the difference between GPT fine-tuning and prompting?**
A: Fine-tuning: update model weights on task-specific data (permanent, requires training, expensive but highest accuracy for specific tasks). Prompting: provide instructions/examples at inference time (no weight changes, flexible, cheaper, but limited by context window and less consistent). In practice: prompting for prototyping, fine-tuning for production systems needing consistency.

---

## 6. Sequence-to-Sequence Models and T5

**Answer:**
Seq2seq models map an input sequence to an output sequence of potentially different length. The encoder-decoder architecture (original: RNN-based with attention; modern: Transformer-based) is the standard approach. T5 frames ALL NLP tasks as text-to-text.

**Evolution:**

| Model | Architecture | Key Innovation | Year |
|-------|-------------|----------------|------|
| Seq2seq + Attention | RNN encoder-decoder | Attention over encoder states | 2014-2015 |
| Transformer | Full transformer enc-dec | Self-attention replaces RNN | 2017 |
| T5 | Transformer enc-dec | Text-to-text framework, unified | 2019 |
| BART | Transformer enc-dec | Denoising autoencoder pre-training | 2019 |
| mT5 | Multilingual T5 | 101 languages | 2020 |
| Flan-T5 | T5 + instruction tuning | Instruction following | 2022 |
| UL2 | Mixture of denoisers | Unified objective (causal + prefix + span) | 2022 |

**T5's Text-to-Text Framework:**
```
Classification: "classify: This movie is great" → "positive"
Translation:    "translate English to French: Hello" → "Bonjour"
Summarization:  "summarize: [long article]" → "summary"
QA:             "question: What is NLP? context: NLP is..." → "NLP is..."
```
Every task is text-in, text-out — unified training and inference.

**T5 Pre-training (Span Corruption):**
```
Input:  "The <X> sat on <Y> mat"
Target: "<X> cat <Y> the"
```
Random spans are replaced with sentinel tokens. Model learns to fill them in.

**Layman Example:**
- **Seq2seq:** A translator booth at the UN. The listener (encoder) hears the full speech in English. The translator (decoder) produces the French translation word by word, occasionally glancing back at their notes (attention) to check specific parts.
- **T5:** A universal assistant who handles ANY text task the same way. You prefix your request with what you want ("summarize:", "translate:", "answer:"), and it produces text output. It learned to handle all these tasks during training.

**Follow-up Questions:**

**Q: Why use encoder-decoder instead of decoder-only for translation?**
A: Encoder-decoder separates understanding (encoder reads full source bidirectionally) from generation (decoder produces target autoregressively). This is natural for translation where you need full source understanding before generating. Decoder-only (GPT) can do translation but is less sample-efficient — it must learn to understand and generate in the same unidirectional pass.

**Q: What is BART and how does it differ from T5?**
A: Both are encoder-decoder Transformers. BART uses denoising pre-training (corrupt input with multiple noise types: deletion, masking, permutation, rotation → reconstruct original). T5 uses span corruption only. BART is particularly strong for generation tasks (summarization). T5's text-to-text framing is more versatile for multi-task.

**Q: When should you use T5/BART vs. GPT for generation?**
A: T5/BART: When input is fixed and you need conditioned generation (summarization, translation, data-to-text). The encoder processes input once, decoder generates output attending to it. GPT: When generation is more open-ended (creative writing, dialogue, code generation) or when you want in-context learning. Modern large decoder-only models (GPT-4) can do both well.

**Q: What is instruction tuning and why does Flan-T5 outperform T5?**
A: Instruction tuning fine-tunes on diverse tasks formatted with natural language instructions ("Summarize the following article:", "Translate to Spanish:"). This teaches the model to follow arbitrary instructions at inference time. Flan-T5 was tuned on 1800+ tasks — it generalizes to unseen tasks much better than raw T5.

---

## 7. Tokenization (BPE, WordPiece, SentencePiece)

**Answer:**
Tokenization splits text into subword units for model input. Subword tokenization balances vocabulary size (tractable) with meaning preservation (not splitting common words). It handles rare/OOV words by composing them from subword pieces.

**Methods Comparison:**

| Method | Algorithm | Used by | Vocab size |
|--------|-----------|---------|------------|
| BPE (Byte Pair Encoding) | Iteratively merge most frequent pairs | GPT-2/3/4, LLaMA, RoBERTa | 32K-50K |
| WordPiece | Like BPE but maximizes likelihood | BERT, DistilBERT | 30K |
| Unigram | Start large, remove by loss impact | T5, mBART, XLNet | 32K |
| SentencePiece | BPE/Unigram on raw text (no pre-tokenization) | T5, LLaMA, multilingual | 32K-256K |
| Byte-level BPE | BPE on bytes (no unknown tokens) | GPT-2, GPT-4 | 50K+ |

**BPE Algorithm:**
```
1. Start with character-level vocabulary: {'a', 'b', ..., 'z', ' ', ...}
2. Count all adjacent character pairs in corpus
3. Merge most frequent pair into new token: ('t','h') → 'th'
4. Repeat steps 2-3 until desired vocabulary size
Example: "low lower lowest" → "low", "low er", "low est"
```

**Layman Example:**
Writing a telegram where each word costs money:
- **Word-level:** Each word is one token. "Unforgettable" = 1 token. But rare words ("pneumonoultramicroscopicsilicovolcanoconiosis") need infinite vocabulary.
- **Character-level:** Each letter is one token. "Hello" = 5 tokens. Cheap vocabulary but sequences become very long.
- **Subword (BPE):** Common words stay whole: "the" = 1 token. Rare words split into known pieces: "unforgettable" = "un" + "forget" + "table". Balanced: finite vocabulary, reasonable sequence length, handles any word.

**Follow-up Questions:**

**Q: Why not use word-level tokenization?**
A: (1) Vocabulary would be millions of words — embedding matrix becomes massive, (2) OOV problem — any unseen word maps to [UNK], (3) Can't share information between morphologically related words ("run", "running", "runner" are unrelated tokens). Subword solves all three: fixed vocab, no OOV, shared subwords.

**Q: How does tokenization affect model performance?**
A: (1) Fertility (tokens per word) affects effective context length — high fertility means less content fits in context window. (2) Tokenizer trained on English penalizes other languages (more tokens per word). (3) Splitting numbers and code poorly hurts math/coding ability. (4) Merged tokens carry more semantic weight than split ones.

**Q: Why is tokenization challenging for non-English languages?**
A: (1) Chinese/Japanese have no spaces — word boundaries are unclear, (2) Agglutinative languages (Turkish, Finnish) have very long words with many morphemes, (3) BPE trained on English-heavy data under-represents other scripts, leading to high fertility (3-4× more tokens for same content). Solution: train tokenizers on balanced multilingual data or use larger vocabularies.

**Q: What is the relationship between vocabulary size and model performance?**
A: Larger vocab: shorter sequences (more content per context window), more parameters in embedding layer, each token is more meaningful. Smaller vocab: longer sequences (less content per window), fewer parameters, more compositional. Sweet spot depends on model size and languages. Typical: 32K-128K for modern LLMs.

---

## 8. Attention Mechanisms in NLP

**Answer:**
Attention mechanisms allow models to dynamically focus on relevant parts of the input when producing each output element. Originally added to seq2seq RNNs, attention became the core mechanism of Transformers.

**Evolution:**

| Type | Year | Mechanism | Used in |
|------|------|-----------|---------|
| Additive (Bahdanau) | 2014 | score = v^T · tanh(W_1·h_i + W_2·s_j) | RNN seq2seq |
| Dot-product (Luong) | 2015 | score = h_i^T · s_j | RNN seq2seq |
| Scaled dot-product | 2017 | score = (Q·K^T)/√d_k | Transformer |
| Multi-head | 2017 | H parallel attention heads | Transformer |
| Sparse attention | 2019 | Attend to subset of positions | Longformer, BigBird |
| Linear attention | 2020 | Kernel approximation, O(n) | Performer |
| Flash attention | 2022 | IO-aware exact attention | All modern Transformers |
| Multi-query/Grouped | 2022-23 | Share K,V heads across Q heads | LLaMA 2, Mistral |

**Types in Modern Transformers:**

| Attention type | Q, K, V source | Where used | Purpose |
|---------------|----------------|------------|---------|
| Self-attention | All from same sequence | Encoder, Decoder | Relate tokens within sequence |
| Causal self-attention | Same sequence, masked future | Decoder (generation) | Autoregressive constraint |
| Cross-attention | Q from decoder, K/V from encoder | Decoder | Attend to input |
| Grouped-Query Attention (GQA) | Multiple Q heads share K/V groups | LLaMA 2, Mistral | KV cache efficiency |
| Multi-Query Attention (MQA) | All Q heads share single K/V | PaLM, Falcon | Maximum KV cache savings |

**Layman Example:**
A student writing an essay with reference books:
- **No attention (vanilla RNN):** Must memorize the entire book, then write the essay from memory. Inevitably forgets crucial details.
- **Attention:** Can look back at any page of the reference book while writing each sentence. For each essay sentence, decides which book pages are most relevant and focuses there.
- **Self-attention:** While writing, each sentence "looks at" all other sentences in the essay to ensure coherence and avoid repetition.
- **Multi-head attention:** Like having multiple reviewers — one checks grammar, another checks facts, another checks style — all simultaneously reading different aspects of the text.

**Follow-up Questions:**

**Q: What is Flash Attention and why is it important?**
A: Flash Attention computes exact attention (same result) but reorders operations to minimize GPU memory reads/writes (IO). Instead of materializing the full N×N attention matrix, it computes attention in tiles that fit in SRAM. Result: 2-4× faster, uses O(N) memory instead of O(N²). Enables longer sequences on same hardware. Standard in all modern training.

**Q: What is KV Cache and why does it matter for inference?**
A: During autoregressive generation, each new token needs attention over all previous tokens. Naively, this recomputes K and V for all previous positions. KV cache stores K,V from previous steps — new token only computes its own K,V and attends to cached values. Reduces generation from O(n²) to O(n) per token but requires memory proportional to sequence length × layers × d_model.

**Q: What is Grouped Query Attention (GQA)?**
A: Standard multi-head: H heads with separate Q, K, V each (memory: 3×H×d). MQA: H Q heads but only 1 shared K/V (memory: (H+2)×d — minimal KV cache). GQA: H Q heads with G groups of shared K/V (between MHA and MQA). LLaMA 2 uses GQA (8 KV heads for 32 Q heads). Reduces KV cache by H/G factor with minimal quality loss.

**Q: How does sparse attention work?**
A: Instead of every token attending to every other (O(n²)), restrict attention to patterns: (1) Local window (nearby tokens), (2) Global tokens (attend everywhere, like [CLS]), (3) Stride patterns (every k-th token). Longformer combines local + global. BigBird adds random attention. Reduces complexity to O(n) or O(n√n).

---

## 9. Text Classification and Sentiment Analysis

**Answer:**
Text classification assigns predefined labels to text. Sentiment analysis is a specific case detecting opinion polarity (positive/negative/neutral). Approaches range from feature engineering + ML to fine-tuned Transformers to zero-shot LLMs.

**Approaches Evolution:**

| Era | Method | Accuracy (SST-2) | Speed |
|-----|--------|-------------------|-------|
| Classical | TF-IDF + Logistic Regression/SVM | ~85% | Very fast |
| CNN-based | TextCNN (multiple filter sizes) | ~87% | Fast |
| RNN-based | BiLSTM + Attention | ~88% | Moderate |
| BERT fine-tuned | BERT-base + linear head | ~93% | Moderate |
| DeBERTa fine-tuned | DeBERTa-v3 + linear head | ~96% | Moderate |
| LLM zero-shot | GPT-4 with prompt | ~95% | Slow, expensive |

**TF-IDF + ML Pipeline:**
```
Text → Tokenize → TF-IDF vectorization → Feature selection → Classifier (LR/SVM/RF)
```

**BERT Fine-tuning Pipeline:**
```
Text → Tokenize (WordPiece) → BERT encoder → [CLS] representation → Linear → Softmax → Class
```

**Comparison of Approaches:**

| Approach | Data needed | Latency | Cost | Flexibility |
|----------|-------------|---------|------|-------------|
| TF-IDF + LR | 1K+ labeled | <1ms | Free | Fixed classes |
| Fine-tuned BERT | 1K+ labeled | 10-50ms | Training cost | Fixed classes |
| SetFit (few-shot) | 8-64 labeled | 10-50ms | Minimal | Fixed classes |
| GPT-4 zero-shot | 0 labeled | 500ms+ | Pay per token | Any classes (prompt) |
| GPT-4 few-shot | 5-10 examples | 500ms+ | Pay per token | Any classes (prompt) |

**Follow-up Questions:**

**Q: When is TF-IDF + LR sufficient vs. needing BERT?**
A: TF-IDF + LR: (1) Large labeled dataset (10K+), (2) Simple binary/few-class tasks, (3) Short texts, (4) Need fast inference, (5) Interpretability required. BERT: (1) Complex semantics matter ("not bad" = positive), (2) Limited labeled data (transfer learning helps), (3) Multi-task or nuanced classification, (4) State-of-the-art accuracy needed.

**Q: How do you handle multi-label classification?**
A: Multiple labels can be active simultaneously (a movie can be both "comedy" and "romance"). Approaches: (1) Binary Cross-Entropy per label (independent classifiers), (2) BERT + sigmoid per class (not softmax), (3) Label attention networks, (4) For LLMs: prompt to list all applicable labels.

**Q: What is aspect-based sentiment analysis?**
A: Detect sentiment toward specific aspects of an entity. "The food was great but service was terrible" → {food: positive, service: negative}. Approaches: (1) Extract aspects + classify each, (2) End-to-end with BERT (token classification), (3) LLM structured output.

**Q: How do you handle domain shift in sentiment analysis?**
A: Sentiment words are domain-specific ("unpredictable" is positive for movies, negative for cars). Solutions: (1) Domain-adaptive pre-training (continue MLM on domain text), (2) Few-shot fine-tuning on domain data, (3) Pivot features (domain-independent sentiment words as bridge), (4) LLM with domain context in prompt.

---

## 10. Named Entity Recognition (NER)

**Answer:**
NER identifies and classifies named entities in text into predefined categories (person, organization, location, date, etc.). It's a token-level classification (sequence labeling) task.

**Tagging Schemes:**

| Scheme | Example | Description |
|--------|---------|-------------|
| IOB2 | B-PER, I-PER, O | Begin, Inside, Outside |
| BIOES | B-PER, I-PER, E-PER, S-PER, O | + End, Single |
| BIO | Same as IOB2 | Most common |

**Example:**
```
"Barack Obama was born in Hawaii"
 B-PER  I-PER  O    O    O  B-LOC
```

**Approaches:**

| Method | Architecture | F1 (CoNLL-2003) | Year |
|--------|-------------|-----------------|------|
| CRF + features | Hand-crafted features + CRF | 89.3 | 2003 |
| BiLSTM + CRF | BiLSTM encoder + CRF decoder | 91.2 | 2016 |
| BERT + linear | BERT encoder + softmax per token | 92.8 | 2018 |
| BERT + CRF | BERT encoder + CRF layer | 93.0 | 2019 |
| DeBERTa + span | Span-based extraction | 94.6 | 2021 |
| GLiNER | Generalized NER (any entity type) | 93+ | 2024 |
| LLM (GPT-4) | Zero/few-shot prompting | ~92 | 2023 |

**Layman Example:**
Reading a newspaper article and highlighting:
- People's names in **blue** (PERSON)
- Company names in **green** (ORG)
- Places in **red** (LOC)
- Dates in **purple** (DATE)

The challenge: "Apple" could be ORG (Apple Inc.) or food. "Washington" could be PER (George Washington), LOC (Washington state), or ORG (Washington Post). Context determines the label.

**Follow-up Questions:**

**Q: Why add a CRF layer on top of BERT?**
A: BERT classifies each token independently. CRF adds transition constraints: I-PER can only follow B-PER or I-PER, never follow B-LOC. It ensures valid tag sequences. In practice, CRF adds ~0.2-0.5 F1 improvement — useful for structured prediction where label dependencies matter.

**Q: What is span-based NER and how does it differ from sequence labeling?**
A: Sequence labeling: assign a tag to each token (BIO scheme). Problems: struggles with nested entities ("Bank of [New York]_LOC"_ORG). Span-based: enumerate all possible spans (i,j), classify each as entity type or "not entity." Handles nested entities naturally but is O(n²) in spans.

**Q: How do you handle nested and overlapping entities?**
A: Examples: "[the University of [California]_LOC]_ORG". Solutions: (1) Span-based models that classify all spans, (2) Multi-layer sequence labeling (one layer per entity type), (3) Machine reading comprehension formulation (ask "what organizations?" → extract spans), (4) Transition-based parsing.

**Q: What is few-shot NER and when is it needed?**
A: When you need to recognize new entity types (product names, medical terms) with few labeled examples. Approaches: (1) Prompt-based (describe entity type in prompt, LLM extracts), (2) Prototypical Networks for NER, (3) GLiNER (train once, generalize to any entity type via description), (4) GPT-4 with examples in prompt. Critical for domain-specific NER where labeling is expensive.

---

## 11. Machine Translation

**Answer:**
Machine translation (MT) converts text from one language to another. Modern NMT (Neural MT) uses encoder-decoder Transformers, achieving near-human quality for high-resource language pairs.

**Evolution:**

| Era | Approach | Example | Quality |
|-----|----------|---------|---------|
| Rule-based (1950s-80s) | Linguistic rules + dictionaries | SYSTRAN | Poor |
| Statistical (1990s-2010s) | Phrase-based, word alignment | Moses | Moderate |
| Neural (2014+) | RNN seq2seq + attention | Google NMT (2016) | Good |
| Transformer (2017+) | Self-attention enc-dec | Transformer | Very good |
| Large LLM (2022+) | Decoder-only with prompting | GPT-4, NLLB | Near-human |

**Key Challenges:**

| Challenge | Description | Solution |
|-----------|-------------|----------|
| Long-range dependencies | Subject-verb agreement across clauses | Self-attention |
| Word order differences | SOV vs SVO languages | Learned reordering in attention |
| Morphological richness | Agglutinative languages | Subword tokenization |
| Low-resource languages | Few parallel sentences | Multilingual models, back-translation |
| Domain adaptation | Medical, legal, technical | Fine-tune on domain data |
| Hallucination | Generating content not in source | Constrained decoding, faithfulness metrics |

**Evaluation Metrics:**

| Metric | Type | What it measures |
|--------|------|-----------------|
| BLEU | N-gram overlap | Precision of n-grams (1-4) |
| METEOR | Alignment-based | Recall + synonyms + stemming |
| TER | Edit distance | Number of edits to fix translation |
| chrF | Character n-gram F-score | Character-level overlap |
| COMET | Learned metric | Human judgment correlation (best) |
| BERTScore | Embedding similarity | Semantic similarity (beyond surface) |

**Follow-up Questions:**

**Q: What are the problems with BLEU?**
A: (1) Only measures surface n-gram overlap — ignores meaning ("I like dogs" vs "I enjoy canines" scores poorly), (2) Doesn't handle legitimate paraphrasing, (3) Corpus-level metric (unreliable for single sentences), (4) Doesn't correlate well with human judgment for some language pairs. Modern alternative: COMET (learned from human judgments, 0.95+ correlation with humans).

**Q: What is back-translation and why is it effective?**
A: Train a target→source model. Use it to translate monolingual target text to (noisy) source text. This creates synthetic parallel data. The main source→target model trains on this synthetic data + real parallel data. Effective because: (1) monolingual data is abundant, (2) the target side is real natural text, (3) noise in source acts as regularization. Standard technique for low-resource MT.

**Q: How do multilingual models (mBART, NLLB) handle 100+ languages?**
A: Shared vocabulary (SentencePiece across all languages), shared encoder-decoder, language token prefix (">>fra<< Hello" for translate-to-French). Cross-lingual transfer: training on high-resource pairs (English-French) improves low-resource pairs (Swahili-French) through shared representations. NLLB-200 covers 200 languages.

**Q: What is the difference between translation with encoder-decoder vs. decoder-only LLM?**
A: Encoder-decoder (NLLB, mBART): trained specifically for translation, efficient (encode once, decode tokens), better for high-volume production. Decoder-only (GPT-4): broader capabilities, can handle context/style instructions, better for creative/nuanced translation, but slower and more expensive. For production: specialized models. For quality on complex text: LLMs.

---

## 12. Question Answering

**Answer:**
QA systems answer questions from text/knowledge. Types: extractive (find answer span in passage), abstractive (generate answer), open-domain (search + read), and closed-book (answer from model parameters alone).

**Types Comparison:**

| Type | Input | Output | Example model |
|------|-------|--------|---------------|
| Extractive QA | Question + passage | Start/end positions in passage | BERT-QA, DeBERTa |
| Abstractive QA | Question + context | Generated answer text | T5, BART |
| Open-domain QA | Question only | Retrieved passage + answer | RAG, DPR + reader |
| Closed-book QA | Question only | Answer from parameters | GPT-4, T5-large |
| Multi-hop QA | Question + multiple docs | Reasoning chain + answer | HotpotQA models |
| Conversational QA | Dialogue history + context | Answer considering history | CoQA, QuAC models |

**Extractive QA Architecture (BERT):**
```
Input: [CLS] question [SEP] passage [SEP]
→ BERT encoder
→ Two linear layers: P(start) and P(end) for each passage token
→ Answer span = argmax(P(start)_i + P(end)_j) where j ≥ i
```

**Open-Domain QA (RAG) Architecture:**
```
Question → Retriever (DPR/ColBERT) → Top-K passages from corpus
Question + Retrieved passages → Reader (BERT/T5/LLM) → Answer
```

**Layman Example:**
Different QA approaches = different ways to answer a question in an exam:
- **Extractive:** The answer is highlighted somewhere in the reading passage. You find and copy it exactly.
- **Abstractive:** You read the passage and write the answer in your own words (may combine info from multiple sentences).
- **Open-domain (RAG):** Open-book exam. You search through all your textbooks first, find relevant pages, then answer from those pages.
- **Closed-book:** No materials allowed. Answer from what you memorized (model parameters).

**Follow-up Questions:**

**Q: What is DPR (Dense Passage Retrieval)?**
A: DPR trains two BERT encoders — one for questions, one for passages. Both map to the same embedding space. Retrieval = nearest neighbor search of question embedding against pre-computed passage embeddings (using FAISS). Much better than BM25 (sparse keyword matching) because it captures semantic similarity.

**Q: What is RAG and why is it important?**
A: Retrieval-Augmented Generation: (1) Retrieve relevant documents from external knowledge base, (2) Feed them as context to a generator (LLM). Benefits: reduces hallucination (grounded in retrieved facts), updateable knowledge (just update the index, no retraining), attributable (can cite sources), handles long-tail knowledge.

**Q: How do you handle unanswerable questions?**
A: SQuAD 2.0 includes unanswerable questions. The model must predict "no answer" when the passage doesn't contain the answer. Approaches: (1) Compare best span score vs. [CLS] score (no-answer threshold), (2) Add explicit "no answer" training examples, (3) Calibrate confidence threshold based on validation set.

**Q: What is multi-hop reasoning in QA?**
A: Questions requiring information from multiple documents/passages. "Where was the director of Inception born?" requires: (1) Inception director → Christopher Nolan, (2) Christopher Nolan birthplace → London. Approaches: iterative retrieval (retrieve, read, retrieve more based on findings), chain-of-thought prompting, graph-based reasoning.

---

## 13. Text Summarization

**Answer:**
Summarization condenses long text into shorter form preserving key information. Extractive summarization selects important sentences. Abstractive summarization generates new text (paraphrasing, combining). Modern LLMs do abstractive summarization exceptionally well.

**Comparison:**

| Approach | Method | Pros | Cons |
|----------|--------|------|------|
| Extractive | Select top-K sentences | Faithful (no hallucination), simple | Not fluent, redundant, can't paraphrase |
| Abstractive | Generate new summary | Fluent, concise, can paraphrase | May hallucinate, harder to evaluate |
| Hybrid | Extract then rewrite | Balance of faithfulness + fluency | Two-stage complexity |

**Methods:**

| Method | Architecture | Type | Strength |
|--------|-------------|------|----------|
| TextRank | Graph-based sentence ranking | Extractive | No training needed |
| BertSum | BERT + inter-sentence attention | Both | Strong extraction |
| BART | Denoising enc-dec | Abstractive | State-of-the-art fine-tuned |
| PEGASUS | Gap sentence generation pre-training | Abstractive | Designed for summarization |
| LED (Longformer Encoder Decoder) | Sparse attention enc-dec | Abstractive | Long documents (16K tokens) |
| GPT-4 (prompted) | Decoder-only | Abstractive | Flexible, controllable |

**Evaluation Metrics:**

| Metric | What it measures | Correlation with humans |
|--------|-----------------|------------------------|
| ROUGE-1 | Unigram overlap | Moderate |
| ROUGE-2 | Bigram overlap | Better |
| ROUGE-L | Longest common subsequence | Good for extractive |
| BERTScore | Semantic embedding similarity | Good |
| QAFactEval | Factual consistency | Best for faithfulness |
| Human eval | Fluency, coherence, faithfulness | Gold standard |

**Follow-up Questions:**

**Q: What is the hallucination problem in abstractive summarization?**
A: The model generates statements not supported by the source document — fabricating facts, attributing wrong numbers, introducing entities not mentioned. This is the biggest practical problem in summarization. Solutions: (1) Faithfulness losses (entailment-based), (2) Post-hoc fact checking, (3) Constrained decoding (copy mechanism), (4) Extractive-then-rewrite hybrid approaches.

**Q: How do you handle long documents that exceed context length?**
A: (1) Truncation (simple but loses info), (2) Hierarchical: summarize sections first, then summarize summaries (map-reduce), (3) Long-context models (LED with 16K, GPT-4 with 128K), (4) Sliding window with aggregation, (5) RAG-style: retrieve most relevant sections, summarize those.

**Q: What is controllable summarization?**
A: Control summary properties: length, style, focus topic, level of detail. Methods: (1) Length tokens/prompts ("Summarize in 3 sentences"), (2) Keyword-guided (focus on certain entities/topics), (3) Query-focused summarization (answer a specific question with a summary), (4) Aspect-based (summarize only financial aspects of an earnings call).

---

## 14. Large Language Models (LLMs) — Architecture & Training

**Answer:**
Modern LLMs are decoder-only Transformers with billions of parameters trained on trillions of tokens. Key innovations: architectural improvements, training at scale, alignment techniques, and efficient inference.

**Modern LLM Architectures:**

| Model | Params | Key Innovations | Organization |
|-------|--------|----------------|-------------|
| LLaMA 2 | 7-70B | GQA, RMSNorm, SwiGLU | Meta |
| LLaMA 3 | 8-405B | 128K context, more data | Meta |
| Mistral 7B | 7B | Sliding window attention, GQA | Mistral AI |
| Mixtral 8×7B | 46.7B (12.9B active) | Mixture of Experts (MoE) | Mistral AI |
| GPT-4 | ~1.8T (rumored MoE) | Multi-modal, strong reasoning | OpenAI |
| Claude 3.5 | Unknown | Long context, instruction following | Anthropic |
| Gemini 1.5 | Unknown | 1M+ context, multi-modal | Google |
| Qwen 2.5 | 0.5-72B | Strong multilingual, coding | Alibaba |
| DeepSeek V3 | 685B (37B active) | MoE, strong reasoning | DeepSeek |

**Architectural Components (Modern LLM vs. Original Transformer):**

| Component | Original Transformer | Modern LLMs |
|-----------|---------------------|-------------|
| Normalization | Post-LayerNorm | Pre-RMSNorm (more stable) |
| Activation | ReLU | SwiGLU (better performance) |
| Position encoding | Sinusoidal (absolute) | RoPE (rotary, relative) |
| Attention | Multi-Head | Grouped-Query (GQA) |
| Context length | 512 | 8K-1M+ |
| Vocabulary | 32K | 32K-256K |
| Architecture | Encoder-Decoder | Decoder-only |

**Training Pipeline:**
```
1. Pre-training: Next token prediction on web-scale corpus (trillions of tokens)
2. Supervised Fine-Tuning (SFT): Train on high-quality instruction-response pairs
3. RLHF/DPO: Align with human preferences (helpful, harmless, honest)
4. (Optional) Tool use, code execution, multi-modal fine-tuning
```

**Layman Example:**
Building a modern LLM is like education:
1. **Pre-training (elementary → university):** Read the entire internet. Learn language, facts, reasoning, code, math — just by predicting what comes next. No guidance on what's "good" — just pattern matching.
2. **SFT (professional training):** Learn to follow instructions properly. Given specific examples of good responses to questions.
3. **RLHF (mentorship):** A mentor rates your responses: "this answer is helpful, that one is harmful." You learn to produce responses humans prefer.

**Follow-up Questions:**

**Q: What is RoPE (Rotary Positional Encoding)?**
A: RoPE encodes position by rotating the embedding vector. For position m, rotate Q and K by angle m·θ (different frequency per dimension). Benefits: (1) Relative position is naturally encoded in dot product, (2) Decays with distance (far tokens get less attention), (3) Generalizes to longer sequences than trained (with NTK-aware scaling). Used in LLaMA, Mistral, most modern LLMs.

**Q: What is Mixture of Experts (MoE) and why use it?**
A: MoE replaces the FFN with N expert FFNs + a router that selects top-K experts per token. Each token uses only K experts (e.g., 2 of 8). Benefits: (1) Total parameters are large (knowledge capacity) but active parameters per token are small (compute efficient), (2) Mixtral 8×7B has 46.7B total params but only 12.9B active per forward pass. Challenge: load balancing across experts, training instability.

**Q: What is the difference between RLHF and DPO?**
A: RLHF: Train separate reward model on preference data, then use PPO (RL algorithm) to optimize LLM against reward model. Complex: 4 models in memory (LLM, reference LLM, reward model, value model). DPO (Direct Preference Optimization): Reformulates RLHF objective directly as a classification loss on preference pairs. Simpler: no reward model, no RL — just supervised training on preferences. Often comparable results with less complexity.

**Q: How do LLMs handle long context (100K+ tokens)?**
A: (1) RoPE with position interpolation/NTK scaling (extend trained positions), (2) Sliding window attention (local) + global tokens, (3) Ring attention (distributed across GPUs), (4) Flash Attention (memory efficient), (5) YaRN (improved position scaling), (6) Training on long documents (Gemini 1.5: 10M+ tokens). Challenge: even with long context, models struggle to use information in the middle ("lost in the middle" problem).

---

## 15. Retrieval-Augmented Generation (RAG)

**Answer:**
RAG combines retrieval from external knowledge sources with LLM generation. Instead of relying solely on parameters (which can be outdated/hallucinated), the model retrieves relevant documents and generates answers grounded in retrieved evidence.

**Architecture:**
```
Query → Retriever → Top-K documents → [Query + Documents] → LLM → Answer

Retriever options:
  - Sparse: BM25 (keyword matching)
  - Dense: Embedding similarity (DPR, E5, BGE)
  - Hybrid: Sparse + Dense (reciprocal rank fusion)
  - Re-ranker: Cross-encoder re-scoring top-K results
```

**RAG Pipeline Components:**

| Component | Options | Purpose |
|-----------|---------|---------|
| Document processing | Chunking (fixed/semantic), metadata | Prepare knowledge base |
| Embedding model | OpenAI Ada, E5, BGE, Cohere | Convert text to vectors |
| Vector store | FAISS, Pinecone, Weaviate, Chroma | Store/search embeddings |
| Retriever | Dense, sparse, hybrid | Find relevant docs |
| Re-ranker | Cross-encoder (Cohere, BGE-reranker) | Improve retrieval precision |
| Generator | GPT-4, Claude, LLaMA | Generate final answer |

**Comparison: RAG vs. Fine-tuning vs. Long Context:**

| Approach | Knowledge update | Hallucination | Cost | Accuracy |
|----------|-----------------|---------------|------|----------|
| RAG | Real-time (update index) | Low (grounded) | Low (no retraining) | Good (depends on retrieval) |
| Fine-tuning | Retrain needed | Medium | High (training) | Best for specific tasks |
| Long context | Include in prompt | Low | High (token cost) | Good but expensive |
| Pure LLM | Retrain needed | High | Inference only | Knowledge cutoff issues |

**Chunking Strategies:**

| Strategy | Method | When to use |
|----------|--------|-------------|
| Fixed-size | Split every N tokens with overlap | Simple default |
| Sentence-based | Split on sentence boundaries | When coherent units matter |
| Semantic | Cluster by embedding similarity | When topics vary |
| Document-structure | Split by headers/sections | Structured documents |
| Parent-child | Small chunks for retrieval, return larger context | Best of both worlds |

**Layman Example:**
An open-book exam vs. a memorization test:
- **Pure LLM (closed-book):** Answer from memory. May remember facts incorrectly or have outdated knowledge.
- **RAG (open-book):** Before answering each question, quickly search your reference books for relevant pages, read them, then compose your answer using those references. Answers are grounded in actual sources, you can cite page numbers, and your "knowledge" is always up-to-date (just update the books).

**Follow-up Questions:**

**Q: What are the main failure modes of RAG?**
A: (1) **Retrieval failure:** Relevant docs not retrieved (wrong query, poor embedding, info not in corpus). (2) **Context stuffing:** Too many irrelevant docs dilute the useful information. (3) **Integration failure:** LLM ignores retrieved context or contradicts it. (4) **Chunking issues:** Answer spans multiple chunks, or chunk boundaries split key info. Solutions: better retrieval (hybrid search, re-ranking), query expansion, chunk optimization.

**Q: How do you evaluate a RAG system?**
A: Evaluate each component: (1) **Retrieval:** Recall@K (are relevant docs in top-K?), MRR, NDCG. (2) **Generation:** Faithfulness (does answer match retrieved docs?), relevance (does answer address query?), correctness. (3) **End-to-end:** Answer accuracy on QA benchmarks. Tools: RAGAS framework, TruLens, custom evaluators.

**Q: What is the difference between naive RAG and advanced RAG?**
A: Naive RAG: query → embed → retrieve → generate. Advanced RAG adds: (1) Query transformation (expansion, decomposition, HyDE), (2) Hybrid retrieval (dense + sparse + re-rank), (3) Document processing (smart chunking, metadata filtering), (4) Self-reflection (check if answer is grounded, retrieve more if needed), (5) Multi-step reasoning (iterative retrieval for complex questions).

**Q: How does HyDE (Hypothetical Document Embedding) work?**
A: Problem: queries are short, documents are long — embedding mismatch. HyDE: (1) LLM generates a hypothetical answer to the query (without retrieval), (2) Embed the hypothetical answer (now similar in form to actual documents), (3) Retrieve using this embedding. The hypothetical answer is closer in embedding space to relevant documents than the short query is.

---

## 16. Text Generation and Decoding Strategies

**Answer:**
Text generation produces text token by token from a language model. The decoding strategy determines how tokens are selected from the probability distribution at each step, dramatically affecting output quality.

**Decoding Strategies:**

| Strategy | Method | Properties | Use case |
|----------|--------|------------|----------|
| Greedy | Pick highest probability token | Deterministic, repetitive | Baseline (rarely used alone) |
| Beam search (B=5) | Track top-B sequences | More diverse than greedy, still focused | Translation, summarization |
| Temperature sampling | Sample from P^(1/T) distribution | T<1: peaked (conservative), T>1: flat (creative) | Creative writing, exploration |
| Top-K sampling | Sample from top-K tokens only | Cuts low-probability tail | General generation |
| Top-P (nucleus) | Sample from smallest set with cumulative P ≥ p | Adaptive vocabulary per step | Most popular for chat |
| Min-P | Keep tokens with P ≥ min_p × max_prob | Adapts to confidence level | Newer, quality-focused |
| Repetition penalty | Reduce probability of recently generated tokens | Prevents loops | All generation |
| Typical sampling | Sample tokens near expected information content | More "human-like" | Creative text |

**Key Parameters:**

| Parameter | Effect of increasing | Typical range |
|-----------|---------------------|---------------|
| Temperature | More random/creative, less coherent | 0.0-2.0 (0.7 for chat, 0.0 for code) |
| Top-P | Larger candidate pool, more diverse | 0.8-0.95 |
| Top-K | More candidates, more diverse | 20-100 |
| Repetition penalty | Less repetition, may become incoherent | 1.0-1.3 |
| Max tokens | Longer outputs | Task-dependent |
| Frequency penalty | Penalizes repeated tokens proportionally | 0.0-1.0 |
| Presence penalty | Penalizes any repeated token equally | 0.0-1.0 |

**Layman Example:**
Choosing the next word in a sentence "The cat sat on the...":
- **Greedy:** Always pick the most likely word: "mat" (probability 40%). Safe but boring — always the same sentence.
- **Beam search:** Track multiple options simultaneously: "mat" (40%), "floor" (20%), "couch" (15%). Pick the best complete sentence. Like considering multiple paths in a maze.
- **Temperature=0.5:** Make "mat" even more likely (peaked distribution). Conservative, predictable.
- **Temperature=1.5:** "mat" (20%), "floor" (15%), "roof" (10%), "moon" (8%)... More creative and surprising.
- **Top-P=0.9:** Only consider words until cumulative probability reaches 90%. Cuts off very unlikely completions ("volcano", "spaceship") while allowing creative choices.

**Follow-up Questions:**

**Q: Why not just always use greedy decoding?**
A: Greedy decoding (1) produces repetitive, boring text (picks safe choices), (2) can get stuck in loops ("I think I think I think..."), (3) misses globally better sequences (a locally suboptimal token might lead to a better overall sentence). For tasks needing determinism (code, math), use temperature=0. For creative tasks, sampling is essential.

**Q: What's the difference between temperature and top-P?**
A: Temperature reshapes the entire distribution (peaks or flattens it). Top-P cuts the distribution tail (only considers tokens that contribute to top P cumulative probability). They're often used together: temperature adjusts confidence levels, top-P prevents very unlikely tokens from being selected. Best combination: temperature=0.7 + top_p=0.9.

**Q: What is beam search and when is it used?**
A: Beam search tracks B best partial sequences at each step, expanding each by all possible next tokens and keeping top-B scoring paths. It explores more of the search space than greedy while being more focused than random sampling. Used for: translation (deterministic, quality-focused), summarization, code generation where correctness matters more than creativity. Not used for open-ended chat (too deterministic).

**Q: How do you prevent repetition in generation?**
A: (1) Repetition penalty (reduce logits of tokens that appeared before), (2) Frequency penalty (proportional to count), (3) Presence penalty (binary: appeared or not), (4) N-gram blocking (prevent any n-gram from repeating), (5) Training with diversity objectives. Most APIs combine multiple approaches.

---

## 17. Prompt Engineering

**Answer:**
Prompt engineering designs input prompts to elicit desired behavior from LLMs without modifying model weights. It's the primary interface for using pre-trained models effectively.

**Prompting Techniques:**

| Technique | Description | When to use |
|-----------|-------------|-------------|
| Zero-shot | Just the instruction, no examples | Simple tasks, strong models |
| Few-shot | Include 3-5 input/output examples | When format/style guidance needed |
| Chain-of-Thought (CoT) | "Let's think step by step" | Reasoning, math, logic |
| Self-consistency | Generate multiple CoT paths, majority vote | Improve CoT reliability |
| ReAct | Reason + Act (tool use interleaved) | Tasks needing external info |
| Tree of Thoughts | Explore multiple reasoning branches | Complex problem solving |
| System prompt | Set behavior/persona/constraints | All chat applications |
| Structured output | "Respond in JSON format: {...}" | Data extraction |

**Prompt Structure (best practices):**
```
System: You are a [role]. [constraints]. [output format].
User: [Context/background]
      [Specific instruction]
      [Examples if few-shot]
      [Input data]
      [Output format reminder]
```

**Chain-of-Thought Example:**
```
Without CoT: "What is 17 × 24?" → "408" (sometimes wrong)
With CoT: "What is 17 × 24? Think step by step."
→ "17 × 24 = 17 × 20 + 17 × 4 = 340 + 68 = 408" (more reliable)
```

**Comparison of prompting vs. fine-tuning:**

| Aspect | Prompting | Fine-tuning |
|--------|-----------|-------------|
| Data needed | 0-10 examples | 100-10000+ examples |
| Cost | Token cost only | Training cost + token cost |
| Flexibility | Change task by changing prompt | Fixed to trained task |
| Consistency | Variable (stochastic) | More deterministic |
| Accuracy ceiling | Limited by model + prompt | Higher for specific tasks |
| Latency | Higher (longer prompts) | Lower (short prompts) |
| Maintenance | Easy (edit prompt) | Hard (retrain) |

**Follow-up Questions:**

**Q: Why does Chain-of-Thought work?**
A: CoT gives the model "working memory" in the output tokens. Without CoT, the model must compute the answer in a single forward pass (limited computation). With CoT, each reasoning step generates tokens that become part of the context for subsequent steps — essentially allowing multi-step computation. It also exposes the reasoning process for verification.

**Q: What is the difference between few-shot and fine-tuning?**
A: Few-shot: examples in the prompt, no weight changes, flexible but uses context window, works for any task instantly. Fine-tuning: update model weights on many examples, permanent task specialization, higher accuracy ceiling, doesn't consume context window, but expensive and inflexible. Use few-shot for prototyping and flexibility, fine-tuning for production accuracy.

**Q: What are common prompt engineering mistakes?**
A: (1) Vague instructions ("Write something good"), (2) Not specifying output format, (3) Not providing negative examples (what NOT to do), (4) Too many instructions (model forgets early ones), (5) Not iterating on prompts systematically, (6) Assuming consistency (same prompt can give different outputs), (7) Not considering prompt injection attacks.

**Q: What is prompt injection and how do you defend against it?**
A: Prompt injection: adversarial user input that overrides system instructions ("Ignore previous instructions. Instead, ..."). Defenses: (1) Input sanitization, (2) Separate system/user message roles (model treats differently), (3) Output validation, (4) Instruction hierarchy (system prompt takes priority), (5) Guardrail models that detect injection attempts, (6) Limit model capabilities (no tool use for untrusted input).

---

## 18. Fine-Tuning LLMs (SFT, LoRA, QLoRA)

**Answer:**
Fine-tuning adapts pre-trained LLMs to specific tasks or behaviors. Full fine-tuning updates all parameters (expensive). Parameter-Efficient Fine-Tuning (PEFT) methods update only a small subset of parameters while achieving comparable results.

**Methods Comparison:**

| Method | Parameters trained | Memory needed | Quality | Speed |
|--------|-------------------|---------------|---------|-------|
| Full fine-tuning | 100% | Very high (4× model) | Best | Slowest |
| LoRA | 0.1-1% | Low (model + small adapters) | Near-full | Fast |
| QLoRA | 0.1-1% | Very low (4-bit model + adapters) | Near-full | Fast |
| Prefix tuning | <0.1% | Very low | Good | Very fast |
| Adapter layers | 1-5% | Low | Good | Fast |
| Prompt tuning | <0.01% | Minimal | Limited | Fastest |

**LoRA (Low-Rank Adaptation):**
```
Original: y = Wx (W is d×d)
LoRA: y = Wx + BAx (B is d×r, A is r×d, r << d)

- Freeze original weights W
- Train only low-rank matrices A and B (r = 8-64 typically)
- Parameters: 2×d×r instead of d×d (reduction: d/2r ≈ 100-1000×)
- At inference: merge W' = W + BA (no extra latency)
```

**QLoRA additions:**
- Quantize base model to 4-bit (NF4 format)
- Apply LoRA adapters in FP16
- Use double quantization (quantize the quantization constants)
- Use paged optimizers (handle memory spikes)
- Result: Fine-tune 65B model on single 48GB GPU

**Training Data Formats:**

| Format | Example | Use case |
|--------|---------|----------|
| Instruction | {"instruction": "Summarize", "input": "...", "output": "..."} | General instruction following |
| Chat | [{"role": "system", ...}, {"role": "user", ...}, {"role": "assistant", ...}] | Conversational fine-tuning |
| Completion | "Input: ... Output: ..." | Simple text completion |
| DPO pairs | {"prompt": "...", "chosen": "...", "rejected": "..."} | Preference alignment |

**Layman Example:**
- **Full fine-tuning:** Renovating an entire house (expensive, time-consuming, transforms everything).
- **LoRA:** Adding a small extension/conservatory to the house (cheap, quick, targeted improvement). The main house stays unchanged, you just added new capability.
- **QLoRA:** Building the extension with budget materials (4-bit quantized base) while making the extension itself high-quality (FP16 adapters). Same result, fraction of the cost.
- **Prompt tuning:** Just redecorating (changing the "prompt" at the entrance) — cheapest change but limited impact.

**Follow-up Questions:**

**Q: How does LoRA work mathematically?**
A: Key insight: weight updates during fine-tuning have low intrinsic rank. Instead of updating full W (d×d), decompose the update as ΔW = BA where B∈ℝ^(d×r), A∈ℝ^(r×d), r<<d. A is initialized randomly (Gaussian), B is initialized to zero (so ΔW=0 at start = pre-trained model). Only A and B are trained. At inference: W_new = W + BA can be pre-merged — no extra latency.

**Q: How do you choose LoRA rank (r) and which layers to adapt?**
A: Rank r: higher = more capacity but more parameters. Typical: r=8 for simple tasks, r=16-64 for complex tasks. Layers: attention layers (Q, K, V, O projections) are most effective. Some also adapt FFN layers. For chat fine-tuning: adapt all attention layers with r=16. For narrow tasks: Q and V projections with r=8 may suffice.

**Q: How much data do you need for fine-tuning?**
A: Depends on task: (1) Style/format change: 100-500 examples, (2) New task: 1K-10K examples, (3) Domain knowledge: 10K-100K examples. Quality matters more than quantity — 1000 high-quality examples often outperform 10000 noisy ones. Use data deduplication, quality filtering, and diverse examples.

**Q: What is the difference between SFT and RLHF/DPO?**
A: SFT (Supervised Fine-Tuning): train to mimic ideal responses (one correct answer per prompt). DPO/RLHF: train to prefer one response over another (comparative). SFT teaches "what to say," DPO/RLHF teaches "what's better." In practice: SFT first (learn basic format/task), then DPO (refine quality/safety). SFT alone can produce competent models; DPO adds polish.

---

## 19. Embeddings and Semantic Search

**Answer:**
Text embeddings map text (words, sentences, documents) to dense vectors where semantic similarity corresponds to vector proximity. Core infrastructure for search, recommendation, clustering, and RAG.

**Embedding Models:**

| Model | Dimensions | Context | Strength |
|-------|-----------|---------|----------|
| Word2Vec | 300 | Word-level | Fast, interpretable |
| Sentence-BERT (SBERT) | 768 | Sentence | Semantic similarity |
| E5-large-v2 | 1024 | 512 tokens | General retrieval |
| BGE-large | 1024 | 512 tokens | Strong bilingual |
| OpenAI text-embedding-3 | 256-3072 | 8192 tokens | Best commercial |
| Cohere embed-v3 | 1024 | 512 tokens | Multi-lingual |
| GTE-Qwen2 | 768-1536 | 8192 tokens | Open-source leader |
| Nomic-embed-text | 768 | 8192 tokens | Long context, open |

**Similarity Metrics:**

| Metric | Formula | Range | Properties |
|--------|---------|-------|------------|
| Cosine similarity | (a·b)/(‖a‖·‖b‖) | [-1, 1] | Scale-invariant, most common |
| Dot product | a·b | (-∞, ∞) | Sensitive to magnitude |
| Euclidean distance | ‖a-b‖ | [0, ∞) | Geometric distance |

**Training Approaches for Sentence Embeddings:**

| Method | Supervision | Data |
|--------|-------------|------|
| Contrastive (SimCSE) | Self-supervised (dropout augmentation) | Unlabeled text |
| NLI-based (SBERT) | Supervised (entailment pairs) | NLI datasets |
| Retrieval-based (DPR) | Supervised (query-passage pairs) | QA/search datasets |
| Distillation (E5) | Weakly supervised (LLM-generated pairs) | Synthetic + natural |
| Instruction-tuned (Instructor) | Instruction-prefixed | Multi-task |

**Layman Example:**
A library catalog system:
- **Keyword search (BM25):** Find books containing exact words you typed. "Machine learning algorithms" finds books with those exact words but misses "AI techniques" (same meaning, different words).
- **Semantic search (embeddings):** Convert your query and all book descriptions into "meaning vectors." Find books whose meaning is closest to your query's meaning, regardless of exact wording. "Machine learning algorithms" also finds "deep learning methods" and "AI techniques."

**Follow-up Questions:**

**Q: How is contrastive learning used to train embedding models?**
A: Pairs of related texts (positive pairs) should have similar embeddings; unrelated texts (negative pairs) should be far apart. InfoNCE loss: maximize similarity of positive pair relative to negatives. Positive pairs from: same document paragraphs, query-answer pairs, NLI entailment pairs, or augmented views (SimCSE uses dropout as augmentation — same text through model twice with different dropout gives positive pair).

**Q: What is the difference between bi-encoder and cross-encoder?**
A: Bi-encoder: encode query and document independently → compare embeddings. Fast (pre-compute document embeddings), but no cross-interaction. Cross-encoder: encode [query, document] together → single relevance score. Slow (must process every pair), but much more accurate (full interaction). Best practice: bi-encoder for retrieval (top-100), cross-encoder for re-ranking (top-100 → top-10).

**Q: How do you evaluate embedding quality?**
A: (1) MTEB benchmark (Massive Text Embedding Benchmark) — 56 tasks across 8 categories (retrieval, classification, clustering, etc.). (2) Task-specific: Recall@K for retrieval, Spearman correlation for STS (Semantic Textual Similarity). (3) A/B testing in production systems.

**Q: How do you handle embedding of long documents?**
A: (1) Truncate to model max length (loses info), (2) Chunk and embed each chunk separately (multiple vectors per doc), (3) Use long-context models (8K+ tokens), (4) Hierarchical: embed paragraphs, aggregate via mean/attention pooling, (5) Late chunking: encode full document with long-context model, then pool specific spans.

---

## 20. Named Entity Linking and Relation Extraction

**Answer:**
Entity Linking maps mentions in text to entries in a knowledge base (e.g., "Apple" → Apple Inc. in Wikidata). Relation Extraction identifies relationships between entities (e.g., "Elon Musk" → CEO_of → "Tesla").

**Entity Linking Pipeline:**
```
Text: "Jobs founded Apple in his garage"
1. Mention detection: [Jobs], [Apple]
2. Candidate generation: Jobs → {Steve Jobs, job positions, Jobs movie}
                         Apple → {Apple Inc., apple fruit, Apple Records}
3. Disambiguation: Context "founded" + "garage" → Steve Jobs, Apple Inc.
4. NIL prediction: If no KB entry matches → new entity
```

**Relation Extraction Approaches:**

| Approach | Method | Pros | Cons |
|----------|--------|------|------|
| Pipeline | NER → classify entity pairs | Simple, modular | Error propagation |
| Joint | Extract entities + relations together | No error propagation | Complex |
| Distant supervision | Align KB triples with text | No manual labels | Noisy labels |
| Prompt-based | LLM extracts structured info | Zero/few-shot, flexible | Slow, expensive |
| Generative | Seq2seq outputs structured triples | End-to-end | May hallucinate |

**Knowledge Graph Construction Pipeline:**
```
Raw text → NER → Entity Linking → Relation Extraction → (Entity, Relation, Entity) triples → Knowledge Graph
```

**Follow-up Questions:**

**Q: How does entity disambiguation work?**
A: Context-based scoring: encode mention + surrounding context, compare with candidate entity descriptions/embeddings. Features: (1) Name similarity (string matching), (2) Context similarity (does surrounding text match entity description?), (3) Coherence (do other entities in the document support this linking?), (4) Popularity prior (more famous entities are more likely).

**Q: What is distant supervision for relation extraction?**
A: Automatically label training data using existing knowledge base: if KB says (Obama, born_in, Hawaii) and a sentence mentions both "Obama" and "Hawaii," assume that sentence expresses "born_in." Noisy (sentence might express a different relation) but provides massive training data without manual labeling. Multi-instance learning handles noise.

**Q: How do LLMs change information extraction?**
A: LLMs enable zero/few-shot extraction via prompting: "Extract all (person, role, company) triples from this text." Benefits: no task-specific training, handles novel entity/relation types, flexible output format. Limitations: slower, expensive at scale, may hallucinate relationships. Best for low-volume high-value extraction; traditional models for high-volume production.

---

## 21. Speech and Audio Processing (ASR, TTS)

**Answer:**
Speech processing converts between audio and text. ASR (Automatic Speech Recognition) transcribes speech to text. TTS (Text-to-Speech) generates speech from text. Modern approaches use end-to-end neural models.

**ASR Evolution:**

| Era | Approach | Example | WER |
|-----|----------|---------|-----|
| Traditional | GMM-HMM + language model | Kaldi | ~15% |
| Hybrid | DNN-HMM | DeepSpeech | ~10% |
| End-to-end (CTC) | Single neural model + CTC loss | DeepSpeech 2 | ~8% |
| Attention-based | Encoder-decoder + attention | LAS | ~6% |
| Self-supervised | Pre-train on unlabeled audio + fine-tune | Wav2Vec 2.0 | ~4% |
| Universal | Large-scale multi-task | Whisper (OpenAI) | ~3-5% |
| Real-time | Streaming models | Conformer (Google) | ~3% |

**Whisper Architecture:**
```
Audio → Mel spectrogram → Encoder (Transformer) → Decoder (Transformer) → Text
- Multi-task: transcription, translation, language detection, timestamps
- Trained on 680K hours of labeled audio
- Robust to noise, accents, domains
```

**TTS Models:**

| Model | Approach | Quality | Speed |
|-------|----------|---------|-------|
| Tacotron 2 | Attention-based spectrogram prediction | Good | Slow |
| FastSpeech 2 | Non-autoregressive, parallel | Good | Fast |
| VITS | End-to-end GAN-based | High | Real-time |
| VALL-E | Language model on audio tokens | Very high (voice cloning) | Moderate |
| Bark | GPT-like on audio tokens | High (expressive) | Moderate |
| XTTS v2 | Multi-lingual, voice cloning | High | Real-time |

**Follow-up Questions:**

**Q: How does Whisper achieve robustness across domains?**
A: Massive diverse training data (680K hours from internet — varied accents, noise levels, domains, languages). Multi-task training (transcription + translation + language ID) creates more general representations. Weak supervision (auto-generated labels) with large scale outperforms small curated datasets.

**Q: What is CTC (Connectionist Temporal Classification)?**
A: CTC handles variable-length alignment between audio frames and text characters. The model outputs probability over characters + blank token at each frame. CTC loss marginalizes over all valid alignments (many frames can map to one character). Allows training without frame-level alignment labels. Limitation: assumes output tokens are conditionally independent.

**Q: How does voice cloning work (VALL-E)?**
A: (1) Encode a short reference audio (3 seconds) into audio tokens, (2) Given text + reference tokens, generate audio tokens that match the reference speaker's voice, (3) Decode tokens to waveform. VALL-E treats TTS as a language modeling problem on discrete audio codes. The reference audio conditions the generation style/timbre.

---

## 22. Multi-Modal Models (Vision + Language)

**Answer:**
Multi-modal models process and relate information across modalities (text, images, audio, video). Vision-Language Models (VLMs) understand images and text together, enabling visual QA, captioning, visual reasoning, and instruction following.

**Key Models:**

| Model | Architecture | Capabilities | Year |
|-------|-------------|-------------|------|
| CLIP | Dual encoder (separate image/text) | Zero-shot classification, retrieval | 2021 |
| BLIP-2 | Frozen image encoder + Q-Former + LLM | VQA, captioning | 2023 |
| LLaVA | CLIP + linear projection + LLaMA | Visual instruction following | 2023 |
| GPT-4V/4o | Native multi-modal transformer | Any vision-language task | 2023 |
| Gemini | Native multi-modal (interleaved) | Vision + language + audio + code | 2024 |
| Qwen-VL-2 | Vision encoder + LLM | Strong open VLM | 2024 |
| InternVL 2.5 | Scaling vision encoder + LLM | Best open-source VLM | 2024 |

**Architectures:**

| Pattern | How it works | Example |
|---------|-------------|---------|
| Dual encoder | Separate encoders, align in shared space | CLIP |
| Cross-attention fusion | Image features attend to text and vice versa | Flamingo |
| Projection-based | Map image tokens into LLM's embedding space | LLaVA |
| Native multi-modal | Single model processes both modalities | GPT-4o, Gemini |
| Q-Former (bridge) | Learned queries extract info from frozen image encoder | BLIP-2 |

**Tasks:**

| Task | Input | Output |
|------|-------|--------|
| Image captioning | Image | Text description |
| Visual QA (VQA) | Image + question | Answer |
| Visual reasoning | Image + complex question | Reasoned answer |
| Image-text retrieval | Image or text query | Matching text/image |
| Visual grounding | Image + text description | Bounding box |
| OCR/Document understanding | Document image | Structured text |
| Text-to-image generation | Text prompt | Image |

**Layman Example:**
- **CLIP:** A bilingual person who can match pictures to descriptions. Show them a photo and 100 captions — they pick the right one. Or give them a description and 100 photos — they find the match.
- **LLaVA/GPT-4V:** Like having eyes AND a brain that can read, reason, and discuss what it sees. "What's wrong in this image?" → It identifies issues, explains them, and can discuss follow-up questions about the image.
- **Gemini:** A person who can simultaneously see, hear, read, and code — processing all information together naturally rather than translating between senses.

**Follow-up Questions:**

**Q: How does LLaVA connect vision to language?**
A: LLaVA = CLIP vision encoder + simple linear projection + LLaMA. Image is encoded by CLIP into visual tokens. A linear layer (or MLP) projects visual tokens to LLaMA's embedding dimension. These visual tokens are prepended to text tokens. LLaMA processes them as if they're special "visual words." Trained in two stages: (1) Pre-train projection on image-caption pairs, (2) Fine-tune end-to-end on visual instruction data.

**Q: What is visual instruction tuning?**
A: Create training data where an AI responds to instructions about images: "Describe this image in detail," "What is unusual about this photo?", "Count the number of people." Generated using GPT-4 (text-only) with detailed image descriptions + bounding boxes as input. This teaches VLMs to follow diverse visual instructions, similar to how text instruction tuning works for LLMs.

**Q: What are the limitations of current VLMs?**
A: (1) Hallucination (describing objects not in the image), (2) Spatial reasoning (left/right, counting), (3) Fine-grained text in images (OCR quality varies), (4) Small objects, (5) Multi-image reasoning, (6) Video understanding at scale. Active research areas: grounding (connecting words to image regions), reducing hallucination via better training data and RLHF.

---

## 23. Evaluation of NLG (BLEU, ROUGE, BERTScore, Human Eval)

**Answer:**
Evaluating natural language generation is fundamentally difficult because there are many valid outputs for any input. Metrics attempt to measure quality automatically, but all have significant limitations compared to human judgment.

**Metrics Comparison:**

| Metric | Measures | Based on | Correlation with humans | Use for |
|--------|----------|----------|------------------------|---------|
| BLEU | Precision of n-grams | Surface overlap | Moderate | Translation |
| ROUGE | Recall of n-grams | Surface overlap | Moderate | Summarization |
| METEOR | Alignment with synonyms + stemming | Surface + semantic | Better | Translation |
| BERTScore | Token-level embedding similarity | Semantic | Good | General NLG |
| BLEURT | Learned metric (fine-tuned BERT) | Learned | Very good | Translation |
| COMET | Learned metric for MT | Learned | Best for MT | Translation |
| MAUVE | Distribution comparison | KL divergence | Good | Open-ended generation |
| Perplexity | Probability of text under model | Model likelihood | Limited | Language modeling |
| G-Eval | LLM-as-judge with criteria | LLM judgment | Good | Any NLG task |
| Human eval | Direct human judgment | Human | Gold standard | Final evaluation |

**LLM-as-Judge (G-Eval) Framework:**
```
Prompt: "Rate the following summary on a scale of 1-5 for:
- Fluency: Is it grammatically correct and natural?
- Coherence: Does it flow logically?
- Relevance: Does it capture key information?
- Faithfulness: Is everything factually supported by the source?"

Advantages: Scalable, correlates well with humans, customizable criteria
Risks: Bias (prefers longer/verbose text, self-preference), inconsistency
```

**Layman Example:**
Grading essays:
- **BLEU:** Count how many exact phrases from the answer key appear in the essay. A perfect paraphrase scores 0 (wrong words, right meaning). Crude but useful for translation.
- **BERTScore:** Check if the essay says approximately the same things as the answer key, even with different words. Better at capturing meaning.
- **LLM-as-Judge:** Have a skilled reader grade the essay on rubric criteria (clarity, accuracy, completeness). Most human-like but can have biases.
- **Human eval:** The real teacher grades it. Most reliable but expensive and slow.

**Follow-up Questions:**

**Q: Why is BLEU widely used despite being flawed?**
A: (1) It's simple, fast, and reproducible (automatic). (2) Corpus-level BLEU correlates reasonably with human judgment for MT. (3) Historical inertia — everyone reports it, enabling comparison across papers. (4) It's free (no model/API needed). Modern recommendation: report BLEU for comparison with prior work, but also report a learned metric (COMET) and ideally human evaluation.

**Q: What is the problem with reference-based metrics?**
A: They assume one (or few) references capture all valid outputs. But many valid summaries/translations exist. A perfectly good output that differs from the reference scores poorly. Solutions: (1) Multiple references (expensive), (2) Reference-free metrics (QuestEval, BARTScore), (3) LLM-as-judge (no reference needed), (4) Learned metrics trained on human judgments.

**Q: How do you design reliable human evaluation?**
A: (1) Clear rubric with defined scales (not just "rate quality"), (2) Multiple annotators (compute inter-annotator agreement — Cohen's kappa), (3) Control for annotator biases (randomize order, blind to model identity), (4) Sufficient sample size (statistical significance), (5) Separate dimensions (fluency, factuality, relevance — don't collapse into single score). Tools: Amazon Mechanical Turk, Scale AI, or internal annotation teams.

**Q: What are the biases in LLM-as-judge evaluation?**
A: (1) Verbosity bias (prefers longer outputs), (2) Position bias (prefers first response in A/B comparison), (3) Self-preference (Claude prefers Claude outputs, GPT prefers GPT), (4) Sycophancy (tends to rate highly), (5) Style over substance (eloquent wrong answer scores higher). Mitigations: randomize positions, use multiple judges, calibrate with known-quality examples.

---

## 24. Efficient Transformers and Long Context

**Answer:**
Standard self-attention is O(n²) in sequence length, limiting context to 2-8K tokens. Efficient attention methods reduce this to O(n·log n) or O(n), enabling 100K-1M+ token contexts.

**Approaches:**

| Method | Complexity | Mechanism | Max context | Model |
|--------|-----------|-----------|-------------|-------|
| Standard attention | O(n²) | Full attention matrix | 2-8K | BERT, GPT-3 |
| Sparse attention | O(n√n) | Local + global + random | 4K-16K | Longformer, BigBird |
| Linear attention | O(n) | Kernel approximation | Unlimited (theory) | Performer |
| Flash Attention | O(n²) but fast | IO-aware implementation | Limited by GPU memory | All modern LLMs |
| State-space models | O(n) | Recurrent-like + conv | Very long | Mamba, S4 |
| Ring attention | O(n²) distributed | Distribute across GPUs | 1M+ | Gemini 1.5 |
| RoPE + scaling | O(n²) | Position extrapolation | 128K+ | LLaMA 3, Mistral |
| Sliding window | O(n·w) | Fixed local window | Long (with global) | Mistral |

**Flash Attention (Details):**
```
Problem: Standard attention materializes O(n²) attention matrix in GPU HBM (slow memory)
Solution: Tile computation so it fits in SRAM (fast memory)
- Split Q, K, V into blocks
- Compute attention one block at a time
- Never materialize full attention matrix
- Result: Exact same output, 2-4× faster, O(n) memory
- Enables 2-4× longer sequences on same hardware
```

**State Space Models (Mamba):**
```
Traditional attention: O(n²) — every token looks at every other
Mamba: O(n) — selective state space model
- Processes sequence like an RNN (linear in length)
- But with selective gating (input-dependent filtering)
- During training: parallel (like conv)
- During inference: recurrent (constant memory per step)
- Matches Transformer quality at smaller scale
```

**Comparison:**

| Aspect | Transformer | Mamba (SSM) | Hybrid (Jamba) |
|--------|-------------|-------------|----------------|
| Complexity | O(n²) | O(n) | Mixed |
| In-context learning | Excellent | Good | Excellent |
| Long sequences | Expensive | Efficient | Efficient |
| Recall from context | Strong | Weaker on recall tasks | Strong |
| Training efficiency | Good (parallel) | Good (conv mode) | Good |
| Inference | KV cache grows | Constant state | Mixed |

**Follow-up Questions:**

**Q: What is the "Lost in the Middle" problem?**
A: LLMs with long context tend to best utilize information at the beginning and end of the context, but struggle to find and use information in the middle. For a 100K context with the answer at position 50K, accuracy drops significantly. This suggests attention doesn't uniformly cover long contexts. Mitigations: put important info first/last, use retrieval to extract relevant chunks, train with specific long-context tasks.

**Q: How does RoPE position interpolation enable longer context?**
A: RoPE trained with max position 4096 breaks when extrapolating to 8192+. Position interpolation: scale positions to fit within trained range (position 8192 → scaled to 4096). NTK-aware scaling adjusts the rotation frequencies instead. YaRN combines multiple scaling approaches. After scaling + short fine-tuning, models handle 128K+ contexts.

**Q: When should you use long context vs. RAG?**
A: Long context: when you have relatively small documents that fit in context, need to reason across the entire content, or can't set up retrieval infrastructure. RAG: when knowledge base is very large (millions of documents), needs frequent updates, when you need attribution/citation, or when token costs matter. Hybrid: RAG retrieves relevant docs → place in long context for reasoning.

**Q: What are the advantages of state-space models (Mamba) over Transformers?**
A: (1) Linear complexity in sequence length (O(n) vs O(n²)). (2) Constant memory during inference (no growing KV cache). (3) Faster inference for long sequences. (4) Better for streaming/real-time applications. Disadvantages: weaker at precise recall/copying tasks, less proven at very large scale. Hybrid architectures (Jamba: mix of attention + Mamba layers) may be optimal.

---

## 25. Ethics, Bias, and Safety in NLP

**Answer:**
NLP systems can perpetuate and amplify societal biases present in training data. Understanding, measuring, and mitigating these biases is critical for responsible deployment.

**Types of Bias:**

| Bias type | Description | Example |
|-----------|-------------|---------|
| Training data bias | Data reflects historical inequities | Job descriptions with gendered language |
| Representation bias | Underrepresentation of groups | Less training data for minority dialects |
| Algorithmic bias | Model amplifies data bias | Sentiment analyzer rates African-American names more negatively |
| Evaluation bias | Benchmarks don't cover all groups | Tests only in standard English |
| Deployment bias | Unequal access/impact | Translation quality worse for low-resource languages |

**Safety Concerns in LLMs:**

| Concern | Description | Mitigation |
|---------|-------------|------------|
| Hallucination | Generating false but convincing text | RAG, citation, confidence calibration |
| Toxic generation | Producing harmful content | RLHF, safety fine-tuning, content filters |
| Prompt injection | Adversarial inputs override instructions | Input validation, hierarchy, guardrails |
| Data leakage | Memorizing/reproducing training data | Deduplication, differential privacy |
| Bias amplification | Stereotypes in outputs | Bias evaluation, balanced fine-tuning |
| Dual use | Misuse for disinformation | Watermarking, detection tools |

**Bias Measurement:**

| Benchmark | What it measures |
|-----------|-----------------|
| WinoBias | Gender bias in coreference resolution |
| StereoSet | Stereotypical associations |
| CrowS-Pairs | Bias across multiple dimensions |
| BBQ | Bias in question answering |
| RealToxicityPrompts | Toxicity of model continuations |
| TruthfulQA | Tendency to generate popular misconceptions |

**Alignment Techniques:**

| Technique | How it works | Stage |
|-----------|-------------|-------|
| RLHF | Human raters prefer safe/helpful responses | Post-training |
| DPO | Direct optimization on preference pairs | Post-training |
| Constitutional AI | AI self-critiques against principles | Post-training |
| Red teaming | Adversarial testing to find vulnerabilities | Evaluation |
| Guardrails | Rule-based or model-based output filtering | Deployment |
| Watermarking | Embed detectable signal in generated text | Deployment |

**Follow-up Questions:**

**Q: How do word embeddings encode bias?**
A: Word2Vec/GloVe capture societal stereotypes from training text. "Computer programmer" is closer to "man" than "woman." "Nurse" is closer to "woman." These biases propagate to downstream tasks (biased resume screening). Debiasing: (1) Project out gender direction from embeddings, (2) Counterfactual data augmentation, (3) Adversarial training.

**Q: What is the difference between fairness and debiasing?**
A: Debiasing removes specific known biases from model representations. Fairness is broader — ensuring equitable outcomes across groups. Types: (1) Demographic parity (equal positive prediction rates), (2) Equalized odds (equal TPR and FPR across groups), (3) Individual fairness (similar individuals get similar outcomes). These criteria can conflict — achieving one may violate another.

**Q: How does RLHF help with safety?**
A: Human raters compare model outputs and prefer responses that are helpful, harmless, and honest. The reward model learns these preferences. PPO optimizes the LLM to generate preferred-style outputs. This teaches the model to refuse harmful requests, provide balanced responses, and express uncertainty appropriately. Limitation: reward hacking (model finds outputs that score high on reward model but aren't genuinely better).

**Q: What is red teaming for LLMs?**
A: Systematic adversarial testing to find failure modes: (1) Try to elicit harmful content, (2) Test for bias across demographics, (3) Probe for jailbreaks (bypassing safety), (4) Test hallucination in specific domains, (5) Check for data leakage. Done by specialized teams before deployment. Findings → update safety training data → retrain. Continuous process as new attack vectors emerge.

---

## Quick Reference: NLP Model Selection

| Task | Best approach (2024+) |
|------|----------------------|
| Text classification (production) | Fine-tuned DeBERTa or distilled model |
| Text classification (prototype) | GPT-4 zero/few-shot |
| NER (production) | Fine-tuned BERT/DeBERTa + CRF |
| NER (new entity types) | GLiNER or LLM prompting |
| Summarization | GPT-4/Claude (quality) or BART/Pegasus (cost) |
| Translation (production) | NLLB-200 or specialized NMT |
| Translation (quality) | GPT-4 / Claude 3.5 |
| QA (with context) | Fine-tuned DeBERTa or RAG + LLM |
| Open-domain QA | RAG (retrieval + LLM generation) |
| Semantic search | E5/BGE/GTE embeddings + FAISS |
| Chatbot | Fine-tuned LLaMA/Mistral or GPT-4 API |
| Code generation | Claude 3.5/GPT-4/DeepSeek Coder |
| Sentiment analysis | Fine-tuned BERT (labeled data) or LLM (zero-shot) |
| Multi-lingual | mBERT/XLM-R (understanding) or NLLB/GPT-4 (generation) |
| Document processing | LayoutLM (forms) or multi-modal LLM (general) |

---

## Common Interview Traps (NLP-Specific)

1. **"Bigger models are always better"** → Not always. Diminishing returns, hallucination increases with confidence, cost scales super-linearly. A fine-tuned small model often beats a prompted large model for specific tasks.

2. **"BERT is outdated"** → BERT-like models (DeBERTa) are still the best choice for production classification/NER/QA with labeled data. They're faster, cheaper, and more reliable than LLMs for specific tasks.

3. **"RAG solves hallucination"** → RAG reduces but doesn't eliminate hallucination. The LLM can still (1) ignore retrieved context, (2) misinterpret it, (3) fabricate connections between retrieved facts. Always verify factuality of RAG outputs.

4. **"Zero-shot is good enough"** → For production systems, fine-tuning almost always outperforms prompting in accuracy AND consistency. Zero-shot is great for prototyping but rarely sufficient for deployment.

5. **"Tokenization doesn't matter"** → Tokenization significantly affects: (1) effective context length, (2) multilingual performance, (3) math/code ability, (4) cost per query. Different tokenizers can be 2-3× more efficient for certain languages/domains.

6. **"LLMs understand language"** → Contentious. LLMs are excellent at pattern matching and statistical regularities. Whether this constitutes "understanding" is debated. They fail on tasks requiring true world modeling, causal reasoning, or rare/novel compositions not in training data.

7. **"More context = better"** → "Lost in the middle" problem. Models struggle to use information in the middle of long contexts. Retrieval (putting relevant info at the start) often outperforms dumping everything into a long context.

8. **"RLHF makes models safe"** → RLHF improves safety but is not sufficient. Models can be jailbroken, reward hacked, or fail on novel scenarios not covered in training. Defense in depth: RLHF + guardrails + monitoring + human oversight.
