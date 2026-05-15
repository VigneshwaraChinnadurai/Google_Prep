# DADS401 – Advanced Machine Learning

**Session:** Jan-Feb 2026
**Program:** Master of Business Administration (MBA)
**Semester:** IV
**Course Code & Name:** DADS401 – Advanced Machine Learning
**Credits:** 04

---

## Assignment Set – 1

---

### Q1(a). Discuss the objectives of Time Series Analysis. [5 Marks]

Time Series Analysis is a statistical technique that deals with data points collected or recorded at successive time intervals. It plays a pivotal role in forecasting, pattern recognition, and decision-making across domains such as finance, healthcare, supply chain, and meteorology. The primary objectives of Time Series Analysis are as follows:

**1. Description:** The first objective is to describe the behaviour of a time series over a period. This involves understanding trends, seasonal patterns, and irregularities by summarising the data using statistical measures and graphical plots, providing a clear picture of how the variable has evolved.

**2. Explanation:** Time series analysis aims to explain the underlying factors that cause changes in the observed data. By decomposing the series into components like trend, seasonality, cyclical, and irregular variations, analysts can identify what drives the changes and how external factors influence the variable.

**3. Forecasting:** Perhaps the most valuable objective is to predict future values based on historical patterns. Models like ARIMA, Exponential Smoothing, and Prophet leverage past data to generate reliable short-term and long-term forecasts, which are essential for business planning, inventory management, and financial modelling.

**4. Control:** Time series analysis is used to monitor and control processes. In manufacturing and quality control, control charts derived from time series data help detect deviations from expected performance, enabling timely corrective action.

**5. Intervention Analysis:** This objective involves studying the impact of specific events or policy changes on a time series. For example, analysing the effect of a government regulation on stock prices or the impact of a marketing campaign on sales figures.

**6. Decomposition:** Breaking down a time series into its constituent components—trend, seasonal, cyclical, and residual—is a key objective. This decomposition helps analysts isolate individual effects and understand their relative contribution to the overall data pattern.

**7. Hypothesis Testing:** Time series analysis enables testing hypotheses about the relationships and dependencies within the data, such as whether a trend is statistically significant or whether two time series are correlated.

In summary, time series analysis provides a structured framework for understanding temporal data, enabling organisations to make data-driven decisions, anticipate future trends, and exercise control over dynamic processes.

---

### Q1(b). Explain Autoregressive Model. [5 Marks]

An Autoregressive (AR) model is a type of time series model that predicts future values based on a linear combination of past values of the same variable. The fundamental idea is that the current value of a variable can be explained by its own previous (lagged) values plus a stochastic error term. It is one of the foundational models in time series econometrics and forecasting.

**Mathematical Representation:**

An AR model of order *p*, denoted as AR(p), is expressed as:

$$Y_t = c + \phi_1 Y_{t-1} + \phi_2 Y_{t-2} + \dots + \phi_p Y_{t-p} + \varepsilon_t$$

Where:
- $Y_t$ is the current value of the time series
- $c$ is a constant (intercept)
- $\phi_1, \phi_2, \dots, \phi_p$ are the autoregressive coefficients
- $Y_{t-1}, Y_{t-2}, \dots, Y_{t-p}$ are the lagged values of the series
- $\varepsilon_t$ is white noise (error term with zero mean and constant variance)
- *p* is the order of the model, indicating how many past values are used

**Key Characteristics:**

1. **Stationarity Requirement:** For an AR model to produce reliable results, the time series should be stationary, meaning its statistical properties (mean, variance) do not change over time. Non-stationary data must be differenced before applying the AR model.

2. **Order Selection:** The order *p* is determined using tools like the Partial Autocorrelation Function (PACF). The PACF plot shows the correlation of a variable with its lagged copies while controlling for intermediate lags, helping identify the optimal number of lags.

3. **AR(1) – First Order:** The simplest case where the current value depends only on the immediately preceding value. This is often used to model economic indicators and stock returns.

4. **AR(2) – Second Order:** The current value depends on the two most recent past values, capturing more complex temporal dependencies.

**Applications:**
- Financial market forecasting (stock prices, exchange rates)
- Economic indicator prediction (GDP, inflation)
- Weather and climate modelling
- Signal processing

