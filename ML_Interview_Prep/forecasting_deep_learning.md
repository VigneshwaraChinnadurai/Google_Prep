# Forecasting & Deep Learning - Interview Concepts

---

## 1. Time Series Fundamentals

**Answer:**
A time series is a sequence of data points ordered by time. Forecasting predicts future values based on historical patterns. Key components: trend (long-term direction), seasonality (repeating patterns), cyclical (non-fixed period fluctuations), and residual (random noise).

**Decomposition:**
```
Y(t) = Trend(t) + Seasonality(t) + Residual(t)     [Additive]
Y(t) = Trend(t) × Seasonality(t) × Residual(t)     [Multiplicative]
```

**Key Properties:**

| Property | Definition | Test | Implication |
|----------|-----------|------|-------------|
| Stationarity | Statistical properties don't change over time | ADF test, KPSS test | Required for ARIMA, simplifies modeling |
| Autocorrelation | Correlation of series with its lagged self | ACF/PACF plots | Identifies AR/MA order |
| Seasonality | Regular repeating patterns | Seasonal decomposition, periodogram | Need seasonal models |
| Trend | Long-term increase/decrease | Visual, moving average | Differencing or detrending |
| Heteroscedasticity | Changing variance over time | Breusch-Pagan test | Log transform, GARCH models |

**Layman Example:**
Predicting daily ice cream sales:
- **Trend:** Sales growing 5% per year (more stores opening)
- **Seasonality:** Higher in summer, lower in winter (repeats yearly)
- **Cyclical:** Economic booms increase spending (non-fixed period)
- **Residual:** Random spike because a celebrity posted about your ice cream

Decomposition separates these components so you can model each appropriately.

**Follow-up Questions:**

**Q: Why is stationarity important?**
A: Most classical models (ARIMA) assume stationarity — constant mean, variance, and autocorrelation over time. Non-stationary data has spurious correlations and unreliable forecasts. Make stationary via: differencing (remove trend), seasonal differencing, log transform (stabilize variance). Deep learning models are more robust to non-stationarity but still benefit from preprocessing.

**Q: What's the difference between ACF and PACF?**
A: ACF (Autocorrelation Function): correlation between y(t) and y(t-k) — includes indirect correlations through intermediate lags. PACF (Partial ACF): correlation between y(t) and y(t-k) AFTER removing the effect of intermediate lags. PACF helps determine AR order (cuts off at lag p). ACF helps determine MA order (cuts off at lag q).

**Q: How do you handle missing values in time series?**
A: (1) Forward fill (last observation carried forward) — assumes no change, (2) Linear/spline interpolation — smooth fill, (3) Seasonal interpolation — use same period from previous cycle, (4) Model-based imputation (state-space models, Kalman filter), (5) For deep learning: masking (tell model which values are missing). Never use future values to fill past (data leakage).

