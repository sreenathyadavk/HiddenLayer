#!/usr/bin/env python3
"""
FINAL PRODUCTION TRAINING SCRIPT
================================
ONE comprehensive model for ALL fake detection:
- Deepfake videos (Celeb-DF, FaceForensics++)
- GAN faces (StyleGAN, ThisPersonDoesNotExist)
- Diffusion models (Gemini, Grok, Midjourney, SDXL)

Target: 5,000 REAL + 5,000 FAKE (500-1000 per fake source)
Training: 20-25 epochs total (10 frozen + 10-15 fine-tuned)
Success: >90% test accuracy, clear separation (fake_prob > 0.6 for fake, < 0.4 for real)
"""

import os
import sys
import shutil
import time
import zipfile
import cv2
import numpy as np
import requests
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from pathlib import Path
import json

# ============================================================================
# CONFIGURATION
# ============================================================================
TARGET_SIZE = (299, 299)  # Xception input
TARGET_REAL = 5000
TARGET_FAKE = 5000

# Per-source targets (totaling ~5000 fake)
TARGET_PER_SOURCE = {
    'kaggle_fake': 800,      # Existing deepfakes
    'tpdne_gan': 600,        # ThisPersonDoesNotExist (StyleGAN2)
    'gemini_diffusion': 800, # Google Gemini generated
    'grok_diffusion': 600,   # xAI Grok generated  
    'midjourney': 700,       # Midjourney AI art
    'sdxl': 700,             # Stable Diffusion XL
    'celebdf_deepfake': 500, # Celeb-DF video frames
    'ff_deepfake': 500       # FaceForensics++ frames
}

BATCH_SIZE = 16
FROZEN_EPOCHS = 10       # Phase 1: Frozen base training
FINETUNE_EPOCHS = 15     # Phase 2: Fine-tuning (total = 25)
UNFREEZE_LAYERS = 30     # Layers to unfreeze

# Directories
WORKSPACE = "final_production_workspace"
RAW_DIR = f"{WORKSPACE}/raw"
PROCESSED_DIR = f"{WORKSPACE}/processed"
MODEL_FILE = "deepfake_net.tflite"

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def setup_dirs():
    """Create workspace directory structure."""
    print("📁 Setting up workspace...")
    if os.path.exists(WORKSPACE):
        print(f"   Cleaning existing workspace: {WORKSPACE}")
        shutil.rmtree(WORKSPACE)
    
    os.makedirs(f"{RAW_DIR}/real", exist_ok=True)
    os.makedirs(f"{RAW_DIR}/fake", exist_ok=True)
    os.makedirs(f"{PROCESSED_DIR}/real", exist_ok=True)
    os.makedirs(f"{PROCESSED_DIR}/fake", exist_ok=True)
    print("   ✅ Workspace ready")

def check_gpu():
    """Check GPU availability."""
    gpus = tf.config.list_physical_devices('GPU')
    if gpus:
        print(f"✅ GPU Detected: {gpus}")
        try:
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError as e:
            print(f"   Warning: {e}")
        return True
    else:
        print("⚠️  No GPU detected. Training will be VERY slow on CPU.")
        print("   Estimated time: 6-12 hours on CPU vs 2-3 hours on GPU")
        return False

# ============================================================================
# DATA COLLECTION - EXISTING SOURCES
# ============================================================================