The AR model forms the backbone of more advanced models like ARMA (Autoregressive Moving Average) and ARIMA (Autoregressive Integrated Moving Average), making it indispensable in time series analysis.

---

### Q2(a). Interpret the ETS Model. [5 Marks]

The ETS (Error, Trend, Seasonality) model, also known as the Exponential Smoothing State Space Model, is a widely used framework for time series forecasting. It systematically captures three fundamental components of a time series—error, trend, and seasonality—and provides a unified statistical framework for exponential smoothing methods.

**Components of the ETS Model:**

1. **Error (E):** The error component represents the residual variation that cannot be explained by trend or seasonality. It can be:
   - **Additive (A):** Error magnitude remains constant regardless of the level of the series.
   - **Multiplicative (M):** Error magnitude scales proportionally with the level of the series.

2. **Trend (T):** The trend component captures the long-term direction of the data. It can be:
   - **None (N):** No trend present.
   - **Additive (A):** Linear trend where the series increases or decreases by a constant amount.
   - **Additive Damped (Ad):** A trend that gradually flattens over time, avoiding unrealistic long-term projections.
   - **Multiplicative (M):** Trend that grows or declines proportionally.

3. **Seasonality (S):** The seasonal component captures repeating patterns at fixed intervals. It can be:
   - **None (N):** No seasonal pattern.
   - **Additive (A):** Seasonal fluctuations are constant in magnitude.
   - **Multiplicative (M):** Seasonal fluctuations scale with the level of the series.

**Notation:**

ETS models are denoted as ETS(E, T, S). For example:
- **ETS(A, A, A):** Additive error, additive trend, and additive seasonality (equivalent to Holt-Winters additive method).
- **ETS(M, A, M):** Multiplicative error, additive trend, and multiplicative seasonality.
- **ETS(A, N, N):** Simple exponential smoothing with additive errors and no trend or seasonality.

**Working Principle:**

The ETS framework uses a state space representation with two equations:
- **Measurement equation:** Links the observed value to the unobserved states (level, trend, seasonality).
- **Transition equations:** Describe how the states evolve over time.

**Advantages:**
- Provides a comprehensive taxonomy of 30 possible model combinations.
- Supports automatic model selection using information criteria (AIC, BIC).
- Generates both point forecasts and prediction intervals.
- Handles a wide variety of time series patterns efficiently.

The ETS model is particularly effective for short-to-medium-term forecasting in retail demand, inventory planning, and financial analysis.

---

### Q2(b). Define the ARCH Model. Explain its usage. [5 Marks]

**Definition:**

The ARCH (Autoregressive Conditional Heteroscedasticity) model, introduced by Robert Engle in 1982, is a statistical model designed to capture and model the time-varying volatility (variance) observed in financial time series data. Unlike traditional time series models that assume constant variance (homoscedasticity), the ARCH model recognises that the variance of the error term may change over time, depending on the magnitudes of past error terms.

**Mathematical Formulation:**

An ARCH(q) model is defined as:

$$Y_t = \mu + \varepsilon_t$$
$$\varepsilon_t = \sigma_t \cdot z_t, \quad z_t \sim N(0,1)$$
$$\sigma_t^2 = \alpha_0 + \alpha_1 \varepsilon_{t-1}^2 + \alpha_2 \varepsilon_{t-2}^2 + \dots + \alpha_q \varepsilon_{t-q}^2$$

Where:
- $\sigma_t^2$ is the conditional variance at time *t*
- $\alpha_0 > 0$ is a constant
- $\alpha_1, \alpha_2, \dots, \alpha_q \geq 0$ are parameters capturing the effect of past squared errors
- $q$ is the order of the ARCH model

**Key Features:**
- Models **volatility clustering**, a phenomenon where large changes in a variable tend to be followed by large changes, and small changes by small changes.
- The conditional variance depends on past squared residuals, making it dynamic.
- It assumes that the unconditional (long-run) variance is constant even though the conditional variance varies.

**Usage and Applications:**

1. **Financial Risk Management:** ARCH models are extensively used to estimate Value at Risk (VaR) and Expected Shortfall, which are critical metrics for quantifying financial risk.

