# 🎯 FINAL PRODUCTION MODEL TRAINING

## Goal
Train ONE production model that detects ALL types of fake images:
- **Deepfake videos** (Celeb-DF, FaceForensics++)
- **GAN faces** (StyleGAN/ThisPersonDoesNotExist)
- **Diffusion models** (Gemini, Grok, Midjourney, SDXL)

**Success Criteria:**
- ✅ >90% test accuracy
- ✅ Gemini/Grok images classified as FAKE (fake_prob > 0.6)
- ✅ Real images classified as REAL (fake_prob < 0.4)
- ✅ Clear separation between classes

---

## 📦 Data Collection (Before Training)

### 1. Automatic Sources (No Action Required)
- **Kaggle Real vs Fake**: Place `archive.zip` in scripts folder
- **TPDNE GAN**: Auto-downloaded (600 images)

### 2. Manual Collection (Optional but Recommended)

#### **Gemini Images (Target: 800)**
```bash
# Create folder
mkdir -p gemini_images

# Generate images:
# 1. Go to ai.google.dev
# 2. Prompt: "Generate realistic face portrait"
# 3. Download 800 images
# 4. Save to gemini_images/
```

#### **Grok Images (Target: 600)**
```bash
mkdir -p grok_images

# Generate images:
# 1. Go to x.com/i/grok
# 2. Generate 600 face portraits
# 3. Save to grok_images/
```

#### **Midjourney (Target: 700)**
```bash
mkdir -p midjourney_images

# Download Midjourney face portraits
# Save to midjourney_images/
```

#### **SDXL (Target: 700)**
```bash
mkdir -p sdxl_images

# Generate using Stable Diffusion XL
# Or download from Civitai/HuggingFace
# Save to sdxl_images/
```

#### **Celeb-DF v2 (Target: 500 frames)**
```bash
mkdir -p celeb_df/real celeb_df/fake

# Download from: https://github.com/yuezunli/celeb-deepfakeforensics
# Place videos in respective folders
```

#### **FaceForensics++ (Target: 500 frames)**
```bash
mkdir -p faceforensics/original faceforensics/manipulated

# Download from: https://github.com/ondyari/FaceForensics
# Place c23 videos in respective folders
```

---

## 🚀 Training

### Prerequisites
```bash
cd /media/sreenath/kannaDisk/iitproject/HiddenLayer/scripts

# Check Python dependencies
python3 -c "import tensorflow, cv2, requests, numpy"

# If missing:
pip3 install tensorflow opencv-python requests numpy
```

### Run Training
```bash
# Make executable
chmod +x train_final_production.py

# Start training (2-3 hours on GPU, 6-12 hours on CPU)
python3 train_final_production.py
```

### Training Process
```
PHASE 1: DATA COLLECTION (8 sources)
├── Kaggle dataset extraction
├── TPDNE GAN download
├── Gemini import
├── Grok import
├── Midjourney import
├── SDXL import
├── Celeb-DF frame extraction
└── FaceForensics++ frame extraction

PHASE 2: PREPROCESSING
├── Face detection & cropping
└── Dataset balancing (5000 real + 5000 fake)

PHASE 3: TRAINING (20-25 epochs)
├── Phase 1: Frozen base (10 epochs)
└── Phase 2: Fine-tuning (15 epochs)

PHASE 4: EXPORT
└── TFLite FP16 export → deepfake_net.tflite
```

---

## 📱 Deploy to App

### After Training Completes:
```bash
# 1. Copy model to app assets
cp deepfake_net.tflite ../app/src/main/assets/

# 2. Rebuild APK
cd ..
./gradlew clean assembleDebug

# 3. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Test!
# Upload Gemini/Grok generated images
# Expected: fake_prob > 0.60, classification = "LIKELY DEEPFAKE"
```

---

## 🎯 Expected Results

### Model Performance
```
Training Accuracy:    92-95%
Validation Accuracy:  90-93%
Test Accuracy:        >90%
```

### Real-World Behavior
```
REAL images:
  fake_prob: 0.05 - 0.35
  Classification: "AUTHENTIC"

GAN/Diffusion FAKE:
  fake_prob: 0.60 - 0.95
  Classification: "LIKELY DEEPFAKE"

Video Deepfakes:
  fake_prob: 0.65 - 0.98
  Classification: "LIKELY DEEPFAKE" or "UNUSUAL PATTERNS"
```

---

## ⚠️ Troubleshooting

### Not Enough Data
```
❌ INSUFFICIENT DATA: 800 real, 1200 fake

Solution: The model needs minimum 1000 per class.
- Add more Kaggle images
- Download more diffusion images
- Include video datasets
```

### Low Accuracy (<90%)
```
Best Val Accuracy: 0.87

Possible causes:
- Dataset imbalance
- Poor face detection (check processed/ folder)
- Need more epochs (increase FINETUNE_EPOCHS to 20)
```

### GPU Issues
```
No GPU detected. Training will be VERY slow on CPU.

Solution:
- Install CUDA + cuDNN
- Or use Google Colab (upload script + data)
```

---

## 📊 Monitor Training

### Check Logs
```bash
# Watch progress live
python3 train_final_production.py 2>&1 | tee training.log

# After training, check accuracy curve
grep "val_accuracy" training.log
```

### Validate Model
```bash
# After deployment, test predictions
adb logcat | grep "🔴 RAW OUTPUT"

# Should see varying probabilities, not constant 0.5!
# Example: [0.823, 0.177] for fake image
```

---

## ✅ Success Checklist

- [ ] Collected 5000+ real images
- [ ] Collected 5000+ fake images (across 8 sources)
- [ ] Training completed 20-25 epochs
- [ ] Validation accuracy >90%
- [ ] Model exported to `deepfake_net.tflite`
- [ ] Copied to `app/src/main/assets/`
- [ ] APK rebuilt and installed
- [ ] Gemini/Grok images classified as FAKE
- [ ] Real images classified as REAL
- [ ] No more constant 0.5 predictions!

---

**🎉 After this training, your model is PRODUCTION-READY!**