**Q: What's the difference between univariate and multivariate time series forecasting?**
A: Univariate: predict one variable from its own history (e.g., predict tomorrow's temperature from past temperatures). Multivariate: predict using multiple related variables (e.g., predict temperature using humidity, pressure, wind speed history). Deep learning excels at multivariate — capturing complex cross-variable relationships that classical methods miss.

---

## 2. Classical Forecasting Methods (ARIMA, ETS, Prophet)

**Answer:**
Classical methods are statistical approaches with well-understood theoretical properties. They serve as strong baselines and are often sufficient for univariate forecasting.

**Methods Comparison:**

| Method | Full name | Strengths | Weaknesses | Best for |
|--------|-----------|-----------|------------|----------|
| ARIMA | AutoRegressive Integrated Moving Average | Principled, interpretable | Univariate, linear, requires stationarity | Stationary data with clear ACF/PACF |
| SARIMA | Seasonal ARIMA | Handles seasonality | Manual order selection | Single seasonal period |
| ETS | Error-Trend-Seasonality | Automatic selection, prediction intervals | Univariate, single seasonality | Business forecasting |
| Prophet | Additive decomposition + holidays | Easy to use, handles holidays/events | Can overfit, limited accuracy | Business forecasting with events |
| Theta | Decompose + extrapolate | Simple, competitive | Limited flexibility | M-competition baselines |
| VAR | Vector AutoRegression | Multivariate, Granger causality | Linear, all same frequency | Economic modeling |

**ARIMA(p,d,q) Components:**
- **p (AR order):** Number of lagged values used (autoregressive)
- **d (Integration):** Number of differences to make stationary
- **q (MA order):** Number of lagged forecast errors used

**SARIMA(p,d,q)(P,D,Q)[m]:** Same + seasonal components with period m

**ETS (ExponenTial Smoothing):**
```
Components: Error (A/M) × Trend (N/A/Ad/M/Md) × Seasonality (N/A/M)
- N = None, A = Additive, M = Multiplicative, d = Damped
- Auto-selects best combination via AIC
```

**Layman Example:**
Predicting next month's electricity bill:
- **ARIMA:** "My bill last month was $100, the month before was $95. There's an upward trend of ~$5/month, so next month: ~$105." Uses recent history and patterns in changes.
- **ETS:** "Recent bills matter more than old ones. Last month counts 50%, two months ago 25%, three months ago 12.5%..." Exponentially decreasing weights on history.
- **Prophet:** "It's December (seasonal peak), Christmas is coming (holiday effect), plus the general upward trend = predicted bill." Explicitly models known calendar effects.

**Follow-up Questions:**

**Q: How do you choose between ARIMA and ETS?**
A: ARIMA: better when clear autocorrelation structure (ACF/PACF indicate specific AR/MA terms), when differencing makes data stationary. ETS: better for smooth trends and seasonal patterns, easier to use (automatic model selection), provides natural prediction intervals. In practice: try both, select by AIC or cross-validation performance. They often give similar results.

**Q: When does Prophet outperform statistical methods?**
A: When: (1) Multiple seasonalities (daily + weekly + yearly), (2) Known holiday/event effects, (3) Data with changepoints (trend shifts), (4) Analysts need quick, interpretable results without statistical expertise. When it doesn't: very short series, high-frequency data, complex multivariate relationships, when accuracy is paramount (deep learning often wins).

**Q: What is the Box-Jenkins methodology?**
A: Systematic approach to ARIMA modeling: (1) Identification — plot data, check stationarity (ADF test), examine ACF/PACF to determine p, d, q. (2) Estimation — fit model parameters via MLE. (3) Diagnostic checking — check residuals are white noise (Ljung-Box test), no remaining autocorrelation. (4) Forecasting. Modern: auto_arima automates this via AIC search.

**Q: What are the limitations of classical methods for modern forecasting problems?**
A: (1) Mostly univariate (can't leverage related series), (2) Linear relationships only (ARIMA), (3) One model per series (can't learn across series), (4) Manual feature engineering for complex patterns, (5) Don't scale to thousands of series, (6) Struggle with intermittent/sparse data. Deep learning addresses all of these.

---

## 3. Deep Learning for Time Series — Overview

**Answer:**
Deep learning approaches to forecasting can learn complex non-linear patterns, handle multivariate inputs, learn across multiple time series simultaneously, and automatically extract features. They've become state-of-the-art for many forecasting benchmarks.

**Architecture Families:**

| Family | Models | Mechanism | Best for |
|--------|--------|-----------|----------|
| RNN-based | DeepAR, MQRNN, LSTNet | Recurrent state | Sequential patterns, probabilistic |
| CNN-based | WaveNet, TCN, TimesNet | Dilated convolutions | Long-range, parallel training |
| Transformer-based | Informer, Autoformer, PatchTST | Self-attention | Long-horizon, multivariate |
| MLP-based | N-BEATS, N-HiTS, DLinear, TSMixer | Fully connected layers | Surprisingly strong baselines |
| Foundation models | TimesFM, Chronos, Moirai, TimeGPT | Pre-trained on many series | Zero-shot forecasting |
| Hybrid | TiDE, TSMixup | Mix of approaches | Practical efficiency |

**Key Paradigm Shifts:**

| Paradigm | Classical | Deep Learning |
|----------|-----------|---------------|
| Model per series | One ARIMA per series | One model learns from ALL series |
| Feature engineering | Manual (lags, rolling stats) | Automatic (learned representations) |
| Relationships | Linear (mostly) | Non-linear, complex interactions |
| Multivariate | Limited (VAR) | Natural (multi-input) |
| Probabilistic output | Parametric (Gaussian) | Flexible distributions (quantiles, mixture) |
| Cross-learning | Not possible | Learn patterns shared across series |

**Layman Example:**
Forecasting sales for 10,000 products:
- **Classical (ARIMA):** Build 10,000 separate models, each learning only from its own product's history. Product #7823 with 6 months of data gets a poor model.
- **Deep Learning:** Build ONE model that learns from ALL 10,000 products simultaneously. It learns "holiday effects boost sales," "new product launches follow this curve," "seasonal products peak in Q4" — and applies these shared patterns even to products with limited history. Product #7823 benefits from patterns learned across all other products.

**Follow-up Questions:**

**Q: When do deep learning methods outperform classical methods?**
A: (1) Large number of related time series (cross-learning opportunity), (2) Complex non-linear patterns, (3) Long forecast horizons, (4) Multivariate with many exogenous variables, (5) Irregular/intermittent data, (6) When pre-trained foundation models are available. Classical methods still win for: small number of series, short/simple series, when interpretability is critical.

**Q: What is the "cross-learning" advantage?**
A: A single model trained on many related series learns shared temporal patterns (seasonality, trend shapes, event effects) that transfer across series. A new product with 2 weeks of data can borrow the "typical product lifecycle curve" from thousands of similar products. This is impossible with per-series classical models.

**Q: What features/inputs do deep forecasting models typically use?**
A: (1) Past target values (lags), (2) Time features (hour, day_of_week, month, is_holiday), (3) Known future events (promotions, holidays, weather forecasts), (4) Static metadata (product category, store location), (5) Related series (other products in same category). Models like TFT explicitly separate these into past-observed, future-known, and static inputs.

---

## 4. Recurrent Models (DeepAR, LSTM for Forecasting)

**Answer:**
RNN-based models process time series sequentially, maintaining hidden state that summarizes history. DeepAR (Amazon) is the landmark model — an autoregressive LSTM that produces probabilistic forecasts across many related time series.

**DeepAR Architecture:**
```
Input at each step t:
  - Previous target value y_{t-1} (or sample from predicted distribution during generation)
  - Covariates x_t (time features, known future info)
  - Static embedding (series identifier/metadata)

LSTM → hidden state h_t → distribution parameters (μ_t, σ_t)
→ Sample from distribution for next step (autoregressive)

Training: Teacher forcing (feed true y_{t-1})
Inference: Sample from predicted distribution, feed back as input (Monte Carlo paths)
```

**Key RNN-based Models:**

| Model | Architecture | Output | Key Innovation |
|-------|-------------|--------|----------------|
| DeepAR | Autoregressive LSTM | Parametric distribution | Probabilistic + cross-learning |
| MQRNN | Multi-horizon LSTM | Quantiles | Direct multi-step quantile prediction |
| LSTNet | CNN + RNN + Skip-RNN | Point forecast | Multi-scale temporal patterns |
| DeepState | LSTM + state space model | Distribution | Neural network drives SSM parameters |
| DeepFactor | LSTM + factor model | Distribution | Shared global factors + local model |

**Comparison: Autoregressive vs. Direct Multi-step:**

| Approach | How it predicts H steps ahead | Pros | Cons |
|----------|------------------------------|------|------|
| Autoregressive | Predict 1 step, feed back, repeat H times | Captures sequential dependencies | Error accumulation, slow inference |
| Direct | Predict all H steps at once | No error accumulation, fast | Ignores inter-step dependencies |
| Hybrid (MIMO) | Predict all H steps from one forward pass but with sequential structure | Balanced | More complex |

**Layman Example:**
DeepAR forecasting demand for 50,000 products:
- Like a weather forecaster who's studied climate patterns across hundreds of cities. When predicting tomorrow's weather for a NEW city, they use patterns learned from all cities ("coastal cities have morning fog," "mountain cities have afternoon storms").
- Each product gets a unique "personality" (static embedding), but temporal patterns are shared.
- The forecast isn't just "sales will be 100 units" — it's "sales will be between 80-120 units with 90% confidence" (probabilistic).

**Follow-up Questions:**

**Q: What is teacher forcing and why is there a train-test discrepancy?**
A: During training, the model sees TRUE previous values (teacher forcing — fast, stable). During inference, it sees its OWN predictions (which contain errors). This mismatch causes error accumulation — small errors compound over many autoregressive steps. Solutions: (1) Scheduled sampling (gradually use own predictions during training), (2) Direct prediction (skip autoregressive), (3) Curriculum learning.

**Q: How does DeepAR handle different scale series?**
A: Each series is normalized by its own scale (e.g., mean of last context window). The model predicts relative values, then rescales. This allows one model to handle series ranging from 10 to 10,000,000 in magnitude. Alternative: log transform + standardization.

**Q: Why have Transformers largely replaced LSTMs for forecasting?**
A: (1) Parallel training (no sequential bottleneck), (2) Better at capturing very long-range dependencies (direct attention vs. compressed state), (3) Scale better with more data/compute, (4) Pre-trained foundation models are Transformer-based. LSTMs still relevant for: streaming/online learning, when model size matters (edge deployment), and some probabilistic forecasting.

**Q: What is probabilistic forecasting and why does it matter?**
A: Predicting a distribution (or quantiles) rather than a single point. Critical for: (1) Risk management (what's the worst case?), (2) Inventory planning (stock enough for 95th percentile demand), (3) Decision-making under uncertainty, (4) Model confidence assessment. Methods: predict distribution parameters (Gaussian μ,σ), predict quantiles directly, or generate sample paths.

---

## 5. Temporal Convolutional Networks (TCN, WaveNet)

**Answer:**
TCNs use 1D causal convolutions with dilation to capture long-range temporal dependencies. They're fully parallelizable (unlike RNNs) and handle long sequences efficiently through exponentially growing receptive fields.

**Key Architecture Elements:**

| Element | What it does | Why |
|---------|-------------|-----|
| Causal convolution | Only uses past values (no future leakage) | Maintains temporal order for forecasting |
| Dilated convolution | Gaps between filter elements (dilation 1,2,4,8,...) | Exponential receptive field growth |
| Residual connections | Skip connections between layers | Enables deep networks |
| 1D convolution | Processes along time axis | Extracts local temporal patterns |

**Receptive Field:**
```
Layer 1 (dilation=1):  ▓ ▓ ▓ ░ ░ ░ ░ ░
Layer 2 (dilation=2):  ▓ ░ ▓ ░ ▓ ░ ░ ░
Layer 3 (dilation=4):  ▓ ░ ░ ░ ▓ ░ ░ ░ ▓
Layer 4 (dilation=8):  ▓ ░ ░ ░ ░ ░ ░ ░ ▓

Receptive field = 2^layers × (kernel_size - 1) + 1
With 10 layers, kernel=3: receptive field = 2^10 × 2 + 1 = 2049 time steps
```

**Comparison: TCN vs. LSTM:**

| Aspect | TCN | LSTM |
|--------|-----|------|
| Training speed | Fast (parallel) | Slow (sequential) |
| Memory | Fixed (no hidden state growth) | O(sequence_length) |
| Receptive field | Bounded (but large with dilation) | Theoretically infinite |
| Gradient flow | Better (shorter paths via dilation) | Vanishing gradient risk |
| Flexibility | Must design receptive field | Automatic any-length |
| Inference | Can be fast (caching) | Sequential |

**WaveNet for Forecasting:**
```
Originally for audio generation (DeepMind, 2016)
- Stack of dilated causal convolutions
- Gated activations: tanh(W_f * x) ⊙ σ(W_g * x)
- Skip connections from all layers → output
- Adapted for time series: WaveNet-style architectures for demand forecasting

Amazon adapted WaveNet for forecasting → highly successful in production
```

**Layman Example:**
Looking at a timeline with a series of magnifying glasses:
- **LSTM:** Reads the timeline left to right, trying to remember everything. By the time it reaches 2024, events from 2020 are hazy.
- **TCN (dilated conv):** Uses magnifying glasses at different zoom levels simultaneously:
  - Layer 1: Looks at every adjacent day (captures daily patterns)
  - Layer 2: Looks at every other day (captures weekly patterns)
  - Layer 3: Looks at every 4th day (captures monthly patterns)
  - Layer 4: Looks at every 8th day (captures quarterly patterns)
  - All processed in parallel — fast and captures multi-scale patterns

**Follow-up Questions:**

**Q: How do you choose the number of layers and dilation rate?**
A: Ensure receptive field covers the longest relevant pattern. If yearly seasonality with daily data → need 365+ receptive field. Stack layers with dilation [1, 2, 4, ..., 2^k] until 2^(k+1) > required_receptive_field. Alternatively, repeat dilation blocks (1,2,4,8,1,2,4,8,...) for redundancy. Kernel size 3-7 typical.

**Q: What are the advantages of TCN over Transformer for time series?**
A: (1) Linear complexity O(n) vs. O(n²) for attention, (2) More parameter-efficient for capturing local patterns, (3) Explicit inductive bias for temporal locality, (4) Stable training (no attention collapse issues), (5) Proven in production at scale (Amazon, Google). Disadvantage: fixed receptive field (must be designed), less flexible for variable-length dependencies.

**Q: How is WaveNet adapted from audio to forecasting?**
A: Same architecture (dilated causal conv + gated activations + skip connections) but: (1) Condition on covariates (time features, known future events), (2) Output is distribution parameters instead of audio samples, (3) Often not autoregressive at inference (predict entire horizon at once), (4) Handle multiple related series via conditioning.

---

## 6. Transformer-based Forecasting Models

**Answer:**
Transformers for time series leverage self-attention to capture long-range dependencies and complex temporal patterns. Key challenge: adapting the O(n²) attention mechanism for long sequences and avoiding overfitting on time series data.

**Key Models:**

| Model | Year | Key Innovation | Complexity | Performance |
|-------|------|---------------|-----------|-------------|
| Transformer (vanilla) | 2017 | Self-attention for sequences | O(n²) | Baseline |
| LogTrans | 2019 | Convolutional self-attention | O(n·log n) | Better efficiency |
| Informer | 2021 | ProbSparse attention + distilling | O(n·log n) | Long-horizon |
| Autoformer | 2021 | Auto-correlation + decomposition | O(n·log n) | Seasonal patterns |
| FEDformer | 2022 | Frequency-domain attention | O(n) | Periodic patterns |
| PatchTST | 2023 | Patch tokenization + channel-independent | O((n/p)²) | SOTA for many benchmarks |
| iTransformer | 2024 | Invert: attention across variables, not time | O(V²) | Multivariate |
| Crossformer | 2023 | Cross-dimension attention (time × variables) | O(n·V) | Multi-variable interactions |

**PatchTST Architecture:**
```
Input time series (length L) 
→ Split into patches of size P with stride S: L/S patches
→ Each patch = one "token" (like ViT patches for images)
→ Linear embedding per patch
→ Add positional encoding
→ Transformer encoder (self-attention across patches)
→ Linear head → forecast horizon H

Key insights:
- Patches reduce sequence length by P× (cheaper attention)
- Each patch contains local temporal info (like n-gram for time)
- Channel-independent: process each variable separately (prevents overfitting)
```

**Comparison:**

| Aspect | Informer | Autoformer | PatchTST | iTransformer |
|--------|----------|-----------|----------|--------------|
| Attention type | ProbSparse (select top queries) | Auto-correlation (period-based) | Standard (on patches) | Across variables |
| Input processing | Point-wise | Decomposition (trend+seasonal) | Patched | Variable-as-token |
| Efficiency trick | Sparse attention + distilling | Sub-series level attention | Patch reduces length | Invert dimensions |
| Best for | Long-horizon forecasting | Seasonal data | General (SOTA baseline) | Multivariate with variable interactions |

**Layman Example:**
- **Vanilla Transformer on time series:** Like reading a very long book word-by-word and comparing every word to every other word (O(n²)). For 1000 time steps, that's 1,000,000 comparisons.
- **PatchTST:** Instead of word-by-word, read paragraph-by-paragraph (patches). Each "paragraph" is a chunk of consecutive time steps. Now you only compare paragraphs to each other — much fewer comparisons, and each paragraph already contains useful local context.
- **iTransformer:** Instead of asking "how does time step 5 relate to time step 100?", ask "how does temperature relate to humidity relate to pressure?" Attention flows across variables, not time steps.

**Follow-up Questions:**

**Q: Why did simple models (DLinear) outperform early Transformer forecasters?**
A: Zeng et al. (2023) "Are Transformers Effective for Time Series Forecasting?" showed a simple linear layer matching/beating Informer, Autoformer, etc. Reasons: (1) Time series have strong temporal ordering that attention doesn't inherently exploit, (2) Overfitting on limited time series data, (3) Permutation-invariant attention loses temporal inductive bias, (4) Simple models with right preprocessing capture dominant linear trends. PatchTST and later models fixed this by adding proper temporal structure.

**Q: What is channel-independent processing and why does it help?**
A: Process each variable (channel) through the same Transformer independently, rather than mixing all variables into one token per time step. Benefits: (1) Prevents overfitting on spurious cross-variable correlations, (2) Shares model parameters across variables (parameter efficiency), (3) Works well when variables have independent patterns. Exception: use channel-dependent (iTransformer) when variable interactions are the key signal.

**Q: How does Autoformer's Auto-Correlation work?**
A: Instead of standard attention (point-to-point), Autoformer computes correlation between time-lagged sub-series. It identifies the most relevant lag (period) and aggregates the rolled series at that period. This naturally captures periodicity — instead of "which time step is relevant?", it asks "which period/cycle is this most similar to?" Built-in decomposition (trend + seasonal) at each layer.

**Q: When should you use a Transformer vs. simpler models for forecasting?**
A: Transformer: (1) Long forecast horizons (100+ steps), (2) Complex multi-scale patterns, (3) Sufficient data (large training set), (4) Pre-trained foundation model available. Simpler models (linear, N-BEATS): (1) Short horizons, (2) Limited data, (3) Need interpretability, (4) Computational constraints, (5) Strong single-seasonality patterns.

---

## 7. N-BEATS, N-HiTS, and MLP-based Models

**Answer:**
MLP-based architectures for forecasting challenge the assumption that complex architectures (RNN, Transformer) are necessary. They use simple fully-connected layers with specific structural designs and achieve state-of-the-art results.

**Key Models:**

| Model | Year | Architecture | Key Idea |
|-------|------|-------------|----------|
| N-BEATS | 2020 | Stack of FC blocks with residual | Basis expansion + doubly residual |
| N-HiTS | 2022 | Multi-rate N-BEATS | Hierarchical interpolation, multi-scale |
| DLinear | 2023 | One linear layer (!) | Decompose + linear per component |
| TSMixer | 2023 | MLP-Mixer for time series | Mix time + feature dimensions alternately |
| TiDE | 2023 | Dense encoder-decoder | Simple, efficient, competitive |

**N-BEATS Architecture:**
```
Stack structure:
Input lookback → [Block 1] → residual → [Block 2] → residual → ... → [Block N]

Each block:
- FC layers → θ (basis coefficients)
- Backcast: reconstruct input (what this block "explains")
- Forecast: predict future (this block's contribution to forecast)
- Residual: input - backcast → pass to next block

Final forecast = sum of all blocks' forecasts

Interpretable version: restrict basis to trend (polynomial) + seasonality (Fourier)
Generic version: any learned basis
```

**N-HiTS Improvement:**
```
Like N-BEATS but each stack operates at different temporal resolution:
- Stack 1: Fine resolution (daily patterns) — looks at recent history
- Stack 2: Medium resolution (weekly) — looks at longer history, downsampled
- Stack 3: Coarse resolution (monthly) — looks at very long history, heavily downsampled

Each stack predicts at its natural frequency, then interpolates to target resolution.
→ More efficient + better multi-scale pattern capture
```

**DLinear:**
```
Input → Decompose into Trend + Seasonal (moving average)
Trend → Linear layer → Trend forecast
Seasonal → Linear layer → Seasonal forecast
Output = Trend forecast + Seasonal forecast

That's it. One linear layer per component. Embarrassingly simple but competitive.
```

**Comparison:**

| Model | Parameters | Training speed | Accuracy | Interpretability |
|-------|-----------|---------------|----------|-----------------|
| N-BEATS (generic) | Medium | Fast | High | Low |
| N-BEATS (interpretable) | Medium | Fast | Good | High (trend + season decomp) |
| N-HiTS | Medium | Fast | Higher | Medium (multi-scale) |
| DLinear | Very few | Very fast | Surprisingly competitive | High |
| TSMixer | Low-Medium | Fast | High | Medium |

**Layman Example:**
N-BEATS = a team of specialists working on leftovers:
1. First specialist looks at sales data and says: "I can explain the overall upward trend — let me extract that." (Block 1 produces trend forecast + removes trend from data)
2. Second specialist: "In the remaining data, I see weekly seasonality — let me extract that." (Block 2 handles seasonality)
3. Third specialist: "In what's left, I see holiday spikes — let me handle that." (Block 3)
4. Final forecast = sum of all specialists' contributions

Each specialist works on the "residual" (what previous specialists couldn't explain).

**Follow-up Questions:**

**Q: Why do simple linear models compete with Transformers?**
A: (1) Time series often have strong linear components (trends, simple seasonality) that linear layers capture directly. (2) Complex models overfit on limited time series data. (3) The temporal structure (autocorrelation) is well-captured by lookback windows + linear mapping. (4) Decomposition (trend/seasonal separation) handles most of the complexity before the model even sees the data.

**Q: What is the "doubly residual" architecture in N-BEATS?**
A: Two types of residuals: (1) Between blocks: each block gets the residual of what previous blocks couldn't explain (like boosting). (2) Within block: backcast tries to reconstruct the input — what it CAN reconstruct is "understood," the residual passes forward. This creates a natural decomposition where each block captures different patterns.

**Q: How does N-BEATS achieve interpretability?**
A: In the interpretable configuration, blocks are constrained to specific basis functions: (1) Trend blocks: polynomial basis (constant, linear, quadratic), (2) Seasonal blocks: Fourier basis (sin/cos at different frequencies). You can visualize each block's output to see exactly what trend/seasonal pattern it captured. Generic blocks (unrestricted FC layers) trade interpretability for flexibility.

**Q: When should you use N-BEATS/N-HiTS vs. a Transformer model?**
A: N-BEATS/N-HiTS: (1) Univariate or few-variable forecasting, (2) When you need interpretable decomposition, (3) Strong baseline that's hard to beat, (4) Computational efficiency. Transformer: (1) Many variables with complex interactions, (2) Very long context needed, (3) Pre-trained model available (zero-shot), (4) When attention patterns over time are informative.

---

## 8. Temporal Fusion Transformer (TFT)

**Answer:**
TFT (Google, 2021) is a multi-horizon forecasting model that handles different types of inputs (static, known future, observed past), provides interpretability through attention and variable selection, and produces quantile forecasts.

**Architecture:**
```
Inputs (3 types):
├── Static metadata (e.g., store_id, product_category)
├── Known future inputs (e.g., day_of_week, planned_promotion, holiday)
└── Past observed inputs (e.g., past sales, past weather)

Processing:
1. Variable Selection Networks — learn which inputs matter (per time step)
2. Static Covariate Encoders — create context vectors from static data
3. LSTM Encoder (past) + LSTM Decoder (future) — temporal processing
4. Multi-Head Attention — attend to relevant past time steps
5. Gated Residual Networks — control information flow
6. Quantile outputs — predict 10th, 50th, 90th percentiles

Key outputs:
- Multi-horizon quantile forecasts
- Variable importance scores (which inputs matter)
- Temporal attention weights (which past time steps matter)
```

**Input Types:**

| Input type | Description | Example | Available when |
|------------|-------------|---------|----------------|
| Static | Time-invariant metadata | Product ID, store location, category | Always |
| Known future | Deterministic future values | Calendar features, planned events | Past + future |
| Past observed | Historical values, not available in future | Past sales, past weather observations | Past only |
| Target | What we're predicting | Sales, demand, price | Past only |

**Layman Example:**
TFT = a smart sales forecaster at a retail company who:
1. **Knows the store** (static): "This is a downtown store, open since 2015, category: electronics"
2. **Sees the calendar** (known future): "Next week has a holiday on Tuesday, a promotion planned for Thursday"
3. **Remembers the past** (observed): "Last month sales were 500/day, foot traffic was declining, a competitor opened nearby"
4. **Decides what matters** (variable selection): "For THIS store, promotions drive sales more than weather"
5. **Focuses attention** (temporal attention): "Black Friday last year is most relevant for predicting this Black Friday"
6. **Gives a range** (quantiles): "I predict 400-600 units (80% confidence), most likely 480"

**Follow-up Questions:**

**Q: What is the Variable Selection Network and why is it important?**
A: VSN applies softmax-weighted gating to input features, learning which variables are relevant for each time step and entity. Importance: (1) Automatic feature selection (no manual engineering), (2) Provides interpretability (which features drive forecasts), (3) Handles heterogeneous input types, (4) Reduces overfitting from irrelevant features. Output: per-variable importance scores that can be visualized.

**Q: How does TFT handle the known future vs. past observed distinction?**
A: Past observed inputs (like actual weather) are processed by the encoder LSTM only — they're used to build historical context but can't be used for future predictions. Known future inputs (like calendar features) are processed by both encoder and decoder — they're available for the forecast period. This prevents data leakage while maximally using available information.

**Q: What are Gated Residual Networks (GRN)?**
A: GRN = LayerNorm(a + GLU(W₁·η₁ + W₂·c + b)), where η₁ = ELU(W₃·a + W₄·c + b₃), c is optional context, and GLU provides gating. Purpose: control information flow — if a component isn't needed, the gate suppresses it (like highway networks). Used throughout TFT to provide flexibility without overfitting.

**Q: When should you use TFT vs. simpler models?**
A: TFT: (1) Rich metadata and covariates available, (2) Need interpretability (which variables matter, when), (3) Multiple related series with static differences, (4) Known future events matter for prediction, (5) Need probabilistic forecasts. Simpler: (1) Univariate with no covariates, (2) Very short series, (3) Computational constraints, (4) When TFT overfits due to limited data.

---

## 9. Foundation Models for Time Series

**Answer:**
Foundation models for time series are large pre-trained models that can forecast any time series zero-shot (without task-specific training). They're trained on massive collections of diverse time series and learn universal temporal patterns.

**Key Models:**

| Model | Organization | Training data | Key innovation | Year |
|-------|-------------|---------------|----------------|------|
| TimeGPT | Nixtla | 100B+ time points, diverse domains | First commercial time series FM | 2023 |
| Chronos | Amazon | Diverse public + synthetic data | Tokenize real values, train as LM | 2024 |
| TimesFM | Google | 100B+ time points from Google data | Decoder-only, input patching | 2024 |
| Moirai | Salesforce | LOTSA (27B observations, 9 domains) | Any-variate, multi-resolution | 2024 |
| MOMENT | CMU | Multiple domains | Pre-trained then fine-tuned | 2024 |
| Lag-LLaMA | ServiceNow | Public TS datasets | Augmented with lag features | 2024 |
| Timer | Tsinghua | Large-scale pre-training | Generative pre-training for TS | 2024 |

**Chronos Architecture:**
```
1. Tokenization: Bin real-valued time series into discrete tokens
   - Mean-scale normalize each series
   - Quantize into B bins (like converting audio to discrete levels)
   
2. Model: T5-like encoder-decoder (or decoder-only)
   - Treat tokenized time series as "language"
   - Train with next-token prediction (cross-entropy loss)
   
3. Inference: Generate future tokens autoregressively
   - Sample multiple paths → probabilistic forecast
   - Map tokens back to continuous values

Key insight: By tokenizing, you can use standard language model architectures and training
```

**TimesFM Architecture:**
```
1. Input: Patch time series into fixed-size segments
2. Model: Decoder-only Transformer (like GPT for time series)
3. Masking: Causal masking over patches (predict next patch from previous)
4. Output: Predict next patch of values
5. Trained on: Internal Google data (search trends, finance, weather, etc.)
```

**Comparison with task-specific models:**

| Aspect | Task-specific (train from scratch) | Foundation model (zero-shot) | Foundation + fine-tune |
|--------|-----------------------------------|------------------------------|----------------------|
| Training data needed | Thousands of observations | Zero | Few hundred |
| Setup time | Hours-days | Minutes (API call) | Hours |
| Accuracy (general) | Depends on data | Good baseline | Often best |
| Accuracy (specialized domain) | Best with enough data | May underperform | Competitive |
| Computational cost | Training cost | Inference only | Low fine-tune cost |
| Cold start | Poor | Good | Good |

**Layman Example:**
- **Task-specific model:** Like learning to forecast ONLY ice cream sales for your one store. You need years of data from that exact store.
- **Foundation model (zero-shot):** Like asking a forecasting expert who's studied millions of businesses across all industries. They've never seen your specific store, but they know patterns: "Ah, seasonal food product, retail, temperate climate — I've seen this pattern 10,000 times. Here's my prediction."
- **Foundation + fine-tune:** Same expert, but you show them your last 3 months of data: "Adjust for this — your store is near a school, so you have an extra spike when school ends."

**Follow-up Questions:**

**Q: How does Chronos convert continuous time series into tokens?**
A: (1) Normalize each series (subtract mean, divide by mean absolute value — handles different scales). (2) Quantize normalized values into B discrete bins (e.g., B=4096). Bin edges are uniformly or quantile-spaced. (3) Map each bin to a token ID. Now it's a discrete sequence → standard language model training applies. (4) At inference: predicted token probabilities over bins → reconstruct continuous distribution.

**Q: What are the limitations of current time series foundation models?**
A: (1) Mostly univariate (don't handle multivariate well yet), (2) Limited ability to incorporate exogenous variables (holidays, promotions), (3) May underperform specialized models for domain-specific patterns, (4) Struggle with distribution shift (training data domains vs. target domain), (5) Lack of interpretability. (6) Context length limitations for very long seasonal patterns.

**Q: How do you decide between zero-shot foundation model vs. training your own?**
A: Zero-shot: (1) Quick prototyping, (2) Cold start (new product, no history), (3) Many diverse series with limited data each, (4) When "good enough" is acceptable. Train your own: (1) Specialized domain with enough data, (2) Specific covariates/features needed, (3) Maximum accuracy required, (4) Latency/cost constraints (smaller model). Best: try zero-shot first as baseline, fine-tune if needed.

---

## 10. Probabilistic Forecasting

**Answer:**
Probabilistic forecasting predicts a distribution (or quantiles/intervals) rather than a single point. It quantifies uncertainty in predictions, which is critical for decision-making under uncertainty.

**Types of Uncertainty:**

| Type | Source | Example | Reducible? |
|------|--------|---------|------------|
| Aleatoric (data) | Inherent randomness in process | Customer demand variability | No |
| Epistemic (model) | Insufficient knowledge/data | New product with no history | Yes (with more data) |
| Distribution shift | Future differs from past | COVID changing demand patterns | Partially |

**Approaches:**

| Method | Output | How it works | Model example |
|--------|--------|-------------|---------------|
| Quantile regression | Specific percentiles (10th, 50th, 90th) | Separate loss per quantile | DeepAR (quantile mode), TFT |
| Parametric distribution | Distribution parameters (μ, σ, ν) | Assume distribution family, predict params | DeepAR (Gaussian/NegBin) |
| Normalizing flows | Flexible learned distribution | Transform simple dist → complex | TimeGrad |
| Monte Carlo dropout | Ensemble of stochastic forward passes | Dropout at test time, multiple samples | Any model + MC dropout |
| Conformal prediction | Distribution-free prediction intervals | Calibrated intervals from residuals | Any model + conformal wrapper |
| Ensemble | Multiple model predictions | Train N models, use spread as uncertainty | Any ensemble |
| Diffusion-based | Sample from learned denoising process | Denoise random noise to forecast | TimeGrad, CSDI |

**Quantile Loss:**
$$L_q(y, \hat{y}_q) = \max[q(y - \hat{y}_q), (q-1)(y - \hat{y}_q)]$$
- For q=0.9: penalizes under-prediction (y > ŷ) more than over-prediction
- For q=0.1: penalizes over-prediction more

**Evaluation Metrics for Probabilistic Forecasts:**

| Metric | What it measures | Lower is better? |
|--------|-----------------|-----------------|
| CRPS | Average quality of predicted CDF | Yes |
| Quantile Loss (QL) | Calibration of specific quantiles | Yes |
| Coverage | % of true values within prediction interval | Target = nominal (e.g., 90%) |
| Winkler Score | Interval width + penalty for misses | Yes |
| Calibration | Do 90% intervals actually contain 90% of truth? | Should be ~0 deviation |
| Sharpness | Width of prediction intervals | Yes (conditional on calibration) |

**Layman Example:**
A weather forecast:
- **Point forecast:** "Tomorrow will be 75°F." (Useful but incomplete — how confident?)
- **Probabilistic forecast:** "Tomorrow: 70-80°F with 80% confidence, 65-85°F with 95% confidence." Now you know:
  - Most likely: ~75°F
  - Very unlikely below 65°F (plan accordingly)
  - Uncertainty is moderate (±5°F for 80% CI)

For inventory: "Predict demand = 100 units" → might stock 100 and run out 50% of the time. "Predict 90th percentile = 130 units" → stock 130 and only run out 10% of the time.

**Follow-up Questions:**

**Q: What is CRPS and why is it preferred over MSE for probabilistic forecasts?**
A: CRPS (Continuous Ranked Probability Score) measures the quality of the entire predicted distribution, not just one point. CRPS = integral of (CDF_predicted(x) - CDF_actual(x))² dx. It reduces to MAE for point forecasts. It's a proper scoring rule (optimal when predicted distribution = true distribution) and evaluates both calibration and sharpness simultaneously.

**Q: What is the difference between quantile regression and predicting distribution parameters?**
A: Quantile regression: directly predict specific percentiles (10th, 50th, 90th) with separate loss for each. Flexible (no distributional assumption) but quantiles may cross (10th > 50th — invalid). Parametric: predict μ, σ (and possibly shape params) of assumed distribution (Gaussian, Student-t, Negative Binomial). Consistent by construction but relies on distributional assumption being correct.

**Q: What is conformal prediction and why is it appealing?**
A: Conformal prediction wraps ANY forecasting model to provide guaranteed coverage intervals. Algorithm: (1) Train model on training data, (2) Compute residuals on calibration set, (3) Use quantile of |residuals| to set interval width. Guarantee: if calibration data is exchangeable with test data, the interval covers the true value at exactly the specified rate (e.g., 90%). Distribution-free — no assumptions about error distribution.

**Q: How do you evaluate if a probabilistic forecast is well-calibrated?**
A: Plot empirical coverage vs. nominal level: for p=10%, 20%, ..., 90%, check what fraction of true values actually fall within the p% prediction interval. A perfectly calibrated model shows a diagonal. Under-confident (too wide intervals): empirical > nominal. Over-confident (too narrow): empirical < nominal. Use reliability diagrams (PIT histograms).

---

## 11. Multi-Horizon and Multi-Step Forecasting

**Answer:**
Multi-horizon forecasting predicts multiple future time steps simultaneously. The approach to handling this significantly affects accuracy, especially for long horizons.

**Strategies:**

| Strategy | How it works | Pros | Cons |
|----------|-------------|------|------|
| Recursive (iterated) | Predict t+1, feed back as input, predict t+2, ... | Uses temporal dependencies | Error accumulation |
| Direct | Train separate model for each horizon | No error accumulation | Ignores inter-horizon correlations, N models |
| MIMO (Multi-Input Multi-Output) | Single model outputs all horizons at once | Fast, captures correlations | Fixed horizon length |
| DirRec (Hybrid) | Direct prediction + use predictions as features for later horizons | Reduced error accumulation + some temporal structure | Complex |
| Seq2Seq | Encoder processes past, decoder generates future step-by-step | Flexible, attention over past | Slower training |

**Error Accumulation in Recursive:**
```
Step 1: ŷ₁ = f(y_past) + ε₁         — small error
Step 2: ŷ₂ = f(..., ŷ₁) + ε₂        — error from ε₁ propagates
Step 3: ŷ₃ = f(..., ŷ₁, ŷ₂) + ε₃    — errors from ε₁, ε₂ propagate
...
Step H: ŷ_H = f(...) + accumulated errors  — potentially large error
```

**Multi-Scale Forecasting:**
```
Predict at multiple granularities simultaneously:
- Daily sales (next 7 days)
- Weekly totals (next 4 weeks)  
- Monthly totals (next 3 months)

Ensure consistency: daily predictions should sum to weekly, which sum to monthly.
Approaches: hierarchical forecasting, reconciliation methods.
```

**Layman Example:**
Predicting weather for the next week:
- **Recursive:** Predict Monday → use Monday's prediction to predict Tuesday → use both to predict Wednesday... By Friday, errors from Monday have compounded. Like a game of telephone.
- **Direct:** Build separate models for "1 day ahead," "2 days ahead," etc. More accurate per step but ignores that Tuesday's weather depends on Monday's.
- **MIMO:** One model looks at history and simultaneously outputs Mon, Tue, Wed, Thu, Fri all at once. Captures the relationships between days without error accumulation.

**Follow-up Questions:**

**Q: Which multi-step strategy is best?**
A: Depends on horizon and data: (1) Short horizon (1-5 steps): Recursive is fine (little accumulation). (2) Medium horizon (5-50): MIMO or Seq2Seq (captures correlations without much accumulation). (3) Long horizon (50+): Direct or MIMO (error accumulation becomes dominant). In practice: modern deep learning models (TFT, PatchTST, N-BEATS) use MIMO — predict entire horizon in one shot.

**Q: What is hierarchical forecasting?**
A: Forecasting at multiple aggregation levels (product → category → total, or daily → weekly → monthly) and ensuring consistency. Methods: (1) Top-down (forecast total, disaggregate), (2) Bottom-up (forecast finest level, sum up), (3) Optimal reconciliation (MinT — find minimum trace combination that satisfies constraints). Modern: predict all levels independently then reconcile.

**Q: How does the forecast horizon affect model choice?**
A: Short-term (1-7 steps): Simple models (ARIMA, ETS) often suffice, strong autocorrelation drives prediction. Medium-term (1-4 weeks): Deep learning shines (captures complex patterns, covariates matter). Long-term (months-years): Trend and seasonality dominate, simpler models with proper decomposition may be better. Foundation models attempt to handle all horizons.

---

## 12. Anomaly Detection in Time Series

**Answer:**
Time series anomaly detection identifies unusual patterns that deviate from expected behavior. Types: point anomalies (single unusual values), contextual anomalies (normal values in wrong context), and collective anomalies (unusual subsequences).

**Approaches:**

| Category | Methods | How it works | Best for |
|----------|---------|-------------|----------|
| Statistical | Z-score, IQR, Grubbs' test | Detect values outside expected range | Simple point anomalies |
| Forecast-based | ARIMA residuals, Prophet anomalies | Detect deviations from forecast | Contextual anomalies |
| Reconstruction | Autoencoder, VAE | High reconstruction error = anomaly | Complex patterns |
| Density-based | Isolation Forest, LOF | Low density regions = anomaly | Point/collective |
| Self-supervised | Contrastive learning | Dissimilar to normal patterns | Unlabeled data |
| Transformer-based | Anomaly Transformer | Attention-based association discrepancy | Complex temporal anomalies |
| Foundation model | TimeGPT anomaly, Chronos | Zero-shot anomaly scoring | Quick deployment |

**Reconstruction-based (Autoencoder):**
```
Training (on normal data only):
  Normal time series → Encoder → Latent → Decoder → Reconstruction
  Loss = ||input - reconstruction||²   (learns to reconstruct normal patterns)

Inference:
  New data → Encoder → Decoder → Reconstruction
  Anomaly score = reconstruction error
  High error → model can't reconstruct → data is abnormal
```

**Evaluation Metrics:**

| Metric | Challenge for time series |
|--------|--------------------------|
| Precision/Recall/F1 | Need point-level labels (expensive) |
| Point-adjusted F1 | If ANY point in anomaly segment is detected → count as TP |
| Range-based metrics | Credit partial detection of anomaly ranges |
| AUC-ROC | Threshold-independent |
| Delay | How quickly anomaly is detected after it starts |
| False alarm rate | Critical for production (alert fatigue) |

**Layman Example:**
A security camera watching a parking lot:
- **Point anomaly:** A car suddenly appearing where there was none (abrupt spike in sensor data)
- **Contextual anomaly:** Normal traffic at 3 PM is fine, same traffic at 3 AM is suspicious (normal value, wrong context)
- **Collective anomaly:** A slow, gradual encroachment that looks normal second-by-second but is clearly unusual over minutes

The autoencoder approach: "I've seen thousands of normal hours of footage. When I try to 'compress and reconstruct' this new footage and can't — something unusual is happening."

**Follow-up Questions:**

**Q: Why is anomaly detection hard in time series?**
A: (1) Anomalies are rare (extreme class imbalance — often <0.1%), (2) Normal behavior changes over time (concept drift), (3) Seasonal patterns create complex "normal" — high demand at Christmas is normal, same demand in February isn't, (4) Labeling anomalies is expensive and subjective, (5) Different stakeholders may define "anomaly" differently.

**Q: How do you handle concept drift in anomaly detection?**
A: (1) Sliding window retraining (update model on recent normal data), (2) Online/incremental learning (continuous adaptation), (3) Ensemble with decay (older models get less weight), (4) Explicit changepoint detection (reset model when distribution changes), (5) Adaptive thresholds (recalculate threshold statistics regularly).

**Q: What is the difference between anomaly detection and changepoint detection?**
A: Anomaly detection: find unusual individual points/segments that deviate from normal. The system returns to normal after the anomaly. Changepoint detection: find points where the statistical properties permanently change (new mean, new variance, new trend). After a changepoint, the new behavior IS the new normal. Different goals, sometimes overlapping tools.

---

## 13. Time Series Classification

**Answer:**
Time series classification assigns a label to an entire time series sequence (or subsequence). Applications: activity recognition (accelerometer → walking/running), medical diagnosis (ECG → arrhythmia type), industrial fault detection (sensor → fault type).

**Approaches:**

| Method | Architecture | Key Idea | Accuracy |
|--------|-------------|----------|----------|
| DTW + KNN | Distance-based | Elastic distance matching | Strong baseline |
| ROCKET/MiniRocket | Random convolutional kernels | 10,000 random features → linear classifier | SOTA efficiency |
| InceptionTime | CNN ensemble | Multiple kernel sizes (like Inception for images) | Strong |
| ResNet-1D | 1D ResNet | Residual connections for deep 1D CNN | Good |
| TimesNet | 2D CNN on reshaped time series | Convert 1D → 2D (period × intra-period) | Strong |
| HIVE-COTE 2 | Ensemble of diverse classifiers | Combines distance, shapelet, frequency, interval | Best accuracy (slow) |
| Foundation model | Pre-trained + fine-tune | Transfer temporal features | Emerging |

**ROCKET (Random Convolutional Kernel Transform):**
```
1. Generate 10,000 random 1D convolutional kernels
   - Random length (7-11)
   - Random weights
   - Random dilation
   - Random bias
2. Apply each kernel to the input time series
3. Extract 2 features per kernel: max value + proportion of positive values
4. → 20,000 features
5. Feed to Ridge classifier (linear)

Why it works: Random projections to high-dimensional space → linear separability
Extremely fast to train (no neural network optimization)
```

**Comparison:**

| Aspect | DTW + KNN | ROCKET | InceptionTime | Transformer |
|--------|-----------|--------|---------------|-------------|
| Training speed | None (lazy) | Very fast | Moderate | Moderate |
| Inference speed | Slow (compute distance to all training) | Fast | Fast | Fast |
| Accuracy | Good baseline | Near-SOTA | Near-SOTA | Good |
| Interpretability | High (show nearest neighbor) | Low | Low | Attention maps |
| Multivariate | Requires adaptation | Natural | Natural | Natural |
| Memory | Stores all training data | Stores features + linear model | Stores model | Stores model |

**Follow-up Questions:**

**Q: What is Dynamic Time Warping (DTW)?**
A: DTW measures similarity between time series of different lengths/speeds by finding the optimal alignment (warping path). It stretches/compresses one series to best match the other. Unlike Euclidean distance (point-by-point), DTW handles temporal shifts: if two heartbeat signals have the same shape but different speeds, DTW recognizes them as similar. Complexity: O(n²) but can be pruned.

**Q: Why is ROCKET so effective despite using random kernels?**
A: The Johnson-Lindenstrauss lemma: random projections to high-dimensional space preserve distances. With 10,000 random kernels of various dilations and lengths, you're likely to "randomly discover" the discriminative patterns. The linear classifier then selects which random features actually matter. Similar to Random Kitchen Sinks for kernel approximation. MiniRocket further speeds this up by restricting kernel weights to {-1, 0, 1}.

**Q: How do you handle variable-length time series?**
A: (1) Padding (pad shorter with zeros) + masking, (2) Truncation (cut to fixed length), (3) Resampling (interpolate to fixed length), (4) Time-warp invariant methods (DTW handles naturally), (5) Adaptive pooling (pool to fixed size regardless of input), (6) Set-based methods (treat time steps as unordered set with positional encoding).

---

## 14. Multivariate Time Series Forecasting

**Answer:**
Multivariate forecasting predicts multiple related time series simultaneously, capturing cross-variable dependencies. Key challenge: modeling temporal patterns WITHIN each variable AND relationships BETWEEN variables.

**Approaches:**

| Strategy | How variables are handled | Example models | When to use |
|----------|--------------------------|----------------|-------------|
| Channel-independent | Each variable modeled separately (shared model) | PatchTST, DLinear | When cross-variable info doesn't help |
| Channel-dependent | All variables modeled jointly | iTransformer, Crossformer | When variable interactions matter |
| Graph-based | Variables as nodes, learn graph structure | MTGNN, StemGNN | When spatial/relational structure exists |
| Attention across variables | Cross-variable attention | iTransformer, Crossformer | Complex dependencies |
| Correlation modeling | Explicit correlation matrix | LSTNet, DCRNN | Traffic, spatial networks |

**iTransformer Approach:**
```
Traditional: Each time step = one token (attention across time)
iTransformer: Each variable = one token (attention across variables!)

Input: N variables × T time steps
→ Embed each variable's full time series into one token
→ Self-attention across N variable tokens (learn relationships)
→ Predict future values for each variable

Why: Often variable relationships are the key signal
  (temperature affects humidity, traffic on one road affects neighboring roads)
```

**Graph Neural Network Approach:**
```
Variables as nodes in a graph:
- Traffic sensors: geographic proximity → graph edges
- IoT sensors: physical connections → graph
- Financial instruments: correlation → graph

Model: GNN propagates information along edges + temporal model within nodes
Examples: DCRNN (diffusion convolution), STGCN (spatial-temporal GCN), MTGNN (learned graph)
```

**Comparison: Channel-Independent vs. Channel-Dependent:**

| Aspect | Channel-Independent | Channel-Dependent |
|--------|--------------------|--------------------|
| Cross-variable learning | No | Yes |
| Overfitting risk | Lower (fewer params) | Higher |
| Works when variables are... | Independent or weakly correlated | Strongly correlated/causal |
| Parameter efficiency | High (shared across channels) | Lower |
| Scalability (many variables) | Excellent | May struggle |
| Example | PatchTST: one Transformer shared across all variables | iTransformer: attention between variables |

**Layman Example:**
Forecasting energy consumption across 100 buildings:
- **Channel-independent:** Forecast each building separately using the same model architecture. Like 100 separate weather forecasts — each uses only its own history.
- **Channel-dependent:** Model ALL buildings together. "When the office building on 5th St. turns off at 6 PM, the restaurant next door sees increased load (people leave work, go eat)." Cross-building relationships captured.
- **Graph-based:** Connect buildings by proximity and shared grid. Information flows along power grid connections — a transformer failure affecting one cluster is predicted to ripple to connected buildings.

**Follow-up Questions:**

**Q: When does channel-independent outperform channel-dependent?**
A: (1) When variables are actually independent or weakly related, (2) When dataset is small (fewer parameters = less overfitting), (3) When the number of variables is large (quadratic attention across 1000 variables is expensive), (4) When variables have very different scales/patterns (joint modeling may compromise individual accuracy). PatchTST showed channel-independent often wins on standard benchmarks — surprising but consistent finding.

**Q: How do you handle different sampling frequencies across variables?**
A: (1) Resample all to common frequency (interpolation for upsampling, aggregation for downsampling), (2) Multi-resolution models (N-HiTS, separate encoders per frequency), (3) Masking (missing values at fine resolution where coarse variable hasn't updated), (4) Irregularly-sampled time series methods (Neural ODE, attention with continuous time encoding).

**Q: What is Granger causality and how is it useful?**
A: Variable X "Granger-causes" Y if past values of X improve prediction of Y beyond Y's own past. It's a statistical test for predictive causality (not true causality). Useful for: (1) Feature selection (which variables to include), (2) Graph structure learning (directed edges based on Granger causality), (3) Understanding dependencies. Deep learning extension: use attention weights or ablation to approximate Granger causality.

---

## 15. Spatial-Temporal Forecasting (Traffic, Weather)

**Answer:**
Spatial-temporal forecasting predicts values that vary across both space and time. Applications: traffic flow, weather prediction, air quality, epidemic spread, ride-sharing demand. The key challenge is jointly modeling spatial correlations and temporal dynamics.

**Architecture Patterns:**

| Pattern | Spatial component | Temporal component | Example |
|---------|-------------------|-------------------|---------|
| ST-GNN | Graph convolution | RNN/TCN/Transformer | DCRNN, STGCN |
| Spatial attention | Cross-location attention | Temporal attention | GMAN, ASTGCN |
| Factorized | Separate spatial and temporal models | Combined at output | STNorm |
| ConvLSTM | 2D convolution (grid) | Recurrent | ConvLSTM (weather) |
| Vision Transformer | Spatial patches | Temporal patches | ViTs for weather (Pangu-Weather) |
| Physics-informed | Neural operators + physics constraints | Time integration | FourCastNet, GraphCast |

**Traffic Forecasting (Canonical Problem):**
```
Input: N road sensors × T historical time steps
Output: N sensors × H future time steps

Graph structure: road network (sensors connected by roads)
Key relationships:
- Upstream traffic → downstream traffic (directional flow)
- Time of day determines traffic pattern
- Incidents propagate spatially over time

Model (DCRNN):
- Diffusion convolution on road graph (captures spatial propagation)
- GRU for temporal evolution
- Seq2seq architecture (encode history, decode future)
```

**AI Weather Prediction (2023+ breakthrough):**

| Model | Organization | Innovation | Resolution |
|-------|-------------|-----------|------------|
| Pangu-Weather | Huawei | 3D Earth Transformer, multi-resolution | 0.25° |
| GraphCast | DeepMind | GNN on multi-mesh (icosahedral) | 0.25° |
| FourCastNet | NVIDIA | Fourier Neural Operator | 0.25° |
| GenCast | DeepMind | Diffusion model for ensemble forecasts | 0.25° |
| Aurora | Microsoft | Foundation model for Earth sciences | 0.1° |

```
These models: Trained on ERA5 reanalysis (40 years of global weather)
Performance: Match or exceed NWP (numerical weather prediction) for 10-day forecasts
Speed: 1 minute (AI) vs. 1 hour (NWP on supercomputer)
```

**Layman Example:**
Predicting traffic in a city:
- **Temporal only:** "This highway had 500 cars/hour at 8 AM every weekday for the past month → predict 500 cars/hour tomorrow at 8 AM."
- **Spatial-temporal:** "This highway connects to the downtown exit ramp. Traffic entering downtown 15 minutes ago is now arriving at your sensor. There's also a sports event at the nearby stadium ending at 8 PM, which will affect all surrounding sensors 30 minutes later." The spatial structure (road network) tells you WHERE information flows, temporal modeling tells you WHEN.

**Follow-up Questions:**

**Q: How do GNNs handle the spatial component?**
A: GNNs propagate information along graph edges. For traffic: node = sensor, edge = road connection. Graph convolution: each node's representation = weighted sum of its neighbors' representations. Multiple layers = information propagates multiple hops. Diffusion convolution (DCRNN) models random walks on the graph — capturing how traffic "flows" through the network. Attention-based GNNs learn edge weights dynamically.

**Q: Why did AI weather models beat NWP in 2023?**
A: (1) Trained on 40 years of global reanalysis data (enormous training set), (2) Learn complex non-linear relationships that physical equations approximate, (3) Can be run in seconds vs. hours, (4) Capture data-driven corrections to model biases. Limitations: struggle with extreme events (rare in training data), don't respect physical laws (can violate conservation), less interpretable than physics-based models.

**Q: What is the difference between structured (grid) and unstructured (graph) spatial data?**
A: Grid: regular spatial arrangement (weather on lat/lon grid, image pixels). Use 2D convolutions or ViT patches. Graph: irregular arrangement (traffic sensors at intersections, weather stations at arbitrary locations). Use GNNs. Some problems can use both: GraphCast uses an icosahedral mesh (graph) to represent the Earth's surface more uniformly than lat/lon grids.

---

## 16. Demand Forecasting (Retail, Supply Chain)

**Answer:**
Demand forecasting predicts future product demand for inventory planning, production scheduling, and supply chain optimization. Unique challenges: intermittent demand, promotions, new product launches, and hierarchical aggregation.

**Challenges specific to demand forecasting:**

| Challenge | Description | Solution |
|-----------|-------------|----------|
| Intermittent demand | Many zeros (slow-moving items) | Croston's method, Zero-inflated models |
| Promotion effects | Price changes, advertising, displays | Include as known future covariates |
| New product cold start | No history available | Transfer from similar products, foundation models |
| Cannibalization | Product A promotion reduces Product B demand | Cross-product modeling |
| Stockout censoring | Observed sales ≠ true demand (can't sell what's not stocked) | Censored demand estimation |
| Hierarchy consistency | Product-store-day must align with category-region-week | Hierarchical reconciliation |
| Long tail | 80% of revenue from 20% of products; rest is sparse | Different models for head vs. tail |

**Production Pipeline:**

```
Data: Sales history + Promotions + Calendar + Weather + Pricing + Inventory
  ↓
Feature engineering: Lags, rolling stats, time features, promotion encoding
  ↓
Model training: Global model across all products/stores (cross-learning)
  ↓
Hierarchical reconciliation: Ensure consistency across levels
  ↓
Business rules: Minimum order quantities, shelf life, supplier constraints
  ↓
Output: Order recommendations with safety stock
```

**Models used in industry:**

| Company | Approach | Scale |
|---------|----------|-------|
| Amazon | DeepAR, probabilistic forecasting | Millions of products |
| Walmart | LightGBM + deep learning ensemble | Millions |
| Uber (Eats) | DeepAR-like + real-time features | Thousands of restaurants × hours |
| Google (Cloud) | TimesFM (foundation model) | General |
| Instacart | Gradient boosting + embeddings | Millions |

**Evaluation for demand forecasting:**

| Metric | Formula | Why used |
|--------|---------|----------|
| MAPE | Mean |actual-predicted|/actual × 100 | Interpretable %, BUT undefined for 0s |
| WMAPE | Sum|actual-predicted|/Sum(actual) × 100 | Handles zeros, volume-weighted |
| MASE | MAE / MAE_naive (seasonal naive) | Scale-free, handles zeros |
| Bias | Mean(predicted - actual) | Detect systematic over/under-forecasting |
| Service level | % of time demand met from stock | Business KPI |
| Quantile loss (P90) | Quantile loss at 90th percentile | For safety stock decisions |

**Layman Example:**
A grocery store ordering milk:
- **Too little stock (under-forecast):** Empty shelves → lost sales + unhappy customers
- **Too much stock (over-forecast):** Milk expires → waste + cost
- **The goal:** Order the RIGHT amount. But uncertainty exists → need probabilistic forecast

A good system considers: "Last Tuesday we sold 100 units. But this Tuesday there's a promotion (-20% price → expect +30% demand) AND a holiday (−15% foot traffic). Temperature forecast is hot (+10% for dairy). My prediction: 100 × 1.3 × 0.85 × 1.1 = 121 units (80% CI: 95-150)."

**Follow-up Questions:**

**Q: How do you handle intermittent demand (many zeros)?**
A: Standard models assume continuous demand. For items sold rarely: (1) Croston's method: separately model demand probability (Bernoulli) and demand size (when > 0), (2) Zero-inflated models: explicit probability of zero + distribution for non-zero, (3) Negative Binomial distribution (handles excess zeros), (4) Deep learning: Tweedie distribution, zero-inflated NB, or ISQF (Incremental Sequence to Function). Essential for spare parts, luxury goods.

**Q: What is hierarchical forecast reconciliation?**
A: Ensure forecasts are consistent across aggregation levels: Daily product-store forecasts must sum to weekly category-region forecasts. Methods: (1) Bottom-up: forecast at finest level, aggregate up (noisy for sparse items), (2) Top-down: forecast total, disaggregate down (proportionally), (3) Middle-out: forecast at mid-level, reconcile both directions, (4) Optimal (MinT): minimize total variance subject to aggregation constraints. Improves accuracy at all levels.

**Q: How do you incorporate promotion effects?**
A: (1) Include promotion as known future input (binary: on/off, or detailed: % discount, display type, ad type), (2) Model interaction: promotion × product × week_of_promotion (saturation effects), (3) Pre/post-promotion effects (pantry loading → post-promo dip), (4) Cross-product cannibalization (promo on Coke reduces Pepsi), (5) TFT/DeepAR naturally handles known future covariates.

**Q: What is stockout-censored demand and why does it matter?**
A: When a product is out of stock, observed sales = 0 but true demand > 0. Training on observed sales underestimates true demand. Solutions: (1) Remove stockout periods from training, (2) Estimate censored demand (fit truncated distribution), (3) Include inventory level as feature (model learns demand > sales when inventory = 0), (4) Survival analysis approach.

---

## 17. Financial Time Series Forecasting

**Answer:**
Financial forecasting (stock prices, returns, volatility) has unique challenges: non-stationarity, regime changes, low signal-to-noise ratio, adversarial nature (markets adapt to patterns), and the Efficient Market Hypothesis limiting predictability.

**Key differences from other time series:**

| Aspect | Regular TS (demand, weather) | Financial TS |
|--------|------------------------------|-------------|
| Signal-to-noise ratio | Medium-high | Very low |
| Patterns persist? | Generally yes | Disappear once exploited |
| Stationarity | Often achievable | Regime-switching |
| Distribution | Often Normal-ish | Fat tails (heavy tails) |
| Volatility | Usually stable | Clustered (GARCH) |
| Prediction horizon useful | Days to months | Minutes to days (for alpha) |
| Data quality | Usually clean | Look-ahead bias, survivorship bias |

**Tasks:**

| Task | What to predict | Difficulty | Models |
|------|----------------|-----------|--------|
| Returns forecasting | Direction or magnitude of price change | Very hard | ML features + ensemble, LSTM |
| Volatility forecasting | Future realized volatility | Moderate | GARCH, HAR, deep learning |
| Risk modeling (VaR) | Worst-case loss at confidence level | Moderate | EVT, GARCH, quantile regression |
| Portfolio optimization | Optimal asset weights | Moderate | Mean-variance, Black-Litterman + ML |
| High-frequency | Microsecond price movements | Specialized | Limit order book models |

**Volatility Models:**

| Model | Mechanism | Use |
|-------|-----------|-----|
| GARCH(1,1) | σ²_t = ω + α·ε²_{t-1} + β·σ²_{t-1} | Standard volatility model |
| EGARCH | Asymmetric (leverage effect) | Captures "bad news = more vol" |
| HAR (Heterogeneous AR) | Realized vol at daily+weekly+monthly frequencies | Simple, competitive |
| Deep learning | LSTM/TCN on returns + features | Captures complex patterns |

**Common Pitfalls:**

| Pitfall | Description | Prevention |
|---------|-------------|------------|
| Look-ahead bias | Using future info in features | Point-in-time data, careful feature engineering |
| Survivorship bias | Only modeling surviving companies | Include delisted companies |
| Overfitting to noise | Model fits random patterns | Walk-forward validation, regularization |
| Transaction costs ignored | Profitable in theory, not in practice | Include realistic costs in backtest |
| Non-stationarity | Patterns that worked in 2020 don't work in 2024 | Regime detection, adaptive models |
| Data snooping | Testing many strategies, reporting best | Multiple testing correction, out-of-sample |

**Follow-up Questions:**

**Q: Can deep learning beat the market?**
A: The Efficient Market Hypothesis suggests prices reflect all available information, making prediction impossible. In practice: (1) Short-term microstructure patterns exist (HFT exploits these), (2) Alternative data (satellite, social media) provides temporary edge, (3) Volatility and risk are more predictable than returns, (4) Most academic claims of market-beating ML don't survive realistic backtesting with costs. DL adds value for: risk modeling, portfolio construction, execution optimization.

**Q: What is walk-forward validation for financial data?**
A: Never use random train/test splits (future data leaks to training). Walk-forward: (1) Train on [0, T], validate on [T, T+h]. (2) Expand: train on [0, T+h], validate on [T+h, T+2h]. (3) Continue expanding/sliding. This simulates real-world deployment where you only have past data. Also called time-series cross-validation or expanding window.

**Q: How do you handle regime changes?**
A: Financial markets switch between regimes (bull/bear, high/low volatility, crisis/normal). Methods: (1) Hidden Markov Models (detect regime, model per regime), (2) Adaptive windows (forget old data when regime changes), (3) Regime-aware features (VIX level, credit spreads as regime indicators), (4) Online learning (continuous model adaptation), (5) Ensemble with recent-data bias.

---

## 18. Energy and Load Forecasting

**Answer:**
Energy forecasting predicts electricity demand, renewable generation, and prices. Critical for grid operation (supply must exactly match demand), energy trading, and infrastructure planning.

**Forecasting tasks:**

| Task | Horizon | Granularity | Key drivers |
|------|---------|-------------|-------------|
| Very short-term load | Minutes to hours | Seconds/minutes | Immediate ramp events |
| Short-term load | 1-7 days ahead | Hourly | Weather, calendar, time-of-day |
| Medium-term load | Weeks to months | Daily/weekly | Economic activity, policy |
| Long-term load | Years | Annual | Population growth, EVs, climate |
| Solar generation | Hours to days | Hourly | Cloud cover, solar angle |
| Wind generation | Hours to days | Hourly | Wind speed, turbulence |
| Price forecasting | Hours to days | Hourly | Supply, demand, fuel costs |

**Key Characteristics:**

| Property | Description |
|----------|-------------|
| Multiple seasonalities | Daily (24h) + weekly (7d) + annual (365d) |
| Weather dependence | Temperature, humidity, cloud cover dominate |
| Calendar effects | Weekday/weekend, holidays, special events |
| Non-linearity | Heating AND cooling both increase demand (U-shaped temp relationship) |
| Hierarchy | Substation → feeder → transformer → household |
| Regime change | COVID demand drop, EV adoption, solar installations |

**Models:**

| Model | Type | Horizon | Strength |
|-------|------|---------|----------|
| ARIMA + exogenous (SARIMAX) | Statistical | Short-term | Interpretable, proven |
| Gradient boosting (LightGBM) | ML | Short-medium | Feature engineering + covariates |
| TFT | Deep learning | Short-medium | Multiple inputs, interpretable attention |
| N-BEATS + weather features | Deep learning | Short | Multi-scale patterns |
| NeuralProphet | Hybrid (Prophet + neural) | Medium | Auto-regressive + decomposition |
| Graph NN (spatial) | Deep learning | Short | Network topology |
| Probabilistic (DeepAR) | Deep learning | Any | Uncertainty for reserves |

**Follow-up Questions:**

**Q: Why is energy forecasting particularly suited to deep learning?**
A: (1) Large amounts of historical data (smart meters generate millions of readings), (2) Multiple complex seasonalities (overlapping daily, weekly, annual), (3) Non-linear relationships (temperature effect on demand is U-shaped), (4) Rich covariates available (weather forecasts, calendar, prices), (5) Spatial correlations (neighboring areas have correlated demand), (6) Need probabilistic forecasts (for reserve planning).

**Q: How do you handle the temperature non-linearity?**
A: Demand vs. temperature is U-shaped: heating degree days (HDD = max(0, base-temp)) for cold, cooling degree days (CDD = max(0, temp-base)) for hot. Base ≈ 18°C/65°F. Deep learning captures this naturally. For classical models: create HDD/CDD features, or use piecewise linear temperature features.

**Q: What is the role of probabilistic forecasts in grid operation?**
A: Grid operators must balance supply and demand in real-time. They need: (1) Point forecast for scheduling, (2) Quantiles for reserve allocation (99th percentile → spinning reserve), (3) Scenarios for stress testing. Under-forecasting → rolling blackouts. Over-forecasting → wasted expensive reserve capacity. The cost of under-prediction is much higher → use asymmetric loss or high quantiles.

---

## 19. Evaluation and Backtesting Strategies

**Answer:**
Proper evaluation of forecasting models requires time-aware validation strategies that prevent data leakage and simulate real-world deployment conditions.

**Validation Strategies:**

| Strategy | How it works | Pros | Cons |
|----------|-------------|------|------|
| Rolling window | Fixed training window, slide forward | Constant training size, adapts | May lose early patterns |
| Expanding window | Growing training set, slide test forward | Uses all available data | Training cost grows |
| Blocked CV | K blocks, each as test once | Efficient, multiple test periods | Blocks may not be IID |
| Purged CV | Gap between train/test to avoid leakage | Prevents information leakage | Loses data in gap |
| Combinatorial | All possible train/test splits respecting time | Maximum statistical power | Expensive |

**Rolling/Expanding Window:**
```
Expanding window:
Train: [────────]
Test:               [──]
Train: [──────────────]
Test:                     [──]
Train: [────────────────────]
Test:                           [──]

Rolling window (fixed size):
Train:      [────────]
Test:                    [──]
Train:           [────────]
Test:                         [──]
Train:                [────────]
Test:                              [──]
```

**Error Metrics Comparison:**

| Metric | Formula | Scale-dependent? | Handles zeros? | Symmetric? |
|--------|---------|-----------------|---------------|-----------|
| MAE | Mean|y-ŷ| | Yes | Yes | Yes |
| MSE/RMSE | Mean(y-ŷ)² / √MSE | Yes | Yes | Yes |
| MAPE | Mean|y-ŷ|/|y| × 100 | No | NO (div by 0) | No (asymmetric) |
| sMAPE | Mean(|y-ŷ|/(|y|+|ŷ|)) × 200 | No | Partially | More symmetric |
| MASE | MAE / MAE_naive | No | Yes | Yes |
| WAPE | Sum|y-ŷ| / Sum|y| | No | Yes | Yes |
| RMSSE | RMSE / RMSE_naive | No | Yes | Yes |

**Best Practices:**

| Practice | Why |
|----------|-----|
| Always compare to naive baselines | Seasonal naive, last-value naive — if model can't beat these, it's useless |
| Use time-respecting splits | Never random split; time always flows forward |
| Test on multiple horizons | Model might be great at h=1 but terrible at h=30 |
| Evaluate across many series | Average performance + worst-case + breakdown by characteristics |
| Include business metrics | Forecast accuracy → inventory cost, service level, waste |
| Test robustness | Performance during regime changes, missing data, extreme events |
| Statistical significance | Diebold-Mariano test for comparing forecast accuracy |

**Layman Example:**
Evaluating a weather forecaster:
- **Wrong approach:** Give them weather data from all of 2024 (including next week's). Ask them to predict random days from 2024. They'll seem perfect (data leakage — they saw the future).
- **Right approach (expanding window):** 
  - Train on Jan-Mar → predict April (check accuracy)
  - Train on Jan-Jun → predict July (check accuracy)
  - Train on Jan-Sep → predict October (check accuracy)
  - Average their performance across all these genuine forward-looking tests

**Follow-up Questions:**

**Q: Why is MASE preferred over MAPE?**
A: MAPE problems: (1) Undefined when actual = 0 (common in demand), (2) Asymmetric (over-predictions penalized less than under-predictions), (3) Biased toward models that under-predict. MASE (Mean Absolute Scaled Error) = MAE divided by naive forecast MAE. Advantages: scale-free, handles zeros, symmetric, interpretable (MASE < 1 means beating naive). M5 competition used WRMSSE (weighted RMSSE).

**Q: What is the Diebold-Mariano test?**
A: A statistical test for whether two forecast methods have significantly different accuracy. H₀: both models have equal expected loss. Compute loss differential series d_t = L(e₁_t) - L(e₂_t), test if mean(d_t) ≈ 0 using t-test with HAC standard errors (robust to autocorrelation). p < 0.05 → statistically significant difference. Essential to avoid claiming improvement from random variation.

**Q: How do you handle regime changes in evaluation?**
A: (1) Evaluate separately before/during/after regime changes, (2) Report performance on "crisis" periods specifically, (3) Use adaptive methods and evaluate adaptation speed, (4) Weight recent performance higher, (5) Include regime-change events in test set (don't only test on "normal" periods).

**Q: What is the naive baseline for seasonal data?**
A: Seasonal naive: ŷ_{t+h} = y_{t+h-m} (predict same value as one full season ago). For daily data with weekly seasonality: predict this Monday = last Monday. For monthly data with yearly seasonality: predict this December = last December. Any model that can't beat seasonal naive is not capturing useful patterns.

---

## 20. Forecasting in Production Systems

**Answer:**
Deploying forecasting models in production requires addressing scale, automation, monitoring, and integration with business systems. The model is just one component of a larger MLOps system.

**Production Pipeline:**

```
Data ingestion → Feature store → Model training → Model registry → Serving
     ↓                ↓              ↓                ↓              ↓
  Validation    Versioning    Hyperparameter    A/B testing    Monitoring
                              tuning (auto)                    & alerts
```

**Components:**

| Component | Purpose | Tools |
|-----------|---------|-------|
| Feature store | Consistent feature computation, avoid training/serving skew | Feast, Tecton, Vertex AI |
| Automated retraining | Update models on new data (scheduled or triggered) | Airflow, Kubeflow, Vertex AI |
| Model registry | Version, track, compare models | MLflow, Weights & Biases |
| Forecast store | Pre-computed forecasts accessible by downstream | Database/API |
| Monitoring | Detect performance degradation | Custom dashboards, anomaly alerts |
| Fallback | Graceful degradation when model fails | Seasonal naive, last valid forecast |
| Backtesting | Continuous evaluation of new model versions | Automated evaluation pipeline |

**Monitoring Metrics:**

| Metric | What to watch | Action if violated |
|--------|---------------|-------------------|
| Prediction error (MASE, WAPE) | Should stay stable over time | Investigate, retrain |
| Forecast bias | Should be ~0 (symmetric errors) | Check for concept drift |
| Coverage (for PI) | 90% intervals should cover ~90% | Recalibrate |
| Data quality | Missing values, outliers, delays | Alert, use fallback |
| Latency | Serving time per request | Scale infrastructure |
| Feature drift | Distribution shift in inputs | Trigger retraining |

**Challenges at Scale:**

| Challenge | Problem | Solution |
|-----------|---------|----------|
| Millions of series | Can't manually tune each | Global models + per-series adaptation |
| Heterogeneous patterns | One model doesn't fit all | Model selection per cluster/series |
| Missing data | Sensors fail, APIs go down | Imputation pipeline + graceful degradation |
| Cold start | New products, new stores | Transfer learning, foundation models |
| Computational budget | Training millions of models daily | Efficient models, smart retraining triggers |
| Stakeholder trust | Users distrust black boxes | Interpretability, explain unusual forecasts |

**Layman Example:**
Running a national weather service:
- You can't manually adjust models for each city every day
- Need **automated pipelines** that pull new data, retrain models, publish forecasts
- Need **monitoring**: if today's forecasts were terrible in one region, you get an alert
- Need **fallback**: if the satellite feed breaks, use yesterday's model rather than nothing
- Need **A/B testing**: new model looks better in backtest — slowly roll it out to 10% of regions, monitor, then expand
- Need **explanation**: "Why does the model predict a cold snap?" → show feature contributions (attention to similar historical patterns)

**Follow-up Questions:**

**Q: How do you decide when to retrain a model?**
A: Triggers: (1) Scheduled (weekly/monthly for most), (2) Performance degradation detected (error exceeds threshold), (3) Data drift detected (feature distributions shifted — KS test, PSI), (4) Business events (new product launch, policy change). Balance: too frequent = expensive + unstable; too rare = stale model. Typical: weekly retrain for demand, daily for financial, monthly for long-term.

**Q: How do you handle the cold start problem in production?**
A: New product/location with no history: (1) Use similar items' forecasts (collaborative filtering for forecasting), (2) Foundation model zero-shot prediction, (3) Use category-level average + metadata-based adjustment, (4) Start with simple rules (industry average), transition to model-based as data accumulates, (5) Hierarchical models that share strength from parent categories.

**Q: What is concept drift and how do you detect it in forecasting?**
A: The relationship between inputs and targets changes over time (e.g., COVID changed demand patterns, remote work changed energy patterns). Detection: (1) Monitor forecast error over time (increasing error = possible drift), (2) Statistical tests on residuals (CUSUM, Page-Hinkley), (3) Feature drift detection (compare recent vs. historical input distributions), (4) Two-window comparison (old data model performance vs. recent data). Response: retrain on recent data, adjust windows, or switch to adaptive model.

**Q: How do you serve forecasts for millions of series?**
A: (1) Pre-compute: Generate all forecasts in batch (e.g., nightly), store in database, serve from cache. (2) On-demand: For real-time needs, serve from model behind API (fast models like linear/N-BEATS preferred). (3) Hybrid: Pre-compute most, on-demand for high-priority or event-triggered updates. Batch approach handles 99% of cases; real-time adds complexity for marginal benefit.

---

## 21. Conformal Prediction for Time Series

**Answer:**
Conformal prediction provides distribution-free prediction intervals with finite-sample coverage guarantees. Unlike parametric methods (assuming Gaussian), conformal prediction works with ANY forecasting model and ANY distribution.

**Basic Algorithm:**
```
1. Split data: Training set + Calibration set + Test set
2. Train model on training set
3. Compute residuals on calibration set: r_i = |y_i - ŷ_i|
4. For desired coverage (1-α), find q = (1-α) quantile of residuals
5. Prediction interval: [ŷ_test - q, ŷ_test + q]

Guarantee: P(y_test ∈ interval) ≥ 1-α (if calibration ≈ test distribution)
```

**Adaptations for Time Series:**

| Method | Adaptation | Benefit |
|--------|-----------|---------|
| ACI (Adaptive Conformal) | Online updating of quantile based on recent coverage | Handles distribution shift |
| EnbPI | Ensemble of models bootstrapped at different times | Reduces model dependency |
| CF-RNN | Calibrate RNN predictions conformally | Probabilistic RNN without distributional assumptions |
| Copula-based | Model dependencies between time steps | Valid intervals for multi-step |

**Comparison with alternatives:**

| Method | Assumptions | Guarantees | Sharpness | Computational |
|--------|-------------|-----------|-----------|---------------|
| Gaussian interval (±1.96σ) | Normality | Approximate (if Normal) | Can be sharp | Fast |
| Quantile regression | None on distribution | Asymptotic only | Can be sharp | Training cost |
| Bootstrap PI | Correct model specification | Approximate | Variable | Expensive |
| Conformal prediction | Exchangeability | Finite-sample exact | May be conservative | Calibration cost |
| Bayesian posterior | Prior + likelihood correct | If assumptions hold | Often sharp | Expensive |

**Follow-up Questions:**

**Q: What is the exchangeability assumption and when does it fail for time series?**
A: Exchangeability means the joint distribution is invariant to permutation — i.e., calibration and test data come from the same distribution. Time series violates this (autocorrelation, non-stationarity, trend). Solutions: (1) Use residuals (approximately exchangeable if model is good), (2) ACI (adaptive conformal inference) adjusts intervals online, (3) Sliding window calibration (use only recent residuals).

**Q: How does Adaptive Conformal Inference (ACI) work?**
A: ACI updates the threshold online: if the model recently under-covered (too many points outside intervals), widen intervals. If recently over-covered (too conservative), narrow. Specifically: α_t+1 = α_t + γ·(err_t - α), where err_t = 1 if y_t was outside interval, 0 otherwise. This adapts to non-stationarity while maintaining average coverage.

**Q: What is the practical benefit over just using quantile regression?**
A: (1) Guaranteed finite-sample coverage (quantile regression is only asymptotically valid), (2) Model-agnostic (wrap ANY model — even XGBoost, neural net), (3) No retraining needed (just calibrate on held-out residuals), (4) Handles model misspecification (intervals widen if model is bad). Cost: may be conservative (wider intervals than parametric if assumptions hold).

---

## 22. Neural ODE and Continuous-Time Models

**Answer:**
Neural ODEs model time series as continuous dynamical systems, naturally handling irregular time intervals and missing data. Instead of discrete steps (RNN at t=1,2,3...), they define continuous dynamics: dh/dt = f_θ(h(t), t).

**Key Models:**

| Model | Architecture | Strength |
|-------|-------------|----------|
| Neural ODE | ODE solver + neural f_θ | Continuous dynamics, irregular time |
| Latent ODE | Encoder → latent state → Neural ODE → decoder | Irregularly-sampled sequences |
| Neural CDE (Controlled DE) | Driven by input path | Continuous-time RNN analogue |
| Neural SDE | Add stochastic term to ODE | Uncertainty quantification |
| GRU-ODE-Bayes | GRU updates at observations, ODE between | Practical hybrid |

**Neural ODE vs. RNN:**

| Aspect | RNN/LSTM | Neural ODE |
|--------|----------|-----------|
| Time handling | Discrete steps (fixed Δt) | Continuous (any Δt) |
| Irregular sampling | Requires imputation or special handling | Natural |
| Memory | O(sequence_length) | O(1) (adjoint method) |
| Computation | Fixed per step | Adaptive (more for complex dynamics) |
| Training | Backprop through time | Adjoint sensitivity method |
| Interpretability | Hidden state | Dynamical system (phase portraits) |

**Layman Example:**
- **RNN:** Tracking a ball's position by taking photos every second (t=1,2,3...). Between photos, you have no idea where the ball is.
- **Neural ODE:** Modeling the ball's velocity and acceleration (the physics). You can ask "where is the ball at t=1.37 seconds?" — the model integrates the dynamics to any point in time. Works naturally even if your camera takes photos at irregular intervals (t=0.5, 1.3, 4.7...).

**Follow-up Questions:**

**Q: When are continuous-time models necessary?**
A: (1) Irregularly-sampled data (medical records — visits at random times), (2) Multi-rate data (some sensors at 1Hz, others at 100Hz), (3) Modeling physical systems with continuous dynamics, (4) When you need predictions at arbitrary time points (not just next step), (5) Sparse observations with long gaps.

**Q: What is the adjoint method and why is it memory-efficient?**
A: Instead of storing all intermediate states for backprop (like BPTT for RNN), the adjoint method: (1) Solve ODE forward (only keep final state), (2) Solve an adjoint ODE backward (computes gradients by integrating backward in time). Memory: O(1) regardless of number of ODE solver steps. Trade: more compute (backward ODE solve) for much less memory.

**Q: What are the practical challenges of Neural ODEs?**
A: (1) Training instability (ODE solver errors affect gradients), (2) Computational cost (adaptive solvers take many steps for complex dynamics), (3) Harder to implement than standard RNNs, (4) Overhead of ODE solver call in forward/backward pass, (5) Limited practical advantage for regularly-sampled data. Best reserved for applications where irregular timing is inherent.

---

## 23. Forecasting with External Covariates

**Answer:**
External (exogenous) covariates are additional information beyond the target variable's own history. Incorporating them correctly is critical for forecasting accuracy but introduces complexities around availability and causality.

**Types of Covariates:**

| Type | Known at prediction time? | Example | How to use |
|------|---------------------------|---------|------------|
| Known future (deterministic) | Yes | Calendar (day_of_week, holiday), planned promotions | Feed as encoder + decoder input |
| Known future (forecasted) | Approximately | Weather forecast, economic forecasts | Feed as decoder input (with uncertainty) |
| Past-only (observed) | No | Competitor pricing, social media sentiment | Feed as encoder input only |
| Static | Always | Store size, product category, location | Condition the entire model |

**Incorporation Strategies:**

| Strategy | How covariates enter | Model example |
|----------|---------------------|---------------|
| Feature engineering | Create lag/window features from covariates | LightGBM, linear models |
| Concatenation | Append covariates to input at each time step | LSTM, TCN |
| Cross-attention | Attend to covariate sequence | Transformer |
| Conditioning | Modulate hidden state based on covariates | Film layers, TFT |
| Multi-input architecture | Separate encoders for different input types | TFT, DeepAR |

**Common Covariates by Domain:**

| Domain | Key covariates |
|--------|---------------|
| Retail demand | Price, promotions, holidays, weather, events |
| Energy load | Temperature, humidity, solar radiation, calendar |
| Traffic | Events, weather, road closures, time of day |
| Finance | Macro indicators, sentiment, volatility index |
| Healthcare | Patient demographics, comorbidities, treatments |

**Layman Example:**
Predicting restaurant sales:
- **Without covariates:** "We sold 200 meals last Tuesday, 210 the Tuesday before → predict ~215 this Tuesday"
- **With covariates:** "This Tuesday is Valentine's Day (known future), weather forecast says rain (known future forecast), Yelp reviews improved last month (past observed), we're in downtown with 50 seats (static). Given all this → predict 280 meals"

The covariates transform a mediocre forecast into an accurate one by explaining what drives variation beyond mere historical patterns.

**Follow-up Questions:**

**Q: How do you handle covariates that are themselves uncertain (weather forecasts)?**
A: (1) Use ensemble weather forecasts (multiple scenarios as input), (2) Add noise during training (simulate forecast uncertainty), (3) Use separate model for covariate uncertainty + propagate through main model, (4) At inference, run main model with multiple covariate scenarios → ensemble of forecasts. (5) For short horizons, use actual observed covariates from recent data.

**Q: What is the danger of covariate leakage?**
A: Using information that wouldn't be available at prediction time. Example: using ACTUAL weather as input when predicting 3 days ahead (you only have FORECASTED weather). Prevention: (1) Clearly separate past-observed from known-future, (2) Time-stamp every feature derivation, (3) Ask "would I have this value at the moment I need to make the prediction?" If no → don't use it as input to the decoder.

**Q: How many covariates should you include?**
A: More isn't always better — irrelevant covariates add noise and risk overfitting. Guidelines: (1) Start with domain knowledge (what actually drives the target?), (2) Use feature importance (TFT's variable selection, permutation importance), (3) Add covariates incrementally, measure lift on validation, (4) Deep learning with variable selection (TFT) handles many covariates better than manual selection, (5) Regularization helps when including many.

---

## 24. Time Series Data Augmentation

**Answer:**
Data augmentation for time series creates synthetic training samples while preserving temporal structure. Less developed than image augmentation but increasingly important for deep learning models.

**Techniques:**

| Technique | How it works | Preserves |
|-----------|-------------|-----------|
| **Jittering** | Add small random noise | Trend + seasonality |
| **Scaling** | Multiply by random factor | Shape, changes magnitude |
| **Time warping** | Non-linear time distortion | Shape, changes speed |
| **Window slicing** | Random subsequences | Local patterns |
| **Rotation** | Rotate multivariate series | Inter-variable relationships |
| **Permutation** | Shuffle segments | Distribution (not order — use carefully) |
| **Magnitude warping** | Multiply by smooth random curve | Overall shape |
| **Mixup** | Blend two series: λ·x₁ + (1-λ)·x₂ | Regularization |
| **Synthetic generation** | Generate from fitted model (ARIMA, GAN) | Statistical properties |
| **Bootstrapping** | Resample residuals from fitted model | Model properties |

**Comparison:**

| Method | Appropriate for | NOT for | Impact |
|--------|----------------|---------|--------|
| Jittering | Classification, anomaly | Forecasting (future noise unknown) | Low risk |
| Time warping | Gesture recognition, ECG | If exact timing matters | Moderate |
| Window slicing | Classification with long series | Short series | Low risk |
| Synthetic (GAN/model) | Any task with limited data | If synthetic is unrealistic | High potential, high risk |
| Mixup | Classification | Regression (interpolated labels may be wrong) | Regularization |

**TSMix/STAug (Modern Approaches):**
```
1. Decompose series into trend + seasonal + residual
2. Augment components separately:
   - Trend: slight slope/level shifts
   - Seasonal: phase shift, amplitude change
   - Residual: resample from empirical distribution
3. Recompose: new_series = aug_trend + aug_seasonal + aug_residual
```

**Follow-up Questions:**

**Q: Is augmentation as important for time series as for images?**
A: Less so, because: (1) Time series often have less variation to augment (images have rotation, flip, color — time series is more constrained), (2) Some augmentations destroy temporal structure (unlike spatial transforms for images), (3) Cross-learning across many series provides natural "augmentation." But it helps when: very few labeled series (classification), small dataset, or when you need robustness to specific perturbations.

**Q: How do you augment for forecasting (not classification)?**
A: (1) Generate synthetic series from fitted models (ARIMA parameters, GAN-generated), (2) Vary noise levels in historical data during training (teaches robustness), (3) Train on subwindows of different lengths (multi-scale), (4) Inject synthetic events/anomalies to teach model to handle them, (5) Use foundation model to generate similar series (Chronos can generate synthetic training data).

**Q: What is the risk of bad augmentation?**
A: (1) Destroying discriminative patterns (shuffling segments removes temporal information), (2) Creating unrealistic examples that confuse the model, (3) Label corruption in classification (warped ECG may change from normal to abnormal), (4) Over-augmenting (model sees too few real examples, learns artificial patterns). Always validate that augmented data improves validation performance.

---

## 25. Forecasting Competitions and Benchmarks

**Answer:**
Forecasting competitions (M-competitions, Kaggle) have driven methodological advances by providing standardized evaluation on diverse real-world data. Key finding: simple methods + good ensembles often beat complex individual models.

**Major Competitions:**

| Competition | Year | Winner approach | Key insight |
|-------------|------|----------------|-------------|
| M3 | 2000 | Theta method (simple) | Simple methods beat complex ones |
| M4 | 2018 | ES-RNN (hybrid statistical + neural) | Hybrid approach works |
| M5 | 2020 | LightGBM ensemble | Tree models dominate retail demand |
| M6 | 2022 | Various ML | Financial forecasting is hard |
| Kaggle (various) | Ongoing | Gradient boosting + engineered features | Feature engineering > model complexity |

**M5 Competition Insights (Walmart demand):**

| Rank | Approach | Key takeaway |
|------|----------|-------------|
| Top 1-5 | LightGBM with massive feature engineering | Tree models + features > deep learning |
| Top 10 | Ensemble of LightGBM + NN | Diversity helps |
| Notable | Deep learning (DeepAR, N-BEATS) | Competitive but didn't win |
| Observation | Uncertainty track winners used quantile approaches | Probabilistic matters |

**Standard Benchmarks:**

| Benchmark | Dataset | Size | Typical models evaluated |
|-----------|---------|------|------------------------|
| ETTh1/ETTh2 | Electricity transformer temperature | 17K × 7 vars | Transformers, MLPs |
| ETTm1/ETTm2 | Same, minute-level | 70K × 7 vars | Transformers, MLPs |
| Weather | 21 weather indicators | 52K × 21 vars | All methods |
| Electricity | 321 clients hourly | 26K × 321 vars | Multivariate models |
| Traffic | 862 sensors hourly | 17K × 862 vars | Spatial-temporal models |
| ILI (Influenza) | CDC data | 966 × 7 vars | Health forecasting |
| M4 | 100K diverse series | Various frequencies | Universal models |
| Monash archive | 30+ diverse datasets | Various | Meta-evaluation |

**Key Lessons from Competitions:**

| Lesson | Description |
|--------|-------------|
| Ensembles win | Combining diverse models almost always outperforms individual best |
| Feature engineering matters | Often more impactful than model architecture choice |
| Simple baselines are strong | Never skip naive and seasonal naive comparisons |
| Domain knowledge is crucial | Understanding the data generation process guides feature/model choices |
| Probabilistic forecasting is undervalued | Many applications care about uncertainty, not just point accuracy |
| Validation strategy is critical | Many teams overfit to leaderboard; proper temporal CV prevents this |
| Global models (one model, many series) often win | Cross-learning > per-series optimization |
| Post-processing helps | Reconciliation, clipping, rounding to valid values |

**Follow-up Questions:**

**Q: Why did LightGBM beat deep learning in M5?**
A: (1) Feature engineering captured domain knowledge (lagged sales, rolling statistics, price features, holiday effects) — these features encode information deep learning would need to discover. (2) M5 has hierarchical structure that tree models handle well with engineered hierarchical features. (3) Tree models handle mixed feature types (categorical + continuous) naturally. (4) Less overfitting risk with proper regularization. Deep learning needs: more data OR pre-trained foundation model to overcome this.

**Q: What are the common winning strategies across Kaggle competitions?**
A: (1) Extensive EDA and feature engineering (80% of the effort), (2) Time-aware validation that mirrors the test set evaluation, (3) Ensemble of diverse models (LightGBM + CatBoost + XGBoost + NN), (4) Target transformation (log for skewed targets), (5) Careful handling of time features and lagged variables, (6) Post-processing (clip negative predictions, round if needed), (7) Iterate quickly on features, not models.

**Q: How do foundation models (Chronos, TimesFM) perform on benchmarks?**
A: Zero-shot: competitive with tuned statistical methods, below tuned deep learning. With fine-tuning: competitive with or exceeding task-specific models. Key advantage: no training time, work out-of-the-box. Results vary by dataset — foundation models excel on datasets similar to their training distribution, struggle on very specialized domains. They're the best "first try" approach.

---

## Quick Reference: Forecasting Model Selection

| Scenario | Recommended approach |
|----------|---------------------|
| Few series, lots of data each | ARIMA/ETS per series, or N-BEATS |
| Many related series | Global deep learning (DeepAR, TFT, PatchTST) |
| Rich covariates available | TFT or LightGBM with feature engineering |
| Long forecast horizon (100+ steps) | PatchTST, N-HiTS, direct multi-step |
| Intermittent/sparse demand | Zero-inflated models, Croston's, DeepAR with NB |
| Spatial-temporal (traffic, weather) | Graph neural networks, ST-GNN |
| Cold start (no history) | Foundation model (Chronos, TimesFM) zero-shot |
| Need probabilistic forecasts | DeepAR, TFT with quantile loss, conformal prediction |
| Need interpretability | N-BEATS interpretable, TFT (attention + variable importance) |
| Real-time/streaming | Online learning, simple models with fast update |
| Maximum accuracy (competition) | LightGBM features + deep learning ensemble |
| Quick prototype | Foundation model or Prophet |

---

## Common Interview Traps (Forecasting-Specific)

1. **"Deep learning always beats classical methods"** → No. For univariate or few series with clean patterns, ARIMA/ETS often win. Deep learning shines with many series (cross-learning) and complex covariates.

2. **"More data always helps"** → Not if it includes regime changes you don't account for. Training on pre-COVID data may hurt post-COVID forecasts. Recency often matters more than quantity.

3. **"Point forecast is sufficient"** → Almost never in business. Decisions depend on uncertainty: inventory needs 95th percentile, risk management needs 1st percentile. Always provide prediction intervals.

4. **"MAPE is a good metric"** → Undefined for zeros (common in demand), asymmetric, misleading. Use MASE, WAPE, or RMSSE instead.

5. **"Random train/test split is fine"** → NEVER for time series. Always use temporal splits. Random splits = data leakage = over-optimistic evaluation = models that fail in production.

6. **"My model achieves 98% accuracy"** → Meaningless without context. Compare to naive baseline. If seasonal naive gets 96%, your model adds only 2% relative improvement. Report skill score = (model_error - naive_error) / naive_error.

7. **"The model captured the pattern perfectly"** → On training data? Check out-of-sample. Time series models can memorize seasonal patterns on training data while failing to generalize to concept drift, events, or distribution shifts.

8. **"Transformers are the best architecture for time series"** → Debated. DLinear (one linear layer!) often matches Transformers. N-BEATS/N-HiTS are competitive with fewer parameters. Architecture matters less than: good data, proper evaluation, appropriate covariates, and ensembling.

9. **"We need real-time retraining"** → Usually not. Most forecasting tasks are served well by batch predictions (daily/weekly retrain). Real-time retraining adds complexity, instability risk, and infrastructure cost. Only justified when: distribution shifts happen hourly (HFT, flash events) or when you need minute-level adaptation.

10. **"Foundation models will replace everything"** → Not yet. They're excellent baselines and solve cold start, but fine-tuned domain-specific models with proper covariates still win when you have the data and domain knowledge. Foundation models are the starting point, not the end point.