2. **Option Pricing:** Volatility modelling through ARCH improves the pricing accuracy of options and derivatives, where volatility is a key input.

3. **Portfolio Optimisation:** By modelling time-varying volatility, ARCH helps in dynamic portfolio allocation, adjusting weights based on changing risk profiles.

4. **Stock Market Analysis:** ARCH captures the volatility patterns observed in stock returns, bond yields, and exchange rates, providing more realistic models than those assuming constant variance.

5. **Forecasting Volatility:** It enables forecasting of future volatility, which is essential for trading strategies, hedging decisions, and monetary policy analysis.

The ARCH model paved the way for its generalised version, GARCH (Generalised ARCH), which is more parsimonious and widely adopted in practice.

---

### Q3(a). Describe a few risks associated with Artificial Intelligence. [5 Marks]

Artificial Intelligence (AI), despite its transformative potential, carries several risks that organisations and society must carefully consider:

**1. Bias and Discrimination:**
AI systems learn from historical data, and if that data contains biases—racial, gender, socioeconomic—the models will perpetuate and even amplify these biases. For example, AI-based hiring tools have been found to discriminate against certain demographics because the training data reflected historical hiring patterns that favoured specific groups. This raises serious ethical and legal concerns, particularly in sensitive areas like criminal justice, lending, and healthcare.

**2. Lack of Transparency and Explainability:**
Many advanced AI models, especially deep learning neural networks, operate as "black boxes." Their decision-making processes are opaque, making it difficult for stakeholders to understand why a particular prediction or decision was made. This lack of explainability undermines trust and complicates regulatory compliance, especially in industries like finance and healthcare where accountability is paramount.

**3. Job Displacement and Economic Disruption:**
AI-driven automation poses a significant risk to employment. Routine and repetitive tasks across manufacturing, retail, customer service, and data entry are increasingly being automated, leading to workforce displacement. While AI creates new roles, the transition period can cause significant economic hardship for displaced workers who lack the skills for emerging positions.

**4. Security and Privacy Threats:**
AI systems can be exploited for malicious purposes, including deepfake generation, automated cyberattacks, and mass surveillance. Adversarial attacks—where small, imperceptible modifications to input data cause AI models to make incorrect predictions—represent a growing security concern. Additionally, AI's reliance on large datasets raises significant privacy issues, particularly regarding personal data collection and usage.

**5. Autonomous Decision-Making Risks:**
As AI systems gain more autonomy—such as self-driving vehicles or autonomous weapons—the risk of catastrophic errors without human oversight increases. Errors in autonomous systems can have life-or-death consequences, and the question of accountability (who is responsible when an AI causes harm) remains legally and ethically unresolved.

**6. Concentration of Power:**
AI development is dominated by a few large technology corporations and nations, leading to concentration of economic and political power. This imbalance can exacerbate inequality between organisations and nations that can leverage AI and those that cannot.

These risks underscore the necessity for robust governance frameworks, ethical AI guidelines, and continuous monitoring to ensure AI is developed and deployed responsibly.

---

### Q3(b). Appraise some challenges or limitations we face with Deep Learning. [5 Marks]

Deep Learning, a subset of machine learning based on artificial neural networks with multiple layers, has achieved remarkable successes, but several significant challenges and limitations persist:

**1. Requirement for Massive Datasets:**
Deep learning models are data-hungry. They require enormous volumes of labelled training data to learn effectively. In domains where labelled data is scarce—such as rare medical conditions or niche industrial applications—deep learning models struggle to achieve adequate performance. Data collection, annotation, and curation remain expensive and time-consuming processes.

**2. High Computational Cost:**
Training deep neural networks demands substantial computational resources, including powerful GPUs or TPUs, extensive memory, and significant electrical energy. This makes deep learning prohibitively expensive for small and medium enterprises. Training a single large language model, for instance, can cost millions of dollars and generate a significant carbon footprint, raising sustainability concerns.

**3. Lack of Interpretability:**
Deep learning models are inherently "black boxes." Unlike simpler models such as decision trees or linear regression, it is extremely difficult to understand how deep neural networks arrive at their predictions. This lack of interpretability is a major barrier in critical applications like medical diagnosis, autonomous driving, and financial regulation, where stakeholders need to understand and trust the decision-making process.

