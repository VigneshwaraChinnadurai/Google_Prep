# Computer Vision & Deep Learning - Interview Concepts

---

## 1. Convolutional Neural Networks (CNNs) — Fundamentals

**Answer:**
CNNs are neural networks designed for spatial data (images, video). They use convolutional layers that apply learnable filters (kernels) to detect local patterns (edges, textures, shapes). Key properties: parameter sharing (same filter applied everywhere), local connectivity (each neuron sees only a small patch), and translation equivariance (detecting a cat works regardless of position).

**Architecture: Input → [Conv → ReLU → Pool]×N → Flatten → FC → Output**

**Core Operations:**
- **Convolution:** Slide a filter across the image, computing dot product at each position → feature map
- **Pooling:** Downsample feature maps (Max Pool or Average Pool) → reduce spatial dimensions, add translation invariance
- **Stride:** Step size of filter movement (stride 2 = halves spatial dimensions)
- **Padding:** Add zeros around input borders (SAME = output size equals input size, VALID = no padding)

**Layman Example:**
Reading a book with a magnifying glass:
- The magnifying glass is the **filter** (sees a small area at a time)
- Moving it across the page is the **convolution** operation
- You look for specific patterns (letters, words) — the filter learns what to look for
- First pass finds edges, second pass finds shapes, third pass finds objects — **hierarchical features**
- Summarizing each paragraph in one sentence is **pooling** (compression while retaining key info)

**Output Size Formula:**
$$O = \frac{W - K + 2P}{S} + 1$$
Where W = input size, K = kernel size, P = padding, S = stride

**Comparison with Fully Connected Networks:**

| Aspect | Fully Connected | CNN |
|--------|----------------|-----|
| Parameters | W×H×C × neurons (huge) | K×K×C_in×C_out (small) |
| Spatial awareness | None (flattened input) | Preserves spatial structure |
| Translation invariance | No | Yes (via weight sharing + pooling) |
| Input size flexibility | Fixed | Can handle varying sizes (with adaptive pool) |
| For 224×224×3 image | 150K+ params per neuron | 3×3×3×64 = 1,728 params per filter |

**Follow-up Questions:**

**Q: Why do CNNs work better than FC networks for images?**
A: Three reasons: (1) Local connectivity — pixels interact mostly with neighbors, so local filters capture spatial patterns efficiently; (2) Parameter sharing — same filter applied everywhere reduces parameters by 1000×; (3) Hierarchical feature learning — early layers detect edges, deeper layers combine them into complex objects.

**Q: What does each layer in a CNN learn?**
A: Layer 1: edges, gradients, colors. Layer 2: textures, corners, simple patterns. Layer 3: object parts (eyes, wheels). Layer 4-5: whole objects, scenes. This was visualized by Zeiler & Fergus (2013) using deconvolution.

**Q: What's the difference between 1×1, 3×3, and 5×5 convolutions?**
A: 1×1 conv = channel-wise mixing (no spatial context), used to reduce/increase channel dimensions cheaply. 3×3 = standard spatial filter, good balance. 5×5 = larger receptive field but more expensive. Two stacked 3×3 convs have same receptive field as one 5×5 but fewer params (2×9=18 vs 25) and more non-linearity.

**Q: What is the receptive field?**
A: The region of the input that influences a particular neuron's output. Grows with depth: after N layers of 3×3 conv, receptive field = 2N+1 pixels. Dilated convolutions increase receptive field without adding parameters. Important for detection/segmentation — neurons need large enough receptive fields to "see" the objects.

**Additional Info:**
- Number of parameters in a conv layer: K × K × C_in × C_out + C_out (bias)
- 1×1 conv (Network-in-Network) is computationally the same as a FC layer applied per spatial position
- Depthwise separable convolution (MobileNet) splits into depthwise (K×K×1 per channel) + pointwise (1×1 mixing) — reduces params by ~K² factor

---

## 2. CNN Architectures Evolution

**Answer:**
CNN architectures have evolved from simple stacking (LeNet, AlexNet) to deeper networks with skip connections (ResNet), efficient designs (MobileNet, EfficientNet), and attention-based approaches (Vision Transformers).

**Architecture Timeline & Comparison:**

| Architecture | Year | Depth | Key Innovation | Top-5 Error (ImageNet) | Params |
|-------------|------|-------|----------------|----------------------|--------|
| LeNet-5 | 1998 | 5 | First practical CNN | — | 60K |
| AlexNet | 2012 | 8 | ReLU, Dropout, GPU training | 15.3% | 60M |
| VGGNet | 2014 | 16/19 | Only 3×3 filters, deep | 7.3% | 138M |
| GoogLeNet/Inception | 2014 | 22 | Inception modules (multi-scale) | 6.7% | 5M |
| ResNet | 2015 | 50/101/152 | Skip connections (residual learning) | 3.6% | 25M (ResNet-50) |
| DenseNet | 2017 | 121/169 | Dense connections (all-to-all) | ~3.5% | 8M |
| MobileNet v2 | 2018 | — | Inverted residuals, depthwise sep | — | 3.4M |
| EfficientNet | 2019 | — | Compound scaling (width+depth+resolution) | 2.9% | 5-66M |
| Vision Transformer (ViT) | 2020 | — | Pure self-attention, no convolutions | ~1.5% | 86-632M |
| ConvNeXt | 2022 | — | Modernized CNN matching ViT | ~1.5% | 29-350M |

**Layman Example:**
Building a skyscraper:
- **AlexNet:** 8-story building. First tall building, proved the concept.
- **VGG:** 19-story building using identical small bricks. Simple but heavy.
- **Inception:** Building with different room sizes on each floor (captures different scales).
- **ResNet:** 152-story building with elevators (skip connections) so information reaches the top without degrading. Without elevators, people on floor 150 would never communicate with the ground floor.
- **MobileNet:** A lightweight prefab house — fast to build, efficient, for mobile deployment.
- **EfficientNet:** Optimally scaling all dimensions together (height, width, room size).
- **ViT:** Completely different architecture — no floors, just one open space where everyone can see everyone (self-attention).

**Follow-up Questions:**

**Q: Why was ResNet's skip connection so important?**
A: Deep networks suffered from vanishing gradients and degradation (deeper = worse training error). Skip connections let gradients flow directly through identity mappings. The network learns residuals F(x) = H(x) - x instead of H(x) directly. If the optimal H(x) ≈ x, it's easier to learn F(x) ≈ 0 than to learn identity. Enabled training 1000+ layer networks.

**Q: What is the Inception module?**
A: It processes input through parallel branches of different filter sizes (1×1, 3×3, 5×5) plus a max-pool branch simultaneously, then concatenates outputs. This captures multi-scale features without choosing a single filter size. 1×1 convolutions before 3×3/5×5 reduce channel dimensions (bottleneck), making it computationally efficient.

**Q: When would you use MobileNet vs. ResNet?**
A: MobileNet for edge/mobile deployment (phones, IoT, real-time inference) where model size and latency matter. ResNet for server-side applications where accuracy is priority and compute is available. MobileNet v2 achieves ~72% ImageNet top-1 with 3.4M params; ResNet-50 achieves ~76% with 25M params.

**Q: How does EfficientNet's compound scaling work?**
A: Instead of arbitrarily scaling depth/width/resolution independently, it uses a compound coefficient φ that uniformly scales all three: depth = α^φ, width = β^φ, resolution = γ^φ, where α·β²·γ² ≈ 2 (to roughly double FLOPs). This balanced scaling consistently outperforms single-dimension scaling.

---

## 3. Residual Networks (ResNet) — Deep Dive

**Answer:**
ResNet introduces skip (shortcut) connections that add the input of a block directly to its output: y = F(x) + x. This enables training of very deep networks (50-1000+ layers) by solving the vanishing gradient problem and the degradation problem.

**Block Types:**
- **Basic Block** (ResNet-18/34): Two 3×3 conv layers + skip connection
- **Bottleneck Block** (ResNet-50/101/152): 1×1 (reduce) → 3×3 (conv) → 1×1 (expand) + skip connection

```
Basic Block:           Bottleneck Block:
x ──┐                  x ──┐
│   │                  │   │
Conv3×3                Conv1×1 (reduce)
│   │                  │   │
Conv3×3                Conv3×3
│   │                  │   │
+ ←─┘                  Conv1×1 (expand)
│                      │   │
ReLU                   + ←─┘
                       │
                       ReLU
```

**Layman Example:**
A game of telephone (passing a message through many people):
- **Without skip connections:** Message degrades with each person. After 100 people, the message is completely garbled (vanishing gradient).
- **With skip connections:** Each person passes both their interpretation AND the original message. The next person can always reference the original. Even after 100 people, the core message survives.

**Comparison of ResNet variants:**

| Variant | Layers | Params | FLOPs | Top-1 Accuracy |
|---------|--------|--------|-------|----------------|
| ResNet-18 | 18 | 11.7M | 1.8G | 69.8% |
| ResNet-34 | 34 | 21.8M | 3.7G | 73.3% |
| ResNet-50 | 50 | 25.6M | 4.1G | 76.1% |
| ResNet-101 | 101 | 44.5M | 7.8G | 77.4% |
| ResNet-152 | 152 | 60.2M | 11.6G | 78.3% |
| ResNeXt-50 | 50 | 25M | 4.2G | 77.8% |
| SE-ResNet-50 | 50 | 28M | 4.1G | 77.6% |

**Follow-up Questions:**

**Q: What's the difference between pre-activation and post-activation ResNet?**
A: Original (post-activation): Conv → BN → ReLU → Conv → BN → Add → ReLU. Pre-activation (He et al. 2016): BN → ReLU → Conv → BN → ReLU → Conv → Add. Pre-activation puts BN/ReLU before conv, making the skip connection a pure identity mapping. This improves gradient flow and generally performs better for very deep networks (1000+ layers).

**Q: What happens when dimensions don't match for the skip connection?**
A: When spatial dimensions or channel counts differ (e.g., after stride-2 conv), use a projection shortcut: 1×1 conv with appropriate stride to match dimensions. Three options: (A) zero-pad extra channels, (B) project only when dims change, (C) always project. Option B is standard in practice.

**Q: How does ResNeXt differ from ResNet?**
A: ResNeXt uses "cardinality" — instead of one wide conv, it uses multiple parallel narrow convolutions (grouped convolutions) then sums them. 32 groups of narrow convs outperform one wide conv with same parameter count. Adds a third dimension (cardinality) alongside depth and width.

**Q: Why doesn't a plain deep network (without skip connections) just learn identity?**
A: In theory it could, but in practice, optimizing through many nonlinear layers to learn identity is hard — the optimization landscape has many local minima. Skip connections make identity the default (just pass input through), and the network only needs to learn the residual deviation from identity.

---

## 4. Transfer Learning & Fine-Tuning

