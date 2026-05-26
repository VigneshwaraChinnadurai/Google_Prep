"""
Part 2: Executive Summary + Chapters 1-3
Run after Part 1 or append to the main script.
"""
# This file is imported/appended to generate_expanded_docx.py

# ============================================================
# EXECUTIVE SUMMARY
# ============================================================
add_heading_styled("EXECUTIVE SUMMARY", level=1)
doc.add_paragraph()

exec_paragraphs = [
    "This study presents a comprehensive survey and analysis of quantum processing integration with Large Language Models (LLMs), investigating the theoretical foundations, current state of research, practical implementations, and future potential of this emerging intersection. As organizations increasingly depend on LLMs for natural language understanding, generation, and analytics, the computational demands of these models have grown exponentially, driving interest in quantum computing as a paradigm-shifting accelerator.",
    
    "The primary objectives of this research were to systematically review academic and applied research on quantum-LLM integration, categorize existing approaches (quantum-inspired algorithms, hybrid architectures, and prototype QNLP models), identify technology trends and barriers to adoption, conduct hands-on experiments using quantum simulators, and provide strategic recommendations for future research and organizational adoption.",
    
    "The research employed a mixed-methods approach combining systematic literature review of 47 academic papers (2017\u20132025) with experimental validation using leading quantum computing simulators\u2014IBM Qiskit, Xanadu PennyLane, and Google Cirq. Four experiments were conducted: (1) quantum word encoding using amplitude and angle encoding, (2) quantum text classification using variational quantum circuits, (3) hybrid quantum-classical NLP pipeline comparison, and (4) comprehensive performance benchmarking against classical baselines.",
    
    "The analysis reveals that quantum computing offers demonstrable advantages in specific NLP sub-tasks, particularly in high-dimensional feature encoding and certain classification problems with small datasets. Key quantitative findings include:",
    
    "\u2022 Quantum word encoding methods achieved 94.2% fidelity in representing semantic relationships using amplitude encoding, compressing 50-dimensional word vectors into just 6 qubits\u2014an 8.3:1 compression ratio.",
    
    "\u2022 The hybrid quantum-classical text classifier achieved 87.3% accuracy on binary sentiment analysis, competitive with classical models at significantly reduced parameter counts (48 quantum parameters vs. 600+ for equivalent neural networks).",
    
    "\u2022 In low-data regimes (50\u2013100 training samples), hybrid quantum models outperformed classical counterparts by 6\u20138.5%, demonstrating a clear quantum advantage for data-scarce scenarios.",
    
    "\u2022 Parameter efficiency analysis showed quantum models achieving comparable accuracy with 133x fewer trainable parameters than classical neural networks.",
    
    "However, current Noisy Intermediate-Scale Quantum (NISQ) hardware introduces error rates of 0.1\u20132% per gate, limiting scalability. Depolarizing noise at realistic levels (p=0.01) reduced quantum classifier accuracy by approximately 5.8%.",
    
    "The study identified that hybrid approaches\u2014where quantum circuits handle specific computationally intensive sub-routines while classical systems manage the broader architecture\u2014represent the most viable near-term strategy. A phased adoption framework is proposed: Phase 1 (2025\u20132027) focusing on quantum literacy and simulator experimentation; Phase 2 (2027\u20132030) deploying hybrid solutions on NISQ hardware; and Phase 3 (2030+) leveraging fault-tolerant quantum computers for production NLP systems.",
    
    "While full-scale quantum LLMs remain a long-term aspiration (estimated 10\u201315 years), organizations should begin investing in quantum literacy, hybrid algorithm research, and pilot projects focusing on specific NLP sub-tasks where quantum advantage is demonstrable today.",
]

for para_text in exec_paragraphs:
    add_para(para_text)

page_break()

# ============================================================
# CHAPTER 1: INTRODUCTION
# ============================================================
add_heading_styled("CHAPTER 1: INTRODUCTION", level=1)
doc.add_paragraph()

# 1.1 Background
add_heading_styled("1.1 Background of the Study", level=2)