**4. Overfitting:**
Deep networks with millions or billions of parameters can memorise training data rather than learning generalisable patterns. Overfitting leads to excellent performance on training data but poor generalisation to unseen data. While techniques like dropout, regularisation, and data augmentation mitigate this, it remains a persistent challenge, especially with limited training data.

**5. Adversarial Vulnerability:**
Deep learning models are susceptible to adversarial attacks, where carefully crafted, barely perceptible perturbations to input data can cause the model to make entirely wrong predictions with high confidence. This vulnerability raises serious concerns for security-critical applications such as autonomous vehicles and facial recognition systems.

**6. Transfer Learning Limitations:**
While transfer learning has improved efficiency by allowing pre-trained models to be fine-tuned for new tasks, performance degrades significantly when the target domain differs substantially from the pre-training domain. Domain adaptation remains an active area of research.

**7. Vanishing and Exploding Gradients:**
During training, gradients can become extremely small (vanishing) or extremely large (exploding) as they propagate through many layers. Although architectures like LSTMs, GRUs, and ResNets have addressed this to some extent, it remains a fundamental challenge in training very deep networks.

In conclusion, while deep learning continues to push boundaries, addressing these limitations through better algorithms, efficient architectures, and ethical frameworks is essential for its sustainable advancement.

---

## Assignment Set – 2

---

### Q4(a). Discuss ANN classification models. [5 Marks]

Artificial Neural Network (ANN) classification models are supervised learning algorithms inspired by the structure and functioning of the human brain. They consist of interconnected nodes (neurons) organised in layers, and are widely used for classification tasks where the goal is to assign input data to one of several predefined categories.

**Architecture of ANN for Classification:**

An ANN classification model typically comprises three types of layers:

1. **Input Layer:** Receives the raw feature data. Each neuron in this layer corresponds to one input feature. For example, in a customer churn classification problem, features might include age, tenure, monthly charges, and contract type.

2. **Hidden Layers:** One or more intermediate layers where the actual computation and learning occur. Each neuron applies a weighted sum of its inputs, adds a bias, and passes the result through an activation function (such as ReLU, sigmoid, or tanh). Multiple hidden layers allow the network to learn complex, non-linear relationships.

3. **Output Layer:** Produces the final classification result. For binary classification, a single neuron with a sigmoid activation function outputs a probability (0 or 1). For multi-class classification, the softmax activation function is used, outputting a probability distribution across all classes.

**Training Process:**

1. **Forward Propagation:** Input data flows through the network, layer by layer, producing an output prediction.
2. **Loss Computation:** The difference between the predicted output and actual label is measured using a loss function (e.g., binary cross-entropy for binary classification, categorical cross-entropy for multi-class).
3. **Backpropagation:** Gradients of the loss with respect to each weight are computed, and weights are updated using optimisation algorithms like Stochastic Gradient Descent (SGD), Adam, or RMSprop.
4. **Iteration:** This process is repeated over many epochs until the model converges to minimal loss.

**Types of ANN Classification Models:**

- **Perceptron:** The simplest ANN with a single layer, suitable for linearly separable data.
- **Multi-Layer Perceptron (MLP):** Contains one or more hidden layers and can model non-linear decision boundaries.
- **Radial Basis Function (RBF) Networks:** Use radial basis functions as activation functions, effective for pattern recognition.

**Applications:**
- Image recognition and classification
- Sentiment analysis and text classification
- Medical diagnosis (disease detection)
- Fraud detection in financial transactions
- Customer segmentation and churn prediction

ANN classification models are powerful tools that, when properly tuned and trained, can achieve high accuracy on complex classification problems.

---

### Q4(b). Explain the classification of layers of CNN. [5 Marks]

Convolutional Neural Networks (CNNs) are specialised deep learning architectures primarily designed for processing structured grid data such as images. CNNs consist of several distinct types of layers, each serving a specific function in the feature extraction and classification pipeline.