**Answer:**
Transfer learning uses knowledge from a model trained on one task (source) to improve learning on a different task (target). Typically: take a model pre-trained on ImageNet (14M images, 1000 classes), replace the final classification head, and fine-tune on your task. Works because early CNN layers learn universal features (edges, textures) that transfer across tasks.

**Strategies (ordered by target data size):**

| Strategy | Target data | What to do | When |
|----------|-------------|------------|------|
| Feature extraction | Very small (<1K) | Freeze all layers, only train new head | Target very similar to source |
| Fine-tune top layers | Small (1K-10K) | Freeze early layers, train last few + head | Moderate similarity |
| Fine-tune all layers | Medium (10K-100K) | Unfreeze all, use small learning rate | Different from source |
| Train from scratch | Large (100K+) | Random init, full training | Very different domain or enough data |

**Layman Example:**
A chef trained in French cuisine (ImageNet) moving to a Japanese restaurant:
- **Feature extraction:** They already know knife skills, heat control, timing (frozen universal skills). Just learn the specific Japanese recipes (new head).
- **Fine-tune top layers:** Adjust seasoning preferences and plating style (later layers) while keeping core techniques (early layers frozen).
- **Full fine-tuning:** Gradually adapt everything to Japanese cooking, but starting from French knowledge (not from zero) — still much faster than training a new chef from scratch.

**Follow-up Questions:**

**Q: Why do early layers transfer better than later layers?**
A: Early layers learn low-level features (Gabor-like edges, color blobs) that are universal across all visual tasks. Later layers learn increasingly task-specific features (dog faces, car wheels for ImageNet). The more different your target task, the more later layers need retraining.

**Q: What learning rate should you use for fine-tuning?**
A: 10-100× smaller than training from scratch. Typical: 1e-4 to 1e-5 for fine-tuning (vs. 1e-2 to 1e-3 for scratch training). Use discriminative learning rates: even smaller for early layers (1e-5), larger for later layers (1e-4), largest for new head (1e-3). Prevents catastrophic forgetting of pre-trained knowledge.

**Q: What is catastrophic forgetting and how do you prevent it?**
A: When fine-tuning destroys useful pre-trained features by updating weights too aggressively. Prevention: (1) Low learning rate, (2) Gradual unfreezing (unfreeze layers one by one from top), (3) Discriminative learning rates, (4) Early stopping on validation loss, (5) EWC (Elastic Weight Consolidation) for continual learning.

**Q: When does transfer learning NOT work?**
A: When source and target domains are very different (e.g., ImageNet → medical X-rays may need domain-specific pre-training). When target task has fundamentally different input structure (e.g., 3D point clouds). When you have enough target data that starting fresh gives better results. When source model has strong biases that hurt target performance.

**Q: What's the difference between transfer learning and domain adaptation?**
A: Transfer learning assumes some labeled target data for fine-tuning. Domain adaptation handles the case where target domain has no labels — it aligns source and target feature distributions so the classifier trained on source works on target. Methods: adversarial training (DANN), maximum mean discrepancy (MMD).

---

## 5. Object Detection

**Answer:**
Object detection = classification + localization for multiple objects in an image. Output: bounding boxes (x, y, w, h) + class labels + confidence scores. Two families: two-stage (propose regions then classify) and one-stage (direct prediction).

**Architecture Families:**

| Family | Method | Speed | Accuracy | Example |
|--------|--------|-------|----------|---------|
| Two-stage | Region proposals → classify each | Slow | Higher | Faster R-CNN, Cascade R-CNN |
| One-stage anchor-based | Direct prediction at anchor positions | Fast | Good | YOLO, SSD, RetinaNet |
| One-stage anchor-free | Predict center + size directly | Fast | Good | CenterNet, FCOS |
| Transformer-based | Set prediction, no NMS needed | Moderate | High | DETR, DINO |

**Evolution:**

| Model | Year | Key Innovation | FPS | mAP (COCO) |
|-------|------|---------------|-----|-------------|
| R-CNN | 2014 | CNN features for proposals | 0.02 | 58.5 |
| Fast R-CNN | 2015 | ROI Pooling, shared features | 2 | 66.9 |
| Faster R-CNN | 2015 | RPN (learnable proposals) | 7 | 73.2 |
| SSD | 2016 | Multi-scale one-stage | 59 | 74.3 |
| YOLOv1 | 2016 | Single-shot grid prediction | 45 | 63.4 |
| RetinaNet | 2017 | Focal Loss (class imbalance) | 8 | 77.1 |
| YOLOv3 | 2018 | Multi-scale + FPN | 30 | 73.0 |
| DETR | 2020 | Transformer, no NMS/anchors | 28 | 77.2 |
| YOLOv8 | 2023 | Anchor-free YOLO | 80+ | 80+ |

**Layman Example:**
Finding animals in a "Where's Waldo?" book:
- **Two-stage (Faster R-CNN):** First, quickly circle all areas that might contain something interesting (region proposals). Then, carefully examine each circle to identify the animal and draw a tight box.
- **One-stage (YOLO):** Divide the page into a grid. For each cell, simultaneously predict "what's here?" and "how big is it?" — much faster but might miss small or overlapping animals.
- **DETR:** Look at the entire page at once (self-attention), predict all animals as a set. No need to circle areas first or remove duplicate detections.

**Key Concepts:**

- **IoU (Intersection over Union):** Overlap between predicted and ground truth boxes. IoU > 0.5 = correct detection (mAP@0.5)
- **NMS (Non-Maximum Suppression):** Remove duplicate detections by keeping highest-confidence box and suppressing overlapping boxes with IoU > threshold
- **Anchor Boxes:** Pre-defined box shapes at each position. Model predicts offsets from anchors. Different ratios and scales handle varying object shapes.
- **FPN (Feature Pyramid Network):** Multi-scale feature extraction — use features from different network depths to detect objects at different sizes

**Follow-up Questions:**

**Q: What is the Focal Loss and why was it introduced?**
A: In one-stage detectors, 99%+ of predictions are background (easy negatives). Standard cross-entropy spends most optimization effort on these easy examples. Focal Loss: FL = -α(1-p)^γ · log(p). The (1-p)^γ term down-weights easy examples (high p). With γ=2, a sample classified at 0.9 confidence gets 100× less weight than one at 0.5. This lets the model focus on hard examples.

**Q: How does Faster R-CNN work?**
A: (1) Backbone CNN extracts feature maps. (2) Region Proposal Network (RPN) slides over features, predicting objectness score + box refinement for K anchors per position. (3) Top ~300 proposals selected by NMS. (4) ROI Pooling extracts fixed-size features for each proposal. (5) Classification head predicts class + box refinement. End-to-end trainable.

**Q: What's the difference between YOLO versions?**
A: YOLOv1: Single grid, one prediction per cell. v2: Anchors, batch norm, multi-scale. v3: FPN, multi-scale detection, better backbone. v4/v5: Bag of tricks (Mosaic augmentation, CSP backbone). v7: Extended efficient layer aggregation. v8: Anchor-free, decoupled head. Each version improves speed-accuracy tradeoff.

**Q: How does DETR eliminate the need for NMS and anchors?**
A: DETR treats detection as a set prediction problem. It uses a transformer encoder-decoder with learned object queries. Hungarian matching assigns each prediction to a ground truth (or "no object") uniquely. Since each prediction is for a unique object by design, no duplicate suppression (NMS) is needed.

---

## 6. Image Segmentation

**Answer:**
Segmentation assigns a label to each pixel in an image. Three types:
- **Semantic segmentation:** Label every pixel with a class (all cars = same label)
- **Instance segmentation:** Distinguish individual objects of same class (car 1, car 2, car 3)
- **Panoptic segmentation:** Combines both (stuff classes like sky/road + thing instances like individual cars)

**Architecture Comparison:**

| Model | Type | Key Innovation | Year |
|-------|------|---------------|------|
| FCN | Semantic | Fully convolutional, upsampling | 2015 |
| U-Net | Semantic | Encoder-decoder + skip connections | 2015 |
| DeepLab v3+ | Semantic | Atrous/dilated convolutions + ASPP | 2018 |
| Mask R-CNN | Instance | Faster R-CNN + mask branch | 2017 |
| YOLACT | Instance | Real-time instance segmentation | 2019 |
| Panoptic FPN | Panoptic | Unified panoptic segmentation | 2019 |
| SAM (Segment Anything) | Promptable | Foundation model, any segmentation | 2023 |
| SAM 2 | Video | Extends SAM to video segmentation | 2024 |

**U-Net Architecture:**
```
Encoder (downsampling)      Decoder (upsampling)
    Conv + Pool    ──skip──>   Upconv + Concat + Conv
    Conv + Pool    ──skip──>   Upconv + Concat + Conv
    Conv + Pool    ──skip──>   Upconv + Concat + Conv
         └── Bottleneck ──┘
```

**Layman Example:**
Coloring a coloring book:
- **Semantic segmentation:** Color all trees green, all sky blue, all cars red — you don't distinguish between individual trees.
- **Instance segmentation:** Each tree gets a unique shade of green (tree 1 = light green, tree 2 = dark green) — you know exactly which tree is which.
- **Panoptic:** Sky and road get class colors (stuff), each person/car gets a unique color (things).

**Follow-up Questions:**

**Q: Why are skip connections important in U-Net?**
A: The encoder loses spatial details during downsampling. Skip connections pass high-resolution features from encoder directly to decoder at corresponding scales. The decoder combines these fine-grained spatial details with the semantically rich deep features. This produces precise boundaries — critical for medical segmentation where pixel accuracy matters.

**Q: What are dilated/atrous convolutions and why use them?**
A: Dilated convolutions insert gaps between filter weights, increasing receptive field without adding parameters or reducing resolution. A 3×3 conv with dilation rate 2 has a 5×5 receptive field. DeepLab uses ASPP (Atrous Spatial Pyramid Pooling) — parallel dilated convs at multiple rates — to capture multi-scale context without downsampling.

**Q: How does Mask R-CNN extend Faster R-CNN?**
A: Adds a parallel mask prediction branch alongside classification and box regression. For each ROI, it predicts a binary mask (per class). Key innovation: ROI Align (bilinear interpolation) instead of ROI Pooling (quantized) — the misalignment from rounding in ROI Pool degrades mask quality.

**Q: What metrics are used for segmentation?**
A: 
- **mIoU (mean Intersection over Union):** Average IoU across all classes. Standard for semantic segmentation.
- **Dice coefficient:** 2|A∩B|/(|A|+|B|) — equivalent to F1 for pixels. Common in medical imaging.
- **Pixel accuracy:** % of correctly classified pixels — misleading for imbalanced classes.
- **AP (Average Precision):** For instance segmentation at various IoU thresholds.
- **PQ (Panoptic Quality):** For panoptic = SQ (Segmentation Quality) × RQ (Recognition Quality).