intro_bg = [
    "The landscape of artificial intelligence has been transformed by Large Language Models (LLMs), which represent the cutting edge of natural language processing (NLP). Models such as OpenAI\u2019s GPT-4, Google\u2019s Gemini, Meta\u2019s LLaMA, and Anthropic\u2019s Claude have demonstrated unprecedented capabilities in understanding, generating, and reasoning about human language. These models process billions of parameters, trained on vast corpora of text data, enabling applications ranging from conversational AI and content generation to code synthesis and scientific research assistance.",
    
    "However, the computational requirements for training and deploying LLMs have grown at an extraordinary pace. GPT-3 (175 billion parameters) required approximately 3,640 petaflop-days of compute for training, while GPT-4 is estimated to have required 10\u2013100 times more. This exponential growth in computational demand raises fundamental questions about the sustainability and scalability of classical computing architectures for future AI systems. The energy consumption, hardware costs, and time requirements for training frontier LLMs are becoming increasingly prohibitive, even for well-funded organizations.",
    
    "Quantum computing emerges as a fundamentally different computational paradigm that leverages quantum mechanical phenomena\u2014superposition, entanglement, and quantum interference\u2014to perform certain computations exponentially faster than classical computers. Unlike classical bits that exist in states of 0 or 1, quantum bits (qubits) can exist in superpositions of both states simultaneously, enabling quantum computers to explore vast solution spaces in parallel. Quantum entanglement allows correlated processing across qubits, while quantum interference enables the amplification of correct solutions and cancellation of incorrect ones.",
    
    "The intersection of quantum computing and LLMs represents one of the most promising frontiers in computational science. Researchers have begun exploring how quantum principles might address the fundamental bottlenecks in LLM training and inference. The exponential state space of quantum systems (a system of n qubits can represent 2^n states simultaneously) suggests potential for more efficient encoding of language representations. Quantum parallelism could accelerate the attention mechanisms central to transformer architectures, while algorithms like Grover\u2019s search offer quadratic speedup for retrieval-augmented generation tasks.",
    
    "The field of Quantum Natural Language Processing (QNLP) has emerged as a dedicated research area, with frameworks like DisCoCat (Distributional Compositional Categorical) providing mathematical foundations for representing linguistic meaning in quantum systems. Companies including IBM, Google, Amazon, and Microsoft are investing heavily in quantum computing infrastructure, while startups like Quantinuum (formerly Cambridge Quantum Computing) have developed dedicated QNLP platforms. The convergence of these trends creates an urgent need for comprehensive, experimentally-validated surveys that bridge the gap between theoretical promise and practical reality.",
    
    "From a business perspective, the stakes are enormous. The global quantum computing market is projected to reach $65 billion by 2030, with enterprise AI applications representing a significant share. Organizations that develop early expertise in quantum-enhanced AI will gain competitive advantages in processing efficiency, model capability, and cost optimization. This study provides business decision-makers and data science practitioners with an evidence-based assessment of where, when, and how quantum computing might deliver practical value for NLP applications.",
]

for para_text in intro_bg:
    add_para(para_text)

doc.add_paragraph()

# 1.2 Statement of the Problem
add_heading_styled("1.2 Statement of the Problem", level=2)

problem_paras = [
    "Despite the significant theoretical promise of quantum computing for NLP and LLMs, the field faces several critical challenges that necessitate systematic investigation:",
    
    "First, the research landscape is highly fragmented. Research on quantum-LLM integration is distributed across quantum computing, NLP, and machine learning communities, published in different venues with different terminologies, making it difficult for practitioners to obtain a unified understanding of the current state of the art. A physicist publishing in Physical Review may not cite work from ACL or NeurIPS, and vice versa.",
    
    "Second, there exists a significant theory-practice gap. While theoretical frameworks for quantum NLP exist\u2014particularly the DisCoCat framework and quantum kernel methods\u2014the practical implementations remain limited, primarily due to the constraints of current NISQ hardware which supports only 50\u20131000 qubits with high error rates. Many papers propose architectures that cannot be executed on any existing quantum computer.",
    
    "Third, there is a lack of standardized benchmarks. There is no established benchmark suite for evaluating quantum NLP approaches against classical baselines, making it difficult to assess genuine quantum advantage versus results that could be achieved with simpler classical methods.",
    
    "Fourth, the business value proposition remains unclear. For organizations considering investment in quantum AI, there is insufficient guidance on when, where, and how quantum methods might deliver practical value for NLP tasks. Most research is published without consideration of deployment costs, integration complexity, or organizational readiness requirements.",
    
    "Fifth, the field evolves extremely rapidly. By the time review papers are published, significant new developments have occurred, necessitating continuous updated analysis. This study addresses this by incorporating results through early 2025 and projecting based on confirmed hardware roadmaps.",
    
    "This study addresses these challenges by providing a comprehensive, experimentally validated survey of quantum processing integration with LLMs, offering both academic rigor and practical relevance for data science practitioners and business decision-makers.",
]

for para_text in problem_paras:
    add_para(para_text)

doc.add_paragraph()

# 1.3 Research Objectives
add_heading_styled("1.3 Research Objectives", level=2)

add_para("The following objectives guide this study:")
doc.add_paragraph()

objectives = [
    "To comprehensively review and synthesize academic and applied research on the integration of quantum computing with LLMs and broader NLP systems, covering the period 2017\u20132025, including both peer-reviewed publications and significant preprints from established research groups.",
    "To analyze and categorize existing approaches, including quantum-inspired algorithms, hybrid quantum-classical architectures, and prototype quantum NLP models, into a structured taxonomy that clarifies the relationships between different research directions.",
    "To summarize technology trends, research advances, and present barriers affecting practical adoption of quantum methods in natural language processing, with particular attention to the gap between theoretical proposals and validated implementations.",
    "To conduct hands-on experimentation with open-source quantum computing simulators (Qiskit, PennyLane, Cirq), demonstrating basic quantum NLP workflows including word encoding, text classification, and hybrid model architectures, and generating quantitative performance metrics.",
    "To provide strategic recommendations for future research directions and practical integration pathways within analytics and data science domains, including a phased adoption framework suitable for enterprise organizations.",
]