**1. Convolutional Layer:**
The convolutional layer is the core building block of a CNN. It applies learnable filters (kernels) that slide across the input image, performing element-wise multiplication and summation to produce feature maps. Each filter detects specific features such as edges, textures, or patterns. Key parameters include:
- **Filter size** (e.g., 3×3, 5×5)
- **Stride** (step size of filter movement)
- **Padding** (zero-padding to preserve spatial dimensions)

Multiple convolutional layers stacked together enable the network to learn hierarchical features—from low-level edges in early layers to high-level objects in deeper layers.

**2. Activation Layer (ReLU):**
After each convolution operation, an activation function—most commonly Rectified Linear Unit (ReLU)—is applied element-wise. ReLU replaces all negative values with zero ($f(x) = \max(0, x)$), introducing non-linearity into the model. This enables the CNN to learn complex, non-linear mappings between inputs and outputs.

**3. Pooling Layer:**
The pooling layer reduces the spatial dimensions (height and width) of the feature maps, thereby reducing computational cost and controlling overfitting. Common types include:
- **Max Pooling:** Selects the maximum value within each pooling window, retaining the most prominent features.
- **Average Pooling:** Computes the average value within each pooling window, providing a smoother downsampling.

Pooling makes the representation invariant to small translations in the input.

**4. Fully Connected (Dense) Layer:**
After several convolutional and pooling operations, the feature maps are flattened into a one-dimensional vector and fed into fully connected layers. These layers function like a traditional neural network, combining the extracted features to perform the final classification. Each neuron in a fully connected layer is connected to every neuron in the previous layer.

**5. Dropout Layer:**
Dropout is a regularisation technique where randomly selected neurons are ignored during training to prevent overfitting. A typical dropout rate is 0.25–0.5, meaning 25–50% of neurons are deactivated in each training iteration.

**6. Output Layer:**
The final layer uses a softmax activation for multi-class classification or sigmoid for binary classification, producing probability scores for each class.

These layers work together to enable CNNs to automatically learn spatial hierarchies of features, making them exceptionally effective for image classification, object detection, and visual recognition tasks.

---

### Q5(a). Describe the classification of RNN based upon architecture. [5 Marks]

Recurrent Neural Networks (RNNs) are a class of neural networks designed to handle sequential data by maintaining an internal memory state. RNNs can be classified based on the relationship between input and output sequences, leading to several architectural variants:

**1. One-to-One (Vanilla Neural Network):**
This is the simplest architecture where there is a single input and a single output. Technically, this is a standard feedforward network and does not leverage the recurrence mechanism. It is included in the taxonomy for completeness.
- **Example:** Standard image classification.

**2. One-to-Many:**
A single input produces a sequence of outputs. The network receives one input and generates multiple outputs over successive time steps.
- **Example:** Image captioning, where a single image is input and the model generates a sequence of words describing the image. Music generation from a single seed note is another example.

**3. Many-to-One:**
A sequence of inputs produces a single output. The network processes the entire input sequence and produces one consolidated output at the final time step.
- **Example:** Sentiment analysis, where a sequence of words (a sentence or review) is input and the model outputs a single sentiment label (positive, negative, or neutral).

**4. Many-to-Many (Equal Length):**
A sequence of inputs produces a sequence of outputs of the same length. Each input time step corresponds to an output time step.
- **Example:** Named Entity Recognition (NER), where each word in a sentence is tagged with its entity category (person, organisation, location, etc.). Part-of-speech tagging is another application.

**5. Many-to-Many (Unequal Length / Encoder-Decoder):**
A sequence of inputs produces a sequence of outputs of a different length. This is typically implemented using an encoder-decoder (sequence-to-sequence) architecture, where the encoder processes the input sequence into a fixed-length context vector, and the decoder generates the output sequence from this context.
- **Example:** Machine translation (translating a sentence from English to French, where input and output lengths differ). Text summarisation and conversational AI also use this architecture.

**Advanced RNN Variants:**

Beyond these architectural classifications, RNNs have evolved to address limitations like the vanishing gradient problem:
- **LSTM (Long Short-Term Memory):** Uses gating mechanisms (input, forget, output gates) to selectively retain or discard information.
- **GRU (Gated Recurrent Unit):** A simplified variant of LSTM with fewer parameters, using reset and update gates.
- **Bidirectional RNN:** Processes the sequence in both forward and backward directions, capturing context from both past and future.

