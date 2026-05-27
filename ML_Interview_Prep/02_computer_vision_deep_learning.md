# Computer Vision - Deep Learning Comprehensive Guide

## Table of Contents
1. [Foundations of Computer Vision](#foundations)
2. [Convolutional Neural Networks (CNNs)](#cnns)
3. [Key Architectures Evolution](#architectures)
4. [Object Detection](#object-detection)
5. [Image Segmentation](#image-segmentation)
6. [Vision Transformers (ViT)](#vision-transformers)
7. [Generative Models for Vision](#generative-models)
8. [Transfer Learning & Fine-tuning](#transfer-learning)
9. [Data Augmentation](#data-augmentation)
10. [Interview Questions with Answers](#interview-questions)
11. [Comparisons & Alternatives](#comparisons)

---

## Foundations of Computer Vision

### What is Computer Vision?
Computer Vision enables machines to interpret and understand visual information from the world — images and videos.

**Layman Example:** Teaching a computer to "see" like humans do. When you look at a photo and instantly recognize faces, objects, text — CV aims to replicate this capability.

### Key Tasks in CV

| Task | Description | Example |
|------|-------------|---------|
| Image Classification | Assign label to entire image | Cat vs Dog |
| Object Detection | Locate + classify objects | Self-driving car detecting pedestrians |
| Semantic Segmentation | Label every pixel by class | Medical imaging |
| Instance Segmentation | Separate individual objects | Count cells in microscopy |
| Pose Estimation | Detect body keypoints | Fitness apps |
| Image Generation | Create new images | DALL-E, Stable Diffusion |
| OCR | Extract text from images | Document digitization |

### How Images are Represented
- **Grayscale:** H × W matrix (0-255)
- **Color (RGB):** H × W × 3 tensor
- **Normalization:** Typically scale to [0,1] or standardize per channel

---

## Convolutional Neural Networks (CNNs)

### Core Building Blocks

#### 1. Convolution Layer
**Concept:** Slides a small filter (kernel) over the input, computing dot products to detect features.

**Layman Example:** Like running a magnifying glass over a photo. Each position of the magnifying glass looks at a small patch and detects specific patterns (edges, textures, shapes).

**Key Parameters:**
- **Kernel size:** Typically 3×3 or 5×5
- **Stride:** Step size (1 = overlap, 2 = skip)
- **Padding:** 'same' (preserve size) or 'valid' (no padding)
- **Number of filters:** Determines output depth (channels)

**Output Size Formula:**
```
Output = (Input - Kernel + 2×Padding) / Stride + 1
```

**Example:** Input 32×32, Kernel 3×3, Padding 1, Stride 1 → Output 32×32

#### 2. Pooling Layer
**Purpose:** Downsamples feature maps, reduces computation, adds translation invariance.

**Types:**
- **Max Pooling:** Takes maximum value in each patch (most common)
- **Average Pooling:** Takes average value
- **Global Average Pooling (GAP):** Average over entire feature map (replaces FC layers)

#### 3. Activation Functions
- **ReLU:** f(x) = max(0, x) — most common, simple, fast
- **Leaky ReLU:** f(x) = x if x>0, αx if x≤0 — prevents dying neurons
- **GELU:** Used in transformers, smooth approximation
- **Swish:** f(x) = x × sigmoid(x) — self-gated

#### 4. Batch Normalization
- Normalizes layer inputs to zero mean and unit variance
- Reduces internal covariate shift
- Acts as mild regularization
- Allows higher learning rates
- Applied AFTER convolution, BEFORE activation (commonly)

#### 5. Dropout
- Randomly zeros out neurons during training (p=0.25-0.5 typical)
- Prevents co-adaptation of features
- Acts as ensemble of subnetworks
- Disabled at inference time (scale outputs)

### CNN Feature Hierarchy
```
Layer 1: Edges, corners, colors
Layer 2: Textures, patterns
Layer 3: Object parts (eyes, wheels)
Layer 4: Objects (faces, cars)
Layer 5: Scenes, complex compositions
```

**Follow-up Q: Why convolutions instead of fully connected layers?**
- **Parameter sharing:** One filter applied everywhere (fewer parameters)
- **Sparse connectivity:** Each output depends on small input region
- **Translation equivariance:** Same pattern detected regardless of position
- A 224×224×3 image with FC layer = 150K parameters per neuron; Conv 3×3 = 27 parameters per filter

---

## Key Architectures Evolution

### LeNet-5 (1998)
- Pioneer CNN for digit recognition
- 5 layers, ~60K parameters
- Conv → Pool → Conv → Pool → FC → FC → Output

### AlexNet (2012) — ImageNet Breakthrough
- 8 layers, 60M parameters
- Key innovations: ReLU, Dropout, Data Augmentation, GPU training
- Won ImageNet 2012 (15.3% error vs 26.2% runner-up)

### VGGNet (2014)
- Deep (16-19 layers) with only 3×3 convolutions
- Key insight: Two 3×3 convs = one 5×5 conv (fewer parameters, more non-linearity)
- 138M parameters (too large for mobile)

### GoogLeNet/Inception (2014)
- 22 layers, only 5M parameters
- **Inception module:** Parallel paths with 1×1, 3×3, 5×5 convs + pooling
- 1×1 convolutions for dimensionality reduction (bottleneck)

### ResNet (2015) — Game Changer
- **Skip connections (residual connections):** H(x) = F(x) + x
- Enables training of very deep networks (50, 101, 152 layers)
- Solves vanishing gradient problem in deep networks
- Key insight: Learning residual is easier than learning full mapping

**Layman Example:** Like an express elevator — instead of taking stairs through every floor, you can skip floors while still visiting them.

**Follow-up Q: Why do skip connections help?**
- Gradient flows directly through identity mapping
- Network can learn identity if additional layers aren't needed
- Enables ensemble-like behavior (different effective depths)
- Combats vanishing gradients

### DenseNet (2017)
- Every layer connected to every other layer
- Feature reuse: concatenation instead of addition
- More parameter efficient than ResNet

### EfficientNet (2019)
- Compound scaling: width, depth, resolution scaled together
- NAS (Neural Architecture Search) to find base network
- State-of-the-art accuracy with fewer parameters
- EfficientNet-B0 to B7 (increasing complexity)

### MobileNet (2017)
- Depthwise separable convolutions
- Designed for mobile/edge devices
- Standard conv (3×3×C_in×C_out) → Depthwise (3×3×1×C) + Pointwise (1×1×C_in×C_out)
- Reduces computation by ~8-9x

---

## Object Detection

### Two-Stage Detectors (Region-based)

#### R-CNN Family
1. **R-CNN (2014):** Selective Search → Crop regions → CNN → SVM
2. **Fast R-CNN (2015):** Shared CNN computation, ROI Pooling
3. **Faster R-CNN (2016):** Region Proposal Network (RPN) replaces Selective Search

**Key Components:**
- **Anchor Boxes:** Predefined boxes of different sizes/ratios at each position
- **RPN:** Small network that proposes regions likely to contain objects
- **ROI Pooling/Align:** Extracts fixed-size features from variable-size proposals
- **NMS (Non-Max Suppression):** Removes duplicate detections

### One-Stage Detectors

#### YOLO (You Only Look Once)
- Divides image into grid, each cell predicts boxes + classes simultaneously
- **YOLO v1-v8+ evolution:** Speed and accuracy improvements
- Real-time detection (30+ FPS)
- Single forward pass

**Layman Example:** Instead of scanning an image region by region (two-stage), YOLO looks at the entire image at once — like glancing at a scene and immediately identifying everything.

#### SSD (Single Shot MultiBox Detector)
- Multi-scale feature maps for detecting objects of different sizes
- Anchor boxes at each scale
- Faster than Faster R-CNN, slower than YOLO

### Comparison

| Detector | Speed | Accuracy | Use Case |
|----------|-------|----------|----------|
| Faster R-CNN | Slow | High | Research, high accuracy needed |
| YOLO v8 | Very Fast | High | Real-time applications |
| SSD | Fast | Medium | Balanced speed/accuracy |
| RetinaNet | Medium | High | Focal loss for imbalance |

### Key Concepts

**IoU (Intersection over Union):**
- Measures overlap between predicted and ground truth boxes
- IoU = Area of Overlap / Area of Union
- Typical threshold: 0.5 (AP50) or 0.5:0.95 (AP)

**mAP (Mean Average Precision):**
- Average of AP across all classes
- AP = area under precision-recall curve for each class
- Standard metric for object detection

**Focal Loss (RetinaNet):**
- Addresses class imbalance in one-stage detectors
- Down-weights easy examples, focuses on hard ones
- FL(p) = -α(1-p)^γ × log(p)

**Follow-up Q: Why are two-stage detectors more accurate but slower?**
Two-stage detectors first propose potential regions, then classify each region carefully. This two-step process allows more focused attention on likely object locations. One-stage detectors must handle all locations simultaneously, trading some accuracy for speed.

---

## Image Segmentation

### Semantic Segmentation
- Classifies every pixel into a class
- No distinction between instances of the same class
- Architecture: Encoder-Decoder with skip connections

**Key Models:**
- **FCN (2015):** Fully Convolutional Network, upsampling with deconv
- **U-Net (2015):** Encoder-decoder with skip connections (great for medical)
- **DeepLab (2017):** Atrous (dilated) convolutions + ASPP
- **PSPNet:** Pyramid Pooling Module for multi-scale context

### Instance Segmentation
- Detects individual objects AND segments them pixel-wise
- **Mask R-CNN:** Faster R-CNN + mask prediction branch
- **YOLACT:** Real-time instance segmentation

### Panoptic Segmentation
- Combines semantic + instance segmentation
- Every pixel gets a class AND instance ID
- "Stuff" (sky, road) + "Things" (cars, people)

### U-Net Architecture (Important for Medical Imaging)
```
Encoder (Contracting Path):
  Conv → Conv → MaxPool (repeated 4x)
  
Bottleneck:
  Conv → Conv

Decoder (Expanding Path):
  UpConv → Concat(skip connection) → Conv → Conv (repeated 4x)
```

**Why U-Net works well for medical imaging:**
- Works with very few training images
- Skip connections preserve spatial details
- Symmetric architecture ensures precise localization
- Data augmentation handles small datasets

---

## Vision Transformers (ViT)

### Concept
Applies transformer architecture (originally for NLP) to image recognition.

**How it works:**
1. Split image into fixed-size patches (e.g., 16×16)
2. Flatten patches and project to embedding dimension
3. Add positional embeddings
4. Feed through standard transformer encoder
5. Use [CLS] token or GAP for classification

**Layman Example:** Instead of sliding a magnifying glass (CNN), you cut the photo into puzzle pieces and let the model figure out how all pieces relate to each other simultaneously.

### ViT vs CNN

| Aspect | CNN | ViT |
|--------|-----|-----|
| Inductive bias | Translation equivariance, locality | None (learns from data) |
| Data efficiency | Better with small data | Needs large data (or pretraining) |
| Global context | Limited (local receptive field) | Full (self-attention) |
| Computation | Efficient | Quadratic with image size |
| Performance (large data) | Good | Excellent |

### Key ViT Variants
- **DeiT:** Data-efficient Image Transformers (distillation)
- **Swin Transformer:** Shifted window approach, hierarchical
- **BEiT:** BERT-style pretraining for vision
- **MAE:** Masked Autoencoder for self-supervised learning

### Swin Transformer (Important)
- Hierarchical feature maps (like CNNs)
- Shifted window self-attention (linear complexity)
- Can serve as backbone for detection/segmentation
- Currently state-of-the-art on many benchmarks

---

## Generative Models for Vision

### GANs (Generative Adversarial Networks)
- **Generator:** Creates fake images from noise
- **Discriminator:** Distinguishes real from fake
- **Training:** Minimax game → Generator gets better at fooling discriminator

**Key GAN Variants:**
- DCGAN: Deep Convolutional GAN
- StyleGAN: High-quality face generation
- CycleGAN: Unpaired image-to-image translation
- Pix2Pix: Paired image-to-image translation
- ProGAN: Progressive growing for high resolution

**GAN Challenges:**
- Mode collapse: Generator produces limited variety
- Training instability
- Evaluation difficulty (FID, IS scores)

### Diffusion Models
- **Forward process:** Gradually add noise to image
- **Reverse process:** Learn to denoise step by step
- Currently state-of-the-art for image generation

**Key Models:**
- DDPM: Denoising Diffusion Probabilistic Models
- Stable Diffusion: Latent diffusion in compressed space
- DALL-E 2/3: Text-to-image generation
- Midjourney: Artistic image generation

**Follow-up Q: Why are diffusion models better than GANs?**
- More stable training (no adversarial dynamics)
- Better mode coverage (less mode collapse)
- High-quality diverse outputs
- But slower inference (multiple denoising steps)

### VAE (Variational Autoencoder)
- Encoder maps to latent distribution
- Decoder samples from distribution to reconstruct
- KL divergence regularizes latent space
- Less sharp outputs than GANs but more stable training

---

## Transfer Learning & Fine-tuning

### Why Transfer Learning?
- Pretrained models learn generalizable features
- Reduces training time and data requirements
- Critical when you have limited labeled data

### Strategies

| Strategy | When | How |
|----------|------|-----|
| Feature extraction | Small dataset, similar domain | Freeze pretrained layers, train new head |
| Fine-tuning (last layers) | Medium dataset | Unfreeze last few layers + new head |
| Full fine-tuning | Large dataset, different domain | Unfreeze all layers, low LR |
| Progressive unfreezing | Careful adaptation | Gradually unfreeze from top to bottom |

### Best Practices
- Use lower learning rate for pretrained layers (10x-100x smaller)
- Always normalize inputs same as pretraining (ImageNet: mean=[0.485, 0.456, 0.406])
- Start with feature extraction, then fine-tune if needed
- Use learning rate warmup and cosine annealing

---

## Data Augmentation

### Standard Augmentations
- Random crop, flip (horizontal), rotation
- Color jittering (brightness, contrast, saturation, hue)
- Gaussian blur, noise
- Scaling, shearing

### Advanced Augmentations
- **Cutout:** Randomly mask square patches
- **Mixup:** Blend two images (and labels) linearly
- **CutMix:** Cut-paste patches between images
- **RandAugment:** Random selection from augmentation pool
- **AutoAugment:** Learned augmentation policies
- **Mosaic (YOLO):** Combine 4 images into one

### Test-Time Augmentation (TTA)
- Apply augmentations at inference
- Average predictions across augmented versions
- Typically improves accuracy by 1-2%

---

## Interview Questions with Answers

### Q1: Explain 1×1 convolution and its purpose
**Answer:**
- Acts as a cross-channel pooling (mixes information across channels)
- Dimensionality reduction (reduce number of channels cheaply)
- Adds non-linearity (with activation) without changing spatial dimensions
- Used in Inception (bottleneck), ResNet (bottleneck blocks), Network-in-Network

### Q2: What is receptive field and why does it matter?
**Answer:**
- Region of input that affects a particular output neuron
- Deeper layers have larger receptive fields
- Important: Must be large enough to capture relevant patterns
- Can be increased by: deeper networks, larger kernels, dilated convolutions, pooling

### Q3: How does dilated/atrous convolution work?
**Answer:**
- Inserts gaps (dilation rate) between kernel elements
- Increases receptive field without increasing parameters or reducing resolution
- Dilation rate 2: 3×3 kernel covers 5×5 area
- Used in DeepLab for segmentation (maintains spatial resolution)

### Q4: Explain Batch Norm vs Layer Norm vs Group Norm vs Instance Norm
| Normalization | Normalizes Across | Use Case |
|---------------|-------------------|----------|
| Batch Norm | Batch dimension | CNNs (large batches) |
| Layer Norm | Feature dimensions | Transformers, RNNs |
| Instance Norm | Spatial (H,W) per channel | Style transfer |
| Group Norm | Groups of channels | When batch size is small |

### Q5: What is the vanishing gradient problem and how is it solved in CV?
**Answer:**
- Gradients become extremely small in deep networks during backpropagation
- Early layers barely update → can't learn useful features
- **Solutions:**
  - Skip/residual connections (ResNet)
  - Batch normalization
  - ReLU activation (non-saturating)
  - Proper initialization (He, Xavier)
  - Gradient clipping

### Q6: Explain Non-Maximum Suppression (NMS)
**Answer:**
1. Sort all detections by confidence score
2. Select the highest confidence box
3. Remove all boxes with IoU > threshold with selected box
4. Repeat until no boxes remain
- **Soft-NMS:** Instead of removing, reduce confidence of overlapping boxes
- Threshold typically 0.5

### Q7: What is Feature Pyramid Network (FPN)?
**Answer:**
- Builds multi-scale feature maps with strong semantics at all levels
- Top-down pathway + lateral connections
- Combines low-resolution (semantically strong) with high-resolution (spatially precise)
- Essential for detecting objects at different scales
- Used in Faster R-CNN, Mask R-CNN, RetinaNet

### Q8: How do you handle class imbalance in object detection?
**Answer:**
- Most locations are background (thousands of negatives vs few positives)
- **Focal Loss:** Down-weights easy negatives
- **Hard Negative Mining:** Sample hardest negatives for training
- **OHEM:** Online Hard Example Mining
- **Class-aware sampling**

### Q9: Explain depthwise separable convolutions
**Answer:**
Standard conv (K×K×C_in×C_out): Full 3D convolution
Depthwise separable:
1. **Depthwise:** K×K×1 filter per input channel (spatial filtering)
2. **Pointwise:** 1×1×C_in×C_out (channel mixing)

Computation reduction: K²×C_in×C_out → K²×C_in + C_in×C_out
For 3×3 with 256 channels: ~9x fewer computations

### Q10: What are attention mechanisms in CV?
**Answer:**
- **Channel attention (SE-Net):** Learn to weight channels by importance
- **Spatial attention:** Learn which spatial locations are important
- **Self-attention (ViT):** Every patch attends to every other patch
- **CBAM:** Channel + Spatial attention sequentially
- Allows network to focus on relevant features dynamically

---

## Comparisons & Alternatives

### CNN vs ViT Decision Guide
- **Small dataset (<10K images):** CNN with pretrained weights
- **Medium dataset (10K-100K):** Either, try both
- **Large dataset (>100K):** ViT often wins
- **Real-time requirement:** CNN (MobileNet, EfficientNet) or efficient ViT
- **Edge deployment:** CNN (well-optimized compilers)

### Self-Supervised Learning in CV
- **Contrastive Learning (SimCLR, MoCo):** Pull similar views together, push different apart
- **Masked Image Modeling (MAE, BEiT):** Mask patches, predict missing
- **DINO/DINOv2:** Self-distillation with no labels
- Enables pretraining on massive unlabeled datasets

### Current State-of-the-Art (2024-2025)
- **Classification:** Large ViTs pretrained on billions of images
- **Detection:** DINO (DETR variants), YOLOv8+
- **Segmentation:** SAM (Segment Anything Model), Mask2Former
- **Generation:** Diffusion models (Stable Diffusion, DALL-E 3)
- **Foundation Models:** DINOv2, SAM, CLIP (vision-language)

### CLIP (Contrastive Language-Image Pre-training)
- Learns joint embedding space for images and text
- Zero-shot classification via text prompts
- Foundation for many multimodal models
- Trained on 400M image-text pairs

### Segment Anything Model (SAM)
- Promptable segmentation (point, box, text)
- Trained on 11M images, 1.1B masks
- Zero-shot segmentation for any object
- Foundation model for segmentation tasks