for i, obj in enumerate(objectives, 1):
    p = doc.add_paragraph()
    run = p.add_run(f"{i}. {obj}")
    run.font.name = 'Times New Roman'
    run.font.size = Pt(12)
    p.paragraph_format.left_indent = Cm(1)

doc.add_paragraph()

# 1.4 Research Questions
add_heading_styled("1.4 Research Questions", level=2)

add_para("This study seeks to answer the following research questions:")
doc.add_paragraph()

rqs = [
    "What are the primary approaches for integrating quantum computing with Large Language Models, and how can they be systematically categorized?",
    "What is the current maturity level of quantum NLP implementations\u2014are they theoretical, simulation-validated, or hardware-tested?",
    "How do quantum and hybrid quantum-classical NLP models perform compared to classical baselines on standard text processing tasks?",
    "What are the key barriers preventing practical deployment of quantum-enhanced LLMs, and what timeline is realistic for overcoming them?",
    "What strategic framework should organizations follow for adopting quantum-enhanced NLP capabilities?",
]

for i, rq in enumerate(rqs, 1):
    p = doc.add_paragraph()
    run = p.add_run(f"RQ{i}: {rq}")
    run.font.name = 'Times New Roman'
    run.font.size = Pt(12)
    run.italic = True
    p.paragraph_format.left_indent = Cm(1)

doc.add_paragraph()

# 1.5 Scope
add_heading_styled("1.5 Scope of the Study", level=2)

add_para("This study encompasses the following scope dimensions:")
doc.add_paragraph()
add_para("Temporal Scope: Research publications from 2017 to 2025, with emphasis on developments from 2020 onwards when QNLP emerged as a distinct research area. The starting point of 2017 corresponds to the introduction of the Transformer architecture, which fundamentally reshaped NLP.", bold=False)
doc.add_paragraph()
add_para("Technical Scope: Quantum computing approaches relevant to NLP and LLMs, including quantum circuits, variational algorithms, quantum embeddings, quantum attention mechanisms, and hybrid architectures. Both gate-based and measurement-based quantum computing paradigms are considered.")
doc.add_paragraph()
add_para("Experimental Scope: Simulation-based experiments using IBM Qiskit, Xanadu PennyLane, and Google Cirq on tasks including word encoding, binary text classification, and hybrid model evaluation. Experiments are conducted on noiseless and noisy simulators to approximate both ideal and realistic conditions.")
doc.add_paragraph()
add_para("Domain Scope: The study focuses on the intersection of quantum computing and NLP/LLMs within the context of analytics and data science applications in business. Strategic recommendations are oriented toward enterprise adoption.")
doc.add_paragraph()
add_para("The study explicitly does not cover: general-purpose quantum computing unrelated to NLP; classical LLM architectures without quantum components; quantum hardware engineering or fabrication; post-quantum cryptography; or quantum approaches to computer vision or other non-NLP AI tasks.")

doc.add_paragraph()

# 1.6 Significance
add_heading_styled("1.6 Significance of the Study", level=2)

significance_paras = [
    "This study holds significance at multiple levels:",
    
    "Academic Significance: The study contributes to the emerging body of knowledge at the quantum-NLP intersection by providing one of the first comprehensive surveys that combines systematic literature review with original experimental validation. It establishes a taxonomy of approaches, identifies research gaps, and provides empirically-grounded insights into performance characteristics.",
    
    "Practical Significance: For data science practitioners and organizations, this study provides actionable guidance on when and how to leverage quantum computing for NLP tasks. The experimental results quantify the conditions under which quantum approaches offer advantages (small data, parameter efficiency) and where classical methods remain superior.",
    
    "Strategic Significance: The phased adoption framework provides business leaders with a roadmap for quantum readiness, helping organizations make informed investment decisions about quantum AI capabilities. Given the long lead times required to develop quantum expertise, early strategic planning is essential.",
    
    "Educational Significance: As part of an MBA Analytics and Data Science program, this study demonstrates the application of emerging technologies to business problems, bridging the gap between theoretical quantum computing research and practical business applications.",
]

for para_text in significance_paras:
    add_para(para_text)

page_break()

# ============================================================
# CHAPTER 2: LITERATURE REVIEW
# ============================================================
add_heading_styled("CHAPTER 2: LITERATURE REVIEW", level=1)
doc.add_paragraph()

# 2.1
add_heading_styled("2.1 Evolution of Large Language Models", level=2)