These architectural classifications enable RNNs to be applied across a wide spectrum of sequential and temporal tasks.

---

### Q5(b). Illustrate the difference between SARSA and Q-Learning. [5 Marks]

SARSA and Q-Learning are two fundamental temporal-difference (TD) reinforcement learning algorithms used by agents to learn optimal policies through interaction with an environment. While they share a common framework, they differ critically in how they update their action-value (Q) estimates.

**SARSA (State-Action-Reward-State-Action):**

SARSA is an **on-policy** algorithm, meaning it learns the value of the policy that the agent is actually following, including its exploration behaviour.

**Update Rule:**

$$Q(s, a) \leftarrow Q(s, a) + \alpha \left[ r + \gamma \cdot Q(s', a') - Q(s, a) \right]$$

Where $a'$ is the **actual action taken** in state $s'$ according to the current policy (e.g., ε-greedy).

**Q-Learning:**

Q-Learning is an **off-policy** algorithm, meaning it learns the value of the optimal policy regardless of the agent's current behaviour policy.

**Update Rule:**

$$Q(s, a) \leftarrow Q(s, a) + \alpha \left[ r + \gamma \cdot \max_{a'} Q(s', a') - Q(s, a) \right]$$

Where $\max_{a'} Q(s', a')$ represents the **maximum Q-value** across all possible actions in state $s'$, not necessarily the action actually taken.

**Key Differences:**

| Aspect | SARSA | Q-Learning |
|--------|-------|------------|
| **Policy Type** | On-policy | Off-policy |
| **Update Basis** | Uses actual next action $a'$ taken by the agent | Uses the greedy (best) action regardless of what was actually taken |
| **Exploration Impact** | Exploration actions directly affect learning; learns a "safer" policy | Exploration does not affect the learned Q-values; learns the optimal policy |
| **Convergence** | Converges to the optimal policy under the current exploration strategy | Converges to the globally optimal policy |
| **Risk Sensitivity** | More conservative; avoids risky states because it accounts for exploratory (potentially poor) actions | More aggressive; always targets the best outcome regardless of exploration |
| **Use Case** | Preferred in environments where safety matters (e.g., robotics, real-world control) | Preferred when the goal is to find the absolute best policy (e.g., game playing) |

**Illustrative Example:**

Consider a grid-world with a cliff. SARSA, being on-policy, learns to take a longer but safer path away from the cliff because its updates account for the chance of exploratory moves falling off the cliff. Q-Learning, being off-policy, learns the shorter path right along the cliff edge because it always assumes the optimal (greedy) action will be taken, ignoring the risk of exploration.

Both algorithms are foundational in reinforcement learning and are chosen based on the risk tolerance and requirements of the application domain.

---

### Q6(a). Demonstrate the phases we need for doing Neural Network Analysis. [5 Marks]

Neural Network Analysis involves a systematic sequence of phases, each critical to building an effective and reliable model. The key phases are:

**Phase 1: Problem Definition and Data Collection**
The first step is to clearly define the problem—whether it is a classification, regression, or clustering task. Once defined, relevant data is collected from appropriate sources such as databases, sensors, APIs, or public datasets. The quality and quantity of data directly influence the neural network's performance. This phase also involves understanding the business context and defining success metrics (accuracy, precision, recall, F1-score, etc.).

**Phase 2: Data Preprocessing**
Raw data is rarely ready for modelling. This phase involves:
- **Handling missing values** through imputation or removal.
- **Normalisation/Standardisation** to scale features to a uniform range (e.g., Min-Max scaling or Z-score normalisation), which is essential for gradient-based optimisation.
- **Encoding categorical variables** using one-hot encoding or label encoding.
- **Feature selection and extraction** to identify the most relevant inputs, reducing dimensionality and noise.
- **Splitting data** into training, validation, and test sets (commonly 70:15:15 or 80:10:10 splits).

**Phase 3: Network Architecture Design**
This phase involves selecting and designing the neural network structure:
- **Choosing the type** of network (ANN, CNN, RNN, etc.) based on the problem type.
- **Determining the number of layers** and neurons per layer.
- **Selecting activation functions** (ReLU, sigmoid, tanh, softmax) for hidden and output layers.
- **Defining the loss function** (cross-entropy for classification, MSE for regression).
- **Choosing the optimiser** (SGD, Adam, RMSprop) and setting the learning rate.

**Phase 4: Model Training**
The network is trained on the training data through iterative forward and backward propagation:
- **Forward pass** computes predictions.
- **Loss calculation** measures the error.
- **Backpropagation** computes gradients.
- **Weight updates** adjust parameters to minimise loss.
- Training continues across multiple epochs, with the validation set used to monitor for overfitting.

**Phase 5: Model Evaluation and Testing**
The trained model is evaluated on the unseen test set using relevant metrics:
- **Classification:** Accuracy, precision, recall, F1-score, confusion matrix, ROC-AUC.
- **Regression:** MAE, MSE, RMSE, R-squared.
- This phase validates the model's generalisation capability.

**Phase 6: Hyperparameter Tuning and Optimisation**
Based on evaluation results, hyperparameters (learning rate, batch size, number of layers, dropout rate, etc.) are fine-tuned using techniques like grid search, random search, or Bayesian optimisation to improve performance.

**Phase 7: Deployment and Monitoring**
The final model is deployed into a production environment (via APIs, embedded systems, or cloud services). Post-deployment monitoring ensures the model continues to perform well, with retraining scheduled as new data becomes available or performance degrades.

---

### Q6(b). Reframe some algorithms commonly used with Image Recognition Systems. [5 Marks]

Image recognition systems leverage various machine learning and deep learning algorithms to identify, classify, and interpret visual content. The most commonly used algorithms are:

**1. Convolutional Neural Networks (CNNs):**
CNNs are the backbone of modern image recognition. They use convolutional layers to automatically extract spatial features (edges, textures, shapes) from images, followed by pooling layers for dimensionality reduction and fully connected layers for classification. Notable architectures include:
- **LeNet-5:** One of the earliest CNNs, designed for handwritten digit recognition.
- **AlexNet:** Won the 2012 ImageNet competition, popularising deep learning for image recognition.
- **VGGNet:** Uses very deep architectures (16–19 layers) with small 3×3 filters, achieving high accuracy.

**2. ResNet (Residual Networks):**
ResNet introduced skip connections (residual connections) that allow gradients to flow directly through the network, enabling training of extremely deep networks (50, 101, or even 152 layers) without degradation. ResNet significantly improved accuracy on complex image classification benchmarks.

**3. GoogLeNet / Inception:**
The Inception architecture uses parallel convolutions of different filter sizes within the same layer (1×1, 3×3, 5×5), capturing features at multiple scales simultaneously. This makes the network wider rather than deeper, improving computational efficiency while maintaining high accuracy.

**4. YOLO (You Only Look Once):**
YOLO is a real-time object detection algorithm that frames detection as a single regression problem, predicting bounding boxes and class probabilities simultaneously. It is extremely fast, making it ideal for applications requiring real-time processing such as autonomous driving and surveillance.

**5. Support Vector Machines (SVM):**
Before the deep learning era, SVMs were widely used for image classification. SVMs find the optimal hyperplane that maximises the margin between classes in high-dimensional feature space. They are still effective for smaller datasets or when combined with hand-crafted feature extractors like HOG (Histogram of Oriented Gradients).

**6. Transfer Learning Models:**
Pre-trained models such as VGG, ResNet, and EfficientNet, trained on massive datasets like ImageNet, are fine-tuned for specific image recognition tasks. Transfer learning dramatically reduces training time and data requirements, making it feasible to build accurate image classifiers with limited domain-specific data.

**7. R-CNN (Region-based CNN) Family:**
R-CNN, Fast R-CNN, and Faster R-CNN are designed for object detection and localisation. They combine region proposals with CNN-based feature extraction, achieving high accuracy in identifying and localising objects within images.

These algorithms, individually or in combination, power modern image recognition systems used in healthcare (medical imaging), autonomous vehicles, facial recognition, satellite imagery analysis, and retail (visual search).

---

*End of Assignment*