---

## 7. Generative Models (GANs, VAEs, Diffusion)

**Answer:**
Generative models learn the data distribution P(x) and can generate new samples. Three main families: GANs (adversarial training), VAEs (variational inference), and Diffusion Models (iterative denoising).

**Comparison:**

| Aspect | GAN | VAE | Diffusion Model |
|--------|-----|-----|-----------------|
| Training | Adversarial (G vs D) | ELBO maximization | Denoising score matching |
| Sample quality | High (sharp) | Lower (blurry) | Highest |
| Training stability | Unstable (mode collapse) | Stable | Stable |
| Diversity | Can suffer mode collapse | Good diversity | Excellent diversity |
| Inference speed | Fast (single forward pass) | Fast | Slow (many denoising steps) |
| Latent space | Not explicitly structured | Smooth, interpolatable | Implicit |
| Likelihood | Not tractable | Lower bound (ELBO) | Tractable |
| Examples | StyleGAN, CycleGAN, Pix2Pix | β-VAE, VQ-VAE | DDPM, Stable Diffusion, DALL-E |

**GAN Architecture:**
```
Generator: Random noise z → Fake image
Discriminator: Image → Real/Fake probability
Training: G tries to fool D, D tries to catch fakes
Equilibrium: G produces realistic images, D can't distinguish
```

**Diffusion Model Process:**
```
Forward (training): Clean image → Add noise step by step → Pure noise
Reverse (generation): Pure noise → Denoise step by step → Clean image
Model learns: Given noisy image at step t, predict the noise to subtract
```

**Layman Example:**
- **GAN:** A counterfeiter (generator) vs. a detective (discriminator). The counterfeiter makes fake paintings, the detective tries to spot them. Over time, the counterfeiter gets so good that even the detective can't tell the difference.
- **VAE:** A postal system that compresses images into small zip codes (latent vectors) and reconstructs them from the zip code. The zip code space is organized so similar images have similar codes, and you can sample any code to get a new image.
- **Diffusion:** Restoring a old photograph that's been progressively damaged by static. You learn to remove a tiny bit of static at each step. Starting from pure static, applying 1000 tiny de-noising steps creates a beautiful new photo.

**Follow-up Questions:**

**Q: What is mode collapse in GANs and how do you fix it?**
A: Mode collapse = generator produces only a few types of outputs, ignoring the full data diversity (e.g., generating only one type of face). Fixes: Wasserstein GAN (gradient penalty instead of JS divergence), mini-batch discrimination, spectral normalization, progressive growing, diversity-encouraging losses.

**Q: How does Stable Diffusion work?**
A: (1) Encode image into a compact latent space using a VAE encoder. (2) Apply diffusion (noising + denoising) in this latent space (much cheaper than pixel space). (3) Use a U-Net with cross-attention to text embeddings (CLIP) for text conditioning. (4) Decode final latent back to pixels with VAE decoder. Running diffusion in latent space makes it 10-100× more efficient.

**Q: What is classifier-free guidance?**
A: A technique to improve conditional generation quality. During training, randomly drop the condition (text) with some probability. At inference, compute both conditional and unconditional predictions, then extrapolate: output = unconditional + scale × (conditional - unconditional). Higher guidance scale = more adherence to condition but less diversity.

**Q: What are the practical tradeoffs between GANs and Diffusion Models?**
A: GANs: fast inference (single pass), harder to train, less diverse. Diffusion: slow inference (50-1000 steps), easy to train (simple MSE loss), more diverse and higher quality. Modern approaches: distilled diffusion models (1-4 steps), consistency models, LCM — closing the speed gap while maintaining quality.

---

## 8. Vision Transformers (ViT)

**Answer:**
Vision Transformer (ViT) applies the transformer architecture (originally for NLP) directly to images. An image is split into fixed-size patches (e.g., 16×16), each patch is linearly embedded into a vector, positional embeddings are added, and the sequence is fed to a standard transformer encoder. Classification uses a [CLS] token.

**Architecture:**
```
Image (224×224) → Split into patches (14×14 grid of 16×16 patches = 196 patches)
→ Linear projection (each patch → D-dim vector)
→ Add positional embeddings + [CLS] token
→ Transformer Encoder (L layers of Multi-Head Self-Attention + MLP)
→ [CLS] token → Classification head
```

**Comparison: CNN vs. Vision Transformer:**

| Aspect | CNN | Vision Transformer |
|--------|-----|-------------------|
| Inductive bias | Locality + translation equivariance | Minimal (learns from data) |
| Global context | Only at deep layers (large receptive field) | Every layer (self-attention) |
| Data efficiency | Better with small data (strong priors) | Needs large data (or pre-training) |
| Scalability | Saturates | Scales with data + compute |
| Computational complexity | O(K²·C²·H·W) per layer | O(N²·D) per layer (N=patches) |
| Position handling | Implicit (convolution structure) | Explicit (positional embeddings) |
| Performance (large data) | Good | Superior |
| Performance (small data) | Better (unless ViT is pre-trained) | Worse without pre-training |

**Layman Example:**
Reading a page of text:
- **CNN:** Reading with a magnifying glass, word by word, left to right. Only sees nearby context at each step. Needs many passes to connect distant words.
- **ViT:** Seeing the entire page at once (self-attention). Every word can directly relate to every other word. More powerful but needs more text examples to learn this skill effectively.

The patches are like cutting a photo into a jigsaw puzzle, turning each piece into a "word," and letting a language model figure out what the whole picture shows.

**Follow-up Questions:**

**Q: Why does ViT need more data than CNNs?**
A: CNNs have built-in inductive biases (locality, translation equivariance) that constrain the hypothesis space — they "know" to look locally and can detect a cat regardless of position without learning this. ViT has no such priors — it must learn spatial relationships purely from data. With ImageNet-1K alone, ViT underperforms ResNet. With JFT-300M or ImageNet-21K pre-training, it surpasses CNNs.

**Q: What are DeiT's contributions?**
A: DeiT (Data-efficient Image Transformer) showed ViT can work with only ImageNet-1K using: (1) Strong data augmentation (RandAugment, Mixup, CutMix), (2) Regularization (stochastic depth, repeated augmentation), (3) Knowledge distillation from a CNN teacher. Also introduced a distillation token.

**Q: What is the computational cost of self-attention and how is it addressed?**
A: Self-attention is O(N²) where N = number of patches. For high-res images (many patches), this is expensive. Solutions: Swin Transformer (windowed attention + shift), PVT (spatial-reduction attention), Efficient ViT (linear attention), pooling attention in later stages.

**Q: How does the Swin Transformer work?**
A: Swin (Shifted Window) Transformer computes self-attention within local windows (e.g., 7×7 patches), then shifts windows by half to enable cross-window connections. Creates a hierarchical representation (like CNN) by merging patches at each stage. Complexity is O(N) instead of O(N²). Widely used as backbone for detection/segmentation.

**Q: What are hybrid architectures?**
A: Combine CNN and transformer strengths. Examples: (1) CNN for early layers (extract local features) + Transformer for later layers (global reasoning). (2) ConvNeXt: modernized pure CNN matching ViT performance. (3) CoAtNet: combines depthwise conv with attention. Generally: conv for efficiency on early high-res features, attention for later semantic features.

---

## 9. Attention Mechanisms in Vision

**Answer:**
Attention allows models to focus on relevant parts of the input, weighting features by importance. In vision: channel attention (which feature maps matter), spatial attention (where to look), and self-attention (relating all positions to each other).

**Types of Visual Attention:**

| Type | What it does | Example | Mechanism |
|------|-------------|---------|-----------|
| Channel attention | Weights feature maps by importance | SE-Net, ECA-Net | Global pool → FC → sigmoid → rescale channels |
| Spatial attention | Weights spatial locations | CBAM (spatial) | Conv on pooled features → sigmoid → spatial mask |
| Self-attention | Relates all positions | ViT, Non-local nets | Q·K^T → softmax → weighted sum of V |
| Cross-attention | Relates two different inputs | DETR, Stable Diffusion | Q from one, K/V from another |
| Deformable attention | Attend to learned sparse locations | Deformable DETR | Learn offsets → attend to relevant positions only |

**Self-Attention Formula:**
$$\text{Attention}(Q, K, V) = \text{softmax}\left(\frac{QK^T}{\sqrt{d_k}}\right)V$$

**Multi-Head Attention:** Run H parallel attention heads with different learned projections, concatenate results. Each head can attend to different relationship types.

**Layman Example:**
Looking at a group photo to find your friend:
- **Without attention:** Process every pixel equally — wasteful, as most of the image is background.
- **Spatial attention:** Your eyes naturally zoom into faces (important regions), ignoring trees and sky.
- **Channel attention:** When looking for your friend's red jacket, your brain "turns up" the red-color channel and ignores others.
- **Self-attention:** Every face "looks at" every other face to understand relative positions and groupings. "This face is next to that face" — capturing relationships.

**Follow-up Questions:**

**Q: What is the Squeeze-and-Excitation (SE) block?**
A: Channel attention mechanism: (1) Squeeze: Global Average Pool compresses H×W×C to 1×1×C (2) Excitation: FC → ReLU → FC → Sigmoid produces channel weights (3) Scale: multiply original feature maps by weights. Each channel gets a learned importance score. Adds ~2.5% params but consistently improves accuracy by 1-2%.

**Q: Why divide by √d_k in self-attention?**
A: Without scaling, dot products grow in magnitude with d_k, pushing softmax into regions with tiny gradients (saturation). Dividing by √d_k keeps variance constant regardless of dimension: Var(q·k) = d_k → after scaling, Var(q·k/√d_k) = 1. This ensures gradients flow properly.

**Q: What's the difference between self-attention and convolution?**
A: Convolution: fixed local receptive field, content-independent (same weights regardless of input), translation equivariant. Self-attention: dynamic global receptive field, content-dependent (attention weights change based on input), permutation equivariant. Convolution is a special case of attention with fixed, local attention pattern.

**Q: How is attention used in object detection (DETR)?**
A: DETR uses: (1) Self-attention in encoder — each image position attends to all others (global context). (2) Cross-attention in decoder — object queries attend to encoder features to locate objects. (3) Self-attention in decoder — object queries attend to each other to avoid duplicates. This replaces anchor boxes, NMS, and hand-designed components.

---

## 10. Data Augmentation

**Answer:**
Data augmentation artificially increases training data diversity by applying transformations to existing images. It acts as regularization, reduces overfitting, and improves generalization — especially critical when labeled data is limited.

**Techniques Comparison:**