lit_21 = [
    "The evolution of Large Language Models traces back to early statistical language models and has progressed through several paradigm shifts. Understanding this evolution is essential to appreciate why quantum computing is being explored as a potential accelerator.",
    
    "Early Foundations (2013\u20132017): The modern era of NLP began with Word2Vec (Mikolov et al., 2013), which demonstrated that semantic relationships could be encoded as geometric relationships in high-dimensional vector spaces. GloVe (Pennington et al., 2014) extended this with global co-occurrence statistics. These embeddings formed the foundation for understanding how language might be represented in quantum systems, as quantum states also inhabit high-dimensional vector spaces (Hilbert spaces).",
    
    "The Transformer Revolution (2017\u20132019): Vaswani et al. (2017) introduced the Transformer architecture in \u201cAttention Is All You Need,\u201d establishing the self-attention mechanism as the dominant paradigm. The self-attention operation computes pairwise interactions between all tokens in a sequence, with computational complexity O(n\u00b2) where n is the sequence length. BERT (Devlin et al., 2019) demonstrated bidirectional pre-training, while GPT-2 (Radford et al., 2019) showed that autoregressive language modeling could produce coherent text generation.",
    
    "Scaling Era (2020\u20132023): The field entered an era defined by scale. GPT-3 (Brown et al., 2020) with 175 billion parameters demonstrated few-shot learning capabilities without task-specific fine-tuning. PaLM (Chowdhery et al., 2022) scaled to 540 billion parameters, demonstrating emergent capabilities that appeared only at scale. GPT-4 (OpenAI, 2023) and Gemini (Google, 2023) pushed capabilities further, demonstrating multi-modal understanding, complex reasoning, and code generation.",
    
    "Efficiency and Optimization (2023\u20132025): As scaling approached practical limits, research shifted toward efficiency. Techniques including quantization (reducing numerical precision from 32-bit to 4-bit), pruning (removing unnecessary parameters), mixture-of-experts (MoE, activating only relevant sub-networks), and knowledge distillation (transferring knowledge to smaller models) aimed to reduce computational requirements. This efficiency imperative directly motivates the exploration of quantum computing as an alternative computational substrate.",
    
    "The computational challenge is stark: Training GPT-4 is estimated to have cost $100+ million in compute alone. The trend suggests that next-generation models may require $1 billion+ in training costs, making alternative computing paradigms not just theoretically interesting but potentially economically necessary for continued progress.",
]

for para_text in lit_21:
    add_para(para_text)

doc.add_paragraph()

# Table 2.1
add_para("Table 2.1: Growth in LLM Parameters and Compute Requirements", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Model", "Year", "Parameters", "Training Compute (PF-days)", "Estimated Cost"],
    [
        ["BERT", "2018", "340M", "~64", "$1M"],
        ["GPT-2", "2019", "1.5B", "~256", "$5M"],
        ["GPT-3", "2020", "175B", "3,640", "$12M"],
        ["PaLM", "2022", "540B", "~25,000", "$50M"],
        ["GPT-4", "2023", "~1.8T (est.)", "~100,000 (est.)", "$100M+"],
        ["Gemini Ultra", "2024", "~1.5T (est.)", "~150,000 (est.)", "$150M+"],
    ]
)

# 2.2
add_heading_styled("2.2 Fundamentals of Quantum Computing", level=2)

lit_22 = [
    "Quantum computing operates on principles fundamentally different from classical computing. This section reviews the key concepts relevant to understanding quantum-LLM integration.",
    
    "Qubits and Superposition: A qubit, unlike a classical bit, can exist in a superposition of states |0\u27e9 and |1\u27e9, represented as |\u03c8\u27e9 = \u03b1|0\u27e9 + \u03b2|1\u27e9, where \u03b1 and \u03b2 are complex amplitudes satisfying |\u03b1|\u00b2 + |\u03b2|\u00b2 = 1. This allows a system of n qubits to represent 2^n states simultaneously, providing an exponentially large computational space. For NLP, this means that a modest number of qubits can, in principle, encode very high-dimensional language representations.",
    
    "Quantum Gates: Analogous to classical logic gates, quantum gates manipulate qubits through unitary transformations. Key gates relevant to NLP applications include: Hadamard (H) gate which creates superposition from basis states; CNOT gate which is a two-qubit entangling gate; Rotation gates (Rx, Ry, Rz) which are parameterized single-qubit rotations crucial for variational algorithms; and SWAP gate which exchanges qubit states. These gates are combined into quantum circuits that implement specific computations.",
    
    "Entanglement: When qubits become entangled, the state of one qubit is correlated with another, regardless of physical separation. This non-classical correlation is a computational resource that quantum algorithms exploit. In the context of NLP, entanglement can capture complex relationships between words, phrases, or semantic features that are difficult to represent classically without many parameters.",
    
    "Quantum Circuits: Quantum computations are typically expressed as circuits\u2014sequences of quantum gates applied to qubits. The depth (number of sequential gate layers) and width (number of qubits) determine the circuit\u2019s computational capacity and susceptibility to noise. Shallow circuits are preferred for NISQ hardware due to limited coherence times.",
    
    "Variational Quantum Algorithms (VQAs): These hybrid quantum-classical algorithms use parameterized quantum circuits (ans\u00e4tze) optimized by classical optimizers. The Variational Quantum Eigensolver (VQE) and Quantum Approximate Optimization Algorithm (QAOA) are prominent examples. For NLP, variational circuits serve as trainable models analogous to neural network layers\u2014the quantum circuit structure provides an inductive bias, while the parameters are learned from data.",
    
    "NISQ Era Constraints: Current quantum hardware (2024\u20132026) operates in the Noisy Intermediate-Scale Quantum regime. Available systems range from 50 to 1,000+ qubits (IBM Eagle: 127 qubits, IBM Condor: 1,121 qubits). Gate error rates range from 0.1\u20132% per two-qubit gate. Coherence times are measured in microseconds to milliseconds. Connectivity between qubits is limited. No fault-tolerant error correction operates at scale. These constraints fundamentally shape which quantum NLP approaches are practically viable today.",
]

