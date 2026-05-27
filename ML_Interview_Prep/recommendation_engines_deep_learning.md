# Recommendation Engines & Deep Learning - Interview Concepts

---

## 1. Recommendation Systems Fundamentals

**Answer:**
Recommendation systems predict user preferences and suggest relevant items (products, content, ads). They solve the information overload problem — helping users discover relevant items from millions of options. Core approaches: collaborative filtering (user-item interactions), content-based (item features), and hybrid methods.

**Taxonomy:**

| Approach | Signal used | Key idea | Example |
|----------|------------|----------|---------|
| Collaborative Filtering (CF) | User-item interactions | "Users who liked X also liked Y" | Matrix factorization, user-based CF |
| Content-Based | Item/user features | "Since you liked action movies, here's another action movie" | TF-IDF similarity, feature matching |
| Knowledge-Based | Explicit constraints/rules | "Given your budget and requirements..." | Rule engines, constraint-based |
| Hybrid | Multiple signals combined | Combine CF + content + knowledge | Most production systems |
| Deep Learning | All of the above + sequences | Learn complex patterns end-to-end | Neural CF, two-tower, transformers |

**Key Metrics:**

| Metric | What it measures | Formula |
|--------|-----------------|---------|
| Precision@K | Of top-K recommendations, how many are relevant | Relevant∩Recommended / K |
| Recall@K | Of all relevant items, how many are in top-K | Relevant∩Recommended / Total_Relevant |
| NDCG@K | Quality of ranking (position matters) | DCG / Ideal_DCG |
| Hit Rate@K | % of users with at least one hit in top-K | Users_with_hit / Total_users |
| MRR | Average reciprocal rank of first relevant item | Mean(1/rank_first_relevant) |
| MAP | Mean average precision across queries | Mean of AP per query |
| AUC | Probability that positive ranked above negative | Area under ROC curve |
| Coverage | % of items ever recommended | Unique_recommended / Total_items |
| Diversity | How different recommendations are from each other | Avg pairwise distance |
| Novelty | How unexpected/surprising recommendations are | -log(popularity) of recommended items |

**Layman Example:**
Netflix recommending movies:
- **Collaborative Filtering:** "1000 users who watched Breaking Bad also watched Better Call Saul → recommend Better Call Saul to you."
- **Content-Based:** "You watched 5 sci-fi movies → here are more sci-fi movies with similar themes."
- **Hybrid:** Combine both: "Users like you watched Movie X (collaborative), AND Movie X matches your sci-fi preference (content) → strong recommendation."

**Follow-up Questions:**

**Q: What is the cold start problem?**
A: When the system has insufficient information to make recommendations. Three types: (1) **New user** — no interaction history (solution: ask preferences, use demographics, popular items, content-based on initial choices). (2) **New item** — no one has interacted yet (solution: content features, similar item mapping, explore/exploit). (3) **New system** — few users and items (solution: import external data, bootstrap with popular items). Deep learning helps via: feature-rich models that use any available signal, pre-trained embeddings, meta-learning.

**Q: What's the difference between explicit and implicit feedback?**
A: Explicit: user directly states preference (ratings, likes/dislikes). Rare, biased (people rate extreme items), but clear signal. Implicit: inferred from behavior (clicks, purchases, watch time, scrolls). Abundant but noisy (click ≠ like, purchase ≠ satisfied). Most production systems use implicit feedback. Models differ: explicit uses MSE on ratings; implicit uses BPR (Bayesian Personalized Ranking) or binary cross-entropy.

**Q: What is the exploration-exploitation tradeoff in recommendations?**
A: Exploitation: recommend items the system is confident the user will like (high predicted score). Exploration: recommend uncertain items to gather information and discover new preferences. Pure exploitation → filter bubble (users only see what they already like). Pure exploration → poor experience. Solutions: epsilon-greedy, Thompson sampling, UCB (Upper Confidence Bound), contextual bandits.

**Q: How do you evaluate recommendations offline vs. online?**
A: Offline: split historical interactions temporally, predict held-out interactions. Metrics: NDCG, Recall, HR. Fast, cheap, but may not reflect real user experience. Online: A/B test with real users. Metrics: CTR, engagement, revenue, retention. Expensive but ground truth. Gap: offline metrics and online metrics often don't correlate well — an offline improvement doesn't guarantee online improvement.

---

## 2. Collaborative Filtering (Matrix Factorization)

**Answer:**
Collaborative filtering predicts preferences from user-item interaction patterns. Matrix Factorization (MF) decomposes the sparse user-item interaction matrix into low-rank user and item embedding matrices, capturing latent factors.

**Matrix Factorization:**
$$\hat{r}_{ui} = \mathbf{p}_u^T \mathbf{q}_i + b_u + b_i + \mu$$

Where:
- $\mathbf{p}_u$ ∈ ℝ^d = user u's latent embedding
- $\mathbf{q}_i$ ∈ ℝ^d = item i's latent embedding
- $b_u$, $b_i$ = user/item biases
- $\mu$ = global bias (average rating)

**Training:**
$$\min_{p,q,b} \sum_{(u,i) \in \text{observed}} (r_{ui} - \hat{r}_{ui})^2 + \lambda(||\mathbf{p}_u||^2 + ||\mathbf{q}_i||^2 + b_u^2 + b_i^2)$$

**Methods Comparison:**

| Method | Approach | Pros | Cons |
|--------|----------|------|------|
| User-based CF | Find similar users, aggregate their ratings | Intuitive, no training | Doesn't scale (O(users²)) |
| Item-based CF | Find similar items, weighted average | Stable, explainable | O(items²) precompute |
| SVD/MF | Factorize interaction matrix | Scalable, handles sparsity | Cold start, linear only |
| SVD++ | MF + implicit feedback signals | Better accuracy | More complex |
| ALS (Alternating Least Squares) | Alternately fix U/V, solve linear system | Parallelizable, works with implicit | Approximate |
| BPR (Bayesian Personalized Ranking) | Pairwise: observed > unobserved | Good for implicit feedback | Sampling-dependent |

**Layman Example:**
A movie taste coordinate system:
- Each user is a point in a "taste space" (e.g., 50 dimensions)
- Each movie is also a point in the same space
- Dimensions might represent: "likes action" (dim 1), "likes complex plots" (dim 2), "prefers short movies" (dim 3)...
- Prediction: how close is the user point to the movie point? Close = high predicted rating.
- Learning: adjust user/movie positions so that known ratings are explained (liked movies are near the user, disliked movies are far).

The magic: these dimensions are NOT manually defined — they're discovered automatically from the pattern of ratings.

**Follow-up Questions:**

**Q: What is the difference between SVD and ALS?**
A: SVD: gradient descent on the loss (works for any loss function, stochastic updates). ALS: alternately fix user embeddings and solve for item embeddings (linear system), then fix items and solve for users. ALS advantages: (1) Naturally parallelizable (each user/item independent when other is fixed), (2) Better for implicit feedback (can use all zeros efficiently), (3) Used at scale (Spark MLlib). SVD: more flexible loss functions.

**Q: How does BPR work for implicit feedback?**
A: BPR assumes: items a user interacted with should rank higher than items they didn't. For each user u, sample a positive item i (interacted) and negative item j (not interacted). Optimize: P(i >_u j) = σ(score(u,i) - score(u,j)). This pairwise ranking approach is better than pointwise (predict click=1/0) because it directly optimizes the ranking objective.

**Q: What are the limitations of matrix factorization?**
A: (1) Only captures linear interactions between user/item embeddings (dot product), (2) Can't easily incorporate side features (user demographics, item metadata), (3) Cold start (no embedding for new users/items), (4) Static (doesn't capture temporal dynamics or sequential patterns), (5) Can't model complex non-linear preference patterns. → Deep learning addresses all these.

**Q: How do you handle the sparsity problem?**
A: Typical interaction matrices are 99%+ sparse (users interact with tiny fraction of items). Solutions: (1) MF naturally handles sparsity (learns from observed only), (2) Regularization prevents overfitting to sparse signals, (3) Side information (features) supplements sparse interactions, (4) Implicit feedback adds more signal (views, not just purchases), (5) Negative sampling (don't need all zeros).

---

## 3. Neural Collaborative Filtering (NCF)

**Answer:**
Neural Collaborative Filtering replaces the dot product in matrix factorization with a neural network, enabling non-linear user-item interactions. It was one of the first deep learning approaches to recommendations.

**Architecture (NCF):**
```
User ID → User Embedding → ┐
                            ├── Concatenate → MLP (hidden layers) → Prediction
Item ID → Item Embedding → ┘

GMF (Generalized MF): element-wise product of embeddings → linear → score
MLP: concatenation → FC layers → score
NeuMF: Combine GMF + MLP → final prediction

NeuMF:
User embedding (GMF) ⊙ Item embedding (GMF) ──┐
                                                ├── Concat → FC → Sigmoid → Score
User embedding (MLP) ⊕ Item embedding (MLP)    │
    → FC → ReLU → FC → ReLU → ──────────────┘
```

**Comparison with Matrix Factorization:**

| Aspect | Matrix Factorization | NCF/NeuMF |
|--------|---------------------|-----------|
| Interaction function | Dot product (linear) | Neural network (non-linear) |
| Expressiveness | Limited to linear | Arbitrary complex patterns |
| Side features | Difficult to incorporate | Easy (concatenate to input) |
| Training | Efficient (closed-form for ALS) | SGD, slower |
| Scalability | Very scalable | Model size limits |
| Cold start | Poor | Better (if features available) |
| Interpretability | Clear (latent factors) | Black box |

**Layman Example:**
- **Matrix Factorization (dot product):** User taste vector · Movie feature vector = compatibility score. Like asking "how aligned are these two arrows?" — only measures direction alignment.
- **NCF (neural network):** User taste vector + Movie feature vector → complex function → compatibility score. Like a sophisticated matchmaking algorithm that considers non-obvious compatibility patterns: "People who like both horror AND cooking shows tend to enjoy documentary thrillers" — a non-linear relationship a dot product can't capture.

**Follow-up Questions:**

**Q: Did NCF actually improve over optimized MF?**
A: Controversial. Rendle et al. (2020) showed that properly tuned dot-product MF (with appropriate embedding dimensions and regularization) matches or beats NCF/NeuMF. The original NCF paper compared against under-tuned baselines. Lesson: simple models well-tuned > complex models poorly tuned. However, NCF's framework (embedding + neural interaction) became the foundation for more advanced models.

**Q: How do you train NCF with implicit feedback?**
A: (1) Positive samples: observed interactions (user clicked/purchased item). (2) Negative samples: sample random items user hasn't interacted with (implicit negative — may have false negatives). (3) Loss: Binary cross-entropy (predict 1 for positive, 0 for negative). (4) Negative sampling ratio: typically 4-10 negatives per positive. (5) Hard negative mining: sample negatives that are "almost positive" (popular items user hasn't seen) for better signal.