| Category | Techniques | Effect |
|----------|-----------|--------|
| **Geometric** | Flip, Rotate, Scale, Crop, Translate, Shear | Translation/rotation invariance |
| **Color/Intensity** | Brightness, Contrast, Saturation, Hue, Color Jitter | Illumination robustness |
| **Noise/Blur** | Gaussian noise, Blur, JPEG compression | Robustness to image quality |
| **Erasing** | Cutout, Random Erasing, GridMask | Occlusion robustness |
| **Mixing** | Mixup, CutMix, Mosaic | Regularization, smoother decision boundaries |
| **Auto-augmentation** | AutoAugment, RandAugment, TrivialAugment | Learned optimal policies |
| **Generative** | GAN-generated samples, Diffusion-based | When real data is scarce |

**Advanced Techniques:**

| Method | How it works | Key benefit |
|--------|-------------|-------------|
| **Mixup** | Blend two images: λ·img1 + (1-λ)·img2, same for labels | Smoother decision boundaries, calibration |
| **CutMix** | Cut patch from one image, paste onto another, adjust labels by area | Better localization than Cutout |
| **Mosaic (YOLOv4)** | Combine 4 images into one | Richer context, smaller objects |
| **RandAugment** | Apply N random transforms from a pool at magnitude M | Simple, only 2 hyperparams |
| **Test-Time Augmentation (TTA)** | Augment at inference, average predictions | ~1-2% accuracy boost, slower |

**Layman Example:**
Teaching a child to recognize dogs:
- Showing only front-facing photos = bad (child can't recognize dogs from the side)
- Showing dogs from all angles, lighting conditions, partially hidden, different sizes = good
- Augmentation = artificially creating these variations from limited photos
- Mixup = blending a dog photo with a cat photo at 70/30 and saying "this is 70% dog, 30% cat" — teaches the model to be less overconfident

**Follow-up Questions:**

**Q: Does augmentation always help?**
A: Almost always, but: (1) Don't augment in ways that destroy labels (vertical flip for digit "6" makes it "9"), (2) Medical imaging needs domain expertise (don't flip left/right for organ laterality), (3) Too aggressive augmentation can hurt if the transformed images are unrealistic. (4) With very large datasets, marginal benefit decreases.

**Q: What's the difference between Cutout and CutMix?**
A: Cutout erases a random patch (replacing with zeros/mean) — the model must classify using remaining visible parts. CutMix replaces the patch with a patch from another image AND adjusts the label proportionally. CutMix is better because: (1) no wasted pixels (filled with useful info), (2) forces better localization, (3) provides label information proportional to visible area.

**Q: How does RandAugment work and why is it popular?**
A: RandAugment uniformly selects N transformations from a pool of 14 (rotate, translate, shear, brightness, etc.) and applies each at magnitude M (single shared magnitude). Only 2 hyperparameters (N, M) vs. AutoAugment's expensive search. Performs comparably while being much simpler to tune. Typical: N=2, M=9.

**Q: What is Test-Time Augmentation (TTA)?**
A: At inference, create multiple augmented versions of the input (flip, crop, scale), run each through the model, and average/vote on predictions. Trades inference speed for accuracy (typically +1-2%). Common in competitions. For detection, you need to un-transform bounding boxes before merging.

---

## 11. Loss Functions for Vision

**Answer:**
Loss functions guide optimization by quantifying prediction error. Different tasks need different losses — classification uses cross-entropy variants, detection uses IoU-based losses, segmentation uses pixel-wise losses, and generation uses adversarial/perceptual losses.

**Classification Losses:**

| Loss | Formula | Use case |
|------|---------|----------|
| Cross-Entropy | -Σyᵢlog(pᵢ) | Multi-class classification |
| Binary Cross-Entropy | -[y·log(p) + (1-y)·log(1-p)] | Binary/multi-label |
| Focal Loss | -α(1-p)^γ · log(p) | Imbalanced data (detection) |
| Label Smoothing CE | Targets become (1-ε) for correct, ε/(K-1) for others | Calibration, prevent overconfidence |

**Detection/Box Losses:**

| Loss | Formula | Properties |
|------|---------|------------|
| Smooth L1 | 0.5x² if |x|<1, else |x|-0.5 | Robust to outliers |
| IoU Loss | 1 - IoU | Scale-invariant |
| GIoU Loss | 1 - GIoU | Handles non-overlapping boxes |
| DIoU Loss | 1 - IoU + distance²/diagonal² | Faster convergence |
| CIoU Loss | DIoU + aspect ratio penalty | Best overall for detection |

**Segmentation Losses:**

| Loss | Use case | Properties |
|------|----------|------------|
| Pixel-wise CE | Balanced classes | Standard |
| Weighted CE | Imbalanced classes | Class weights |
| Dice Loss | 1 - 2|A∩B|/(|A|+|B|) | Handles class imbalance well |
| Focal + Dice (combo) | Very imbalanced | Best for small structures |
| Boundary Loss | Precise boundaries | Penalizes boundary errors |

**Generation/Perceptual Losses:**

| Loss | Use case | What it measures |
|------|----------|-----------------|
| L1/L2 pixel loss | Image reconstruction | Pixel-level difference (blurry) |
| Perceptual loss | Super-resolution, style transfer | Feature-level difference (VGG features) |
| Adversarial loss | GANs | Realism (discriminator feedback) |
| LPIPS | Perceptual similarity | Learned perceptual metric |
| SSIM loss | Image quality | Structural similarity |

**Follow-up Questions:**

**Q: Why does L1/L2 pixel loss produce blurry images?**
A: When predicting uncertain pixels, the minimum L2 loss is the mean of possible values (and for L1, the median). Averaging multiple possible sharp images creates a blurry average. Perceptual loss and adversarial loss encourage choosing one specific sharp output rather than hedging.

**Q: Why use Dice Loss instead of Cross-Entropy for segmentation?**
A: For imbalanced segmentation (small tumor in large image), CE is dominated by the majority class (background). Dice Loss operates on the overlap between prediction and ground truth as sets, naturally handling imbalance. It directly optimizes the evaluation metric (Dice coefficient = F1 for pixels).

**Q: How does Label Smoothing help?**
A: Instead of hard targets [0, 0, 1, 0], use soft targets [0.033, 0.033, 0.9, 0.033]. This prevents the model from becoming overconfident, improves calibration, acts as regularization, and can improve generalization by 0.2-0.5%. Standard practice: ε=0.1.

---

## 12. Batch Normalization and Layer Normalization

**Answer:**
Normalization techniques standardize intermediate activations to stabilize and accelerate training. Batch Norm normalizes across the batch dimension per channel; Layer Norm normalizes across channels per sample.

**Formula (Batch Norm):**
$$\hat{x} = \frac{x - \mu_B}{\sqrt{\sigma_B^2 + \epsilon}} \quad \text{then} \quad y = \gamma\hat{x} + \beta$$

Where μ_B and σ_B are batch statistics, and γ, β are learnable scale and shift parameters.

**Comparison of Normalization Methods:**

| Method | Normalizes across | Best for | Batch-size dependent? |
|--------|-------------------|----------|----------------------|
| Batch Norm (BN) | Batch (N), spatial (H,W) per channel | CNNs with large batches | Yes (fails with small batch) |
| Layer Norm (LN) | Channels (C), spatial (H,W) per sample | Transformers, RNNs | No |
| Instance Norm (IN) | Spatial (H,W) per sample per channel | Style transfer | No |
| Group Norm (GN) | Groups of channels per sample | CNNs with small batches | No |