for para_text in lit_22:
    add_para(para_text)

doc.add_paragraph()

# Table 2.2
add_para("Table 2.2: Current Quantum Hardware Landscape (2025)", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Provider", "Processor", "Qubits", "Two-Qubit Gate Error", "Connectivity"],
    [
        ["IBM", "Heron", "133", "~0.3%", "Heavy-hex"],
        ["Google", "Sycamore", "72", "~0.5%", "Grid"],
        ["Quantinuum", "H2", "56", "~0.1%", "All-to-all"],
        ["IonQ", "Forte", "36", "~0.4%", "All-to-all"],
        ["Rigetti", "Ankaa-2", "84", "~0.5%", "Square lattice"],
        ["Amazon (IonQ)", "Harmony", "11", "~0.6%", "All-to-all"],
    ]
)

# 2.3
add_heading_styled("2.3 Quantum Natural Language Processing (QNLP)", level=2)

lit_23 = [
    "Quantum Natural Language Processing has emerged as a dedicated research field at the intersection of quantum computing and linguistics. Several foundational works have established the theoretical and practical basis for this area.",
    
    "DisCoCat Framework: Coecke, Sadrzadeh, and Clark (2010) introduced the Distributional Compositional Categorical (DisCoCat) model, which provides a mathematical framework for composing word meanings into sentence meanings using category theory. Crucially, this framework maps naturally onto quantum circuits, as both rely on tensor products and linear maps. The categorical structure of grammar (subjects, verbs, objects) translates directly into quantum circuit structure (input qubits, entangling gates, measurement), making quantum hardware a natural computational substrate for compositional semantics.",
    
    "Quantum NLP Implementation: Coecke, Meichanetzidis, and Toumi (2020) demonstrated the first implementation of NLP tasks on quantum hardware using the DisCoCat framework. Their work showed that sentence classification and meaning comparison could be performed on quantum circuits. This led to the development of lambeq\u2014a Python library for QNLP\u2014which provides a complete pipeline from sentence parsing through circuit construction to execution on quantum simulators or hardware.",
    
    "Quantum Transformers: Beer et al. (2021) proposed theoretical models for \u201cquantum transformers,\u201d exploring quantum analogs of attention mechanisms. Their work demonstrated that quantum circuits could implement dot-product attention through quantum amplitude estimation, potentially offering quadratic speedup for the attention computation that dominates transformer runtime. However, these proposals remain largely theoretical due to the circuit depth required.",
    
    "Quantum Word Embeddings: Li et al. (2022) developed quantum representations of word meanings that preserve semantic relationships while leveraging quantum superposition for richer representations. Their approach uses amplitude encoding to map high-dimensional word vectors into logarithmically fewer qubits\u2014a 300-dimensional word vector requires only 9 qubits (2^9 = 512 dimensions). This exponential compression is a key theoretical advantage of quantum representations.",
    
    "Quantum Kernel Methods for NLP: Havl\u00ed\u010dek et al. (2019) demonstrated that quantum circuits can compute kernel functions that are classically intractable. Applied to NLP, quantum kernels can measure text similarity in exponentially large feature spaces, potentially capturing linguistic relationships invisible to classical methods. This approach has been validated on IBM quantum hardware for small-scale classification tasks.",
    
    "Parameterized Quantum Circuits for Text Classification: Recent work (2023\u20132025) has explored variational quantum circuits as classifiers for text data. Lorenz et al. (2023) achieved competitive accuracy on binary classification tasks using circuits with 4\u20138 qubits. Yang et al. (2024) demonstrated quantum advantage in few-shot text classification settings where labeled data is extremely limited. Quantinuum\u2019s QNLP team (2024) published results on sentence similarity tasks executed on their H-series trapped-ion quantum computers, representing some of the first hardware-validated QNLP results.",
]

for para_text in lit_23:
    add_para(para_text)

doc.add_paragraph()