**Q: What is the negative sampling strategy and why does it matter?**
A: Not interacting ≠ disliking (user may not have seen the item). Strategies: (1) Uniform random (simple but includes easy negatives), (2) Popularity-based (harder negatives from popular unseen items), (3) In-batch negatives (other users' positives as negatives — efficient), (4) Hard negatives from retrieval model (items ranked highly but not clicked). Strategy significantly affects model quality — too easy = model doesn't learn; too hard = training instability.

---

## 4. Two-Tower Architecture (Retrieval)

**Answer:**
Two-tower (dual encoder) architecture separately encodes users and items into the same embedding space, enabling efficient large-scale retrieval via approximate nearest neighbor (ANN) search. It's the standard architecture for the retrieval/candidate generation stage.

**Architecture:**
```
User Tower:                    Item Tower:
User features                  Item features
(ID, history, demographics)    (ID, title, category, image)
    ↓                              ↓
FC layers / Transformer        FC layers / Transformer
    ↓                              ↓
User embedding (d-dim)         Item embedding (d-dim)
    ↓                              ↓
         Similarity: dot product or cosine
         score(u, i) = user_emb · item_emb
```

**Key Property: Decomposability**
- Item embeddings can be pre-computed offline (billions of items)
- At serving time: compute user embedding (one forward pass) → ANN search against pre-computed item embeddings
- ANN search: sub-millisecond retrieval from billions of items (FAISS, ScaNN, HNSW)

**Models:**

| Model | Key innovation | Company |
|-------|---------------|---------|
| YouTube DNN (2016) | First large-scale two-tower for video recommendation | Google |
| DSSM | Deep Structured Semantic Model for text matching | Microsoft |
| Facebook EBR (2020) | Embedding-based retrieval with hard negatives | Meta |
| Google Two-Tower (2019) | Mixed negative sampling for search | Google |
| Airbnb (2018) | Listing embeddings from session sequences | Airbnb |

**Comparison: Two-Tower vs. Cross-Encoder:**

| Aspect | Two-Tower (Dual Encoder) | Cross-Encoder |
|--------|--------------------------|---------------|
| Inference | O(1) per candidate (pre-computed) | O(N) must process each pair |
| Accuracy | Good | Better (full cross-attention) |
| Scale | Billions of items | Hundreds (re-ranking only) |
| Use case | Retrieval/candidate generation | Re-ranking |
| Features | Can't use cross-features | Full feature interaction |
| Latency | <10ms for millions of items | 10-100ms for hundreds |

**Layman Example:**
Online dating app matching:
- **Two-Tower:** Create a "profile embedding" for each person (their tower encodes preferences, demographics, behavior). Pre-compute embeddings for all 10 million users. When you open the app, compute YOUR embedding, then instantly find the closest 100 profiles in the embedding space. Takes milliseconds.
- **Cross-Encoder:** For those top 100 candidates, now do a detailed compatibility analysis considering how YOUR specific preferences interact with THEIR specific qualities. Much more accurate but can only process ~100 candidates.

This two-stage approach (two-tower retrieval → cross-encoder re-ranking) is how virtually all production recommendation systems work.

**Follow-up Questions:**

**Q: What is the retrieval-ranking architecture?**
A: Two stages: (1) **Retrieval/Candidate Generation:** From millions/billions of items, quickly narrow to hundreds of candidates. Uses two-tower models, ANN search. Prioritizes recall (don't miss good items). (2) **Ranking:** Score the hundreds of candidates with a complex model (considers cross-features, user-item interactions). Prioritizes precision/NDCG (rank the best items highest). Some systems add a third stage (re-ranking for diversity/business rules).

**Q: What is the Approximate Nearest Neighbor (ANN) problem and how is it solved?**
A: Given a query vector, find the K closest vectors among billions. Exact search is O(N) — too slow. ANN solutions: (1) **FAISS (Facebook):** IVF (inverted file index) + PQ (product quantization). (2) **ScaNN (Google):** Anisotropic quantization. (3) **HNSW:** Hierarchical Navigable Small World graphs. (4) **Annoy (Spotify):** Random projection trees. Trade small accuracy loss for 100-1000× speedup. Typical: search 100M items in <1ms.

**Q: How do you handle feature interaction in two-tower models?**
A: By design, two-tower models can't compute cross-features (user-item interactions) because towers are independent. Solutions: (1) Accept the limitation (trade accuracy for speed), (2) Include user-item interaction features computed from history in the user tower (e.g., "how many action movies user watched" helps item tower action movie embeddings), (3) Use a mixture of retrieval models that capture different interaction types.

**Q: What is the training data for two-tower models?**
A: Positives: user-item interactions (clicks, purchases, long views). Negatives: (1) Random items (easy negatives — any item user didn't see), (2) In-batch negatives (other users' positives — moderate difficulty, memory efficient), (3) Hard negatives (items retrieved but not clicked — hardest). Mix matters: typically batch negatives + some hard negatives. Too many easy negatives = model doesn't discriminate well.

---

## 5. Deep Learning Ranking Models (Wide & Deep, DeepFM, DCN)

**Answer:**
Ranking models score a small set of candidate items (from retrieval) using rich features and feature interactions. Key challenge: capturing both memorization (specific feature combinations) and generalization (learned patterns).

**Model Evolution:**

| Model | Year | Key innovation | Architecture |
|-------|------|---------------|-------------|
| Wide & Deep | 2016 | Memorization (wide) + generalization (deep) | Linear + MLP, jointly trained |
| DeepFM | 2017 | Replace wide with FM (automatic 2nd-order interactions) | FM + MLP |
| DCN (Deep & Cross) | 2017 | Explicit cross-network for bounded-degree interactions | Cross layers + MLP |
| DCN v2 | 2020 | Mix of low-rank cross layers | Improved cross network |
| DIN (Deep Interest Network) | 2018 | Attention over user history w.r.t. candidate item | Attention-based |
| DIEN | 2019 | Interest evolution (GRU on user history) | Sequential interest modeling |
| AutoInt | 2019 | Self-attention over feature interactions | Transformer for features |
| DLRM (Facebook) | 2019 | Industry-standard architecture | Embedding + interaction + MLP |

**Wide & Deep Architecture:**
```
Wide component (memorization):
  Cross-product features → Linear model
  e.g., "user_installed_app=Netflix AND impression_app=Hulu" → weight

Deep component (generalization):
  Dense features + Embedding(sparse features) → MLP → hidden representation

Combined:
  P(y=1) = σ(W_wide · x_wide + W_deep · h_deep + bias)
```

**DeepFM Architecture:**
```
Sparse features → Embeddings → ┐
                                ├── FM: all 2nd-order interactions (e_i · e_j)
                                ├── MLP: higher-order interactions (concat → FC → FC)
                                └── Output = sigmoid(FM_output + MLP_output)

FM automatically learns: "user_age × item_genre", "user_location × item_brand" etc.
No manual cross-feature engineering needed.
```

**DCN (Deep & Cross Network):**
```
Cross Network (explicit feature crosses):
  x_{l+1} = x_0 · x_l^T · w_l + b_l + x_l    (bounded-degree polynomial)
  Layer 1: 2nd-order interactions
  Layer 2: 3rd-order interactions
  ...
  
Deep Network (implicit high-order):
  Standard MLP

Final: Combine cross and deep outputs → prediction
```

**Comparison:**

| Model | Feature interactions | Pros | Cons |
|-------|---------------------|------|------|
| Wide & Deep | Manual cross-features (wide) + learned (deep) | Industry proven (Google) | Requires feature engineering for wide |
| DeepFM | Automatic 2nd-order (FM) + higher (MLP) | No manual engineering | FM only captures 2nd-order |
| DCN v2 | Explicit bounded-degree + implicit (MLP) | Efficient cross-features | Cross layers add complexity |
| DIN | Attention-weighted history | Captures relevant history | Only user interest, not general crosses |
| DLRM | Embedding interaction + MLP | Simple, scalable | No explicit high-order |

**Layman Example:**
Recommending a restaurant:
- **Wide (memorization):** "Users who previously ordered Thai food on rainy Fridays order Thai food on rainy Fridays." Very specific pattern, memorized exactly. Works if you've seen this exact combination before.
- **Deep (generalization):** Learns abstract features: "this user likes spicy food" + "this restaurant serves spicy dishes" → match. Works for new combinations never seen in training.
- **DeepFM:** Automatically discovers that "age × cuisine_type" and "time_of_day × price_range" are important interaction features, without a human engineer specifying them.

**Follow-up Questions:**

**Q: Why do we need both memorization and generalization?**
A: Memorization captures specific co-occurrence patterns that work well for frequent combinations. Generalization captures abstract patterns that work for rare/unseen combinations. Example: "User A who bought exact items {X, Y, Z} tends to buy W" (memorization) vs. "Users who buy electronics tend to buy accessories" (generalization). Real-world recommendation needs both.

**Q: What is the Deep Interest Network (DIN) and why is attention important?**
A: DIN computes attention weights over a user's historical interactions WITH RESPECT TO the candidate item. Not all history is relevant: if recommending a camera, the user's past electronics purchases matter more than their book purchases. Attention score: a(history_item, candidate_item) → weight each historical interaction by relevance to the current candidate. This is local activation (item-specific user representation).

**Q: How does DLRM (Facebook) work in production?**
A: DLRM processes: (1) Dense features → MLP → dense representation. (2) Sparse/categorical features → Embedding lookup → list of embedding vectors. (3) All embedding pairs interact via dot product → interaction features. (4) Concat(dense_representation, interactions) → MLP → prediction. Designed for massive-scale: embedding tables can be terabytes, distributed across many machines.

---

## 6. Sequential Recommendation

**Answer:**
Sequential recommendation models the ORDER of user interactions to predict the next item. Unlike static CF (ignores order), sequential models capture evolving interests, session context, and temporal patterns.

**Models:**

| Model | Architecture | Key Idea | Year |
|-------|-------------|----------|------|
| GRU4Rec | GRU on session items | First neural sequential rec | 2016 |
| Caser | CNN on embedding matrix | Horizontal + vertical filters capture patterns | 2018 |
| SASRec | Transformer (self-attention) on sequence | Attention over history items | 2018 |
| BERT4Rec | Masked item prediction (BERT-style) | Bidirectional + masked training | 2019 |
| FMLP-Rec | MLP with learnable filters | Filter-based sequential modeling | 2022 |
| Rec-Transformer | Full transformer with item features | Rich item representations | 2022+ |

**SASRec Architecture:**
```
User interaction sequence: [item_1, item_2, ..., item_t]
    ↓
Item embeddings + Positional embeddings
    ↓
Causal Transformer (masked self-attention — can only attend to past items)
    ↓
Hidden state at position t = h_t
    ↓
Predict next item: score(item_j) = h_t · e_j (dot product with all item embeddings)
    ↓
Top-K items with highest scores = recommendation
```

**BERT4Rec vs. SASRec:**

| Aspect | SASRec | BERT4Rec |
|--------|--------|----------|
| Attention direction | Left-to-right (causal) | Bidirectional |
| Training objective | Predict next item | Predict masked items (random 15%) |
| Inference | Last position predicts next | Mask last position, predict it |
| Captures | Sequential dependency | Both past and future context |
| Similar to NLP | GPT | BERT |

**Comparison with non-sequential:**

| Aspect | Static CF (MF) | Sequential (SASRec) |
|--------|---------------|---------------------|
| Models order | No | Yes |
| Captures trends | No | Yes (interests evolve) |
| Session-aware | No | Yes (recent items weigh more) |
| Short-term intent | No | Yes (what user is currently looking for) |
| Data needed | User-item matrix | Ordered interaction sequences |
| Cold start (new user) | Bad | Can work with just session |

**Layman Example:**
Shopping recommendation:
- **Static (MF):** "Based on everything you've ever bought (unordered bag), here are recommendations." Ignores that you bought a camera LAST WEEK and might want accessories NOW.
- **Sequential (SASRec):** "You just browsed cameras → lenses → tripods. You're clearly camera shopping. Here are camera bags and memory cards." Captures the current shopping journey.
- **BERT4Rec:** "In the sequence [shoes, _, shirt, tie], what fits in the blank? Probably pants." Uses context from BOTH directions.

**Follow-up Questions:**

**Q: What is the difference between session-based and sequence-based recommendation?**
A: Session-based: only uses current session interactions (no long-term user history). User identity may be unknown (anonymous browsing). Models: GRU4Rec, SR-GNN. Sequence-based: uses full user history (all past sessions). User is identified. Models: SASRec, BERT4Rec. Hybrid: long-term preferences + current session (most practical). Most production systems maintain both a long-term user profile and capture current session intent.

**Q: How do you handle variable-length sequences?**
A: (1) Truncate to last N interactions (e.g., N=50-200). Recent items are most predictive anyway. (2) Pad shorter sequences with a special token. (3) Attention mask (ignore padding positions). (4) For very long histories: summarize old history into a dense vector, keep recent items as sequence.

**Q: What are the limitations of sequential models?**
A: (1) Assume interactions are ordered meaningfully (browsing ≠ purchasing intent), (2) Can't model simultaneous multi-interest users well (browsing phones AND books at same time), (3) Struggle with long-term preference vs. short-term intent separation, (4) Need sufficient sequence length per user. Solutions: multi-interest models (ComiRec), interest disentanglement, separate long/short-term encoders.

**Q: How does GRU4Rec handle sessions without user IDs?**
A: Process session items sequentially through GRU. Each item's embedding is input, GRU hidden state accumulates session context. After processing items [A, B, C], the hidden state h_3 represents session intent → predict next item. No persistent user ID needed — just in-session signal. At start of new session, hidden state resets. Can serve anonymous users effectively.

---

## 7. Graph Neural Networks for Recommendations

**Answer:**
GNN-based recommendations model user-item interactions as a bipartite graph, propagating information through the graph structure to learn enhanced user and item embeddings that capture high-order connectivity.

**Graph Construction:**
```
Bipartite graph:
  Users = one set of nodes
  Items = another set of nodes
  Edges = interactions (click, purchase, rating)

Information propagation:
  User embedding absorbs info from interacted items
  Item embedding absorbs info from users who interacted
  Multi-hop: user → item → other users → their items (collaborative signal)
```

**Key Models:**

| Model | Architecture | Key Innovation | Year |
|-------|-------------|----------------|------|
| GC-MC | Graph convolution on interaction graph | First GNN for CF | 2018 |
| PinSage | Random walk + GraphSAGE on item graph | Industrial-scale (Pinterest) | 2018 |
| NGCF | Embedding propagation on user-item graph | High-order connectivity for CF | 2019 |
| LightGCN | Simplified GNN (no feature transform, no activation) | Simpler = better | 2020 |
| SR-GNN | GNN on session graph | Session-based with graph structure | 2019 |
| UltraGCN | Approximate infinite-layer GCN (skip propagation) | Efficiency | 2021 |

**LightGCN (State-of-the-art simplicity):**
```
Simplification insight: For collaborative filtering on user-item graphs,
feature transformation and non-linear activation HURT performance.

Layer propagation:
  e_u^(k+1) = Σ_{i∈N(u)} (1/√|N(u)|·√|N(i)|) · e_i^(k)
  e_i^(k+1) = Σ_{u∈N(i)} (1/√|N(i)|·√|N(u)|) · e_u^(k)

Final embedding = weighted sum of all layers:
  e_u = Σ_k α_k · e_u^(k)    (α_k = 1/(K+1) for uniform)

That's it. No weight matrices, no activation functions.
Just normalized mean aggregation across layers.
```

**PinSage (Pinterest, production-scale):**
```
Challenge: Item graph with 3 billion nodes, 18 billion edges
Solution:
1. Random walk from each node → sample neighborhood (not full graph)
2. Aggregate neighbor features using GraphSAGE (importance sampling)
3. Batch training on sampled subgraphs
4. Produce item embeddings for candidate generation
5. MapReduce-style distributed computation

Used for: "Related Pins" recommendation at Pinterest (billions of items)
```

**Comparison:**

| Aspect | MF | NCF | LightGCN | GNN + features |
|--------|-----|-----|----------|----------------|
| Captures high-order | No (only direct) | No | Yes (multi-hop) | Yes |
| Uses graph structure | No | No | Yes | Yes |
| Side features | Difficult | Possible | Not directly | Yes |
| Scalability | Best | Good | Good | Challenging |
| Accuracy (CF only) | Baseline | ~ MF | > MF | ≥ LightGCN |

**Layman Example:**
Social network-based recommendations:
- **MF:** "You liked Items A, B, C. Other items similar in latent space: Item D."
- **LightGCN (1 hop):** "You liked Items A, B, C. Users who liked A also liked D. Users who liked B also liked E."
- **LightGCN (2 hops):** "Users who liked A also liked D. Users who liked D also liked F. F might interest you too." (The friend-of-a-friend idea applied to items)
- **PinSage:** "This Pin (image of modern kitchen) is connected via saves/clicks to similar Pins. Walk through the graph → find related Pins for 'Similar to this Pin' feature."

**Follow-up Questions:**

**Q: Why does removing non-linearity help in LightGCN?**
A: In collaborative filtering, the only input features are learnable embeddings (no raw features to transform). Applying weight matrices and non-linear activations to already-learnable embeddings adds parameters without benefit — the embeddings themselves can learn any needed transformation. The GNN's value is purely in the aggregation structure (neighborhood propagation), not in feature transformation.

**Q: How does PinSage scale to billions of nodes?**
A: (1) Random walk sampling (don't process full neighborhood — sample representative subsets). (2) Importance-based pooling (weight neighbors by visit frequency in random walks). (3) Minibatch training on subgraphs (not full graph). (4) MapReduce for distributed embedding computation. (5) Pre-compute and cache embeddings (offline graph propagation). This makes GNNs practical at web scale.

**Q: What is the over-smoothing problem in GNNs?**
A: With too many GNN layers, all node embeddings converge to the same value (indistinguishable). Each layer averages with neighbors → after K layers, nodes within K-hop neighborhood become similar → eventually all nodes look the same. Solution: LightGCN uses 2-3 layers only + layer combination. Deeper GNNs need: residual connections, jumping knowledge (concatenate all layers), or normalization techniques.

---

## 8. Knowledge Graph-Enhanced Recommendations

**Answer:**
Knowledge Graphs (KG) provide structured entity relationships that enrich item representations and enable explainable recommendations. They connect items to attributes, categories, and other items through typed relations.

**Knowledge Graph Structure:**
```
(The Dark Knight, directed_by, Christopher Nolan)
(Christopher Nolan, directed, Inception)
(The Dark Knight, genre, Action)
(The Dark Knight, starring, Heath Ledger)
(Heath Ledger, acted_in, Brokeback Mountain)

Path-based reasoning:
User liked "The Dark Knight" 
→ directed_by → Christopher Nolan → directed → "Inception"
→ Recommendation: Inception (because same director)
```

**Approaches:**

| Approach | Method | Models |
|----------|--------|--------|
| Embedding-based | Learn KG entity/relation embeddings, integrate with CF | CKE, KGAT, KGIN |
| Path-based | Find/learn meaningful paths user→...→item | RippleNet, KPRN |
| Propagation-based | GNN over KG + interaction graph | KGCN, KGAT |
| Unified | Joint KG + CF representation learning | KGAT, CKAN |

**KGAT (Knowledge Graph Attention Network):**
```
1. Build combined graph: user-item interactions + item-entity KG connections
2. GNN with attention over edges:
   - User → items they interacted with (CF signal)
   - Item → KG entities (attributes, relations)
   - Attention weights learned per relation type
3. Multi-hop propagation:
   - User → item → director → other movies by director
   - Captures collaborative + knowledge signals jointly
4. Output: enriched user/item embeddings for scoring
```

**Benefits of KG integration:**

| Benefit | How KG helps |
|---------|-------------|
| Cold start | New items have KG connections (genre, director) even with no interactions |
| Explainability | "Recommended because same director" (path-based explanation) |
| Diversity | KG paths reach diverse but related items |
| Cross-domain | KG connects music artists to movies they acted in |
| Data sparsity | Additional relational signal supplements sparse interactions |

**Follow-up Questions:**

**Q: How do you construct a knowledge graph for recommendations?**
A: Sources: (1) Structured databases (product catalogs with attributes), (2) Wikidata/DBpedia for general entities, (3) Domain-specific KGs (IMDB for movies, MusicBrainz for music), (4) Extracted from text (NER + relation extraction on product descriptions). Link items to KG entities via entity linking. Common relations: category, brand, creator, similar_to, price_range, ingredient, etc.

**Q: When does KG actually help vs. add complexity?**
A: Helps when: (1) Data is sparse (few interactions per user/item), (2) Items have rich structured metadata, (3) Cold start is common (new items), (4) Explainability is required, (5) Cross-domain recommendations needed. Doesn't help when: interactions are abundant (CF signal is strong enough), KG quality is poor, or when user behavior is the dominant signal.

**Q: What is RippleNet and how does it work?**
A: RippleNet propagates user preference along KG: (1) Start from user's historical items (seeds). (2) Expand K hops in KG (1-hop neighbors, 2-hop neighbors...). (3) At each hop, compute attention-weighted aggregation (which entities are relevant to user?). (4) User representation = combination of all hop representations. Like "ripples" spreading from user's known preferences through the knowledge graph.

---

## 9. Multi-Task Learning in Recommendations

**Answer:**
Multi-task learning (MTL) trains a single model to predict multiple objectives simultaneously (click, add-to-cart, purchase, watch time). This captures relationships between tasks and provides a complete user preference picture.

**Why Multi-Task in Recommendations:**
```
User sees item → May click (CTR task)
             → May add to cart (conversion task)
             → May purchase (revenue task)
             → May watch for long time (engagement task)
             → May rate positively (satisfaction task)

These tasks are correlated but different.
Optimizing CTR alone → clickbait (high click, low satisfaction)
Need to balance multiple objectives.
```

**Architectures:**

| Model | Architecture | Key Innovation | Year |
|-------|-------------|----------------|------|
| Shared-Bottom | Shared layers → task-specific heads | Simplest MTL | — |
| MMoE | Multiple expert networks + task-specific gating | Experts specialize, gates select | 2018 |
| PLE (Progressive Layered Extraction) | Task-specific + shared experts + progressive | Reduces negative transfer | 2020 |
| ESMM | Entire Space Multi-Task | Model P(click)×P(buy|click) = P(buy) | 2018 |

**MMoE (Multi-gate Mixture of Experts):**
```
Input features
    ↓
[Expert 1] [Expert 2] [Expert 3] ... [Expert N]   (N shared expert networks)
    ↓           ↓          ↓              ↓
    
Task A gating: g_A = softmax(W_A · input)
Task A output = Σ g_A_i × Expert_i(input) → Task A head → prediction_A

Task B gating: g_B = softmax(W_B · input)
Task B output = Σ g_B_i × Expert_i(input) → Task B head → prediction_B

Key: Each task learns WHICH experts to use (different gates, same experts)
```

**ESMM (Entire Space Multi-Task Model):**
```
Problem: Only clicked items have purchase labels → selection bias
         P(purchase) trained only on clicked subset ≠ P(purchase | impression)

Solution: Decompose P(purchase|impression) = P(click|impression) × P(purchase|click)
         Train CTR model on all impressions
         Train CVR model on clicked items
         Combined P(purchase) = CTR × CVR (over entire impression space)

This eliminates selection bias in conversion modeling.
```

**Comparison:**

| Architecture | Task conflict handling | Complexity | Best for |
|--------------|----------------------|-----------|----------|
| Shared-Bottom | Poor (all tasks share everything) | Simple | Highly related tasks |
| MMoE | Good (gate selects relevant experts) | Medium | Multiple related tasks |
| PLE | Best (explicit shared + private experts) | High | Tasks with conflicts |
| Separate models | Perfect (no sharing) | Highest infrastructure cost | Unrelated tasks |

**Follow-up Questions:**

**Q: What is negative transfer and how do you detect/prevent it?**
A: Negative transfer: adding a task HURTS other tasks' performance (competing gradients, conflicting features). Detection: compare single-task performance vs. MTL performance — if MTL is worse, negative transfer exists. Prevention: (1) MMoE/PLE architecture (tasks can select different experts), (2) Gradient manipulation (PCGrad — project conflicting gradients), (3) Task weighting (reduce weight of conflicting task), (4) Separate models for truly conflicting tasks.

**Q: How do you weight multiple task losses?**
A: Loss = w₁·L_click + w₂·L_purchase + w₃·L_engagement. Strategies: (1) Manual tuning (business importance), (2) Uncertainty weighting (higher uncertainty → lower weight), (3) GradNorm (balance gradient norms across tasks), (4) Dynamic weight adjustment (increase weight for under-performing tasks), (5) Pareto-optimal solutions (multi-objective optimization). In practice: start with business importance weights, adjust based on dev set.

**Q: Why does ESMM solve sample selection bias?**
A: Training CVR only on clicked items means: model sees P(purchase|click, features) but at serving you need P(purchase|impression, features). Clicked items are a biased subset (already filtered by user interest). ESMM trains: P(purchase) = P(click) × P(purchase|click). Since P(click) is trained on ALL impressions (no bias), and the product is over all impressions, the final prediction is unbiased over the full impression space.

---

## 10. Feature Interactions and Embeddings

**Answer:**
Recommendation models process heterogeneous features: sparse categorical (user_id, item_id, category), dense numerical (price, rating), sequential (click history), and multi-valued (tags, genres). Learning meaningful feature representations and interactions is critical.

**Feature Types in Recommendations:**

| Type | Examples | Representation |
|------|----------|---------------|
| Sparse categorical (high-cardinality) | user_id (millions), item_id (millions) | Learned embedding (d=64-256) |
| Sparse categorical (low-cardinality) | gender, country, device | Embedding or one-hot |
| Dense numerical | price, age, CTR history | Normalize, discretize, or direct |
| Sequential | Click history, purchase history | Sequence encoder (avg, attention, RNN) |
| Multi-valued | Genres [action, comedy], tags | Multi-hot → embedding average/attention |
| Text | Item title, description | Pre-trained NLP encoder (BERT) |
| Image | Product photo, thumbnail | Pre-trained CNN encoder (ResNet) |
| Temporal | Time of day, days since last visit | Periodic encoding, embedding |

**Feature Interaction Methods:**

| Method | Interaction type | Mechanism |
|--------|-----------------|-----------|
| FM (Factorization Machines) | All 2nd-order (e_i · e_j) | Dot product of embeddings |
| FFM (Field-aware FM) | Field-specific 2nd-order | Different embedding per field pair |
| Cross Network (DCN) | Bounded polynomial | Explicit cross layers |
| MLP | Implicit all orders | Hidden layers learn interactions |
| Self-attention (AutoInt) | Feature-to-feature attention | Transformer over feature embeddings |
| Bilinear | Learned interaction matrix | e_i^T · W · e_j |

**Embedding Table Design:**

```
Embedding table for user_id (1M users × 64 dimensions):
  - Memory: 1M × 64 × 4 bytes = 256 MB per table
  - With 100 sparse features: 100 × 256 MB = 25.6 GB
  
Challenges at scale:
  - Memory: Billions of entities → terabytes of embeddings
  - Solutions: (1) Hash embeddings (reduce vocabulary), 
               (2) Mixed-dimension (popular items get larger embeddings),
               (3) Compositional embeddings (compose from shared sub-embeddings),
               (4) Distributed storage across machines
```

**Layman Example:**
Think of feature embeddings as creating a universal "language" for recommendations:
- User 12345 → [0.3, -0.1, 0.8, ...] (64 numbers representing their taste)
- Item "iPhone 15" → [0.2, 0.5, 0.1, ...] (64 numbers representing the item)
- Category "Electronics" → [0.4, 0.3, -0.2, ...] (shared context)
- Hour "9 PM" → [0.1, 0.6, 0.3, ...] (temporal pattern)

Feature interactions: "THIS user" × "THIS item" × "THIS time" → how these specific combinations interact to predict a click. FM captures: "users like this tend to click electronics in the evening."

**Follow-up Questions:**

**Q: Why use embedding tables instead of one-hot encoding for categorical features?**
A: One-hot for user_id with 1M users = 1M-dimensional sparse vector. (1) Massive input dimension. (2) No similarity structure (User A and similar User B are equally distant). (3) Can't handle unseen categories. Embeddings: (1) Dense low-dimensional (64-256 dims). (2) Similar entities have similar embeddings (learned from data). (3) Composable (operations on embeddings are meaningful). Memory: 1M × 64 floats = 256 MB vs. one-hot being impractical.

**Q: How do you handle cold-start users/items with no learned embedding?**
A: (1) Default embedding (average of all embeddings), (2) Feature-based construction (compose embedding from known features: age + gender + device → user embedding even without history), (3) Meta-learning (learn to quickly produce embedding from few interactions), (4) Content-based embedding (use item image/text features instead of learned ID embedding), (5) Side-information mapping network.

**Q: What is the difference between FM and DeepFM?**
A: FM captures ALL 2nd-order feature interactions through dot products of embeddings: Σᵢ Σⱼ (eᵢ · eⱼ)xᵢxⱼ. DeepFM adds an MLP branch that captures higher-order non-linear interactions from the same embeddings. FM = explicit 2nd order. MLP = implicit higher-order. Combined: both types of interactions without manual feature engineering.

---

## 11. Conversational and Interactive Recommendations

**Answer:**
Conversational recommender systems (CRS) engage users in multi-turn dialogue to elicit preferences, refine recommendations, and explain suggestions. They combine recommendation logic with natural language understanding and generation.

**Approaches:**

| Approach | Method | Example |
|----------|--------|---------|
| Attribute-based | Ask about item attributes iteratively | "Do you prefer action or comedy?" |
| Critique-based | User critiques current suggestions | "Like this but cheaper" |
| QA-based | Answer user questions about items | "Does this hotel have a pool?" |
| LLM-based | Free-form dialogue + recommendation | ChatGPT-style with item DB |
| Bandit-based | Adaptively choose questions | Maximize info gain per question |

**LLM-based Conversational Recommendation:**
```
System: You are a movie recommendation assistant. Use the following user profile and movie database.

User history: [Inception, Interstellar, The Matrix]
Available movies: [database]

User: "I want something mind-bending but not too long"
Assistant: "Based on your love of sci-fi thrillers, I'd recommend:
1. Arrival (116 min) - linguistics meets alien contact
2. Ex Machina (108 min) - AI consciousness thriller
Would you like something more action-oriented or cerebral?"
```

**Comparison:**

| Aspect | Static recommendation | Conversational |
|--------|----------------------|----------------|
| User interaction | One-shot (show items) | Multi-turn dialogue |
| Preference elicitation | Implicit (from history) | Explicit (ask + implicit) |
| Cold start | Problematic | Solved by asking |
| Explanation | Post-hoc | Natural in dialogue |
| User control | Low | High (refine, critique) |
| Complexity | Lower | Much higher (NLU + NLG + rec) |

**Follow-up Questions:**

**Q: How do LLMs change conversational recommendations?**
A: LLMs enable: (1) Natural dialogue without template responses, (2) Complex preference understanding ("something like The Shawshank Redemption but set in space"), (3) Rich explanations with reasoning, (4) Flexible critiquing ("more of X, less of Y"). Challenges: (1) LLMs hallucinate items that don't exist, (2) Can't access real-time inventory, (3) May not follow business constraints. Solution: RAG over item database + structured recommendation logic with LLM as the dialogue interface.

**Q: How do you decide what to ask the user?**
A: Goal: minimize turns to reach a good recommendation. Methods: (1) Information gain (ask about attributes that most reduce uncertainty), (2) Expected improvement in recommendation quality per question, (3) Bandit framework (balance exploring user preferences vs. exploiting known preferences), (4) Reinforcement learning (learn optimal questioning policy from dialogue data).

---

## 12. Context-Aware Recommendations

**Answer:**
Context-aware recommendations incorporate situational factors beyond user and item: time, location, device, social context, weather, mood. The same user may want different items in different contexts.

**Context Dimensions:**

| Context | Examples | Impact |
|---------|----------|--------|
| Temporal | Time of day, day of week, season | Breakfast recommendations at 8 AM vs. dinner at 7 PM |
| Location | GPS, country, urban/rural | Restaurant nearby vs. general product recommendation |
| Device | Mobile, desktop, smart speaker | Content length, format preferences |
| Social | Alone, with friends, family | Different movie preferences |
| Activity | Working, commuting, exercising | Music type, content format |
| Weather | Rainy, sunny, cold | Food delivery vs. outdoor activities |
| Purchase stage | Browsing, comparing, buying | Different information needs |

**Integration Methods:**

| Method | How context is used | Example model |
|--------|--------------------|--------------| 
| Pre-filtering | Filter items by context before scoring | "Show only breakfast items before 11 AM" |
| Post-filtering | Score all items, filter by context after | "Remove irrelevant items for current context" |
| Contextual modeling | Context as input feature to model | Context embeddings in DeepFM, TFT |
| Context-aware attention | Attention weights modulated by context | "Weight recent history more on Monday mornings" |

**Contextual Bandit for Recommendations:**
```
At each interaction:
1. Observe context c_t (user features + situational context)
2. Select action a_t (which item to recommend)
3. Observe reward r_t (click/no-click)
4. Update policy: learn which items work in which contexts

Exploration: sometimes recommend uncertain items to learn
Exploitation: recommend items with highest predicted reward

Algorithms: LinUCB, Thompson Sampling, Neural Bandits
```

**Follow-up Questions:**

**Q: How do you model time in recommendations?**
A: Multiple time aspects: (1) Time-of-day: periodic encoding (sin/cos of hour/24) or embedding per hour bucket. (2) Recency: decay function on historical interactions (recent matters more). (3) Temporal dynamics: user interests evolve over months/years. (4) Session time: duration within current session affects intent. (5) Periodicity: weekly patterns (weekend vs. weekday).

**Q: What is the difference between contextual bandits and full RL for recommendations?**
A: Contextual bandit: single-step — observe context, make one recommendation, observe reward. No sequential dependencies between actions. Simpler, well-understood. Full RL: multi-step — today's recommendation affects tomorrow's state (user engagement, preference evolution). Models long-term value. Much harder to train (delayed reward, large state/action spaces). In practice: bandits for most scenarios; RL only when long-term engagement optimization is critical.

---

## 13. Cross-Domain and Transfer Learning for Recommendations

**Answer:**
Cross-domain recommendation leverages knowledge from a data-rich source domain to improve recommendations in a data-sparse target domain. Transfer learning adapts pre-trained recommendation models to new domains/tasks.

**Scenarios:**

| Scenario | Example | Method |
|----------|---------|--------|
| User overlap | Same users on Amazon (books + electronics) | Shared user embeddings |
| Item overlap | Same movies on Netflix (rating) + YouTube (watch time) | Shared item embeddings |
| No overlap | Transfer temporal patterns from retail to travel | Feature/pattern transfer |
| Cold start | New e-commerce site leveraging larger site's model | Pre-trained embeddings, fine-tune |

**Methods:**

| Method | How it transfers | When to use |
|--------|-----------------|-------------|
| Shared embeddings | Use user/item embeddings trained in source | User/item overlap between domains |
| Feature mapping | Map source features to target space | Partially overlapping features |
| Knowledge distillation | Large source model → small target model | Resource-constrained target |
| Pre-trained representations | BERT/CLIP embeddings as item features | Any domain (text/image items) |
| Meta-learning | Learn to quickly adapt from few interactions | Frequent cold start |

**Pre-trained Models for Recommendations:**

| Model | Input | Provides | Use |
|-------|-------|----------|-----|
| BERT/GPT | Item text (title, description) | Semantic item embeddings | Cold start items, content-based |
| CLIP | Item image + text | Multi-modal item embeddings | Visual recommendations |
| UniSRec | Universal sequence representation | Pre-trained sequential rec | Cross-domain sequential |
| P5 | Text prompts for any rec task | Unified text-to-text rec | Multi-task recommendations |

**Layman Example:**
A music recommendation system for a new streaming service:
- **Without transfer:** Start from scratch — need millions of user interactions to learn good recommendations. Cold start for months.
- **With transfer (user overlap):** Some users also use Spotify → their Spotify taste profile (embedding) initializes their profile on new service. Instant decent recommendations.
- **With transfer (pre-trained):** Even without user overlap, use pre-trained music embeddings (from genre, audio features, artist knowledge). Songs similar in feature space get similar embeddings. No user interactions needed for item representation.

**Follow-up Questions:**

**Q: How do pre-trained language models help recommendations?**
A: (1) Encode item titles/descriptions into semantic embeddings (cold start items have good representations from text alone). (2) Enable zero-shot recommendations ("find items similar to this description"). (3) Transfer knowledge from pre-training corpus (world knowledge about items). (4) P5-style: frame all rec tasks as text ("Recommend items for user who liked [A, B, C]" → generate item names).

**Q: What are the challenges of cross-domain transfer?**
A: (1) Negative transfer (source domain patterns that don't apply to target), (2) Privacy (sharing user data across platforms), (3) Feature misalignment (different feature spaces), (4) Distribution shift (user behavior differs across domains), (5) Scale mismatch (large source, tiny target). Solutions: selective transfer, domain adaptation, privacy-preserving techniques (federated learning).

---

## 14. Reinforcement Learning for Recommendations

**Answer:**
RL-based recommendations treat the recommendation problem as a sequential decision-making process, optimizing long-term user engagement/satisfaction rather than immediate click probability.

**Why RL for Recommendations:**
```
Standard (myopic): Maximize P(click on this item NOW)
Problem: Clickbait maximizes immediate clicks but reduces long-term engagement

RL (long-term): Maximize cumulative reward over user's lifetime
Considers: "If I recommend this exploratory item now, will the user be more engaged next week?"
```

**Formulation as MDP:**

| Component | Recommendation MDP |
|-----------|-------------------|
| State | User history, current context, candidate pool |
| Action | Which item(s) to recommend |
| Reward | Click, purchase, watch time, satisfaction |
| Transition | User state changes based on action (new history) |
| Policy | π(state) → action (recommendation strategy) |
| Objective | Maximize Σ γ^t · r_t (discounted cumulative reward) |

**Approaches:**

| Method | Type | Key Idea | Challenge |
|--------|------|----------|-----------|
| DQN-based | Value-based | Learn Q(state, item) | Discrete action space (millions of items) |
| Policy Gradient | Policy-based | Directly learn recommendation policy | High variance, slow |
| Actor-Critic | Hybrid | Actor proposes, critic evaluates | Complex training |
| Offline RL | Learn from logged data | No live interaction needed | Distribution shift (off-policy) |
| Slate optimization | Multi-item | Optimize entire recommended list | Combinatorial action space |
| Constrained RL | With business rules | Satisfy constraints while maximizing reward | Constraint specification |

**Offline RL for Recommendations:**
```
Problem: Can't do online exploration (bad recommendations = lost users)
Solution: Learn from historical logged data (what was recommended + what happened)

Challenge: Off-policy evaluation — logged data was collected under OLD policy,
but we want to evaluate NEW policy without deploying it.

Methods:
- IPS (Inverse Propensity Scoring): reweight logged rewards by likelihood ratio
- Doubly Robust: combine model prediction + IPS (lower variance)
- Conservative Q-Learning (CQL): penalize Q-values for unseen state-action pairs
- Batch RL: learn from fixed dataset without environment interaction
```

**Comparison: Supervised Learning vs. RL for Recommendations:**

| Aspect | Supervised (standard) | Reinforcement Learning |
|--------|----------------------|------------------------|
| Objective | Predict immediate outcome (click) | Maximize long-term value |
| Training signal | Labels (click/no-click) | Delayed rewards |
| Exploration | None (exploit learned patterns) | Active exploration |
| User model | Static | Dynamic (state evolves) |
| List composition | Independent item scoring | Joint optimization (slate) |
| Complexity | Lower | Much higher |
| When to use | Most scenarios | When long-term engagement matters |

**Follow-up Questions:**

**Q: Why is offline RL preferred over online RL for recommendations?**
A: Online RL requires exploration (showing potentially bad recommendations to learn). This hurts user experience and metrics in the short term. Offline RL learns from historical data without exploration risk. Challenges: (1) Can't try truly new strategies (bounded by logged policy), (2) Off-policy evaluation is noisy, (3) Extrapolation error for unseen state-actions. In practice: offline RL for initial policy, cautious online exploration (bandit-like) for fine-tuning.

**Q: How does slate/list optimization differ from item-wise scoring?**
A: Item-wise: score each item independently, sort by score, show top-K. Problem: all items might be similar (no diversity). Slate RL: optimize the entire list jointly — considers diversity, complementarity, position effects. Example: [3 action movies] might individually score highest, but [2 action + 1 comedy] might create a better overall experience (higher long-term engagement).

**Q: What is the reward shaping problem?**
A: Defining the reward function is critical and non-trivial. (1) Click alone → clickbait. (2) Purchase alone → expensive items recommended. (3) Watch time alone → addictive content. (4) Composite reward: w₁·click + w₂·purchase + w₃·watch_time + w₄·return_rate. The weights encode business values. Wrong rewards → model optimizes for the wrong thing (Goodhart's Law).

---

## 15. Bias and Fairness in Recommendations

**Answer:**
Recommendation systems can perpetuate and amplify biases, creating unfair outcomes for users, items/creators, and society. Addressing bias is both an ethical obligation and practical necessity (biased systems lose user trust).

**Types of Bias:**

| Bias | Description | Example |
|------|-------------|---------|
| Popularity bias | System over-recommends popular items | Head items get 90% of exposure; long-tail items starve |
| Position bias | Users click top positions regardless of relevance | Item at position 1 gets 10× clicks of position 5 |
| Exposure bias | Model only learns from past recommendations (not all items) | Items never shown → never learned → never shown (feedback loop) |
| Selection bias | Only observed items with interactions → missing data isn't random | Sci-fi fans don't rate romance → system thinks they hate romance |
| Conformity bias | Users rate similarly to peers regardless of true preference | Herding behavior inflates popular items' ratings |
| Filter bubble | Recommendations narrow user exposure over time | User only sees content matching existing beliefs |

**Fairness Dimensions:**

| Stakeholder | Fairness concern | Metric |
|-------------|------------------|--------|
| Users (consumer) | Similar users get similar quality recommendations | User-group parity in NDCG |
| Items (provider) | Fair exposure across items/creators | Exposure proportional to relevance |
| Groups | No discrimination by demographic | Equal recommendation quality across groups |

**Debiasing Methods:**

| Method | What it addresses | How |
|--------|-------------------|-----|
| IPS (Inverse Propensity Scoring) | Selection/position bias | Reweight samples by inverse exposure probability |
| Causal inference | Confounding in observed data | Counterfactual reasoning |
| Exposure-aware training | Popularity bias | Penalize over-exposure of popular items |
| Calibration | Filter bubble | Ensure rec distribution matches user interest distribution |
| Fairness constraints | Group fairness | Add constraints during optimization |
| Explore/exploit | Feedback loops | Actively explore under-exposed items |

**Follow-up Questions:**

**Q: What is the popularity bias feedback loop?**
A: Popular items get recommended more → get more clicks → look even more popular → get recommended even more. Meanwhile: niche items never get shown → get no interactions → model learns they're "bad" → shows them even less. This rich-get-richer cycle concentrates all traffic on a few items. Solutions: (1) Exploration (show some diverse items), (2) Debiased training (IPS), (3) Business rules (diversity quotas), (4) Fairness-aware ranking.

**Q: How does position bias affect model training?**
A: Users are more likely to click higher-positioned items regardless of relevance. If you train on raw click data: model learns "items shown at position 1 are clicked" rather than "relevant items are clicked." Solutions: (1) Position bias models (predict P(click|relevance, position) and factor out position), (2) IPS weighting by examination probability, (3) Unbiased learning-to-rank methods, (4) Randomized controlled experiments for clean signal.

**Q: What is calibration in recommendations?**
A: Calibration ensures the distribution of recommended items matches the user's interest distribution. Example: if user watches 60% comedy, 30% drama, 10% action — their recommendations should roughly match this ratio, not be 100% comedy (even if comedy predicts highest). Prevents over-specialization and maintains discovery. Metric: KL divergence between interest distribution and recommendation distribution.

---

## 16. Evaluation Beyond Accuracy

**Answer:**
Recommendation quality isn't just about predicting clicks/ratings. Production systems must balance accuracy with diversity, novelty, coverage, fairness, and user satisfaction.

**Beyond-Accuracy Metrics:**

| Metric | What it measures | Why it matters |
|--------|-----------------|----------------|
| Diversity (intra-list) | How different items in a list are from each other | Avoid showing 10 nearly identical items |
| Coverage (catalog) | % of items ever recommended across all users | Avoid recommending only top 1% of catalog |
| Novelty | How surprising/unexpected recommendations are | Discovery, not just obvious choices |
| Serendipity | Relevant AND unexpected (novelty + accuracy) | Delightful discoveries |
| Freshness | Recency of recommended content | News, trending items |
| Explainability | Can the system explain why? | User trust and transparency |
| User satisfaction | Long-term engagement, retention, NPS | Ultimate business metric |

**Diversity-Accuracy Tradeoff:**

```
Pure accuracy optimization → monotonous recommendations (all similar to user's top interest)
Pure diversity optimization → irrelevant recommendations (random diverse items)
Optimal → diverse AND relevant (different items that all match user preferences)

Methods to increase diversity:
1. MMR (Maximal Marginal Relevance): greedily select items that are 
   relevant AND dissimilar to already-selected items
2. DPP (Determinantal Point Process): sample diverse subsets that cover 
   the space while maintaining quality
3. Post-processing: re-rank to satisfy diversity constraints
4. Multi-objective: directly optimize relevance + diversity jointly
```

**MMR (Maximal Marginal Relevance):**
$$\text{MMR} = \arg\max_{i \in R \setminus S} [\lambda \cdot \text{Rel}(i) - (1-\lambda) \cdot \max_{j \in S} \text{Sim}(i,j)]$$

Select items that are relevant (Rel) but dissimilar to already-selected items (S).

**Online vs. Offline Metrics Gap:**

| Offline metric | Correlates with online? | Why gap exists |
|---------------|------------------------|----------------|
| NDCG@10 | Moderate | Offline ignores presentation, position, context |
| HR@50 | Low-Moderate | User behavior differs from historical patterns |
| Diversity | Moderate | Users may or may not value diversity |
| Novelty | Low | Novel ≠ interesting (may be irrelevant) |
| AUC | Low | Ranking matters more than classification |

**Follow-up Questions:**

**Q: How do you measure if users are satisfied beyond clicks?**
A: (1) Retention (do users return?), (2) Session length (engaged or frustrated?), (3) Dwell time (actually consumed content?), (4) Explicit feedback (ratings, likes, saves), (5) Skip rate (quickly dismissed?), (6) Long-term metrics (monthly active users, subscription churn), (7) A/B test core experience metrics. Click ≠ satisfaction — track downstream engagement.

**Q: What is the explore-exploit approach to improving diversity?**
A: Allocate recommendation slots: 80% exploitation (high-confidence relevant items) + 20% exploration (uncertain but potentially interesting items). If explored items get engagement, they enter the "exploitation" pool. This naturally increases coverage and diversity while bounding risk. Thompson Sampling or UCB algorithms make this adaptive.

---

## 17. Real-Time and Streaming Recommendations

**Answer:**
Real-time recommendations update based on a user's most recent behavior within the current session. Streaming systems process events as they arrive, updating models and serving fresh recommendations without batch retraining.

**Architecture:**

```
User action (click, view, purchase)
    ↓
Event stream (Kafka/Kinesis)
    ↓
┌─────────────────────────────────────────┐
│ Real-time feature computation            │
│ (user recent clicks, session features)   │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ Online model update (incremental)        │
│ OR real-time feature injection to model   │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ Serving layer (low-latency prediction)   │
└─────────────────────────────────────────┘
    ↓
Fresh recommendation
```

**Components:**

| Component | Batch (traditional) | Real-time/Streaming |
|-----------|--------------------|--------------------|
| Features | Computed daily/hourly in batch | Computed per event, windowed aggregations |
| Model update | Retrain daily/weekly | Incremental learning or frequent micro-batches |
| Item index | Rebuilt periodically | Updated as new items arrive |
| Serving | Pre-computed scores | On-demand scoring with latest features |
| User representation | Static (last batch update) | Dynamic (updated with each action) |

**Real-time Feature Examples:**

| Feature | Computation | Window |
|---------|-------------|--------|
| Items viewed in session | Append to list per event | Current session |
| Category distribution (session) | Count per category | Last 30 minutes |
| Time since last click | Current_time - last_event_time | Real-time |
| Session dwell time | Sum of page durations | Current session |
| Trending items (global) | Click velocity | Last 1 hour |
| User purchase intent score | ML model on recent actions | Last 5 actions |

**Follow-up Questions:**

**Q: How do you update user embeddings in real-time?**
A: (1) **Append + attention:** Keep recent item embeddings in a buffer, apply attention at inference (SASRec-like on recent items). (2) **Running average:** user_emb = α × user_emb + (1-α) × new_item_emb. (3) **Incremental model update:** Fine-tune embedding with each new interaction. (4) **Two-part representation:** static long-term embedding (batch) + dynamic short-term embedding (real-time). Most practical: combine batch user embedding with real-time session features at scoring time.

**Q: What is the latency budget for real-time recommendations?**
A: Typical budgets: (1) Feed/homepage: 100-200ms total (retrieval + ranking + rendering). (2) "Related items": 50-100ms. (3) Search results: 200-500ms. (4) Notifications/push: seconds to minutes (less latent). Within this budget: feature computation (10-20ms), model inference (10-50ms), ANN retrieval (1-5ms), post-processing (5-10ms). Caching and pre-computation are essential.

**Q: How do you handle the cold start for new items in real-time?**
A: New item appears → (1) Immediately: use content features (title, image, category) to create initial embedding. (2) After first interactions (minutes): update embedding based on who interacted. (3) After hours: enough signal for proper embedding. Pipeline: content-based embedding → hybrid (content + collaborative) → fully collaborative. Some systems pre-compute embeddings from content before item launch.

---

## 18. Multi-Modal Recommendations

**Answer:**
Multi-modal recommendations leverage diverse item representations (text, images, audio, video, structured data) to build richer models. Especially valuable for cold-start items and visual/creative domains (fashion, food, design).

**Modalities and Encoders:**

| Modality | Encoder | Output | Application domain |
|----------|---------|--------|-------------------|
| Text (title, description) | BERT, Sentence-BERT | Semantic embedding | E-commerce, news, books |
| Image (product photo) | ResNet, ViT, CLIP | Visual embedding | Fashion, home decor, food |
| Audio (music, podcasts) | Wav2Vec, audio spectrogram | Audio embedding | Music, podcast recommendation |
| Video (content) | Video Transformer, I3D | Visual+temporal embedding | YouTube, TikTok |
| Structured (attributes) | Embedding tables | Feature embedding | All domains |
| Graph (relationships) | GNN | Relational embedding | Social, knowledge-rich |
| User reviews | Sentiment + aspect extraction | Opinion embedding | Any user-review platform |

**Multi-Modal Fusion Strategies:**

| Strategy | Method | When to use |
|----------|--------|-------------|
| Early fusion | Concatenate all modality embeddings → joint model | Modalities are highly complementary |
| Late fusion | Separate models per modality → combine scores | Modalities are independent signals |
| Cross-modal attention | Attention between modalities | Complex inter-modal relationships |
| CLIP-style alignment | Contrastive learning across modalities | Image-text matching |
| Gated fusion | Learn which modalities matter per instance | Variable modality importance |

**Example: Fashion Recommendation:**
```
Item representation:
  - Image: ResNet/ViT encodes product photo → visual style
  - Text: BERT encodes product description → semantic content
  - Attributes: Embeddings for brand, color, material → structured info
  - User reviews: Sentiment toward fit, quality, style → community signal

Fusion:
  item_emb = Gate_img × img_emb + Gate_text × text_emb + Gate_attr × attr_emb
  
User side:
  - User's visual preference (average of liked item images)
  - User's text preference (average of liked item descriptions)
  - User's attribute preference (style profile)

Score = user_multi_modal_emb · item_multi_modal_emb
```

**Follow-up Questions:**

**Q: How does CLIP change recommendation systems?**
A: CLIP provides aligned image-text embeddings (same space for both modalities). For recommendations: (1) Cold start items can be represented by photo OR description (both map to same space). (2) Users can search with text ("red summer dress") and get visually matching items. (3) User's text preference and item's image live in same space → cross-modal matching. (4) Zero-shot item categorization/tagging.

**Q: When do visual features help most?**
A: (1) Fashion/clothing (visual similarity = style match), (2) Home decor/furniture (aesthetic preference), (3) Food/restaurants (appealing food photos drive engagement), (4) Art/design platforms, (5) When items are visually distinctive but hard to describe textually, (6) Cold start (image available before interaction data).

**Q: How do you handle missing modalities?**
A: Not all items have all modalities (some lack images, some lack descriptions). Solutions: (1) Modal dropout during training (randomly drop modalities → model learns to work with subsets), (2) Default/zero embedding for missing modalities, (3) Cross-modal generation (generate text description from image), (4) Gated fusion naturally down-weights missing modalities.

---

## 19. Scalability and System Design

**Answer:**
Production recommendation systems serve billions of requests daily with strict latency constraints. System design must balance model complexity with computational feasibility at scale.

**System Architecture (Industry Standard):**

```
User request
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Stage 1: RETRIEVAL (Candidate Generation)                     │
│ - Multiple retrieval sources (two-tower, popular, similar, etc.) │
│ - Each retrieves 100-500 candidates                            │
│ - Total: 1000-5000 candidates per request                     │
│ - Latency: <10ms (ANN search over pre-computed embeddings)    │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Stage 2: FILTERING                                            │
│ - Remove already-seen, out-of-stock, policy violations        │
│ - Lightweight business rules                                  │
│ - Latency: <5ms                                              │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Stage 3: RANKING (Scoring)                                    │
│ - Complex model scores each candidate (DCN, DeepFM, DIN)     │
│ - Rich features (cross-features, real-time features)          │
│ - Score: P(click), P(purchase), P(long_engagement)            │
│ - Latency: 10-50ms for scoring hundreds of items              │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Stage 4: RE-RANKING (Business Logic)                          │
│ - Diversity injection (MMR, DPP)                              │
│ - Business rules (sponsored items, freshness boost)           │
│ - Fairness constraints                                        │
│ - Position optimization                                       │
│ - Latency: <5ms                                              │
└─────────────────────────────────────────────────────────────┘
    ↓
Final recommendation list (10-50 items)
Total latency budget: 50-200ms
```

**Scale Challenges:**

| Challenge | Numbers | Solution |
|-----------|---------|----------|
| Item corpus | 100M-1B items | ANN index (FAISS, ScaNN), sharding |
| User base | 100M-1B users | Distributed embedding tables |
| QPS | 100K-1M requests/sec | Horizontal scaling, caching |
| Embedding tables | TB-scale | Model parallelism, distributed storage |
| Feature computation | 1000s of features | Feature store, pre-computation |
| Model serving | <50ms latency | Model optimization, batching, GPU/TPU |
| Training | TB of daily data | Distributed training, incremental updates |

**Companies' Architectures:**

| Company | Retrieval | Ranking | Scale |
|---------|-----------|---------|-------|
| YouTube | Two-tower DNN | Wide & Deep | Billions of videos |
| TikTok | Multi-stage retrieval | Multi-task DNN | Billions of videos, millions QPS |
| Netflix | Multiple retrieval models | Personalized ranking | 100M+ users |
| Amazon | Item-to-item CF + DNN | DIEN-style attention | 100M+ products |
| Spotify | ANN on audio/text embeddings | Multi-task, explore/exploit | 100M+ tracks |

**Follow-up Questions:**

**Q: Why use multiple retrieval sources instead of one?**
A: No single retrieval model captures all types of relevance: (1) Collaborative (two-tower): "users like you bought", (2) Content-based: similar items by features, (3) Sequential: "following your recent clicks", (4) Popular/trending: currently popular items, (5) Geographic: nearby items. Each source covers different user intents. Merging candidates from multiple sources increases recall — the ranking model then orders the combined set.

**Q: How do you serve embeddings for billions of items?**
A: (1) Shard embedding tables across machines (each machine holds a subset). (2) Cache frequently accessed embeddings (hot items) in memory. (3) Use product quantization (compress 256-dim float vector to 32 bytes). (4) Tiered storage: hot embeddings in GPU memory, warm in CPU memory, cold on SSD. (5) For ANN: FAISS IVF+PQ enables billion-scale search in milliseconds.

**Q: How do you handle the training-serving skew?**
A: Training uses batch features (computed on historical data). Serving uses real-time features (computed on latest events). If feature computation differs → model performs differently in production. Solutions: (1) Feature store with consistent computation logic (same code for batch + streaming), (2) Log serving-time features for training (train on EXACTLY what model sees in production), (3) Shadow testing (serve old model, log new model's predictions, compare offline).

---

## 20. Explainability in Recommendations

**Answer:**
Explainable recommendations tell users WHY an item was suggested, increasing trust, engagement, and user control. Explanations can be: collaborative ("Users like you liked this"), content-based ("Because you watched X"), or review-based ("People say this product is great for...").

**Explanation Types:**

| Type | Example | When effective |
|------|---------|----------------|
| User-based | "People with similar taste enjoyed this" | Social proof |
| Item-based | "Similar to [item you liked]" | Content exploration |
| Feature-based | "Because you like Action movies" | Attribute matching |
| Review-based | "Customers praise the battery life" | Purchase decisions |
| Social | "Your friend Alice liked this" | Social networks |
| Visual | "Visually similar to items you saved" | Fashion, design |
| Path-based | "You liked A → A is by director X → X also directed B" | Reasoning chain |

**Methods:**

| Method | Approach | Model type |
|--------|----------|------------|
| Attention weights | Show which historical items drove recommendation | DIN, SASRec |
| KG paths | Show knowledge graph path from user interest to item | KG-enhanced models |
| Counterfactual | "Without watching Movie X, this wouldn't be recommended" | Any model (post-hoc) |
| Template-based | Fill templates with extracted features/entities | Production systems |
| NLG (LLM) | Generate natural language explanations | Modern approach |
| SHAP/LIME | Feature importance for the specific prediction | Any ML model |

**LLM-Generated Explanations:**
```
Input: user_profile + recommended_item + model_features
Prompt: "Generate a 1-sentence explanation for why user would like this item. 
         User's top interests: [sci-fi, space, Christopher Nolan]. 
         Item: Interstellar. Key matching features: [space, Nolan, sci-fi]."
Output: "Recommended because you enjoy Christopher Nolan's sci-fi films, and 
         Interstellar is his acclaimed space epic."
```

**Follow-up Questions:**

**Q: Do explanations actually improve user engagement?**
A: Research shows: (1) Good explanations increase trust (+15-30% acceptance rate), (2) Increase click-through rate (user understands why → more willing to try), (3) Help users discover new preferences ("I didn't know I liked this genre"), (4) Enable better feedback (user can correct: "I don't actually like this because..."). But: bad/wrong explanations DECREASE trust more than no explanation.

**Q: What is the faithfulness problem in explanations?**
A: Explanations may not reflect the TRUE reason the model recommended an item. Post-hoc explanations (generated after prediction) may rationalize rather than explain. A model might recommend item X because of collaborative signal, but the explanation says "because you like action movies" (plausible but not the real reason). Faithful explanations require interpretable-by-design models or verified causal analysis.

---

## 21. Privacy-Preserving Recommendations

**Answer:**
Privacy-preserving recommendations protect user data while still delivering personalized experiences. Critical due to regulations (GDPR, CCPA) and user expectations.

**Approaches:**

| Method | Protection level | How it works | Trade-off |
|--------|-----------------|-------------|-----------|
| Differential Privacy (DP) | Mathematical guarantee | Add calibrated noise to data/gradients | Accuracy loss |
| Federated Learning | Data stays on device | Train locally, aggregate updates centrally | Communication cost |
| Secure Multi-Party Computation | Cryptographic | Multiple parties compute without revealing data | Computational overhead |
| On-device inference | No data leaves device | Run model on user's device | Model size constraint |
| Data minimization | Limit collection | Only collect what's needed | Less signal |
| Anonymization | Remove identifiers | k-anonymity, l-diversity | Utility loss |

**Federated Recommendation:**
```
Central server has: global model parameters
User device has: local interaction history (never leaves device)

Training round:
1. Server sends current model to devices
2. Each device trains locally on its own data
3. Devices send gradient updates (NOT raw data) to server
4. Server aggregates gradients → update global model
5. Repeat

Privacy: Raw interactions never leave user's device
Challenge: Communication efficiency, non-IID data distribution, heterogeneous devices
```

**Follow-up Questions:**

**Q: How does differential privacy work for recommendations?**
A: Add noise to: (1) Training data (randomized response — flip some interactions randomly), (2) Gradients during training (DP-SGD — clip + add Gaussian noise), (3) Outputs (add noise to predictions). Formal guarantee: an adversary can't tell if any single user was in the training data (bounded privacy loss ε). Trade-off: more noise (smaller ε) = better privacy but worse recommendations.

**Q: What is the challenge of federated learning for recommendations?**
A: (1) Non-IID data (each user has unique interests — not representative of population), (2) Communication cost (sending model updates for large embedding tables), (3) Heterogeneous devices (phones have different compute/memory), (4) Cold start (new users have no local data), (5) Item embedding aggregation (which items to send to which devices). Solutions: partial model updates, meta-learning for personalization, efficient communication protocols.

---

## 22. Ads and Sponsored Content Recommendations

**Answer:**
Ad recommendation (computational advertising) is a special case of recommendations with unique challenges: auction-based selection, advertiser constraints (budgets, targeting), and revenue optimization alongside user experience.

**Differences from Organic Recommendations:**

| Aspect | Organic Recommendations | Ad Recommendations |
|--------|------------------------|-------------------|
| Objective | User engagement/satisfaction | Revenue (CPC/CPM) + user experience |
| Selection | Best items for user | Auction winner among eligible ads |
| Pricing | N/A | Pay-per-click or pay-per-impression |
| Constraints | Diversity, freshness | Budgets, targeting, frequency caps |
| Feedback | Click, purchase, engagement | Click, conversion, view-through |
| Stakeholders | User + platform | User + platform + advertiser |

**Ad Ranking Formula:**
$$\text{Ad Score} = \text{Bid} \times \text{pCTR} \times \text{Quality Factor}$$

Where:
- Bid: advertiser's willingness to pay
- pCTR: predicted click-through rate (deep learning model)
- Quality Factor: ad relevance, landing page quality, user experience

**CTR Prediction Models (same as ranking models):**

| Model | Used by | Architecture |
|-------|---------|-------------|
| Wide & Deep | Google | Linear + MLP |
| DeepFM | Various | FM + MLP |
| DIN | Alibaba | Attention over user ad history |
| DIEN | Alibaba | Interest evolution for ads |
| DLRM | Facebook/Meta | Embedding interaction + MLP |
| DCN v2 | Google | Cross network + deep |

**Follow-up Questions:**

**Q: What is the CTR prediction problem and why is it hard?**
A: Predict P(click | user, ad, context) for every (user, ad) pair at serving time. Challenges: (1) Extreme class imbalance (CTR often 1-5%), (2) Massive feature space (millions of ads × millions of users), (3) Strict latency (<10ms for billions of requests), (4) Feature interactions are key (ad_category × user_interest × time_of_day), (5) Dynamic (ad creatives change, user interests shift). Deep learning CTR models are the revenue engine of most internet companies.

**Q: What is the second-price auction and why is it used?**
A: Winner pays the second-highest bid + $0.01 (not their own bid). Why: incentive-compatible — the dominant strategy is to bid your true value. Advertisers don't need to game the system. This creates efficient markets. Generalized second-price (GSP) for multiple ad slots. VCG (Vickrey-Clarke-Groves) for theoretically optimal mechanism but complex.

**Q: How do you balance ad revenue with user experience?**
A: (1) Ad load management (limit ads per page/session), (2) Quality threshold (don't show irrelevant ads even if bid is high), (3) Negative feedback signals (hide/report → reduce similar ads), (4) Long-term optimization (RL: sacrificing short-term revenue for user retention), (5) Native ad formats (blend with content). Metric: revenue per user session (captures both ad revenue and engagement).

---

## 23. Evaluation: A/B Testing and Counterfactual Evaluation

**Answer:**
Online evaluation (A/B testing) is the gold standard for recommendation evaluation, but offline evaluation methods are needed for rapid iteration. Counterfactual evaluation bridges the gap.

**A/B Testing for Recommendations:**
```
Control (A): Existing recommendation algorithm
Treatment (B): New recommendation algorithm
Random user split: 50%/50% (or 90%/10% for risky changes)
Duration: 1-4 weeks (capture weekly patterns)
Metrics: CTR, engagement, revenue, retention, satisfaction

Key considerations:
- Network effects (user A's behavior affects user B's recommendations)
- Novelty effect (new UI gets clicks regardless of algorithm quality)
- Primacy effect (users habituated to old system)
- Multiple comparison correction (testing many variants)
```

**Offline Evaluation Methods:**

| Method | How it works | Limitations |
|--------|-------------|-------------|
| Holdout (temporal) | Train on past, evaluate on future interactions | Only evaluates on items actually shown |
| Replay/counterfactual | Reweight logged data by new policy's probability | High variance for different policies |
| Simulation | Simulate user behavior | Simulation may not match reality |
| IPS (Inverse Propensity Scoring) | Correct for logging policy bias | High variance, propensity estimation |
| Doubly Robust | Combine model prediction + IPS correction | Lower variance than IPS alone |

**Counterfactual Evaluation:**
```
Problem: We have logged data from policy π₀ (old recommender).
         We want to evaluate policy π₁ (new recommender) WITHOUT deploying it.

IPS Estimator:
V(π₁) = (1/N) Σ [π₁(a|x) / π₀(a|x)] × r
- If new policy would have chosen the same action: weight by likelihood ratio
- If new policy would have chosen differently: that interaction is discarded

Challenge: If policies are very different, most data is discarded (high variance)
Solution: Doubly Robust combines IPS with a reward model for lower variance
```

**Interleaving (Alternative to A/B):**
```
Instead of splitting users, interleave results from both algorithms for SAME users:
- Mix items from A and B in one list
- Track which algorithm's items get more engagement
- Faster to reach significance (same user sees both)
- Used by Netflix, Spotify for ranking evaluation
```

**Follow-up Questions:**

**Q: Why do offline metrics often not predict online improvements?**
A: (1) Offline evaluates on items that were shown (positively biased selection), (2) User behavior changes when recommendations change (feedback effects), (3) Novelty/position effects not captured offline, (4) Metrics differ (offline: NDCG on held-out; online: actual engagement), (5) Offline can't measure diversity/serendipity impact on long-term behavior. Rule of thumb: offline gain > 5% to expect any online improvement.

**Q: What is the minimum detectable effect (MDE) in A/B testing?**
A: The smallest improvement you can detect with statistical significance given: sample size, baseline metric variance, and desired confidence level. MDE = z_{α/2} × σ × √(2/n). For CTR=2% with 1M users/variant: can detect ~0.1% absolute change. Recommendations often have small effect sizes → need large experiments. Solutions: longer duration, larger traffic, variance reduction techniques.

**Q: How do you handle long-term effects that A/B tests miss?**
A: Short A/B tests (1-2 weeks) capture immediate metrics but miss: (1) User habit formation, (2) Filter bubble development over months, (3) Content creator ecosystem effects, (4) Subscription/retention effects. Solutions: (1) Long-term holdout groups (1% of users stay on old system for months), (2) Surrogate metrics that predict long-term outcomes, (3) User surveys, (4) Cohort analysis.

---

## 24. Industry Case Studies

**Answer:**
Understanding how major companies implement recommendations provides insight into practical architecture decisions, trade-offs, and lessons learned.

**YouTube (Google):**
```
Architecture: Two-stage (candidate generation + ranking)

Candidate Generation (2016 paper):
- User history (watch IDs, search queries) → embeddings → average
- Context features (geo, device, time)
- → MLP → user embedding
- → ANN retrieval from video corpus

Ranking:
- Rich features: video age, channel, user-video interactions
- Expected watch time prediction (not just click)
- Wide & Deep style model

Key insights:
- Predict watch time, not click (avoids clickbait)
- "Fresh" content gets boosted (exploration)
- Example age as feature removes train-serve skew
```

**TikTok (ByteDance):**
```
Known architecture elements:
- Multi-stage: retrieval → pre-ranking → ranking → re-ranking
- Interest modeling: attention over diverse user interests
- Multi-task: P(like), P(comment), P(share), P(finish watching), P(long watch)
- Exploration: substantial explore budget for new users/items
- Creator ecosystem: balance user engagement with creator fairness

Key insight: Heavy exploration for new users (cold start)
→ Learn user preferences quickly (< 10 videos)
→ Filter bubble prevention via diversity constraints
```

**Netflix:**
```
Multiple recommendation rows, each with different purpose:
- "Because you watched X" (item-based CF)
- "Trending Now" (popularity + freshness)
- "Top Picks for You" (personalized ranking)
- Each row uses different model/algorithm

Key innovations:
- Artwork personalization (different poster for different users)
- Interleaving for evaluation (A/B within same user)
- Homepage as a two-dimensional recommendation problem (rows × items)
- Engagement metric: member retention (not clicks)
```

**Spotify:**
```
Discover Weekly (30-song personalized playlist):
1. Collaborative filtering: Taste profiles from listening history
2. NLP on playlists: Word2Vec on track sequences (track = word, playlist = sentence)
3. Audio features: CNN on spectrograms for cold-start tracks
4. Blend: Combine CF + content + audio signals
5. Filter: Remove known tracks, apply diversity

Key insight: Audio features enable cold-start recommendation for new tracks
(no listening history needed — just audio content analysis)
```

**Amazon:**
```
Product recommendations:
- Item-to-item collaborative filtering (scalable, real-time)
- "Frequently bought together" (association rules on purchase data)
- Personalized re-ranking based on user purchase/browse history
- DeepAR for demand forecasting (related to recommendations)

Key insight: Item-to-item CF scales better than user-to-user 
(items don't change, but user histories grow constantly)
```

**Follow-up Questions:**

**Q: What is the common architecture pattern across all these companies?**
A: All use multi-stage pipelines: (1) Multiple retrieval sources → merge candidates. (2) Lightweight pre-scoring → filter to hundreds. (3) Complex ranking model → score with rich features. (4) Re-ranking → business logic, diversity, exploration. This pattern is universal because it's the only way to handle billions of items with strict latency constraints while using complex models.

**Q: What metric do leading companies optimize?**
A: Not clicks alone. YouTube: watch time (value-weighted, not count). Netflix: retention (will user stay subscribed?). Spotify: listening time + discovery. TikTok: multi-objective (watch time + interactions + shares). Amazon: purchase + long-term value. The trend is toward long-term engagement metrics, often requiring RL-style optimization.

---

## 25. LLM-Based Recommendations

**Answer:**
Large Language Models are transforming recommendations by enabling conversational interaction, zero-shot recommendation, reasoning about preferences, and unified multi-task approaches. This is the cutting-edge frontier of recommendation systems (2023-2026).

**Paradigms:**

| Paradigm | How LLM is used | Example |
|----------|----------------|---------|
| LLM as ranker | Score items given user profile in natural language | "Rate relevance of [item] for [user profile]" |
| LLM as generator | Generate item recommendations directly | "Recommend 5 movies for user who liked [A, B, C]" |
| LLM as feature extractor | Extract embeddings/features from item descriptions | BERT/LLM embeddings for items |
| LLM as conversational interface | Natural dialogue for preference elicitation | Chatbot-style recommendation |
| LLM + retrieval (RAG) | Retrieve candidates, LLM reasons about them | Retrieve items → LLM ranks/explains |
| LLM as unified model | All rec tasks as text generation | P5, InstructRec |

**P5 (Pretrain, Personalized Prompt, Predict) Framework:**
```
Unified text-to-text for ALL recommendation tasks:

Rating prediction: 
  Input: "Predict the rating of user_123 for item_456. User has rated: [history]"
  Output: "4"

Sequential rec:
  Input: "User purchased [A, B, C, D] in order. Predict next purchase."
  Output: "E"

Explanation:
  Input: "Explain why user_123 might like item_456."
  Output: "Because you enjoyed similar sci-fi movies with complex plots."

All tasks share the same model — just different prompts.
```

**LLM + Traditional Rec System (Hybrid):**
```
┌─────────────────────────────────────────┐
│ Traditional Pipeline                      │
│ (Two-tower retrieval → DNN ranking)       │
│ → Top 20 candidates with scores           │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ LLM Re-ranker/Explainer                   │
│ Input: "Given user preferences [X, Y, Z]  │
│         and candidates [A, B, ..., T],     │
│         rank by relevance and explain."    │
│ Output: Ranked list + explanations         │
└─────────────────────────────────────────┘
    ↓
Final recommendations with natural language explanations
```

**Comparison:**

| Aspect | Traditional RecSys | LLM-Based |
|--------|-------------------|-----------|
| Training data | Interaction logs | Pre-trained on web text + interaction data |
| Cold start | Poor (needs interactions) | Better (uses item descriptions, world knowledge) |
| Explainability | Limited/template | Natural language, flexible |
| Conversational | Separate system | Native |
| Latency | <50ms | 500ms-5s per request |
| Cost | Low per request | High (LLM inference) |
| Scalability | Billions of items | Limited by context window |
| Personalization | Strong (from interactions) | Weaker (limited context window for history) |

**Follow-up Questions:**

**Q: Can LLMs replace traditional recommendation systems?**
A: Not entirely in 2024-2026. Limitations: (1) Latency too high for real-time serving at scale (500ms+ vs. <50ms needed). (2) Cost prohibitive for billions of requests/day. (3) Context window can't hold full user history (millions of interactions). (4) Not as accurate as specialized models for well-understood domains with rich interaction data. Best role: LLMs enhance traditional systems (better cold-start, explanations, conversational interface) rather than replacing them.

**Q: How do you handle the LLM's tendency to hallucinate items?**
A: LLMs may recommend items that don't exist in the catalog. Solutions: (1) Constrained generation (only generate from valid item IDs/names), (2) Retrieval-augmented (retrieve real items first, LLM only re-ranks/explains), (3) Post-processing (verify recommendations against catalog), (4) Grounding prompts ("Only recommend from this list: [...]"). Most practical: LLM operates on retrieved candidates, not open generation.

**Q: What is the role of LLMs in the recommendation pipeline?**
A: Most effective as: (1) **Cold-start feature extractor:** Generate rich item embeddings from descriptions for new items. (2) **Re-ranker:** Re-rank top-50 candidates considering nuanced preferences. (3) **Explainer:** Generate natural language explanations for recommendations. (4) **Conversational layer:** Interface for users to describe preferences naturally. (5) **Data augmentation:** Generate synthetic user profiles/interactions for training.

**Q: How does InstructRec work?**
A: Converts recommendation tasks to instruction-following format. User preferences → natural language instruction. The LLM is fine-tuned on diverse recommendation instructions. At inference: "I'm looking for a thriller movie similar to Inception but with a female lead" → LLM generates recommendations. Key benefit: handles novel/complex preference descriptions that structured models can't. Combines world knowledge with recommendation logic.

---

## Quick Reference: RecSys Model Selection

| Scenario | Recommended approach |
|----------|---------------------|
| Cold start (new system) | Content-based + popular + LLM features |
| Mature system (rich interactions) | Two-tower retrieval + Deep ranking (DCN/DIN) |
| Sequential behavior matters | SASRec, BERT4Rec, or GRU4Rec for sessions |
| Rich item metadata | Multi-modal (CLIP + text + structured) |
| Need explainability | KG-enhanced, attention-based (DIN), LLM explanations |
| Social network available | GNN-based (PinSage, LightGCN) |
| Long-term engagement | RL-based, multi-task with engagement signals |
| Conversational | LLM + retrieval hybrid |
| Ads/monetization | DeepFM/DCN + auction + multi-task (CTR+CVR) |
| Privacy-constrained | Federated learning, on-device models |
| Millions of items, real-time | Two-tower + ANN + lightweight ranking |
| Few items, complex matching | Cross-encoder ranking, full feature interaction |

---

## Common Interview Traps (RecSys-Specific)

1. **"Just use collaborative filtering"** → CF alone can't handle cold start, doesn't use item features, ignores sequential patterns, and suffers from popularity bias. Production systems are always hybrid.

2. **"Optimize for clicks"** → Clicks ≠ satisfaction. Clickbait maximizes CTR but destroys long-term engagement. Optimize for downstream metrics: watch time, purchase, retention, satisfaction.

3. **"More personalization is always better"** → Over-personalization → filter bubble → users miss relevant content → reduced discovery → eventual disengagement. Balance personalization with exploration and diversity.

4. **"Offline NDCG improvement guarantees online improvement"** → Often offline metrics don't translate to online gains. The offline-online correlation is weak. Always A/B test. Offline is for hypothesis generation, online is for validation.

5. **"One model serves all"** → Production systems use multiple specialized models for different parts of the funnel (retrieval, ranking, re-ranking) and different use cases (homepage, search, notifications). No single model is best for everything.

6. **"Deep learning always beats matrix factorization"** → For pure collaborative filtering with sufficient data, well-tuned MF (dot product) is competitive with NCF. Complexity is only justified when it captures patterns simple models can't (sequences, features, context).

7. **"The recommendation model is the hard part"** → In production: feature engineering (40%), system design (30%), model (20%), evaluation (10%). The model is just one component. Feature pipelines, serving infrastructure, and evaluation frameworks are equally critical.

8. **"Real-time is always better than batch"** → Batch pre-computation handles 90%+ of use cases. Real-time adds complexity, latency risk, and infrastructure cost. Only needed when: immediate session context is crucial (e-commerce search, news feed).

9. **"We should use the latest model from papers"** → Academic SOTA models (on small benchmarks) often don't scale to production. Industry-proven architectures (two-tower, DeepFM, DCN) with good engineering beat "latest paper" models. Validate on YOUR data at YOUR scale.

10. **"LLMs will replace recommendation systems"** → Not for high-throughput, low-latency production serving (too slow, too expensive). LLMs augment (cold start, explanations, conversation) rather than replace the core pipeline. The retrieval + ranking architecture remains necessary at scale.
