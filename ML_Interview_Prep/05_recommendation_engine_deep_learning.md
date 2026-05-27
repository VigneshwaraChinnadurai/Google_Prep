# Recommendation Engine - Deep Learning Comprehensive Guide

## Table of Contents
1. [Recommendation Systems Fundamentals](#fundamentals)
2. [Collaborative Filtering](#collaborative-filtering)
3. [Content-Based Filtering](#content-based)
4. [Deep Learning Approaches](#deep-learning)
5. [Sequential & Session-based Recommendations](#sequential)
6. [Graph-based Recommendations](#graph-based)
7. [Two-Tower & Retrieval Models](#two-tower)
8. [Evaluation Metrics](#evaluation)
9. [Production Considerations](#production)
10. [Interview Questions with Answers](#interview-questions)
11. [Comparisons & Alternatives](#comparisons)

---

## Recommendation Systems Fundamentals

### What is a Recommendation System?
A system that predicts user preferences and suggests relevant items from a large catalog.

**Layman Example:** Netflix suggesting shows you might like based on what you've watched and what similar users enjoyed. Or Amazon's "Customers who bought this also bought..."

### Types of Recommendation Systems

| Type | Approach | Example |
|------|----------|---------|
| Collaborative Filtering | User-item interactions | "Users like you also liked X" |
| Content-Based | Item attributes | "Because you watched action movies" |
| Hybrid | Combine multiple approaches | Netflix (collaborative + content) |
| Knowledge-Based | Domain rules + user requirements | Travel recommendations |

### The Recommendation Problem

**Formal Definition:** Given a set of users U and items I, predict the rating/preference function f(u, i) for unseen user-item pairs.

**Data Types:**
- **Explicit feedback:** Ratings (1-5 stars), likes/dislikes
- **Implicit feedback:** Clicks, views, purchases, time spent, scrolls
- **Contextual data:** Time, location, device, session

**Key Challenge:** Extreme sparsity — users interact with <1% of items typically.

---

## Collaborative Filtering

### Memory-Based Methods

#### User-Based CF
- Find similar users → recommend what they liked
- Similarity: Cosine, Pearson correlation, Jaccard
- **Problem:** Doesn't scale (O(n²) for n users)

#### Item-Based CF
- Find items similar to what user liked → recommend those
- More stable than user-based (items change less than users)
- Amazon's foundational approach

### Model-Based Methods

#### Matrix Factorization (MF)
**Concept:** Decompose user-item interaction matrix into latent factor matrices.

```
R ≈ U × V^T
where R is m×n (users × items)
      U is m×k (user latent factors)
      V is n×k (item latent factors)
```

**Layman Example:** Each user and item is described by hidden characteristics (e.g., for movies: comedy-level, action-level, romance-level). The rating is how well user preferences match movie characteristics.

**Training:** Minimize reconstruction error + regularization
```
Loss = Σ(r_ui - u_i × v_j^T)² + λ(||U||² + ||V||²)
```

**Algorithms:**
- SVD (Singular Value Decomposition)
- ALS (Alternating Least Squares) — parallelizable
- SGD-based optimization

#### SVD++ 
- Extends MF with implicit feedback signals
- r_ui = μ + b_u + b_i + q_i^T(p_u + |N(u)|^{-1/2} × Σ y_j)
- Incorporates which items a user has interacted with (regardless of rating)

#### BPR (Bayesian Personalized Ranking)
- Optimizes ranking (not rating prediction)
- Assumes observed interactions > unobserved
- Pairwise loss: maximize P(user prefers observed over unobserved)
- Better for implicit feedback

---

## Content-Based Filtering

### Concept
Recommend items similar to what the user previously liked, based on item features.

### Feature Representation
- **Text:** TF-IDF, word embeddings, BERT embeddings
- **Images:** CNN features, CLIP embeddings
- **Categorical:** One-hot, embeddings
- **Structured:** Feature vectors

### Advantages
- No cold-start for new items (have features immediately)
- Transparent recommendations (can explain why)
- No need for other users' data

### Limitations
- Limited to existing feature space (no serendipity)
- Cold-start for new users (no interaction history)
- Feature engineering required
- Over-specialization (filter bubble)

---

## Deep Learning Approaches

### Neural Collaborative Filtering (NCF, 2017)
**Concept:** Replace dot product in MF with a neural network to learn complex user-item interactions.

**Architecture:**
```
User ID → Embedding → ┐
                       ├→ Concatenate → MLP layers → Prediction
Item ID → Embedding → ┘
```

**Key Insight:** MF uses linear dot product (limited); NCF uses non-linear interactions.

**Variants:**
- GMF (Generalized MF): Element-wise product of embeddings
- MLP: Concatenation + deep network
- NeuMF: Combines GMF + MLP

### Deep Factorization Machines (DeepFM, 2017)
**Concept:** Combines FM (feature interactions) with DNN (high-order features).

```
Input Features → Sparse → Embeddings →┐
                                       ├→ FM Component (2nd order interactions)
                                       ├→ Deep Component (higher order)
                                       └→ Concatenate → Output
```

**Why it works:** FM captures pairwise interactions efficiently; DNN captures complex higher-order patterns. No manual feature engineering needed.

### Wide & Deep (Google, 2016)
**Concept:** Combines memorization (wide) with generalization (deep).

```
Wide Component: Linear model on cross-product features (memorization)
Deep Component: DNN on dense embeddings (generalization)
Output: σ(W_wide × [x, φ(x)] + W_deep × a^L + b)
```

**Layman Example:** 
- Wide = remembering exact combinations ("user X bought items A and B together before")
- Deep = learning general patterns ("users who like action movies also like sci-fi")

### AutoEncoder-based Recommendations

#### Variational AutoEncoder (VAE) for CF
- Encode user interactions into latent space
- Regularize with KL divergence
- Decode to reconstruct/predict all item scores
- Mult-VAE: Multinomial likelihood for implicit feedback

### Attention-based Models

#### DIN (Deep Interest Network, Alibaba, 2018)
- Local activation unit weights historical behaviors by relevance to candidate
- "When evaluating a dress, your past dress interactions matter more than electronics"
- Attention weighted pooling of user behavior sequence

#### DIEN (Deep Interest Evolution Network, 2019)
- Models how user interests EVOLVE over time
- GRU for interest extraction
- Attention-based GRU for interest evolution

---

## Sequential & Session-based Recommendations

### Why Sequential?
User preferences change over time. Order of interactions matters.

**Layman Example:** If you just watched a horror movie, you might want another horror — or a comedy to lighten up. The sequence of your recent choices reveals your current mood.

### GRU4Rec (2016)
- First major DL model for session-based recommendation
- GRU processes session items sequentially
- Predicts next item in session
- Session-parallel mini-batches for efficiency
- BPR or TOP1 ranking loss

### SASRec (Self-Attentive Sequential Recommendation, 2018)
- Transformer (left-to-right attention) for user action sequences
- Each item is a token, position = time in sequence
- Captures long-range dependencies without RNN
- More effective than GRU4Rec for longer sequences

### BERT4Rec (2019)
- Bidirectional attention for sequential recommendation
- Masked item prediction (like BERT's MLM)
- "Cloze task" for recommendations
- Sees both left and right context of masked items

### Key Design Choices

| Aspect | GRU4Rec | SASRec | BERT4Rec |
|--------|---------|--------|----------|
| Architecture | GRU | Causal Transformer | Bidirectional Transformer |
| Context | Left only | Left only | Both directions |
| Training | Next-item prediction | Next-item prediction | Masked item prediction |
| At inference | Use all history | Use all history | Mask last position |

---

## Graph-based Recommendations

### Why Graphs?
User-item interactions naturally form a bipartite graph. Graph structure captures collaborative signals.

### PinSage (Pinterest, 2018)
- Graph Convolutional Network on Pin-Board graph
- Learns item embeddings via neighbor aggregation
- Importance-based sampling for scalability
- Used in production at Pinterest

### LightGCN (2020)
- Simplified GCN for recommendations
- Removes non-linearity and feature transformation
- Only neighborhood aggregation (mean pooling)
- Layer combination via weighted sum
- Outperforms complex GCN variants

**Architecture:**
```
e_u^(k+1) = Σ(1/√|N_u| × 1/√|N_i|) × e_i^(k)  (for all items i user u interacted with)
e_i^(k+1) = Σ(1/√|N_i| × 1/√|N_u|) × e_u^(k)  (for all users u who interacted with item i)

Final: e_u = Σ α_k × e_u^(k)  (combine all layers)
```

### Knowledge Graph-based
- Incorporate item relationships (genre, director, brand)
- Path-based reasoning: User → Movie A → Genre: Sci-fi → Movie B
- KG embeddings: TransE, TransR for relation modeling
- Improves explainability

---

## Two-Tower & Retrieval Models

### Production Recommendation Pipeline
```
Candidate Generation → Ranking → Re-ranking → Serving
(1000s of items)      (100s)    (10s)        (final list)
```

### Two-Tower Architecture (Google, YouTube)

**Concept:** Separate encoders for user and item, trained to bring positive pairs close in embedding space.

```
User Tower:                    Item Tower:
User features → MLP → u_emb   Item features → MLP → v_emb

Score = dot(u_emb, v_emb) or cosine(u_emb, v_emb)
```

**Why Two Towers?**
- Item embeddings computed offline and indexed
- At serving time, only compute user tower
- ANN (Approximate Nearest Neighbor) search for fast retrieval
- Scales to billions of items

**Training:**
- In-batch negatives (other items in batch as negatives)
- Hard negative mining
- Contrastive loss or softmax cross-entropy

### Multi-Task Learning for Recommendations

#### MMOE (Multi-gate Mixture of Experts)
- Multiple tasks: click prediction, purchase prediction, engagement
- Shared experts + task-specific gates
- Each task learns which experts to use

#### ESMM (Entire Space Multi-Task Model)
- Models: impression → click → conversion
- CVR prediction over entire impression space
- Handles sample selection bias

---

## Evaluation Metrics

### Ranking Metrics

| Metric | Description | Formula/Intuition |
|--------|-------------|-------------------|
| Precision@K | % of top-K items that are relevant | Relevant∩Recommended / K |
| Recall@K | % of relevant items found in top-K | Relevant∩Recommended / Total_Relevant |
| NDCG@K | Ranking quality with position discount | Higher = relevant items ranked higher |
| MAP@K | Mean Average Precision | Average of precision at each relevant position |
| MRR | Mean Reciprocal Rank | 1/rank of first relevant item |
| Hit Rate@K | Did any relevant item appear in top-K? | Binary per user |

### Rating Prediction Metrics
- RMSE, MAE (for explicit feedback)

### Beyond-Accuracy Metrics

| Metric | What it Measures |
|--------|-----------------|
| Coverage | % of catalog recommended |
| Diversity | How different recommended items are |
| Novelty | How surprising/unknown items are |
| Serendipity | Unexpected AND relevant |
| Fairness | Equal exposure across item groups |

### Offline vs Online Evaluation

| Type | Methods | Measures |
|------|---------|----------|
| Offline | Historical replay, hold-out | Ranking metrics |
| Online | A/B testing | CTR, revenue, engagement, retention |
| User studies | Surveys, interviews | Satisfaction, trust |

---

## Production Considerations

### Cold Start Problem

| Type | Problem | Solutions |
|------|---------|-----------|
| New User | No interaction history | Content-based, popular items, onboarding quiz |
| New Item | No one has interacted | Content features, bandit exploration, boosting |
| System | Empty system | Knowledge-based, editorial curation |

### Scalability
- **Candidate generation:** ANN indices (FAISS, ScaNN, HNSW)
- **Feature store:** Precomputed user/item features
- **Model serving:** Low-latency inference (TensorRT, ONNX)
- **Distributed training:** Parameter servers, model parallelism

### Real-time vs Batch
- **Batch:** Daily/hourly model updates, precomputed embeddings
- **Near-real-time:** Stream user actions, update features in minutes
- **Real-time:** Session-based models, immediate adaptation

### Common Pitfalls
- **Popularity bias:** System only recommends popular items
- **Filter bubbles:** Users see only similar content
- **Position bias:** Users click top items regardless of quality
- **Feedback loops:** Recommendations influence future interactions

### Exploration vs Exploitation
- **Exploitation:** Recommend items with highest predicted score
- **Exploration:** Occasionally recommend uncertain items to learn
- **Methods:** ε-greedy, Thompson Sampling, UCB, contextual bandits

---

## Interview Questions with Answers

### Q1: How do you handle the sparsity problem in recommendation systems?
**Answer:**
- Matrix Factorization: Low-rank approximation fills sparse matrix
- Side information: Use item/user features beyond interactions
- Implicit feedback: Use all signals (views, time, scrolls) not just ratings
- Transfer learning: Pretrained embeddings from larger datasets
- Graph-based: Propagate signals through interaction graph
- Data augmentation: Session dropout, feature masking during training

### Q2: Explain the cold start problem and solutions
**Answer:**
- **New user cold start:**
  - Ask preferences during onboarding
  - Content-based recommendations (no history needed)
  - Popular/trending items
  - Bandit-based exploration
  - Social connections (recommend what friends like)
- **New item cold start:**
  - Content features → find similar existing items
  - Boost exposure for new items (exploration)
  - Use metadata (description, category, images)
  - Pre-trained multimodal embeddings (CLIP for images + text)

### Q3: What is the difference between pointwise, pairwise, and listwise learning?
**Answer:**
- **Pointwise:** Predict score for each item independently (MSE loss)
  - Treats each user-item pair separately
  - Example: Rating prediction
- **Pairwise:** Learn relative order between pairs (BPR loss)
  - "Item A should rank higher than item B for this user"
  - Example: BPR, Margin loss
- **Listwise:** Optimize the entire ranked list (NDCG-like loss)
  - Directly optimize ranking metrics
  - Example: LambdaRank, softmax cross-entropy over all items
- **Best practice:** Pairwise/Listwise are better for ranking tasks

### Q4: How does YouTube's recommendation system work?
**Answer:**
**Two-stage architecture:**
1. **Candidate Generation (Deep Neural Network):**
   - Input: Watch history, search history, demographics, context
   - Two-tower: User tower + video tower
   - Outputs ~hundreds of candidates from millions
   - Uses ANN for fast retrieval

2. **Ranking (Deep Neural Network):**
   - Rich features: video age, channel, user history, context
   - Multi-task: predicts engagement (watch time), satisfaction
   - Outputs final ordered list

**Key innovations:**
- Weighted by watch time (not just clicks)
- "Example age" feature to handle fresh content
- Treating recommendation as extreme multiclass classification

### Q5: Explain implicit vs explicit feedback and how training differs
**Answer:**
- **Explicit:** Direct ratings (1-5 stars). Clear positive and negative signals.
  - Train with MSE loss on known ratings
  - Missing = unknown (not negative)
- **Implicit:** Actions (click, view, purchase). Only positive signals.
  - No explicit "dislike" — absence is ambiguous
  - Training approaches:
    - Negative sampling: Sample unobserved as negatives
    - BPR: Observed > unobserved preference
    - Weighted MF: Low confidence for unobserved (WMF)
    - Contrastive learning: Positive vs in-batch negatives

### Q6: What are embedding tables and why are they critical?
**Answer:**
- Lookup table: ID → dense vector (learned during training)
- Convert sparse categorical (user_id, item_id) to dense representations
- Foundation of almost all DL recommendation models
- **Challenges at scale:**
  - Billions of items = massive embedding tables (hundreds of GB)
  - Solutions: Hashing, compositional embeddings, quantization
  - Distributed storage across machines
- **Quality:** Good embeddings capture similarity (similar items cluster together)

### Q7: How do you handle position bias in recommendations?
**Answer:**
- Users tend to click items in top positions regardless of relevance
- **Solutions:**
  - Inverse Propensity Scoring: Weight interactions by 1/P(position)
  - Position feature: Include position as input during training, remove at inference
  - Unbiased learning to rank: Debias click data
  - Randomization: Occasionally shuffle to measure true preferences
  - Separate position model from relevance model

### Q8: What is feature interaction and why does it matter?
**Answer:**
- Feature interaction = combined effect of multiple features
- Example: "young male user" + "action movie" → strong positive signal
- **Methods:**
  - Cross features (manual): feature_a × feature_b
  - FM (Factorization Machines): Learns all pairwise interactions efficiently
  - DeepFM: FM for 2nd order + DNN for higher order
  - Cross Network (DCN): Explicit cross features of arbitrary order
- Interactions capture patterns that individual features cannot

### Q9: How do you evaluate a recommendation system in production?
**Answer:**
- **Offline metrics first:** NDCG, Recall, AUC on held-out data
- **Online A/B testing:** 
  - Primary: CTR, conversion rate, revenue
  - Engagement: Time spent, sessions, return visits
  - Long-term: Retention, user satisfaction
- **Guardrail metrics:** Diversity, coverage, freshness
- **Important:** Offline improvements don't always translate to online gains
- **Interleaving:** More sensitive than A/B testing (fewer users needed)

### Q10: Explain multi-task learning in recommendation systems
**Answer:**
- Multiple objectives: click, add-to-cart, purchase, long-term engagement
- **Why multi-task?**
  - Shared representation learning (more data for common features)
  - Handle multiple business objectives simultaneously
  - Auxiliary tasks can regularize main task
- **Architectures:**
  - Shared-bottom: Common layers → task-specific heads
  - MMOE: Multiple expert networks + task-specific gating
  - PLE (Progressive Layered Extraction): Shared + task-specific experts
- **Challenge:** Task conflicts (optimizing clicks may hurt long-term satisfaction)
- **Solution:** Pareto optimization, uncertainty weighting, gradient manipulation

---

## Comparisons & Alternatives

### Algorithm Selection Guide

| Scenario | Approach | Why |
|----------|----------|-----|
| Sparse explicit ratings | Matrix Factorization (ALS) | Handles sparsity well |
| Rich implicit feedback | NCF, Two-Tower | Learns from behavior |
| Sequential behavior | SASRec, GRU4Rec | Captures temporal patterns |
| Many item features | Content-based + Deep | Leverages item attributes |
| Social network | Graph-based (LightGCN) | Captures relational patterns |
| Real-time session | Session-based (GRU4Rec) | Adapts to current session |
| Large-scale production | Two-Tower + ANN | Scalable retrieval |

### Traditional vs Deep Learning Recommendations

| Aspect | Traditional (MF, CF) | Deep Learning |
|--------|----------------------|---------------|
| Performance | Good baseline | Often better |
| Feature handling | Manual | Automatic embedding |
| Side information | Hard to incorporate | Natural (multi-modal) |
| Scalability | Good (ALS) | Challenging (but solved) |
| Interpretability | High (latent factors) | Low (black box) |
| Cold start | Poor | Better (content features) |
| Compute | Low | High |

### Emerging Trends (2024-2025)
1. **LLM-based Recommendations:** Use LLMs to understand items and generate recommendations
2. **Multi-modal:** Combine text, image, video features (CLIP embeddings)
3. **Foundation models for RecSys:** Pretrained on many datasets, fine-tune for specific domain
4. **Conversational RecSys:** Interactive dialogue-based recommendations
5. **Causal inference:** Debias recommendations, understand true user preferences
6. **Federated learning:** Train on user data without centralizing it
7. **Reinforcement learning:** Optimize long-term user satisfaction, not just immediate clicks