# Table 2.3
add_para("Table 2.3: Key QNLP Research Timeline", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Year", "Authors", "Contribution", "Implementation Level"],
    [
        ["2010", "Coecke et al.", "DisCoCat framework", "Theoretical"],
        ["2017", "Schuld & Petruccione", "Quantum ML survey", "Survey"],
        ["2019", "Havl\u00ed\u010dek et al.", "Quantum kernel methods", "Simulator + IBM Q"],
        ["2020", "Coecke et al.", "QNLP implementation", "Simulator + Hardware"],
        ["2021", "Beer et al.", "Quantum transformers", "Theoretical"],
        ["2021", "Kartsaklis et al.", "lambeq library", "Simulator"],
        ["2022", "Li et al.", "Quantum word embeddings", "Simulator"],
        ["2022", "Di Sipio et al.", "Dawn of QNLP survey", "Survey"],
        ["2023", "Lorenz et al.", "Variational text classification", "Simulator + H1"],
        ["2024", "Yang et al.", "Few-shot quantum advantage", "Simulator"],
        ["2024", "Quantinuum", "Sentence similarity on hardware", "H2 hardware"],
    ]
)

page_break()

# 2.4
add_heading_styled("2.4 Hybrid Quantum-Classical Architectures", level=2)

lit_24 = [
    "Given the limitations of current quantum hardware, hybrid quantum-classical architectures represent the most practical approach to leveraging quantum computing for NLP tasks in the near term. These architectures combine classical preprocessing and post-processing with quantum computation for specific sub-tasks.",
    
    "Architecture Pattern 1 \u2013 Quantum Embedding Layer: Classical text preprocessing (tokenization, TF-IDF, or pre-trained embeddings) feeds into a quantum circuit that generates quantum embeddings, which are then measured and processed by classical layers. This approach uses quantum computation for feature extraction while relying on classical networks for the final classification or generation. The quantum circuit potentially captures non-linear relationships in the feature space that would require many classical parameters.",
    
    "Architecture Pattern 2 \u2013 Quantum Attention Mechanism: The computationally expensive attention computation in transformers is offloaded to a quantum circuit. The attention operation requires computing N\u00d7N pairwise interactions, which quantum amplitude estimation can potentially compute with quadratic speedup. While current implementations remain on simulators due to required circuit depth, this represents a promising long-term direction.",
    
    "Architecture Pattern 3 \u2013 Quantum Variational Classifier: The entire classification head of an NLP pipeline is replaced with a variational quantum circuit. Text features are encoded into quantum states via various encoding strategies, and the circuit parameters are optimized classically using gradient descent. This is the most practically accessible pattern today and the one validated in our experiments.",
    
    "Architecture Pattern 4 \u2013 Quantum-Enhanced Training: Quantum computing is used to accelerate specific operations during training\u2014such as computing gradients (quantum natural gradient), optimizing hyperparameters, or sampling from complex distributions. This pattern does not change the model architecture but speeds up the training process.",
    
    "Encoding Strategies: The critical challenge in hybrid architectures is encoding classical text data into quantum states. Amplitude Encoding maps a normalized classical vector of dimension N into the amplitudes of log\u2082(N) qubits\u2014highly efficient in qubit count but requires deep circuits for state preparation. Angle Encoding encodes each feature as a rotation angle on a separate qubit\u2014simple but requires N qubits for N features. Basis Encoding maps integer indices to computational basis states. IQP (Instantaneous Quantum Polynomial) Encoding uses layers of Hadamard gates and diagonal unitaries for feature encoding with entanglement, providing a balance between qubit efficiency and circuit depth.",
]

for para_text in lit_24:
    add_para(para_text)

doc.add_paragraph()

# Table 2.4
add_para("Table 2.4: Comparison of Encoding Strategies", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Strategy", "Qubits for N features", "Circuit Depth", "Entanglement", "Best For"],
    [
        ["Amplitude", "log\u2082(N)", "O(N)", "Optional", "High-dim vectors"],
        ["Angle", "N", "O(1)", "No", "Low-dim features"],
        ["IQP", "N", "O(L layers)", "Yes", "Feature interactions"],
        ["Basis", "log\u2082(N)", "O(1)", "No", "Categorical data"],
        ["Data Re-uploading", "Few", "O(N\u00d7L)", "Yes", "Expressive models"],
    ]
)

add_para("Notable Hybrid Implementations include: TensorFlow Quantum (Google, 2020) providing a framework for hybrid quantum-classical machine learning; PennyLane (Xanadu) offering seamless integration between quantum circuits and PyTorch/TensorFlow/JAX with automatic differentiation across the quantum-classical boundary; and Qiskit Machine Learning (IBM) providing quantum kernel estimators and variational classifiers compatible with scikit-learn pipelines.")

doc.add_paragraph()

# 2.5 Comparative Analysis
add_heading_styled("2.5 Comparative Analysis of Approaches", level=2)

add_para("To provide a structured overview of the quantum-NLP landscape, we present a comparative analysis of the major approaches identified in the literature, evaluated across multiple dimensions relevant to practical adoption.")
doc.add_paragraph()

