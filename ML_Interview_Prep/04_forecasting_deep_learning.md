# Forecasting - Deep Learning Comprehensive Guide

## Table of Contents
1. [Time Series Fundamentals](#fundamentals)
2. [Traditional Forecasting Methods](#traditional-methods)
3. [Deep Learning for Forecasting](#deep-learning-forecasting)
4. [Transformer-based Forecasting](#transformer-forecasting)
5. [Probabilistic Forecasting](#probabilistic-forecasting)
6. [Multi-step & Multi-variate Forecasting](#multi-step)
7. [Feature Engineering for Time Series](#feature-engineering)
8. [Evaluation Metrics](#evaluation-metrics)
9. [Interview Questions with Answers](#interview-questions)
10. [Comparisons & Alternatives](#comparisons)

---

## Time Series Fundamentals

### What is Time Series Forecasting?
Predicting future values based on historical temporal patterns.

**Layman Example:** Like predicting tomorrow's weather by looking at weather patterns over the past week/month/year. Or predicting next month's sales based on historical sales data.

### Key Components of Time Series

| Component | Description | Example |
|-----------|-------------|---------|
| Trend | Long-term direction | Stock market going up over decades |
| Seasonality | Fixed-period recurring patterns | Ice cream sales spike every summer |
| Cyclical | Non-fixed period patterns | Business cycles (recession/growth) |
| Residual/Noise | Random unexplained variation | Day-to-day fluctuations |

### Stationarity
**Definition:** Statistical properties (mean, variance) don't change over time.

**Why Important:** Many models assume stationarity. Non-stationary data must be transformed.

**Tests for Stationarity:**
- **ADF (Augmented Dickey-Fuller):** Null = non-stationary
- **KPSS:** Null = stationary
- **Visual:** Rolling mean/std plots

**Making Data Stationary:**
- Differencing (first or second order)
- Log transformation
- Seasonal differencing
- Detrending

### Autocorrelation
- **ACF (Autocorrelation Function):** Correlation of series with its own lags
- **PACF (Partial ACF):** Direct correlation (removing intermediate effects)
- Used to identify AR and MA orders in ARIMA

---

## Traditional Forecasting Methods

### ARIMA (AutoRegressive Integrated Moving Average)

**Components:**
- **AR(p):** Uses p past values. y_t = c + φ₁y_{t-1} + ... + φ_py_{t-p}
- **I(d):** Differencing d times for stationarity
- **MA(q):** Uses q past forecast errors. y_t = c + θ₁ε_{t-1} + ... + θ_qε_{t-q}

**Notation:** ARIMA(p, d, q)

**How to Choose p, d, q:**
- d: Number of differencing needed for stationarity
- p: Significant lags in PACF
- q: Significant lags in ACF
- Or use auto_arima (AIC/BIC minimization)

### SARIMA
- ARIMA + Seasonal component
- SARIMA(p,d,q)(P,D,Q,s)
- s = seasonal period (12 for monthly, 7 for daily-weekly)

### Exponential Smoothing (ETS)

| Method | Trend | Seasonality | Use Case |
|--------|-------|-------------|----------|
| Simple ES | None | None | Level only |
| Holt's | Linear | None | Trend data |
| Holt-Winters | Linear | Additive/Multiplicative | Trend + Seasonal |

### Prophet (Facebook/Meta)
- Decomposable model: y(t) = trend + seasonality + holidays + error
- Handles missing data, outliers, changepoints
- Multiple seasonalities (daily, weekly, yearly)
- User-friendly, good for business forecasting

---

## Deep Learning for Forecasting

### Why Deep Learning for Time Series?
- Captures complex non-linear patterns
- Handles multivariate relationships automatically
- Scales to large datasets
- No stationarity assumption needed
- Can incorporate external features naturally

### RNN/LSTM for Forecasting

**Architecture:**
```
Input Sequence [x_{t-n}, ..., x_{t-1}] → LSTM layers → Dense → Prediction [x_t, ..., x_{t+h}]
```

**Key Considerations:**
- Sequence length (lookback window): How much history to use
- Stacked LSTMs for deeper feature extraction
- Bidirectional NOT suitable for forecasting (future info leakage)
- Encoder-decoder for multi-step forecasting

**Layman Example:** Like a person who remembers the last few weeks of stock prices and uses that memory to predict next week.

### WaveNet (2016)
- **Dilated causal convolutions** for time series
- Exponentially increasing dilation: 1, 2, 4, 8, 16...
- Large receptive field with few layers
- Originally for audio generation, adapted for time series
- No RNN recurrence → parallelizable

### Temporal Convolutional Network (TCN)
- 1D causal convolutions (no future leakage)
- Dilated convolutions for large receptive field
- Residual connections
- Often outperforms LSTM with less compute

**Architecture:**
```
Input → [Dilated Causal Conv → ReLU → Dropout] × N → Output
```

**TCN vs LSTM:**
| Aspect | LSTM | TCN |
|--------|------|-----|
| Parallelization | Sequential | Full parallel |
| Memory | Unlimited (theoretically) | Fixed receptive field |
| Training speed | Slower | Faster |
| Gradient flow | Through gates | Through residual connections |

### DeepAR (Amazon, 2019)
- **Autoregressive RNN** for probabilistic forecasting
- Outputs parameters of probability distribution (not point estimates)
- Handles multiple related time series simultaneously
- Learns across all series (global model)
- Supports count data (negative binomial) and continuous (Gaussian, Student-t)

**Key Innovation:** Trains on many related series → learns shared patterns + individual behavior

### N-BEATS (2019)
- Neural Basis Expansion Analysis for Time Series
- **Pure deep learning** (no time series-specific components)
- Stack of fully connected blocks with residual connections
- Interpretable variant decomposes into trend + seasonality
- State-of-the-art on M4 competition

**Architecture:**
```
Input → [FC Block → Backcast + Forecast] × N_stacks
Final Forecast = Sum of all block forecasts
```

### N-HiTS (2022)
- Extension of N-BEATS with hierarchical interpolation
- Multi-rate sampling for different horizon ranges
- More efficient for long horizons
- Better handling of long-term patterns

---

## Transformer-based Forecasting

### Why Transformers for Time Series?
- Self-attention captures long-range dependencies directly
- Parallel computation (faster than RNN)
- Multi-head attention = multiple temporal patterns simultaneously
- But: O(n²) complexity is challenging for very long sequences

### Informer (2021)
**Problem solved:** Standard transformer is O(n²), too expensive for long sequences.

**Key Innovations:**
- **ProbSparse Self-Attention:** Only attend to top-k important queries (O(n log n))
- **Self-attention Distilling:** Progressively reduces sequence length
- **Generative Decoder:** One forward pass for all predictions (not autoregressive)

### Autoformer (2021)
- **Auto-Correlation mechanism** instead of self-attention
- Series decomposition at each layer (trend + seasonal)
- Directly models periodicity through frequency-domain analysis
- Better for strongly seasonal data

### FEDformer (2022)
- Frequency Enhanced Decomposed Transformer
- Attention in frequency domain (Fourier/Wavelet)
- Linear complexity O(n)
- Captures global patterns efficiently

### PatchTST (2023)
- Treats time series like ViT treats images
- **Patches:** Segments of consecutive time steps as tokens
- Channel-independent: Each variable processed separately
- Reduces sequence length → computational savings
- State-of-the-art on many benchmarks

**Key Insight:** Patching reduces input length from L to L/P (patch size P), making attention affordable.

### iTransformer (2024)
- **Inverted Transformer:** Attention across variables (not time)
- Each time step is a token, variables are the "sequence"
- Better for multivariate with complex cross-variable dependencies

### TimesFM (Google, 2024)
- Foundation model for time series
- Pretrained on large corpus of time series data
- Zero-shot forecasting on unseen series
- Like GPT but for time series

### Chronos (Amazon, 2024)
- Tokenizes time series values into bins
- Uses language model architecture for forecasting
- Pretrained on diverse time series
- Zero-shot generalization

---

## Probabilistic Forecasting

### Why Probabilistic?
Point forecasts are incomplete — decision makers need uncertainty estimates.

**Layman Example:** Weather forecast: "75°F tomorrow" is less useful than "75°F ±5° with 80% confidence." The uncertainty helps you decide whether to bring a jacket.

### Methods

#### Quantile Regression
- Predict specific quantiles (10th, 50th, 90th percentile)
- Loss: Pinball loss (asymmetric)
- No distributional assumptions

#### Monte Carlo Dropout
- Keep dropout ON at inference
- Run multiple forward passes
- Distribution of outputs = uncertainty estimate

#### Deep Ensembles
- Train multiple models with different initializations
- Mean = prediction, variance = uncertainty
- Simple but effective

#### Distribution-based (DeepAR)
- Output distribution parameters (μ, σ for Gaussian)
- Sample from distribution for prediction intervals
- Can use any distribution family

### Conformal Prediction
- Distribution-free uncertainty quantification
- Guaranteed coverage under exchangeability assumption
- Compute nonconformity scores on calibration set
- Modern addition to forecasting uncertainty

---

## Multi-step & Multi-variate Forecasting

### Multi-step Strategies

| Strategy | Description | Pros | Cons |
|----------|-------------|------|------|
| Recursive | Predict 1 step, feed back | Simple | Error accumulation |
| Direct | Separate model per horizon | No error accumulation | Many models |
| Multi-output | Single model, all horizons | Efficient | May sacrifice accuracy |
| Seq2Seq | Encoder-decoder | Flexible | Complex |

### Multivariate Approaches

#### Channel-Independent
- Process each variable separately
- Simpler, often competitive
- PatchTST uses this approach

#### Channel-Dependent
- Model cross-variable relationships
- Can capture correlations (e.g., temperature affects energy demand)
- More parameters, risk of overfitting

### Global vs Local Models

| Approach | Description | When to Use |
|----------|-------------|-------------|
| Local | One model per series | Few series, very different patterns |
| Global | One model for all series | Many similar series, limited data per series |
| Clustered | Group similar series | Moderate diversity |

**Key Insight:** Global models (like DeepAR, N-BEATS) often outperform local models by learning shared patterns across many series.

---

## Feature Engineering for Time Series

### Temporal Features
- Hour, day of week, month, quarter, year
- Is_weekend, is_holiday
- Days since event, days until event
- Season (spring/summer/fall/winter)

### Lag Features
- y_{t-1}, y_{t-7}, y_{t-365} (previous values at relevant lags)
- Rolling statistics: rolling mean, std, min, max
- Expanding window statistics

### Domain-Specific Features
- Weather data for energy forecasting
- Promotions/events for retail
- Economic indicators for finance
- Social media trends

### Target Encoding for Time Series
- Historical averages by time group
- E.g., average sales for each day-of-week
- Be careful of leakage (use only past data)

### Window Features
```python
# Example lag and rolling features
df['lag_1'] = df['value'].shift(1)
df['lag_7'] = df['value'].shift(7)
df['rolling_mean_7'] = df['value'].rolling(7).mean()
df['rolling_std_7'] = df['value'].rolling(7).std()
df['ewm_7'] = df['value'].ewm(span=7).mean()
```

---

## Evaluation Metrics

### Point Forecast Metrics

| Metric | Formula | Notes |
|--------|---------|-------|
| MAE | Σ|y-ŷ|/n | Robust to outliers |
| MSE | Σ(y-ŷ)²/n | Penalizes large errors |
| RMSE | √MSE | Same units as target |
| MAPE | Σ|(y-ŷ)/y|/n×100 | Percentage, fails at y=0 |
| sMAPE | Symmetric MAPE | Bounded, handles zeros better |
| MASE | MAE / MAE_naive | Scale-independent, relative to naive |

### Probabilistic Metrics
- **CRPS (Continuous Ranked Probability Score):** Like MAE for distributions
- **Pinball Loss:** For quantile forecasts
- **Coverage:** % of actual values within prediction intervals
- **Winkler Score:** Rewards narrow intervals with good coverage

### Best Practices
- Always compare to naive baseline (last value, seasonal naive)
- Use time-based train/validation/test split
- Report metrics at multiple horizons
- Consider business metrics (not just statistical)

---

## Interview Questions with Answers

### Q1: How do you handle non-stationarity in deep learning models?
**Answer:**
- **Option 1:** Difference the data, model residuals, then integrate
- **Option 2:** Normalize with reversible instance normalization (RevIN)
- **Option 3:** Let the model learn it (deep models CAN handle non-stationary data)
- **Option 4:** Decompose (trend + seasonal + residual), model each separately
- **Key insight:** While traditional methods require stationarity, deep learning models can often handle non-stationary data directly, but normalization still helps

### Q2: Explain the difference between one-step and multi-step forecasting
**Answer:**
- **One-step:** Predict only the next time step
- **Multi-step:** Predict multiple future steps (h steps ahead)
- **Strategies:**
  - Recursive: Chain one-step predictions (error compounds)
  - Direct: Train separate model per horizon (no error accumulation)
  - MIMO: Single model outputs all horizons simultaneously
- **Trade-off:** Recursive is simpler but accumulates errors; direct is more robust but costly

### Q3: How do you prevent data leakage in time series?
**Answer:**
- Never use future data for features or preprocessing
- **Walk-forward validation:** Train on [0,t], test on [t+1, t+k], expand
- Lag features must use strictly past values
- Target encoding must use only historical averages
- No random train/test split (must respect temporal order)
- Be careful with rolling statistics (use .shift() to avoid current value)

### Q4: When would you choose deep learning over ARIMA/Prophet?
**Answer:**
Use DL when:
- Large dataset (thousands of data points)
- Complex non-linear patterns
- Multiple related series (global modeling)
- Multivariate with complex interactions
- Exogenous variables are important

Use ARIMA/Prophet when:
- Small dataset (< 100 points)
- Simple linear patterns
- Need interpretability
- Single univariate series
- Quick baseline needed

### Q5: Explain how attention works differently in time series vs NLP
**Answer:**
- **NLP:** Token-to-token attention (semantic relationships)
- **Time series:** Time-step-to-time-step attention (temporal dependencies)
- **Key differences:**
  - Positional encoding represents actual time (not just order)
  - Causal masking is critical (can't attend to future)
  - Cross-variable attention (not just within-sequence)
  - Periodicity-aware attention (Autoformer's auto-correlation)
- Sparse attention often used (not all past timesteps are equally relevant)

### Q6: What is the cold start problem in forecasting?
**Answer:**
- New series with no/little history (new product, new store)
- **Solutions:**
  - Global models: Learn from similar series, transfer to new
  - Meta-learning: Learn to forecast from few data points
  - Hierarchical models: Borrow strength from aggregate
  - External features: Use covariates instead of history
  - Zero-shot models: TimesFM, Chronos (pretrained on diverse data)

### Q7: How do you handle multiple seasonalities?
**Answer:**
- Daily data may have weekly + yearly seasonality
- **Prophet:** Explicitly models multiple Fourier seasonalities
- **Deep learning:** Model learns multiple periodicities from data
- **Feature engineering:** Encode hour, day-of-week, month as features
- **Decomposition:** STL decomposition handles one seasonality; MSTL for multiple
- **Attention models:** Can capture multiple periodic patterns naturally

### Q8: Explain the concept of forecast reconciliation
**Answer:**
- When forecasts exist at multiple aggregation levels (e.g., product → category → total)
- Individual forecasts may not add up consistently
- **Reconciliation methods:**
  - Bottom-up: Aggregate fine-grained forecasts
  - Top-down: Distribute top-level forecast
  - Optimal combination (MinT): Find best coherent forecasts
  - Machine learning-based reconciliation
- Ensures consistency across hierarchy

### Q9: What are foundation models for time series and how do they work?
**Answer:**
- Large models pretrained on diverse time series data
- **TimesFM (Google):** Decoder-only, patches as tokens, zero-shot
- **Chronos (Amazon):** Tokenizes values into bins, uses LM architecture
- **Lag-Llama:** LLaMA architecture adapted for time series with lag features
- **Key idea:** Pretrain on millions of series, zero-shot on new data
- Like GPT for text, but for numerical sequences
- Currently competitive with tuned baselines on many tasks

### Q10: How do you handle irregular/missing time series data?
**Answer:**
- **Interpolation:** Linear, spline, or model-based
- **Forward/backward fill:** Use last known value
- **Masking:** Indicate missing values, model ignores them
- **Neural ODEs:** Naturally handle irregular sampling
- **GRU-D:** GRU variant designed for missing data (decay mechanism)
- **Imputation models:** Separate model to fill gaps first
- **Important:** Distinguish between missing and zero — different semantics

---

## Comparisons & Alternatives

### Model Selection Guide

| Scenario | Recommended | Reason |
|----------|-------------|--------|
| Few data points (<100) | ARIMA, ETS | Statistical efficiency |
| Many univariate series | N-BEATS, DeepAR | Global learning |
| Complex multivariate | Transformers, TCN | Cross-variable attention |
| Need uncertainty | DeepAR, conformal | Probabilistic output |
| Real-time/fast | TCN, linear models | Low latency |
| Zero-shot (new series) | TimesFM, Chronos | Foundation models |
| Interpretability needed | Prophet, N-BEATS interpretable | Decomposition |

### Classical vs Deep Learning for Forecasting

| Aspect | Classical (ARIMA/ETS) | Deep Learning |
|--------|----------------------|---------------|
| Data needed | Tens of points | Thousands+ |
| Interpretability | High | Low |
| Multivariate | Limited | Natural |
| Feature engineering | Manual | Can be automatic |
| Compute | Low | High |
| Long horizons | Degrades | More robust |
| Uncertainty | Well-calibrated | Requires design choices |

### Surprising Result: Linear Models
Recent research (DLinear, 2022) showed that simple linear models can outperform complex transformers for some forecasting tasks:
- Decompose → linear mapping per component
- Questions whether transformers truly learn temporal patterns
- Highlights importance of proper baselines
- But transformers excel when cross-variable dependencies matter

### Key Libraries & Frameworks
- **Darts:** Unified API for many forecasting models
- **NeuralForecast (Nixtla):** Deep learning forecasting models
- **GluonTS (Amazon):** Probabilistic time series
- **PyTorch Forecasting:** Transformer-based models
- **statsmodels:** ARIMA, ETS, SARIMAX
- **Prophet:** Facebook/Meta's forecasting tool
- **TimesFM/Chronos:** Foundation models