**Visualization (what's normalized together):**
```
Tensor shape: [N, C, H, W]

Batch Norm:    [■ ■ ■ ■]  — all samples, one channel
Layer Norm:    [■ ■ ■ ■]  — one sample, all channels  
Instance Norm: [■]         — one sample, one channel
Group Norm:    [■ ■]       — one sample, group of channels
```

**Layman Example:**
Students taking exams in different subjects:
- **Batch Norm:** Grade each subject on a curve across all students. Math scores normalized among all math takers, English among all English takers.
- **Layer Norm:** Grade each student's subjects relative to their own performance. "Your math is above YOUR average, your English is below YOUR average."
- **Instance Norm:** Each student-subject pair normalized independently. Mainly useful for removing style (like removing each photo's brightness/contrast).

**Follow-up Questions:**

**Q: Why does Batch Norm help training?**
A: Multiple effects: (1) Reduces internal covariate shift (layer inputs stay stable), (2) Allows higher learning rates (gradients are better scaled), (3) Regularization effect (batch statistics add noise), (4) Smooths the loss landscape. The exact mechanism is debated — the "smoothing" effect may matter more than reducing covariate shift.

**Q: Why use Layer Norm in Transformers instead of Batch Norm?**
A: (1) Transformers process variable-length sequences — batch statistics vary wildly across positions, (2) At inference, you may have batch_size=1, (3) Layer Norm normalizes per-sample, making it independent of other samples in the batch — cleaner for attention mechanisms.

**Q: What are the problems with Batch Norm?**
A: (1) Fails with small batches (noisy statistics), (2) Different behavior train vs. test (running mean/var at test), (3) Doesn't work well in RNNs (varying sequence lengths), (4) Breaks with distributed training if batch statistics aren't synced. Use Group Norm or Layer Norm as alternatives.

**Q: Where should you place BN — before or after activation?**
A: Original paper: Conv → BN → ReLU. Some argue: Conv → ReLU → BN is better (normalize activations, not pre-activations). In practice, both work. In ResNets with pre-activation, it's BN → ReLU → Conv which generally performs best for very deep networks.

---

## 13. Optimizers for Deep Learning

**Answer:**
Optimizers update model parameters to minimize the loss function. SGD with momentum is the foundation; Adam and its variants are the most popular adaptive methods.

**Comparison:**

| Optimizer | Update Rule Key Idea | Pros | Cons | Best for |
|-----------|---------------------|------|------|----------|
| SGD | w -= lr · ∇L | Simple, good generalization | Slow, sensitive to LR | CNNs (with schedule) |
| SGD + Momentum | Accumulate velocity, reduces oscillation | Faster convergence | Still needs LR tuning | Standard CNN training |
| Adam | Adaptive LR per parameter (1st + 2nd moment) | Fast, works out of box | Can generalize worse, memory | Default starting point |
| AdamW | Adam + decoupled weight decay | Better generalization than Adam | Slightly more memory | Transformers, ViT |
| LAMB/LARS | Layer-wise adaptive LR scaling | Large batch training | Specialized | Distributed training |
| Lion | Sign-based update, memory efficient | Faster, less memory than Adam | Newer, less proven | Large models |

**Adam Update:**
$$m_t = \beta_1 m_{t-1} + (1-\beta_1) g_t \quad \text{(first moment / mean)}$$
$$v_t = \beta_2 v_{t-1} + (1-\beta_2) g_t^2 \quad \text{(second moment / variance)}$$
$$w_t = w_{t-1} - \frac{lr}{\sqrt{\hat{v}_t} + \epsilon} \cdot \hat{m}_t$$

**Learning Rate Schedules:**

| Schedule | Behavior | When |
|----------|----------|------|
| Step decay | Drop LR by factor every N epochs | Classic training |
| Cosine annealing | Smooth cosine curve from high to low | Modern training |
| Warmup + cosine | Linear increase then cosine decay | Transformers, large LR |
| OneCycleLR | Warmup → high → cooldown (one cycle) | Fast training (super-convergence) |
| ReduceOnPlateau | Reduce when metric stalls | When unsure about schedule |

**Layman Example:**
Finding the lowest point in a mountain range (loss landscape):
- **SGD:** Walk downhill in the steepest direction. Slow, gets stuck in ravines (oscillation).
- **Momentum:** Like a ball rolling downhill — accumulates speed, rolls through small bumps.
- **Adam:** Adjusts step size per direction. In flat directions, takes bigger steps. In steep directions, takes smaller steps. Like having different shoe sizes for different terrain.
- **Learning rate warmup:** Don't sprint at the start (when you don't know the terrain). Walk slowly first, then speed up once you have a sense of direction.

**Follow-up Questions:**

**Q: Why does SGD sometimes generalize better than Adam?**
A: SGD tends to converge to flatter minima (wide basins) which generalize better, while Adam can converge to sharper minima (narrow basins) that don't generalize. AdamW with proper weight decay and cosine schedule largely closes this gap. For CNNs on ImageNet, SGD + momentum + cosine schedule is still very competitive.

**Q: What is the warmup and why is it important?**
A: Start with very small LR and linearly increase to target over first few hundred/thousand steps. Important because: (1) Early gradients are unreliable (random initialization → wild gradients), (2) Adam's variance estimate is inaccurate initially, (3) Prevents large early updates that destabilize training. Critical for transformers and large batch training.

**Q: What's the difference between Adam and AdamW?**
A: Adam: L2 regularization modifies the gradient → interacts poorly with adaptive learning rates (different parameters get different effective regularization). AdamW: applies weight decay directly to weights AFTER the Adam update → decoupled, each parameter gets the same regularization strength regardless of adaptive LR. AdamW is standard for transformers.

**Q: How do you choose an optimizer and learning rate?**
A: Start with AdamW (lr=1e-3 to 3e-4) for transformers, SGD (lr=0.1) + momentum(0.9) + cosine schedule for CNNs. Use LR finder (plot loss vs. LR, pick steepest descent point). For fine-tuning: 10-100× smaller LR than training from scratch.

---

## 14. Activation Functions

**Answer:**
Activation functions introduce non-linearity, enabling neural networks to learn complex patterns. Without them, stacking layers reduces to a single linear transformation.

**Comparison:**

| Function | Formula | Range | Pros | Cons | Used in |
|----------|---------|-------|------|------|---------|
| ReLU | max(0, x) | [0, ∞) | Fast, no vanishing gradient for x>0 | Dead neurons (x<0 → gradient=0) | Most CNNs |
| Leaky ReLU | max(αx, x), α=0.01 | (-∞, ∞) | No dead neurons | α is arbitrary | Alternative to ReLU |
| PReLU | max(αx, x), α learned | (-∞, ∞) | Adapts negative slope | Extra parameters | ResNets |
| ELU | x if x>0, α(e^x-1) if x≤0 | (-α, ∞) | Smooth, pushes mean toward 0 | Exp computation | Some CNNs |
| GELU | x·Φ(x) ≈ x·σ(1.702x) | ≈(-0.17, ∞) | Smooth, probabilistic | Slightly slower | Transformers, BERT, GPT |
| Swish/SiLU | x·σ(x) | ≈(-0.28, ∞) | Smooth, self-gated | Slightly slower | EfficientNet, modern CNNs |
| Sigmoid | 1/(1+e^(-x)) | (0, 1) | Probability output | Vanishing gradient, not zero-centered | Output layer (binary) |
| Tanh | (e^x-e^(-x))/(e^x+e^(-x)) | (-1, 1) | Zero-centered | Vanishing gradient | RNNs (gates) |
| Softmax | e^xᵢ/Σe^xⱼ | (0, 1), sum=1 | Probability distribution | Not per-element | Multi-class output |
| Mish | x·tanh(softplus(x)) | ≈(-0.31, ∞) | Smooth, self-regularizing | Expensive | YOLOv4 |

**Layman Example:**
Activation functions are like decision thresholds:
- **ReLU:** A door that's fully open (passes everything) or fully closed (blocks everything). Simple and fast, but once closed (dead neuron), it might never open again.
- **GELU:** A door that opens smoothly — for very negative inputs it's almost closed, for positive inputs it's fully open, and near zero it's partially open. Smoother decisions lead to better training.
- **Sigmoid:** A dimmer switch for a light — smoothly goes from off (0) to on (1). Great for "how likely is this a cat?" but problematic in deep networks (gradients vanish at extremes).

**Follow-up Questions:**

**Q: What is the "dying ReLU" problem?**
A: If a neuron's input is always negative (due to large negative bias or bad initialization), ReLU outputs 0 and its gradient is 0. The neuron never updates → permanently dead. Solutions: Leaky ReLU, PReLU, careful initialization (He initialization), lower learning rate.

**Q: Why is GELU used in Transformers?**
A: GELU(x) = x·P(X≤x) where X~N(0,1). It's smooth (differentiable everywhere), combines properties of ReLU and dropout (stochastically zeros inputs based on magnitude), and empirically works better than ReLU for attention-based models. It's the default in BERT, GPT, ViT.

**Q: Why not use sigmoid/tanh in hidden layers of deep networks?**
A: Vanishing gradient problem. Sigmoid saturates at 0 and 1 where gradient ≈ 0. In a 100-layer network, gradients multiply: (0.25)^100 ≈ 0 — early layers learn nothing. ReLU/GELU maintain gradient = 1 for positive inputs, enabling deep training.

**Q: What's the relationship between activation function choice and initialization?**
A: They're coupled. ReLU kills half the neurons (negative half has gradient 0), so He initialization (Var = 2/n_in) compensates by using larger weights. Sigmoid/Tanh use Xavier initialization (Var = 1/n_in). Mismatched init + activation → exploding/vanishing gradients.

---

## 15. Image Classification Best Practices

**Answer:**
A complete image classification pipeline involves data preparation, model selection, training strategy, and deployment optimization.

**Pipeline:**
```
Data → Preprocessing → Augmentation → Model → Training → Evaluation → Deployment
```

**Model Selection Guide:**

| Scenario | Recommended Model | Why |
|----------|-------------------|-----|
| Mobile/Edge (< 5M params) | MobileNet v3, EfficientNet-Lite | Optimized for latency |
| Server (accuracy priority) | EfficientNet-B4/B5, ConvNeXt-B | Best accuracy/compute |
| Very large data | ViT-L, SwinV2-L | Scales with data |
| Small data (<5K images) | Pre-trained ResNet-50 + fine-tune | Strong transfer learning |
| Real-time (>30 FPS) | MobileNet v3-Small, ShuffleNet | Minimal latency |
| Medical/Specialized | Pre-trained + fine-tune + domain augmentation | Domain shift handling |

**Training Recipe (Modern Baseline):**
```
Model: ResNet-50 or ConvNeXt-T
Optimizer: AdamW (lr=4e-3, weight_decay=0.05) OR SGD (lr=0.1, momentum=0.9)
Schedule: Cosine annealing with 5-epoch warmup
Augmentation: RandAugment(N=2, M=9) + Mixup(α=0.2) + CutMix(α=1.0)
Regularization: Label smoothing(0.1) + Stochastic depth(0.1)
Epochs: 300 (ImageNet), 100-200 (smaller datasets)
Batch size: 256-1024 (with linear LR scaling)
```

**Follow-up Questions:**

**Q: How do you handle class imbalance in image classification?**
A: (1) Oversampling minority classes (repeated sampling), (2) Class-weighted loss (weight inversely proportional to class frequency), (3) Focal loss, (4) Data augmentation focused on minority classes, (5) Two-stage training: first balanced, then fine-tune on natural distribution. For extreme imbalance, treat as anomaly detection.

**Q: What's the difference between fine-tuning and training from scratch?**
A: Fine-tuning starts from pre-trained weights (faster convergence, better with small data, leverages learned features). Scratch starts from random init (needs more data, more compute, but no domain mismatch). Rule of thumb: always fine-tune unless you have 10M+ domain-specific images and very different input (e.g., satellite SAR images).

**Q: How do you handle different image resolutions?**
A: (1) Resize to fixed size (common but loses aspect ratio), (2) Resize longest edge + pad (preserves aspect ratio), (3) Multi-scale training (random resize during training), (4) Adaptive pooling before FC layers (handles any input size), (5) Progressive resizing: start training at low res, gradually increase (saves compute, acts as regularization).

**Q: What's the impact of batch size on training?**
A: Large batch = faster training (more parallelism) but often worse generalization (converges to sharp minima). Solutions for large batch: linear LR scaling (multiply LR by batch_size/256), warmup, LARS/LAMB optimizer. Small batch = noisier gradients (regularization effect) but slower wall-clock time.

---

## 16. Semantic Segmentation — Deep Dive

**Answer:**
Semantic segmentation requires per-pixel classification. Key challenge: maintaining high resolution for precise boundaries while having large receptive field for context. Main approaches: encoder-decoder, dilated convolutions, and multi-scale processing.

**Key Architectures:**

| Architecture | Approach | Key Innovation | mIoU (Cityscapes) |
|-------------|----------|----------------|-------------------|
| FCN | Upsample from classification CNN | First end-to-end segmentation | 65.3 |
| U-Net | Encoder-decoder + skip connections | Skip connections for spatial precision | — (medical) |
| PSPNet | Pyramid Pooling Module | Multi-scale global context | 81.2 |
| DeepLab v3+ | Encoder-decoder + ASPP + dilated | Atrous Spatial Pyramid Pooling | 82.1 |
| HRNet | Maintain high-resolution throughout | Parallel multi-resolution streams | 81.6 |
| SegFormer | Transformer encoder + lightweight decoder | Hierarchical ViT for dense prediction | 84.0 |
| Mask2Former | Universal architecture | Masks + queries for all segmentation types | 84.3 |

**DeepLab v3+ Architecture:**
```
Input → Encoder (ResNet/Xception with dilated convs)
     → ASPP (parallel: 1×1, 3×3 dil=6, 3×3 dil=12, 3×3 dil=18, global pool)
     → Concatenate + 1×1 conv
     → Decoder (4× upsample + concat low-level features + conv)
     → 4× upsample → Output
```

**Layman Example:**
A cartographer creating a land-use map from satellite imagery:
- **FCN:** Quick first draft — identifies forests, water, cities but boundaries are blurry (lost resolution from downsampling).
- **U-Net:** Makes the draft, but keeps detailed notes from each zoom level. When drawing final map, refers back to detailed notes for precise boundaries.
- **DeepLab:** Uses different magnifying glasses simultaneously (dilated convolutions at multiple rates) to see both fine details and big picture without zooming in/out.
- **HRNet:** Works at all zoom levels simultaneously, constantly cross-referencing between them.

**Follow-up Questions:**

**Q: Why not just use a classification CNN and upsample?**
A: Classification CNNs downsample 32× (224→7), so upsampling 7→224 produces very coarse boundaries. Solutions: (1) Skip connections recover spatial info, (2) Dilated convolutions maintain resolution, (3) Decoder networks gradually upsample, (4) Keep some parallel high-res branches (HRNet).

**Q: What is Atrous Spatial Pyramid Pooling (ASPP)?**
A: Parallel dilated convolutions at multiple rates (e.g., 6, 12, 18) plus image-level pooling. Each captures context at a different scale without changing resolution. Concatenating them gives multi-scale features. This outperforms simply stacking dilated convolutions sequentially.

**Q: How do you handle class imbalance in segmentation?**
A: Background often dominates (90%+ pixels). Solutions: (1) Class-weighted cross-entropy (inverse frequency weights), (2) Dice loss (directly optimizes overlap), (3) Focal loss (down-weight easy background pixels), (4) Online hard example mining (OHEM — train on hardest pixels only), (5) Combo: Focal + Dice is common.

**Q: What's the difference between bilinear upsampling and transposed convolution?**
A: Bilinear: fixed interpolation (no learnable params), fast, produces smooth results. Transposed conv (deconv): learnable upsampling, can produce checkerboard artifacts (uneven overlap). Hybrid approach: bilinear upsample + regular conv is common and avoids artifacts while being learnable.

---

## 17. Object Detection Metrics and Evaluation

**Answer:**
Detection evaluation is complex because predictions involve both localization (where) and classification (what). The primary metric is mAP (mean Average Precision) computed across IoU thresholds and classes.

**Key Metrics:**

| Metric | What it measures | Standard |
|--------|-----------------|----------|
| IoU | Box overlap quality | Threshold for TP (0.5, 0.75) |
| Precision | Of detections, how many are correct | Per-confidence threshold |
| Recall | Of ground truths, how many are detected | Per-confidence threshold |
| AP | Area under Precision-Recall curve per class | Computed per class |
| mAP@0.5 | Mean AP at IoU≥0.5 | PASCAL VOC standard |
| mAP@[.5:.95] | Mean AP averaged over IoU from 0.5 to 0.95 (step 0.05) | COCO primary metric |
| AP_S / AP_M / AP_L | AP for small/medium/large objects | COCO (by area) |
| AR (Average Recall) | Max recall given N detections per image | COCO secondary metric |
| FPS | Inference speed | Throughput |

**Computing AP (step by step):**
1. Sort all detections by confidence (descending)
2. For each detection: if IoU with unmatched GT > threshold → TP, else FP
3. Compute precision and recall at each confidence threshold
4. Compute area under the P-R curve (interpolated at 101 recall points for COCO)
5. Average across all classes → mAP

**Layman Example:**
A search engine analogy:
- **Precision:** "Of the 10 links shown (detections), how many are actually relevant (correct)?"
- **Recall:** "Of all 50 relevant pages that exist (ground truths), how many did the search engine find?"
- **AP:** The average precision as you look at more and more results (from top-1 to all detections)
- **IoU threshold:** How strictly you judge "relevance" — at IoU=0.5, a roughly correct box counts. At IoU=0.95, the box must be nearly perfect.

**Follow-up Questions:**

**Q: Why does COCO use mAP@[.5:.95] instead of mAP@0.5?**
A: mAP@0.5 is too lenient — a box that overlaps 50% with ground truth counts as correct, but this may not be precise enough for downstream tasks (cropping, segmentation). Averaging across IoU 0.5-0.95 rewards both detection AND precise localization. Models that do well at high IoU are genuinely better localizers.

**Q: How is mAP affected by small objects?**
A: Small objects are harder (few pixels, less information, easily missed). COCO reports AP_S (area < 32²), AP_M (32² < area < 96²), AP_L (area > 96²) separately. Most models have much lower AP_S. Solutions: FPN (multi-scale features), high-resolution inputs, mosaic augmentation.

**Q: What are common failure modes in object detection?**
A: (1) Missing small or occluded objects (low recall), (2) Duplicate detections (NMS not aggressive enough), (3) Mislocalization (box is off-center or wrong size), (4) Class confusion (car vs. truck), (5) Background false positives (confident detections on non-objects). Analyze per-class AP and error breakdown (TIDE toolkit).

---

## 18. Image Generation & Super-Resolution

**Answer:**
Image generation creates new images from noise/conditions. Super-resolution reconstructs high-resolution images from low-resolution inputs. Both require specialized architectures and loss functions to produce visually pleasing results.

**Super-Resolution Methods:**

| Method | Approach | Quality | Speed |
|--------|----------|---------|-------|
| Bicubic interpolation | Classical, fixed | Low (blurry) | Very fast |
| SRCNN | First CNN-based | Medium | Fast |
| EDSR | Deep residual CNN | Good | Moderate |
| ESRGAN | GAN-based | Best perceptual quality | Slow |
| SwinIR | Swin Transformer | High | Moderate |
| Real-ESRGAN | Real-world degradation | Best for real photos | Slow |
| Stable Diffusion Upscaler | Diffusion-based | Excellent | Very slow |

**Loss Functions for Super-Resolution:**

| Loss | Optimizes for | Result |
|------|--------------|--------|
| L1/L2 (pixel) | PSNR | Blurry but high PSNR |
| Perceptual (VGG features) | Feature similarity | Sharper, more natural |
| Adversarial (GAN) | Realism | Sharpest, may hallucinate details |
| LPIPS | Perceptual distance | Good perceptual quality |
| Combined (L1 + Perceptual + GAN) | All aspects | Best overall |

**Image Quality Metrics:**

| Metric | Measures | Higher is better? | Notes |
|--------|----------|-------------------|-------|
| PSNR | Pixel-level error | Yes | Doesn't correlate well with perception |
| SSIM | Structural similarity | Yes | Better than PSNR, still imperfect |
| LPIPS | Learned perceptual distance | No (lower=better) | Best correlation with human judgment |
| FID | Distribution distance (generation) | No (lower=better) | Standard for generative models |
| IS (Inception Score) | Quality + diversity | Yes | Less reliable than FID |
| KID | Kernel Inception Distance | No (lower=better) | Unbiased, works with few samples |

**Follow-up Questions:**

**Q: Why is PSNR not a good metric for image quality?**
A: PSNR measures pixel-level mean squared error. A slightly shifted sharp image has low PSNR but looks great. A blurry average of possible images has high PSNR but looks terrible. Human perception cares about structure, texture, and sharpness — not pixel-exact matches. Use LPIPS or FID for perceptual quality.

**Q: What is FID and how does it work?**
A: Fréchet Inception Distance measures how similar generated images are to real images in feature space. (1) Extract features from both sets using Inception v3, (2) Fit Gaussians to both feature sets, (3) Compute Fréchet distance between Gaussians: FID = ||μ₁-μ₂||² + Tr(Σ₁+Σ₂-2(Σ₁Σ₂)^½). Lower FID = generated images are more similar to real ones. FID=0 means identical distributions.

**Q: How do diffusion models generate images?**
A: Forward process: gradually add Gaussian noise over T steps until image becomes pure noise. Reverse process: train a neural network (U-Net) to predict noise at each step. Generation: start from random noise, iteratively denoise T steps. Each step slightly improves image quality. DDPM uses 1000 steps; DDIM enables fewer steps (50-100) without retraining.

---

## 19. Self-Supervised Learning for Vision

**Answer:**
Self-supervised learning (SSL) learns visual representations without human labels by defining pretext tasks from the data itself. The model learns features that transfer well to downstream tasks with minimal labeled data. Modern SSL approaches match or exceed supervised pre-training.

**Methods Comparison:**

| Method | Category | Pretext task | Key idea |
|--------|----------|-------------|----------|
| SimCLR | Contrastive | Same image augmentations → close, different images → far | Large batch contrastive |
| MoCo v3 | Contrastive | Momentum encoder for stable negatives | Queue of negatives |
| BYOL | Non-contrastive | Predict one view from another (no negatives) | Stop-gradient + momentum |
| SimSiam | Non-contrastive | Siamese nets with stop-gradient | Simplest — no negatives, no momentum |
| DINO | Self-distillation | Student predicts teacher (momentum) output | ViT + self-attention maps → segmentation |
| MAE | Generative/Masking | Mask 75% patches, reconstruct pixels | Efficient ViT pre-training |
| BEiT | Generative/Masking | Predict discrete visual tokens for masked patches | BERT-like for vision |
| DINOv2 | Self-distillation | Scaled DINO with diverse data | Best general visual features |

**Contrastive Learning Framework (SimCLR):**
```
Image x → Augmentation → View 1 (x_i)  → Encoder → Projector → z_i
       → Augmentation → View 2 (x_j)  → Encoder → Projector → z_j

Loss: Pull z_i and z_j together, push apart from all other images in batch
InfoNCE Loss = -log(exp(sim(z_i,z_j)/τ) / Σ_k exp(sim(z_i,z_k)/τ))
```

**MAE (Masked Autoencoder) Framework:**
```
Image → Split into patches → Randomly mask 75% → Encode visible 25% with ViT
→ Add mask tokens → Decode to reconstruct masked patches (pixel-level)
```

**Layman Example:**
Learning without a teacher:
- **Contrastive (SimCLR):** Show the model two crops of the same photo — it learns they're "the same thing" even though they look different. Two crops from different photos are "different things." Over millions of such comparisons, it learns what makes images similar or different.
- **MAE:** Cover most of a jigsaw puzzle (75%) and ask the model to fill in the missing pieces. To predict the missing patches, it must understand object shapes, textures, and context — developing visual understanding without any labels.
- **DINO:** A teacher (slow-moving average) and student looking at different views of the same image. Student must predict what teacher sees. This self-distillation produces features where attention maps naturally segment objects.

**Follow-up Questions:**

**Q: Why does masking 75% work better than 25% or 50% in MAE?**
A: Images have high spatial redundancy — neighboring pixels are highly correlated. With only 25% masking, the model can "cheat" by interpolating from visible neighbors without understanding content. 75% masking forces genuine semantic understanding — predicting missing patches requires understanding objects, not just local texture. Also: only encoding 25% of patches makes training efficient.

**Q: How does self-supervised learning compare to supervised pre-training?**
A: SSL pre-training (DINOv2, MAE) now matches or exceeds supervised ImageNet pre-training for transfer learning. Benefits: (1) Uses unlimited unlabeled data, (2) Features are more general (not biased toward ImageNet classes), (3) Better for diverse downstream tasks. Especially valuable when: labels are expensive (medical), target domain differs from ImageNet.

**Q: What's the difference between contrastive and non-contrastive methods?**
A: Contrastive (SimCLR, MoCo) requires negative pairs — pushes different images apart. Needs large batch sizes or memory banks for enough negatives. Non-contrastive (BYOL, SimSiam) only uses positive pairs — avoids collapse through architectural tricks (stop-gradient, momentum encoder, predictor network). Non-contrastive is simpler but theory of why it works is less clear.

**Q: What downstream tasks benefit from self-supervised features?**
A: Almost all vision tasks: classification (linear probe or fine-tune), detection (DINO features + Faster R-CNN), segmentation (DINO attention maps are natural segmentors), depth estimation, medical imaging, satellite imagery. The more labeled data is scarce, the more SSL helps.

---

## 20. Model Deployment & Optimization

**Answer:**
Deploying vision models requires balancing accuracy, latency, memory, and power consumption. Key techniques: quantization, pruning, knowledge distillation, and architecture optimization.

**Optimization Techniques:**

| Technique | What it does | Speedup | Accuracy drop |
|-----------|-------------|---------|---------------|
| Quantization (INT8) | Reduce weight precision from FP32 to INT8 | 2-4× | <1% |
| Quantization (INT4) | Extreme precision reduction | 4-8× | 1-3% |
| Pruning (structured) | Remove entire filters/channels | 2-5× | 1-2% |
| Pruning (unstructured) | Zero out individual weights | Hardware-dependent | <1% |
| Knowledge Distillation | Train small model to mimic large | — (better small model) | Recovered |
| TensorRT/ONNX optimization | Graph optimization + fusion | 2-5× | 0% |
| Dynamic batching | Batch multiple requests | Throughput: N× | 0% (adds latency) |
| Mixed precision (FP16) | Train/infer in half precision | 2× | ~0% |

**Quantization Types:**

| Type | When applied | Calibration needed? | Quality |
|------|-------------|---------------------|---------|
| Post-Training Quantization (PTQ) | After training, no fine-tuning | Yes (calibration set) | Good |
| Quantization-Aware Training (QAT) | During training (fake quantization) | No | Best |
| Dynamic Quantization | At inference time | No | Good for some ops |

**Knowledge Distillation:**
```
Teacher (large model) → Soft predictions (with temperature T)
Student (small model) → Train to match teacher's soft predictions + hard labels
Loss = α·KL(soft_teacher, soft_student) + (1-α)·CE(hard_labels, student)
```

**Deployment Frameworks:**

| Framework | Platform | Specialty |
|-----------|----------|-----------|
| TensorRT | NVIDIA GPU | Maximum GPU inference speed |
| ONNX Runtime | Cross-platform | Portable, good performance |
| TFLite | Mobile/Edge | Android/iOS deployment |
| Core ML | Apple devices | iOS/macOS optimization |
| OpenVINO | Intel hardware | Intel CPU/VPU optimization |
| NCNN | Mobile (ARM) | Lightweight mobile inference |

**Layman Example:**
Packing for a trip with limited luggage:
- **Quantization:** Instead of packing full-size toiletries (FP32), use travel-size bottles (INT8). 4× less space, works almost as well.
- **Pruning:** Leave behind clothes you won't wear (remove unimportant weights). Lighter suitcase, still have what you need.
- **Distillation:** An experienced traveler (teacher) teaches a beginner (student) exactly what to pack. The beginner learns to be efficient without years of experience.
- **TensorRT:** An expert packer rearranges your suitcase to eliminate all wasted space (graph optimization, operator fusion).

**Follow-up Questions:**

**Q: How does INT8 quantization work?**
A: Map FP32 values to INT8 range [-128, 127] using a scale factor and zero-point: x_int8 = round(x_fp32/scale) + zero_point. The scale is determined by calibrating on representative data (finding the activation range). Convolutions in INT8 are 2-4× faster on modern hardware (Tensor Cores, VNNI). Accuracy loss is typically <1% with proper calibration.

**Q: When should you use knowledge distillation vs. just training a small model?**
A: Distillation helps when: (1) You have a strong teacher but limited labeled data for the student, (2) The task is complex (soft labels carry more information than hard labels — "this looks 70% cat, 30% dog" vs. just "cat"), (3) You want to transfer ensemble knowledge to a single model. The student can exceed its standalone performance by 1-5%.

**Q: What is the latency vs. throughput tradeoff?**
A: Latency = time for one prediction. Throughput = predictions per second. Large batches increase throughput (GPU parallelism) but increase latency (must wait for batch to fill). Real-time applications prioritize latency (batch=1). Offline processing prioritizes throughput (large batches). Dynamic batching in serving frameworks (Triton) optimizes this automatically.

**Q: How do you profile and optimize inference speed?**
A: (1) Profile with PyTorch Profiler / TensorBoard / Nsight. (2) Identify bottlenecks (specific layers, data loading, pre/post-processing). (3) Try: TensorRT conversion, INT8 quantization, operator fusion, reducing input resolution, using efficient architectures. (4) Benchmark end-to-end including preprocessing. Often data loading/preprocessing is the bottleneck, not the model.

---

## 21. Depth Estimation and 3D Vision

**Answer:**
Depth estimation predicts per-pixel distance from camera. Critical for robotics, AR, autonomous driving. Can be monocular (single image), stereo (two cameras), or multi-view (multiple viewpoints).

**Approaches:**

| Method | Input | Output | Key models |
|--------|-------|--------|------------|
| Monocular depth | Single RGB image | Relative/metric depth map | MiDaS, DPT, Depth Anything |
| Stereo matching | Left + right images | Disparity map → depth | RAFT-Stereo, PSMNet |
| Multi-view stereo | Multiple views | 3D reconstruction | COLMAP, MVSNet |
| LiDAR | Active sensor | Sparse 3D points | Hardware-based |
| Depth completion | Sparse depth + RGB | Dense depth | IP-Basic, PENet |

**Monocular Depth Networks:**

| Model | Architecture | Key Innovation |
|-------|-------------|----------------|
| MiDaS | Encoder-decoder | Multi-dataset training, robust zero-shot |
| DPT | ViT encoder + decoder | Transformer features for dense prediction |
| Depth Anything v2 | DINOv2 encoder + DPT head | Best zero-shot, labeled + unlabeled data |
| Metric3D | Canonicalized input | Actual metric depth (not just relative) |
| ZoeDepth | MiDaS + metric bins | Relative-to-metric depth |

**Follow-up Questions:**

**Q: Why is monocular depth estimation inherently ambiguous?**
A: A single image has infinite possible 3D interpretations (a small nearby object looks identical to a large far object). The network learns statistical priors: larger objects are further, texture gradient indicates distance, known object sizes provide scale. But without additional info (camera intrinsics, known object sizes), only relative depth can be estimated.

**Q: What's the difference between relative and metric depth?**
A: Relative depth: ordering of depths is correct but absolute values are unknown (up to scale and shift). Metric depth: actual distance in meters. Most monocular methods produce relative depth. Metric depth requires camera intrinsics, known scale reference, or explicit metric supervision.

**Q: How is depth estimation used in autonomous driving?**
A: (1) 3D object detection (distance to vehicles/pedestrians), (2) Path planning (free space estimation), (3) SLAM (simultaneous localization and mapping), (4) Sensor fusion (combine LiDAR sparse depth with camera dense estimation), (5) Bird's eye view projection for planning.

---

## 22. Video Understanding

**Answer:**
Video understanding extends image understanding to the temporal dimension. Tasks include action recognition, temporal action detection, video object tracking, video segmentation, and video generation.

**Approaches for Action Recognition:**

| Method | Architecture | Temporal modeling | Example |
|--------|-------------|-------------------|---------|
| Two-stream | Spatial CNN + Optical flow CNN | Optical flow captures motion | Two-Stream Networks |
| 3D CNN | 3D convolutions (space + time) | Conv across frames | C3D, I3D, SlowFast |
| Transformer | ViT + temporal attention | Self-attention across frames | TimeSformer, ViViT |
| Efficient | 2D CNN + temporal module | Shift/difference/aggregation | TSM, TDN |

**Video Object Tracking:**

| Paradigm | Method | How it works |
|----------|--------|-------------|
| Correlation-based | SiamFC, SiamRPN | Template matching with learned features |
| Transformer-based | TransT, MixFormer | Cross-attention between template and search |
| Segment-based | SAM 2 | Track anything with prompted segmentation |

**Key Concepts:**
- **Optical Flow:** Per-pixel motion vectors between consecutive frames. Dense motion representation. FlowNet, RAFT.
- **Temporal Modeling:** How to capture motion across frames. Options: 3D conv, temporal attention, flow, frame difference, temporal shift.
- **SlowFast Networks:** Two pathways — Slow (few frames, rich spatial) + Fast (many frames, lightweight temporal). Captures both appearance and motion efficiently.

**Follow-up Questions:**

**Q: What's the difference between 2D+temporal vs. 3D convolutions?**
A: 2D+temporal: process each frame with 2D CNN then aggregate temporally (cheaper, can use ImageNet pre-trained weights). 3D conv: jointly learn spatial-temporal features (captures motion patterns directly but expensive, needs video pre-training). Practical sweet spot: factorized 3D conv (spatial 2D + temporal 1D) as in R(2+1)D.

**Q: Why is video understanding computationally expensive?**
A: Video = many frames × spatial resolution. A 10-sec clip at 30fps = 300 frames. Processing all with a ViT is ~300× single image cost. Solutions: sparse sampling (8-16 frames), efficient architectures (TSM shifts channels instead of new ops), token pruning, early exit.

---

## 23. Few-Shot and Zero-Shot Learning

**Answer:**
Few-shot learning recognizes new classes from very few examples (1-5 per class). Zero-shot learning recognizes classes never seen during training, using auxiliary information (text descriptions, attributes, embeddings).

**Approaches:**

| Paradigm | Method | How it works |
|----------|--------|-------------|
| **Metric learning** | Siamese, Prototypical Nets | Learn embedding space; classify by distance |
| **Meta-learning** | MAML | Learn to learn; fast adaptation from few examples |
| **Transfer + fine-tune** | Pre-train → fine-tune on few shots | Leverage large pre-trained models |
| **Zero-shot (CLIP)** | Vision-language alignment | Match image embeddings to text embeddings |
| **In-context learning** | GPT-4V, LLaVA | Show examples in prompt, model generalizes |

**CLIP (Zero-Shot Classification):**
```
Training: Contrastive learning on 400M image-text pairs
  - Image encoder → image embedding
  - Text encoder → text embedding
  - Align matching pairs, separate non-matching

Inference (zero-shot): 
  - Encode image
  - Encode class names as text ("a photo of a {class}")
  - Predict class with highest image-text similarity
```

**Comparison:**

| Scenario | Approach | Data needed |
|----------|----------|-------------|
| 0 examples of new class | Zero-shot (CLIP, text description) | None (class name only) |
| 1-5 examples | Few-shot (Prototypical Nets, CLIP + linear probe) | 1-5 per class |
| 10-100 examples | Fine-tuning pre-trained model | 10-100 per class |
| 1000+ examples | Standard supervised learning | Full dataset |

**Layman Example:**
- **Few-shot:** A child sees 2 photos of a "quokka" and can now recognize quokkas anywhere. They learned what features distinguish quokkas from other animals from just 2 examples.
- **Zero-shot (CLIP):** You've never seen a "quokka" but someone describes it: "small, brown, marsupial, always smiling." You can now pick one out from a lineup using the description alone.
- **Meta-learning (MAML):** A polyglot who's learned 10 languages. They haven't learned Japanese yet, but their "learning to learn" ability means they pick it up from just a few examples much faster than someone who only knows one language.

**Follow-up Questions:**

**Q: How does CLIP enable zero-shot classification?**
A: CLIP jointly trains image and text encoders on 400M image-text pairs using contrastive loss. At inference, encode the image and encode each class name as "a photo of a {class}." The class whose text embedding is closest to the image embedding is the prediction. No task-specific training needed — just change the class names.

**Q: What are the limitations of few-shot learning?**
A: (1) Performance significantly below full supervised with enough data, (2) Sensitive to which few examples are chosen (high variance), (3) Doesn't work well for fine-grained recognition where subtle differences matter, (4) Meta-learning methods are complex to train. Modern approach: large pre-trained models (CLIP, DINOv2) + simple fine-tuning often beats specialized few-shot methods.

**Q: How does Prototypical Networks work?**
A: (1) Encode all support examples (few labeled images per class) with a shared encoder. (2) Compute class prototypes (mean embedding of each class's examples). (3) For a query image, encode it and predict the class whose prototype is nearest (Euclidean distance). Simple, effective, and doesn't require meta-learning.

---

## 24. Foundation Models for Vision (CLIP, SAM, DINOv2)

**Answer:**
Foundation models are large-scale pre-trained models that serve as general-purpose visual understanding systems. They're trained on massive data and transfer to diverse tasks with minimal adaptation.

**Key Foundation Models:**

| Model | Training | Capability | Key use |
|-------|----------|------------|---------|
| CLIP | 400M image-text pairs, contrastive | Image-text matching | Zero-shot classification, retrieval |
| SAM | 11M images, 1B masks | Promptable segmentation | Segment anything with points/boxes/text |
| DINOv2 | 142M images, self-supervised | General visual features | Feature backbone for any task |
| Florence-2 | Multi-task vision-language | Caption, detect, segment, OCR | Unified vision model |
| Grounding DINO | Detection + language grounding | Open-vocabulary detection | Detect anything described in text |
| DALL-E 3 / SD3 | Image-text generation | Text-to-image generation | Creative image generation |

**SAM (Segment Anything Model):**
```
Input: Image + Prompt (point/box/text/mask)
Architecture: 
  - Image Encoder: ViT-H (pre-compute once per image)
  - Prompt Encoder: Encode points/boxes/text into tokens
  - Mask Decoder: Lightweight transformer → output masks

Key properties:
  - Promptable: different prompts → different segmentations
  - Zero-shot: works on any image/object without fine-tuning
  - Ambiguity-aware: outputs multiple valid masks with confidence
```

**Layman Example:**
- **CLIP:** A bilingual person who can translate between images and text. Show them any image and they can describe it. Give them any description and they can find matching images. They understand both "languages."
- **SAM:** A universal highlighter. Point to anything in any image, and it perfectly highlights/outlines that object. Works on microscope images, satellite photos, paintings — anything visual. You just need to point.
- **DINOv2:** An art expert who's studied millions of images without any labels. They developed such deep understanding that they can tell you about structure, depth, boundaries, and meaning in any new image.

**Follow-up Questions:**

**Q: How do foundation models change the CV workflow?**
A: Traditional: Collect labeled data → Train task-specific model → Deploy. Foundation model era: Use pre-trained foundation model → Prompt or minimally fine-tune → Deploy. This dramatically reduces data requirements, training time, and expertise needed. Many tasks are now zero-shot or few-shot.

**Q: What is open-vocabulary detection?**
A: Traditional detectors only find classes they were trained on. Open-vocabulary detectors (Grounding DINO, GLIP) can find ANY object described in text. Architecture: combine visual features with language features (CLIP-like), allowing detection of novel categories. "Find all fire hydrants" works without ever training on fire hydrant boxes.

**Q: What are the limitations of current foundation models?**
A: (1) Massive compute for training (environmental cost), (2) May have biases from training data, (3) Not always best for specialized domains (medical, satellite) without adaptation, (4) Large model size for deployment, (5) May "hallucinate" or be overconfident, (6) Evaluation is challenging (hard to measure zero-shot generalization comprehensively).

---

## 25. 3D Vision and Neural Radiance Fields (NeRF)

**Answer:**
3D vision reconstructs and understands 3D scenes from 2D images. NeRF (Neural Radiance Fields) represents scenes as continuous volumetric functions learned from images, enabling novel view synthesis.

**3D Representation Methods:**

| Representation | Description | Pros | Cons |
|----------------|-------------|------|------|
| Point clouds | Sparse 3D points | Simple, from LiDAR/SfM | Sparse, no surface |
| Meshes | Triangulated surfaces | Rendering, editing | Fixed topology |
| Voxels | 3D grid | Simple, regular | Memory cubic O(N³) |
| NeRF | Implicit neural field | Photorealistic, compact | Slow rendering |
| 3D Gaussian Splatting | Explicit Gaussians | Fast rendering, high quality | Large storage |
| Signed Distance Fields | Distance to nearest surface | Smooth surfaces | Limited detail |

**NeRF Architecture:**
```
Input: 3D point (x,y,z) + viewing direction (θ,φ)
  → Positional encoding (high-frequency sinusoids)
  → MLP (8 layers, 256 units)
  → Output: color (r,g,b) + density (σ)

Rendering: Ray marching through scene, accumulate color weighted by density
Training: Minimize photometric loss between rendered and real images
```

**3D Gaussian Splatting (2023):**
```
Represent scene as millions of 3D Gaussians
Each Gaussian has: position, covariance (shape), opacity, color (spherical harmonics)
Rendering: Project Gaussians to 2D, alpha-blend → image
Training: Optimize Gaussians to match training images
Key advantage: 100+ FPS rendering vs. NeRF's seconds per frame
```

**Layman Example:**
- **NeRF:** Like a CT scan for scenes. Take many photos from different angles, and the model learns a "3D recipe" for the entire scene. Give it any new viewpoint, and it can render what you'd see — including realistic lighting, reflections, and transparency.
- **3D Gaussian Splatting:** Instead of an implicit recipe, scatter millions of tiny colored blobs (Gaussians) in 3D space. From any angle, these blobs overlap to create a photorealistic image. Like pointillism painting but in 3D — fast because you just project and blend.

**Follow-up Questions:**

**Q: Why is NeRF slow and how is it improved?**
A: NeRF requires evaluating an MLP hundreds of times per pixel (ray marching). Solutions: (1) Instant-NGP: hash-grid encoding replaces MLP queries → 1000× faster, (2) Baking NeRF into explicit representations for real-time rendering, (3) Tri-planes, (4) 3D Gaussian Splatting abandons NeRF entirely for explicit representation with real-time rendering.

**Q: What is 3D Gaussian Splatting and why is it important?**
A: It represents scenes as optimizable 3D Gaussians with position, shape, color, and opacity. Differentiable rasterization enables training from images. Key advantages: (1) Real-time rendering (100+ FPS), (2) Quality matches NeRF, (3) Explicit representation (editable), (4) No ray marching needed. Rapidly replacing NeRF for novel view synthesis.

**Q: What are the applications of 3D reconstruction?**
A: (1) Virtual/augmented reality (place virtual objects in real scenes), (2) Autonomous driving (3D scene understanding), (3) Robotics (manipulation, navigation), (4) Cultural heritage (digitize monuments), (5) E-commerce (3D product views), (6) Film VFX (digital doubles, set extensions), (7) Mapping/surveying.

---

## Quick Reference: CV Model Selection

| Task | Best approach (2024+) |
|------|----------------------|
| Image classification (general) | ConvNeXt, EfficientNet-V2, or ViT + fine-tune |
| Image classification (mobile) | MobileNet v3, EfficientNet-Lite |
| Object detection (accuracy) | Co-DETR, DINO-DETR |
| Object detection (speed) | YOLOv8, RT-DETR |
| Semantic segmentation | Mask2Former, SegFormer |
| Instance segmentation | Mask2Former, YOLACT for real-time |
| Image generation | Stable Diffusion 3, DALL-E 3 |
| Zero-shot classification | CLIP, SigLIP |
| Segment anything | SAM 2 |
| Visual features (backbone) | DINOv2 |
| Open-vocabulary detection | Grounding DINO |
| 3D reconstruction | 3D Gaussian Splatting |
| Video understanding | VideoMAE v2, InternVideo |
| Depth estimation | Depth Anything v2 |
| Super-resolution | Real-ESRGAN, SwinIR |

---

## Common Interview Traps (CV-Specific)

1. **"CNNs are obsolete because of ViT"** → No. ConvNeXt (pure CNN) matches ViT. CNNs are better for small data and edge deployment. The field is converging toward hybrid approaches.

2. **"More layers = better"** → Not without skip connections. Plain networks degrade after ~20 layers. ResNet/DenseNet solved this.

3. **"GANs are the best generative model"** → Diffusion models surpass GANs in quality and diversity as of 2022+. GANs remain relevant for speed.

4. **"Pre-training on ImageNet is always best"** → For medical/satellite/specialized domains, domain-specific pre-training or SSL on domain data often outperforms ImageNet pre-training.

5. **"Higher resolution = better accuracy"** → Diminishing returns + quadratic compute cost. EfficientNet's compound scaling optimally trades resolution, depth, and width. Often 384×384 is the sweet spot.

6. **"Batch Norm is always needed"** → Transformers use Layer Norm. Group Norm for small batches. Recent ConvNeXt uses Layer Norm in CNN. BN has issues with small batches and inference behavior.

7. **"mAP@0.5 means detection is solved"** → mAP@0.5 is lenient. mAP@[.5:.95] is much harder. Most models have significant drop from AP@0.5 to AP@0.75, showing localization is still imprecise.

8. **"Data augmentation can replace more data"** → It helps but has limits. Augmented data is not truly novel information. With extremely limited data, augmentation bridges a gap but can't replace having 10× more real data.