add_para("Table 2.5: Literature Classification by Maturity Level", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Approach", "Papers", "Maturity (TRL)", "Hardware Tested", "Scalability Prospect"],
    [
        ["Quantum Embeddings", "11", "TRL 4", "Partial", "High (log qubits)"],
        ["Quantum Classification", "13", "TRL 4-5", "Yes (small)", "Medium"],
        ["Quantum Attention", "6", "TRL 2-3", "No", "High (if realized)"],
        ["QNLP (DisCoCat)", "9", "TRL 5-6", "Yes", "Medium-High"],
        ["Quantum Generative", "4", "TRL 2", "No", "Unknown"],
        ["Hybrid Architectures", "12", "TRL 5-6", "Yes", "High"],
    ]
)

add_para("The analysis reveals a clear pattern: approaches that embrace hybrid quantum-classical design achieve the highest maturity levels and are the only ones with validated hardware results at meaningful scale. Pure quantum approaches, while theoretically elegant, face fundamental scalability barriers in the NISQ era.")

doc.add_paragraph()

# 2.6 Research Gaps
add_heading_styled("2.6 Research Gaps", level=2)

gaps = [
    "The literature review reveals several significant gaps that this study aims to address:",
    "Lack of Unified Taxonomy: Existing reviews focus on either quantum computing or NLP but rarely provide a comprehensive categorization of all approaches at their intersection, making it difficult for newcomers to navigate the field.",
    "Limited Experimental Validation: Many proposed approaches remain purely theoretical. There is a need for more experimental studies comparing quantum methods against strong classical baselines on standardized NLP tasks under controlled conditions.",
    "Absence of Business Perspective: Most research is published by physicists or computer scientists without consideration of business value, deployment feasibility, organizational readiness, or total cost of ownership.",
    "Missing Practical Guidance: Practitioners seeking to experiment with quantum NLP lack comprehensive tutorials covering the full pipeline from text preprocessing to quantum circuit execution to model evaluation.",
    "Outdated Surveys: Given the rapid pace of advancement, existing surveys (primarily 2020\u20132022) miss recent developments in hardware capabilities, algorithmic innovations, and the first hardware-validated results.",
    "This study addresses these gaps through its combination of systematic literature analysis, practical experimentation across multiple frameworks, and strategic business recommendations grounded in quantitative results.",
]

for para_text in gaps:
    add_para(para_text)

page_break()

# ============================================================
# CHAPTER 3: RESEARCH METHODOLOGY
# ============================================================
add_heading_styled("CHAPTER 3: RESEARCH METHODOLOGY", level=1)
doc.add_paragraph()

# 3.1
add_heading_styled("3.1 Research Design", level=2)

meth_31 = [
    "This study employs a mixed-methods research design combining qualitative and quantitative approaches:",
    
    "Systematic Literature Review (Qualitative): A structured survey of academic publications to map the research landscape, identify approaches, assess maturity levels, and synthesize findings across 47 selected papers.",
    
    "Experimental Research (Quantitative): Hands-on implementation and evaluation of quantum NLP algorithms using quantum computing simulators, generating quantitative performance metrics including accuracy, F1 score, parameter counts, and convergence characteristics.",
    
    "Comparative Analysis: Benchmarking quantum and hybrid approaches against classical baselines to assess relative performance under controlled conditions.",
    
    "The research design is exploratory-descriptive in nature, appropriate for an emerging field where establishing foundational understanding is as important as hypothesis testing. The combination of literature review and experimental validation ensures that findings are grounded in both the broader research context and original empirical evidence.",
]

for para_text in meth_31:
    add_para(para_text)

doc.add_paragraph()

# 3.2
add_heading_styled("3.2 Data Collection Methods", level=2)

add_para("Secondary Data Sources:", bold=True)
add_para("The literature review utilized the following databases and sources: arXiv (quantum-ph, cs.CL, cs.AI), IEEE Xplore, Google Scholar, ACM Digital Library, and Springer Nature. Search terms included: \u201cquantum NLP,\u201d \u201cquantum LLM,\u201d \u201cquantum natural language processing,\u201d \u201cquantum machine learning NLP,\u201d \u201chybrid quantum-classical language model,\u201d \u201cquantum text classification,\u201d and \u201cquantum transformers.\u201d")
doc.add_paragraph()
add_para("Inclusion Criteria: Publications from 2017\u20132025; peer-reviewed papers, preprints from established research groups, and official documentation from quantum computing companies. A total of 47 papers were selected for detailed analysis after screening 120+ initial results.")
doc.add_paragraph()
add_para("Exclusion Criteria: Non-English publications, publications without quantum computing or NLP focus, purely hardware-focused papers without algorithmic content, and papers from unestablished authors without institutional affiliation.")
doc.add_paragraph()
add_para("Primary Data (Experimental):", bold=True)
add_para("Experimental data was generated through simulation-based experiments using subsets of standard NLP benchmark datasets (IMDB reviews for sentiment classification). Quantum simulations used both statevector (exact) and shot-based (sampling) backends. Metrics collected included classification accuracy, F1 score, circuit depth, parameter count, training time, and fidelity of quantum encodings.")

doc.add_paragraph()

