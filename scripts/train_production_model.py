#!/usr/bin/env python3
"""
Production Model Training Script for HiddenLayer Deepfake Detection

Uses approved datasets:
- Celeb-DF v2
- FaceForensics++ (c23 subset)
- Kaggle Real vs Fake
- Modern AI Faces (StyleGAN, Stable Diffusion, TPDNE)

Target: 5,000 REAL + 5,000 FAKE balanced dataset
Architecture: Xception (frozen base + fine-tuned layers)
Output: deepfake_net.tflite (FP16 quantized)
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
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint
from pathlib import Path

# ============================================================================
# CONFIGURATION (LOCKED - DO NOT MODIFY)
# ============================================================================
TARGET_SIZE = (299, 299)  # Xception input
TARGET_REAL = 5000
TARGET_FAKE = 5000
BATCH_SIZE = 16
INITIAL_EPOCHS = 10  # Frozen base training
FINETUNE_EPOCHS = 5  # Unfrozen layers training
UNFREEZE_LAYERS = 20  # Number of layers to unfreeze for fine-tuning

# Directories
WORKSPACE = "production_workspace"
RAW_DIR = f"{WORKSPACE}/raw"
PROCESSED_DIR = f"{WORKSPACE}/processed"
MODEL_FILE = "deepfake_net.tflite"

# Dataset sources
KAGGLE_ARCHIVE = "archive.zip"
TPDNE_URL = "https://thispersondoesnotexist.com/"
CELEBDF_URL = "https://github.com/yuezunli/celeb-deepfakeforensics"
FF_URL = "https://github.com/ondyari/FaceForensics"

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
        print("⚠️  No GPU detected. Training will be slow on CPU.")
        return False

# ============================================================================
# DATASET ACQUISITION
# ============================================================================

def extract_kaggle_dataset(target_real=2000, target_fake=1500):
    """Extract Kaggle Real vs Fake dataset from archive.zip."""
    print(f"\n📦 [1/4] Extracting Kaggle Dataset (Target: {target_real} Real, {target_fake} Fake)")
    
    if not os.path.exists(KAGGLE_ARCHIVE):
        print(f"   ❌ {KAGGLE_ARCHIVE} not found. Skipping Kaggle source.")
        print(f"   Expected location: {os.path.abspath(KAGGLE_ARCHIVE)}")
        return 0, 0
    
    print(f"   Found: {KAGGLE_ARCHIVE} ({os.path.getsize(KAGGLE_ARCHIVE) / 1024 / 1024:.1f} MB)")
    
    # Extract to temp
    temp_extract = f"{WORKSPACE}/temp_kaggle"
    print("   Extracting archive...")
    with zipfile.ZipFile(KAGGLE_ARCHIVE, 'r') as z:
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
        print("   ❌ Could not locate real/fake folders in archive")
        return 0, 0
    
    print(f"   Real source: {real_src}")
    print(f"   Fake source: {fake_src}")
    
    # Copy files
    def copy_images(src, dst, limit):
        files = [f for f in os.listdir(src) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        count = 0
        for f in files[:limit]:
            shutil.copy(os.path.join(src, f), os.path.join(dst, f))
            count += 1
        return count
    
    real_count = copy_images(real_src, f"{RAW_DIR}/real", target_real)
    fake_count = copy_images(fake_src, f"{RAW_DIR}/fake", target_fake)
    
    print(f"   ✅ Extracted {real_count} Real, {fake_count} Fake from Kaggle")
    
    # Cleanup temp
    shutil.rmtree(temp_extract)
    return real_count, fake_count

def download_modern_ai_faces(target=1000):
    """Download modern AI-generated faces from ThisPersonDoesNotExist."""
    print(f"\n🤖 [2/4] Downloading Modern AI Faces (Target: {target})")
    print("   Source: ThisPersonDoesNotExist.com (StyleGAN2)")
    
    headers = {'User-Agent': 'Mozilla/5.0'}
    success = 0
    
    for i in range(target):
        try:
            r = requests.get(TPDNE_URL, headers=headers, timeout=15)
            if r.status_code == 200:
                filename = f"{RAW_DIR}/fake/tpdne_{i:04d}.jpg"
                with open(filename, 'wb') as f:
                    f.write(r.content)
                success += 1
                
                if success % 50 == 0:
                    print(f"   Progress: {success}/{target} ({success/target*100:.1f}%)")
            
            time.sleep(0.8)  # Rate limiting
            
        except Exception as e:
            if i % 100 == 0:
                print(f"   Warning: Error at image {i}: {e}")
            continue
    
    print(f"   ✅ Downloaded {success} AI-generated faces")
    return success

def download_celebdf_v2():
    """Download Celeb-DF v2 dataset (requires manual download)."""
    print(f"\n🎬 [3/4] Celeb-DF v2 Dataset")
    print(f"   Source: {CELEBDF_URL}")
    print("   ⚠️  Celeb-DF requires manual download from GitHub")
    print("   Instructions:")
    print("   1. Visit: https://github.com/yuezunli/celeb-deepfakeforensics")
    print("   2. Download Celeb-real and Celeb-synthesis")
    print("   3. Place videos in: celeb_df/real/ and celeb_df/fake/")
    print("   4. This script will auto-detect and extract frames")
    
    # Check if user has downloaded it
    celebdf_real = "celeb_df/real"
    celebdf_fake = "celeb_df/fake"
    
    if os.path.exists(celebdf_real) and os.path.exists(celebdf_fake):
        print("   ✅ Celeb-DF videos found!")
        return extract_frames_from_videos(celebdf_real, celebdf_fake, target_per_video=10)
    else:
        print("   ⏭️  Skipping Celeb-DF (not found, continuing with other datasets)")
        return 0, 0

def download_faceforensics_pp():
    """Download FaceForensics++ c23 subset (requires manual download)."""
    print(f"\n🎥 [4/4] FaceForensics++ (c23 subset)")
    print(f"   Source: {FF_URL}")
    print("   ⚠️  FaceForensics++ requires manual download")
    print("   Instructions:")
    print("   1. Visit: https://github.com/ondyari/FaceForensics")
    print("   2. Download c23 (light compression) subset")
    print("   3. Place in: faceforensics/original/ and faceforensics/manipulated/")
    print("   4. This script will auto-detect and extract frames")
    
    ff_real = "faceforensics/original"
    ff_fake = "faceforensics/manipulated"
    
    if os.path.exists(ff_real) and os.path.exists(ff_fake):
        print("   ✅ FaceForensics++ videos found!")
        return extract_frames_from_videos(ff_real, ff_fake, target_per_video=10)
    else:
        print("   ⏭️  Skipping FaceForensics++ (not found, continuing with other datasets)")
        return 0, 0

def extract_frames_from_videos(real_dir, fake_dir, target_per_video=10):
    """Extract frames from video files."""
    print("   Extracting frames from videos...")
    
    def extract_from_dir(video_dir, output_dir, label):
        video_files = [f for f in os.listdir(video_dir) if f.lower().endswith(('.mp4', '.avi', '.mov'))]
        total_frames = 0
        
        for i, video_file in enumerate(video_files[:50]):  # Limit to 50 videos per class
            video_path = os.path.join(video_dir, video_file)
            cap = cv2.VideoCapture(video_path)
            
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            interval = max(1, frame_count // target_per_video)
            
            extracted = 0
            for frame_num in range(0, frame_count, interval):
                if extracted >= target_per_video:
                    break
                
                cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
                ret, frame = cap.read()
                
                if ret:
                    output_path = f"{output_dir}/{label}_{i:04d}_f{frame_num:04d}.jpg"
                    cv2.imwrite(output_path, frame)
                    extracted += 1
                    total_frames += 1
            
            cap.release()
            
            if (i + 1) % 10 == 0:
                print(f"      Processed {i+1}/{len(video_files[:50])} videos ({total_frames} frames)")
        
        return total_frames
    
    real_frames = extract_from_dir(real_dir, f"{RAW_DIR}/real", "real_video")
    fake_frames = extract_from_dir(fake_dir, f"{RAW_DIR}/fake", "fake_video")
    
    print(f"   ✅ Extracted {real_frames} real frames, {fake_frames} fake frames")
    return real_frames, fake_frames

# ============================================================================
# PREPROCESSING
# ============================================================================

def crop_faces():
    """Detect and crop faces from all raw images."""
    print("\n✂️  Face Detection & Cropping")
    print("   Using OpenCV Haar Cascade (frontal face detector)")
    
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    
    for label in ['real', 'fake']:
        src_dir = f"{RAW_DIR}/{label}"
        dst_dir = f"{PROCESSED_DIR}/{label}"
        
        files = [f for f in os.listdir(src_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        print(f"\n   Processing {label.upper()}: {len(files)} images")
        
        saved = 0
        for i, fname in enumerate(files):
            img_path = os.path.join(src_dir, fname)
            img = cv2.imread(img_path)
            
            if img is None:
                continue
            
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(100, 100))
            
            if len(faces) > 0:
                # Get largest face
                largest = max(faces, key=lambda r: r[2] * r[3])
                x, y, w, h = largest
                
                # Add 20% margin
                margin = int(w * 0.2)
                x = max(0, x - margin)
                y = max(0, y - margin)
                w = min(img.shape[1] - x, w + 2*margin)
                h = min(img.shape[0] - y, h + 2*margin)
                
                # Crop and resize
                face = img[y:y+h, x:x+w]
                face_resized = cv2.resize(face, TARGET_SIZE)
                
                # Save
                output_path = os.path.join(dst_dir, fname)
                cv2.imwrite(output_path, face_resized)
                saved += 1
            
            if (i + 1) % 500 == 0:
                print(f"      Progress: {i+1}/{len(files)} ({saved} faces detected)")
        
        print(f"   ✅ {label.upper()}: {saved}/{len(files)} images with faces ({saved/len(files)*100:.1f}%)")

def balance_dataset():
    """Balance real/fake classes to target counts."""
    print("\n⚖️  Balancing Dataset")
    
    real_files = os.listdir(f"{PROCESSED_DIR}/real")
    fake_files = os.listdir(f"{PROCESSED_DIR}/fake")
    
    print(f"   Current: {len(real_files)} Real, {len(fake_files)} Fake")
    print(f"   Target:  {TARGET_REAL} Real, {TARGET_FAKE} Fake")
    
    # Remove excess files if over target
    if len(real_files) > TARGET_REAL:
        print(f"   Trimming real to {TARGET_REAL}...")
        for f in real_files[TARGET_REAL:]:
            os.remove(f"{PROCESSED_DIR}/real/{f}")
    
    if len(fake_files) > TARGET_FAKE:
        print(f"   Trimming fake to {TARGET_FAKE}...")
        for f in fake_files[TARGET_FAKE:]:
            os.remove(f"{PROCESSED_DIR}/fake/{f}")
    
    final_real = len(os.listdir(f"{PROCESSED_DIR}/real"))
    final_fake = len(os.listdir(f"{PROCESSED_DIR}/fake"))
    
    print(f"   ✅ Final: {final_real} Real, {final_fake} Fake")
    return final_real, final_fake

# ============================================================================
# MODEL TRAINING
# ============================================================================

def build_model():
    """Build Xception model for binary classification."""
    print("\n🏗️  Building Xception Model")
    print("   Architecture: Xception (ImageNet weights)")
    print("   Modification: Binary classifier head (Real/Fake)")
    
    base_model = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base_model.trainable = False  # Freeze for initial training
    
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu')(x)
    x = Dropout(0.5)(x)
    predictions = Dense(2, activation='softmax')(x)  # [Real, Fake]
    
    model = Model(inputs=base_model.input, outputs=predictions)
    
    print(f"   Total params: {model.count_params():,}")
    print(f"   Trainable params: {sum([tf.size(v).numpy() for v in model.trainable_variables]):,}")
    
    return model, base_model

def train_frozen(model, train_gen, val_gen):
    """Phase 1: Train with frozen base."""
    print(f"\n🏋️  Phase 1: Training (Frozen Base) - {INITIAL_EPOCHS} epochs")
    
    model.compile(
        optimizer=Adam(learning_rate=1e-4),
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks = [
        EarlyStopping(monitor='val_loss', patience=3, restore_best_weights=True, verbose=1),
        ModelCheckpoint('best_model_frozen.h5', monitor='val_accuracy', save_best_only=True, verbose=1)
    ]
    
    history = model.fit(
        train_gen,
        epochs=INITIAL_EPOCHS,
        validation_data=val_gen,
        callbacks=callbacks,
        verbose=1
    )
    
    print(f"   ✅ Phase 1 Complete")
    print(f"      Best Val Accuracy: {max(history.history['val_accuracy']):.4f}")
    return history

def train_finetuned(model, base_model, train_gen, val_gen):
    """Phase 2: Fine-tune last N layers."""
    print(f"\n🔥 Phase 2: Fine-Tuning (Last {UNFREEZE_LAYERS} layers) - {FINETUNE_EPOCHS} epochs")
    
    # Unfreeze last N layers
    base_model.trainable = True
    for layer in base_model.layers[:-UNFREEZE_LAYERS]:
        layer.trainable = False
    
    trainable_count = sum([tf.size(v).numpy() for v in model.trainable_variables])
    print(f"   Trainable params after unfreezing: {trainable_count:,}")
    
    # Recompile with lower learning rate
    model.compile(
        optimizer=Adam(learning_rate=1e-5),  # Lower LR for fine-tuning
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    
    callbacks = [
        EarlyStopping(monitor='val_loss', patience=2, restore_best_weights=True, verbose=1),
        ModelCheckpoint('best_model_finetuned.h5', monitor='val_accuracy', save_best_only=True, verbose=1)
    ]
    
    history = model.fit(
        train_gen,
        epochs=FINETUNE_EPOCHS,
        validation_data=val_gen,
        callbacks=callbacks,
        verbose=1
    )
    
    print(f"   ✅ Phase 2 Complete")
    print(f"      Best Val Accuracy: {max(history.history['val_accuracy']):.4f}")
    return history

def export_tflite(model):
    """Export model to TFLite with FP16 quantization."""
    print(f"\n📦 Exporting to {MODEL_FILE}")
    print("   Quantization: FP16 (16-bit floating point)")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    with open(MODEL_FILE, 'wb') as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(MODEL_FILE) / 1024 / 1024
    print(f"   ✅ Model saved: {os.path.abspath(MODEL_FILE)} ({size_mb:.2f} MB)")

# ============================================================================
# MAIN PIPELINE
# ============================================================================

def main():
    print("=" * 70)
    print("  HiddenLayer Production Model Training")
    print("  Xception + Multi-Dataset Deepfake Detection")
    print("=" * 70)
    
    # Environment check
    print(f"\n🔧 Environment: TensorFlow {tf.__version__}")
    check_gpu()
    
    # Setup
    setup_dirs()
    
    # Data Acquisition
    print("\n" + "=" * 70)
    print("  PHASE 1: DATASET ACQUISITION")
    print("=" * 70)
    
    kaggle_real, kaggle_fake = extract_kaggle_dataset(target_real=2500, target_fake=1500)
    ai_faces = download_modern_ai_faces(target=1500)
    celebdf_real, celebdf_fake = download_celebdf_v2()
    ff_real, ff_fake = download_faceforensics_pp()
    
    total_raw_real = kaggle_real + celebdf_real + ff_real
    total_raw_fake = kaggle_fake + ai_faces + celebdf_fake + ff_fake
    
    print(f"\n📊 Raw Dataset Summary:")
    print(f"   Real: {total_raw_real}")
    print(f"   Fake: {total_raw_fake}")
    
    # Preprocessing
    print("\n" + "=" * 70)
    print("  PHASE 2: PREPROCESSING")
    print("=" * 70)
    
    crop_faces()
    final_real, final_fake = balance_dataset()
    
    if final_real < 1000 or final_fake < 1000:
        print("\n❌ ERROR: Insufficient data after preprocessing")
        print(f"   Need at least 1000 per class, have {final_real} real, {final_fake} fake")
        sys.exit(1)
    
    # Training
    print("\n" + "=" * 70)
    print("  PHASE 3: MODEL TRAINING")
    print("=" * 70)
    
    # Data generators
    train_datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.xception.preprocess_input,
        horizontal_flip=True,
        rotation_range=15,
        zoom_range=0.15,
        validation_split=0.2
    )
    
    train_gen = train_datagen.flow_from_directory(
        PROCESSED_DIR,
        target_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        classes=['real', 'fake'],
        subset='training'
    )
    
    val_gen = train_datagen.flow_from_directory(
        PROCESSED_DIR,
        target_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        class_mode='categorical',
        classes=['real', 'fake'],
        subset='validation'
    )
    
    # Build and train
    model, base_model = build_model()
    train_frozen(model, train_gen, val_gen)
    train_finetuned(model, base_model, train_gen, val_gen)
    
    # Export
    print("\n" + "=" * 70)
    print("  PHASE 4: MODEL EXPORT")
    print("=" * 70)
    
    export_tflite(model)
    
    # Success
    print("\n" + "=" * 70)
    print("  ✅ TRAINING COMPLETE")
    print("=" * 70)
    print(f"\n📋 Next Steps:")
    print(f"   1. Copy model to app:")
    print(f"      cp {MODEL_FILE} ../app/src/main/assets/")
    print(f"   2. Rebuild APK:")
    print(f"      cd .. && ./gradlew clean assembleDebug")
    print(f"   3. Install and test:")
    print(f"      adb install -r app/build/outputs/apk/debug/app-debug.apk")
    print(f"   4. Monitor predictions:")
    print(f"      adb logcat | grep DeepfakeDetector")
    print()

if __name__ == "__main__":
    main()