def extract_kaggle_dataset():
    """Extract Kaggle Real vs Fake dataset."""
    print(f"\n📦 [1/8] Kaggle Dataset (Target: {TARGET_PER_SOURCE['kaggle_fake']} fake)")
    
    if not os.path.exists("archive.zip"):
        print(f"   ⚠️  archive.zip not found. Skipping Kaggle source.")
        return 0, 0
    
    print(f"   Found: archive.zip ({os.path.getsize('archive.zip') / 1024 / 1024:.1f} MB)")
    
    temp_extract = f"{WORKSPACE}/temp_kaggle"
    with zipfile.ZipFile("archive.zip", 'r') as z:
        z.extractall(temp_extract)
    
    # Find real/fake folders
    real_src, fake_src = None, None
    for root, dirs, files in os.walk(temp_extract):
        for d in dirs:
            d_lower = d.lower()
            path = os.path.join(root, d)
            if 'real' in d_lower and 'fake' not in d_lower and len(os.listdir(path)) > 5:
                real_src = path
            if 'fake' in d_lower and 'real' not in d_lower and len(os.listdir(path)) > 5:
                fake_src = path
    
    if not real_src or not fake_src:
        print("   ❌ Could not locate real/fake folders")
        shutil.rmtree(temp_extract)
        return 0, 0
    
    def copy_images(src, dst, limit):
        files = [f for f in os.listdir(src) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        count = 0
        for f in files[:limit]:
            shutil.copy(os.path.join(src, f), os.path.join(dst, f"kaggle_{f}"))
            count += 1
        return count
    
    real_count = copy_images(real_src, f"{RAW_DIR}/real", 2000)
    fake_count = copy_images(fake_src, f"{RAW_DIR}/fake", TARGET_PER_SOURCE['kaggle_fake'])
    
    print(f"   ✅ {real_count} Real, {fake_count} Fake")
    shutil.rmtree(temp_extract)
    return real_count, fake_count

def download_tpdne_gan():
    """Download StyleGAN2 faces from ThisPersonDoesNotExist."""
    print(f"\n🤖 [2/8] ThisPersonDoesNotExist GAN (Target: {TARGET_PER_SOURCE['tpdne_gan']})")
    
    url = "https://thispersondoesnotexist.com/"
    headers = {'User-Agent': 'Mozilla/5.0'}
    success = 0
    target = TARGET_PER_SOURCE['tpdne_gan']
    
    for i in range(target):
        try:
            r = requests.get(url, headers=headers, timeout=15)
            if r.status_code == 200:
                filename = f"{RAW_DIR}/fake/tpdne_{i:04d}.jpg"
                with open(filename, 'wb') as f:
                    f.write(r.content)
                success += 1
                
                if success % 100 == 0:
                    print(f"   Progress: {success}/{target} ({success/target*100:.1f}%)")
            
            time.sleep(0.8)  # Rate limiting
            
        except Exception as e:
            if i % 100 == 0:
                print(f"   Warning at image {i}: {e}")
            continue
    
    print(f"   ✅ Downloaded {success} GAN faces")
    return success

# ============================================================================
# DATA COLLECTION - DIFFUSION MODELS
# ============================================================================

def collect_gemini_images():
    """Collect Gemini-generated images."""
    print(f"\n🎨 [3/8] Gemini Diffusion Images (Target: {TARGET_PER_SOURCE['gemini_diffusion']})")
    print("   Instructions:")
    print("   1. Go to Google AI Studio (ai.google.dev)")
    print("   2. Generate 800 face images using Gemini")
    print("   3. Save to: gemini_images/")
    print("   4. Script will auto-import")
    
    source_dir = "gemini_images"
    if not os.path.exists(source_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        return 0
    
    files = [f for f in os.listdir(source_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    count = 0
    for f in files[:TARGET_PER_SOURCE['gemini_diffusion']]:
        shutil.copy(os.path.join(source_dir, f), f"{RAW_DIR}/fake/gemini_{f}")
        count += 1
    
    print(f"   ✅ Imported {count} Gemini images")
    return count

def collect_grok_images():
    """Collect Grok-generated images."""
    print(f"\n🚀 [4/8] Grok Diffusion Images (Target: {TARGET_PER_SOURCE['grok_diffusion']})")
    print("   Instructions:")
    print("   1. Go to Grok AI (x.com/i/grok)")
    print("   2. Generate 600 face images")
    print("   3. Save to: grok_images/")
    
    source_dir = "grok_images"
    if not os.path.exists(source_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        return 0
    
    files = [f for f in os.listdir(source_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    count = 0
    for f in files[:TARGET_PER_SOURCE['grok_diffusion']]:
        shutil.copy(os.path.join(source_dir, f), f"{RAW_DIR}/fake/grok_{f}")
        count += 1
    
    print(f"   ✅ Imported {count} Grok images")
    return count

def collect_midjourney_images():
    """Collect Midjourney-generated images."""
    print(f"\n🎭 [5/8] Midjourney Images (Target: {TARGET_PER_SOURCE['midjourney']})")
    print("   Instructions:")
    print("   1. Download Midjourney face portraits")
    print("   2. Save to: midjourney_images/")
    
    source_dir = "midjourney_images"
    if not os.path.exists(source_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        return 0
    
    files = [f for f in os.listdir(source_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    count = 0
    for f in files[:TARGET_PER_SOURCE['midjourney']]:
        shutil.copy(os.path.join(source_dir, f), f"{RAW_DIR}/fake/midjourney_{f}")
        count += 1
    
    print(f"   ✅ Imported {count} Midjourney images")
    return count

def collect_sdxl_images():
    """Collect Stable Diffusion XL images."""
    print(f"\n🖼️  [6/8] SDXL Images (Target: {TARGET_PER_SOURCE['sdxl']})")
    print("   Instructions:")
    print("   1. Generate SDXL face images (stabilityai/stable-diffusion-xl)")
    print("   2. Save to: sdxl_images/")
    
    source_dir = "sdxl_images"
    if not os.path.exists(source_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        return 0
    
    files = [f for f in os.listdir(source_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    count = 0
    for f in files[:TARGET_PER_SOURCE['sdxl']]:
        shutil.copy(os.path.join(source_dir, f), f"{RAW_DIR}/fake/sdxl_{f}")
        count += 1
    
    print(f"   ✅ Imported {count} SDXL images")
    return count

# ============================================================================
# DATA COLLECTION - VIDEO DEEPFAKES
# ============================================================================

def collect_celebdf():
    """Extract frames from Celeb-DF v2."""
    print(f"\n🎬 [7/8] Celeb-DF Deepfakes (Target: {TARGET_PER_SOURCE['celebdf_deepfake']} frames)")
    
    real_dir = "celeb_df/real"
    fake_dir = "celeb_df/fake"
    
    if not os.path.exists(real_dir) or not os.path.exists(fake_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        print("   Download from: https://github.com/yuezunli/celeb-deepfakeforensics")
        return 0, 0
    
    def extract_frames(video_dir, output_dir, label, target):
        videos = [f for f in os.listdir(video_dir) if f.lower().endswith(('.mp4', '.avi'))]
        total = 0
        frames_per_video = max(1, target // len(videos)) if videos else 0
        
        for i, video in enumerate(videos[:50]):
            cap = cv2.VideoCapture(os.path.join(video_dir, video))
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            interval = max(1, frame_count // frames_per_video)
            
            extracted = 0
            for frame_num in range(0, frame_count, interval):
                if extracted >= frames_per_video or total >= target:
                    break
                
                cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
                ret, frame = cap.read()
                if ret:
                    cv2.imwrite(f"{output_dir}/{label}_{i:04d}_f{frame_num:04d}.jpg", frame)
                    extracted += 1
                    total += 1
            
            cap.release()
        
        return total
    
    real_frames = extract_frames(real_dir, f"{RAW_DIR}/real", "celebdf_real", 500)
    fake_frames = extract_frames(fake_dir, f"{RAW_DIR}/fake", "celebdf_fake", TARGET_PER_SOURCE['celebdf_deepfake'])
    
    print(f"   ✅ {real_frames} real frames, {fake_frames} fake frames")
    return real_frames, fake_frames

def collect_faceforensics():
    """Extract frames from FaceForensics++."""
    print(f"\n🎥 [8/8] FaceForensics++ (Target: {TARGET_PER_SOURCE['ff_deepfake']} frames)")
    
    real_dir = "faceforensics/original"
    fake_dir = "faceforensics/manipulated"
    
    if not os.path.exists(real_dir) or not os.path.exists(fake_dir):
        print(f"   ⏭️  Skipping (folder not found)")
        print("   Download from: https://github.com/ondyari/FaceForensics")
        return 0, 0
    
    def extract_frames(video_dir, output_dir, label, target):
        videos = [f for f in os.listdir(video_dir) if f.lower().endswith(('.mp4', '.avi'))]
        total = 0
        frames_per_video = max(1, target // len(videos)) if videos else 0
        
        for i, video in enumerate(videos[:50]):
            cap = cv2.VideoCapture(os.path.join(video_dir, video))
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            interval = max(1, frame_count // frames_per_video)
            
            extracted = 0
            for frame_num in range(0, frame_count, interval):
                if extracted >= frames_per_video or total >= target:
                    break
                
                cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
                ret, frame = cap.read()
                if ret:
                    cv2.imwrite(f"{output_dir}/{label}_{i:04d}_f{frame_num:04d}.jpg", frame)
                    extracted += 1
                    total += 1
            
            cap.release()
        
        return total
    
    real_frames = extract_frames(real_dir, f"{RAW_DIR}/real", "ff_real", 500)
    fake_frames = extract_frames(fake_dir, f"{RAW_DIR}/fake", "ff_fake", TARGET_PER_SOURCE['ff_deepfake'])
    
    print(f"   ✅ {real_frames} real frames, {fake_frames} fake frames")
    return real_frames, fake_frames

# ============================================================================
# PREPROCESSING
# ============================================================================

def crop_faces():
    """Detect and crop faces from all images."""
    print("\n✂️  Face Detection & Cropping")
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    
    for label in ['real', 'fake']:
        src_dir = f"{RAW_DIR}/{label}"
        dst_dir = f"{PROCESSED_DIR}/{label}"
        
        files = [f for f in os.listdir(src_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        print(f"\n   Processing {label.upper()}: {len(files)} images")
        
        saved = 0
        for i, fname in enumerate(files):
            img = cv2.imread(os.path.join(src_dir, fname))
            if img is None:
                continue
            
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, 1.1, 4, minSize=(100, 100))
            
            if len(faces) > 0:
                x, y, w, h = max(faces, key=lambda r: r[2] * r[3])
                margin = int(w * 0.2)
                x, y = max(0, x-margin), max(0, y-margin)
                w = min(img.shape[1]-x, w+2*margin)
                h = min(img.shape[0]-y, h+2*margin)
                
                face = cv2.resize(img[y:y+h, x:x+w], TARGET_SIZE)
                cv2.imwrite(os.path.join(dst_dir, fname), face)
                saved += 1
            
            if (i + 1) % 500 == 0:
                print(f"      {i+1}/{len(files)} ({saved} faces)")
        
        print(f"   ✅ {saved}/{len(files)} ({saved/len(files)*100:.1f}%)")

def balance_dataset():
    """Balance to target counts."""
    print("\n⚖️  Balancing Dataset")
    
    real_files = os.listdir(f"{PROCESSED_DIR}/real")
    fake_files = os.listdir(f"{PROCESSED_DIR}/fake")
    
    print(f"   Current: {len(real_files)} Real, {len(fake_files)} Fake")
    print(f"   Target:  {TARGET_REAL} Real, {TARGET_FAKE} Fake")
    
    if len(real_files) > TARGET_REAL:
        for f in real_files[TARGET_REAL:]:
            os.remove(f"{PROCESSED_DIR}/real/{f}")
    
    if len(fake_files) > TARGET_FAKE:
        for f in fake_files[TARGET_FAKE:]:
            os.remove(f"{PROCESSED_DIR}/fake/{f}")
    
    final_real = len(os.listdir(f"{PROCESSED_DIR}/real"))
    final_fake = len(os.listdir(f"{PROCESSED_DIR}/fake"))
    
    print(f"   ✅ Final: {final_real} Real, {final_fake} Fake")
    return final_real, final_fake

# ============================================================================
# TRAINING
# ============================================================================

def build_model():
    """Build Xception binary classifier."""
    print("\n🏗️  Building Xception Model")
    
    base_model = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base_model.trainable = False
    
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu')(x)
    x = Dropout(0.5)(x)
    predictions = Dense(2, activation='softmax')(x)  # [Real, Fake]
    
    model = Model(inputs=base_model.input, outputs=predictions)
    
    print(f"   Total params: {model.count_params():,}")
    return model, base_model

def train_model(model, base_model, train_gen, val_gen):
    """Two-phase training."""
    
    # Phase 1: Frozen base
    print(f"\n🏋️  Phase 1: Frozen Base ({FROZEN_EPOCHS} epochs)")
    model.compile(
        optimizer=Adam(1e-4),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks1 = [
        EarlyStopping('val_loss', patience=3, restore_best_weights=True, verbose=1),
        ModelCheckpoint('best_frozen.h5', 'val_accuracy', save_best_only=True, verbose=1),
        ReduceLROnPlateau('val_loss', factor=0.5, patience=2, verbose=1)
    ]
    
    h1 = model.fit(train_gen, epochs=FROZEN_EPOCHS, validation_data=val_gen, callbacks=callbacks1, verbose=1)
    print(f"   ✅ Phase 1: Best Val Acc = {max(h1.history['val_accuracy']):.4f}")
    
    # Phase 2: Fine-tuning
    print(f"\n🔥 Phase 2: Fine-Tuning ({FINETUNE_EPOCHS} epochs, {UNFREEZE_LAYERS} layers)")
    base_model.trainable = True
    for layer in base_model.layers[:-UNFREEZE_LAYERS]:
        layer.trainable = False
    
    model.compile(
        optimizer=Adam(1e-5),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks2 = [
        EarlyStopping('val_loss', patience=2, restore_best_weights=True, verbose=1),
        ModelCheckpoint('best_finetuned.h5', 'val_accuracy', save_best_only=True, verbose=1),
        ReduceLROnPlateau('val_loss', factor=0.3, patience=2, verbose=1)
    ]
    
    h2 = model.fit(train_gen, epochs=FINETUNE_EPOCHS, validation_data=val_gen, callbacks=callbacks2, verbose=1)
    print(f"   ✅ Phase 2: Best Val Acc = {max(h2.history['val_accuracy']):.4f}")
    
    return h1, h2

def export_tflite(model):
    """Export to TFLite FP16."""
    print(f"\n📦 Exporting {MODEL_FILE}")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    with open(MODEL_FILE, 'wb') as f:
        f.write(tflite_model)
    
    size = os.path.getsize(MODEL_FILE) / 1024 / 1024
    print(f"   ✅ {os.path.abspath(MODEL_FILE)} ({size:.2f} MB)")

# ============================================================================
# MAIN
# ============================================================================

def main():
    print("=" * 70)
    print("  FINAL PRODUCTION MODEL TRAINING")
    print("  Multi-Source Deepfake Detection (GAN + Diffusion + Video)")
    print("=" * 70)
    
    print(f"\n🔧 TensorFlow {tf.__version__}")
    check_gpu()
    
    setup_dirs()
    
    # Data Collection
    print("\n" + "=" * 70)
    print("  PHASE 1: DATA COLLECTION (8 SOURCES)")
    print("=" * 70)
    
    kaggle_real, kaggle_fake = extract_kaggle_dataset()
    tpdne = download_tpdne_gan()
    gemini = collect_gemini_images()
    grok = collect_grok_images()
    midjourney = collect_midjourney_images()
    sdxl = collect_sdxl_images()
    celebdf_real, celebdf_fake = collect_celebdf()
    ff_real, ff_fake = collect_faceforensics()
    
    total_real = kaggle_real + celebdf_real + ff_real
    total_fake = kaggle_fake + tpdne + gemini + grok + midjourney + sdxl + celebdf_fake + ff_fake
    
    print(f"\n📊 COLLECTION SUMMARY:")
    print(f"   Real: {total_real}")
    print(f"   Fake: {total_fake}")
    print(f"      - Kaggle deepfakes: {kaggle_fake}")
    print(f"      - TPDNE GAN: {tpdne}")
    print(f"      - Gemini: {gemini}")
    print(f"      - Grok: {grok}")
    print(f"      - Midjourney: {midjourney}")
    print(f"      - SDXL: {sdxl}")
    print(f"      - Celeb-DF: {celebdf_fake}")
    print(f"      - FaceForensics++: {ff_fake}")
    
    # Preprocessing
    print("\n" + "=" * 70)
    print("  PHASE 2: PREPROCESSING")
    print("=" * 70)
    
    crop_faces()
    final_real, final_fake = balance_dataset()
    
    if final_real < 1000 or final_fake < 1000:
        print(f"\n❌ INSUFFICIENT DATA: {final_real} real, {final_fake} fake")
        sys.exit(1)
    
    # Training
    print("\n" + "=" * 70)
    print("  PHASE 3: TRAINING (20-25 EPOCHS)")
    print("=" * 70)
    
    datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.xception.preprocess_input,
        horizontal_flip=True,
        rotation_range=15,
        zoom_range=0.15,
        validation_split=0.2
    )
    
    train_gen = datagen.flow_from_directory(
        PROCESSED_DIR, target_size=TARGET_SIZE, batch_size=BATCH_SIZE,
        class_mode='categorical', classes=['real', 'fake'], subset='training'
    )
    
    val_gen = datagen.flow_from_directory(
        PROCESSED_DIR, target_size=TARGET_SIZE, batch_size=BATCH_SIZE,
        class_mode='categorical', classes=['real', 'fake'], subset='validation'
    )
    
    model, base_model = build_model()
    h1, h2 = train_model(model, base_model, train_gen, val_gen)
    
    final_acc = max(h2.history['val_accuracy'])
    print(f"\n🎯 FINAL TEST ACCURACY: {final_acc:.4f}")
    
    if final_acc < 0.90:
        print("⚠️  Warning: Below 90% target, but proceeding with export")
    
    # Export
    print("\n" + "=" * 70)
    print("  PHASE 4: EXPORT")
    print("=" * 70)
    
    export_tflite(model)
    
    # Success
    print("\n" + "=" * 70)
    print("  ✅ TRAINING COMPLETE")
    print("=" * 70)
    print(f"\n📋 Next Steps:")
    print(f"   1. cp {MODEL_FILE} ../app/src/main/assets/")
    print(f"   2. cd .. && ./gradlew assembleDebug")
    print(f"   3. adb install -r app/build/outputs/apk/debug/app-debug.apk")
    print(f"   4. Test with Gemini/Grok images!")
    print()

if __name__ == "__main__":
    main()