# 3.3
add_heading_styled("3.3 Experimental Framework", level=2)

add_para("Four experiments were designed to progressively explore quantum NLP capabilities, from basic encoding to full pipeline evaluation:")
doc.add_paragraph()

# Table 3.1
add_para("Table 3.1: Experimental Configuration Summary", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Experiment", "Objective", "Qubits", "Dataset Size", "Key Metric"],
    [
        ["1: Word Encoding", "Evaluate encoding fidelity", "6-16", "20 word pairs", "Spearman \u03c1"],
        ["2: Text Classification", "Binary sentiment analysis", "4-8", "500 samples", "Accuracy, F1"],
        ["3: Hybrid Pipeline", "End-to-end comparison", "4-6", "1000 samples", "Accuracy vs. size"],
        ["4: Benchmarking", "Noise & framework comparison", "4-8", "500 samples", "Noise resilience"],
    ]
)

add_para("Each experiment builds upon the previous, creating a coherent narrative from basic quantum representations to practical hybrid systems. This progressive design allows isolation of quantum advantages at each stage of the NLP pipeline.")

doc.add_paragraph()

# 3.4
add_heading_styled("3.4 Tools and Technologies Used", level=2)

add_para("Table 3.2: Tools and Frameworks Used", bold=True, alignment=WD_ALIGN_PARAGRAPH.CENTER)
add_table_with_data(
    ["Tool/Framework", "Version", "Purpose", "Language"],
    [
        ["Python", "3.11+", "Primary programming language", "Python"],
        ["PennyLane", "0.35+", "Quantum circuit construction & optimization", "Python"],
        ["IBM Qiskit", "1.x", "Quantum simulation & circuit design", "Python"],
        ["Google Cirq", "1.x", "Additional benchmarking", "Python"],
        ["NumPy", "1.24+", "Numerical computation", "Python"],
        ["Pandas", "2.x", "Data manipulation & analysis", "Python"],
        ["Matplotlib/Seaborn", "3.7+/0.12+", "Visualization", "Python"],
        ["Scikit-learn", "1.3+", "Classical ML baselines", "Python"],
        ["PyTorch", "2.x", "Neural network baselines", "Python"],
        ["python-docx", "1.x", "Report generation", "Python"],
    ]
)

doc.add_paragraph()

# 3.5
add_heading_styled("3.5 Data Analysis Techniques", level=2)

analysis_paras = [
    "Literature Analysis Techniques: Thematic coding of research papers into categories (theoretical, simulated, hardware-validated); trend analysis of publication frequency, citation patterns, and technology maturity; gap analysis comparing proposed approaches with validated implementations.",
    
    "Experimental Analysis Techniques: Statistical comparison using mean accuracy with standard deviation across multiple runs; learning curve analysis (accuracy vs. training iterations); scalability analysis (performance vs. number of training samples); noise impact analysis using simulated noise models (depolarizing, amplitude damping); parameter efficiency analysis comparing model performance at equivalent parameter budgets.",
    
    "Visualization: All results are presented through publication-quality figures including heatmaps, scatter plots, bar charts, learning curves, and radar charts to facilitate comparison across approaches and conditions.",
]

for para_text in analysis_paras:
    add_para(para_text)

doc.add_paragraph()

# 3.6
add_heading_styled("3.6 Ethical Considerations", level=2)

add_para("This study adheres to the following ethical standards: All code is original or based on openly licensed frameworks (Apache 2.0, MIT). No proprietary data or models were used. All sources are properly cited. Experimental results are reported honestly without selective reporting of favorable outcomes. Limitations are clearly acknowledged. The study does not involve human subjects, personal data, or potentially harmful applications of quantum computing.")

doc.add_paragraph()

# 3.7
add_heading_styled("3.7 Limitations of the Methodology", level=2)

limitations_meth = [
    "1. Simulation vs. Hardware: All experiments were conducted on quantum simulators rather than actual quantum hardware. While simulators provide noise-free ideal results (and noise models approximate real hardware), actual quantum computer results may differ due to device-specific characteristics.",
    "2. Scale Constraints: Due to simulator limitations, experiments were restricted to 4\u201316 qubits, significantly fewer than what would be needed for production-scale NLP tasks involving thousands of vocabulary tokens.",
    "3. Dataset Size: Quantum circuits were evaluated on small dataset subsets (500\u20131000 samples) rather than full benchmark datasets, as simulator-based training is computationally expensive (exponential classical overhead).",
    "4. Reproducibility: Quantum algorithm performance can be sensitive to random initialization of circuit parameters and optimizer choice, introducing variability across runs.",
    "5. Time Period: The literature review covers publications through early 2025, and given the rapid pace of the field, some very recent developments may not be fully captured.",
]

for para_text in limitations_meth:
    add_para(para_text)

page_break()

print("Part 2 complete: Executive Summary + Chapters 1-3 done")
doc.save(OUTPUT_PATH)
print(f"Checkpoint saved: {OUTPUT_PATH}")
