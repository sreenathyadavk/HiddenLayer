"""
🚀 FIXED: Production Deepfake Detector Training
Handles missing images and validates data before training

SETUP:
1. Runtime → Change runtime type → GPU (T4)
2. Run all cells (no uploads needed - auto-configured)
"""

# ============================================================================
# SETUP & VERIFICATION
# ============================================================================

import tensorflow as tf
import os
import shutil
import zipfile
import cv2
import numpy as np
import requests
import time
import json
from pathlib import Path
from tensorflow import keras

print('=' * 70)
print('GPU VERIFICATION')
print('=' * 70)
gpus = tf.config.list_physical_devices('GPU')
print(f'GPUs: {gpus}')
if not gpus:
    raise SystemExit('❌ Enable GPU: Runtime → Change runtime type → GPU')
print('✅ GPU Ready!\n')

# Install dependencies
print('Installing dependencies...')
!pip install -q kaggle opencv-python pillow requests
print('✅ Dependencies installed\n')

# Setup Kaggle (hardcoded credentials)
print('Setting up Kaggle API...')
!mkdir -p ~/.kaggle
kaggle_creds = {"username": "snapdragoon77", "key": "KGAT_a0e47466be9a5de9ac5b0e203f5e42c0"}
with open('/root/.kaggle/kaggle.json', 'w') as f:
    json.dump(kaggle_creds, f)
!chmod 600 ~/.kaggle/kaggle.json
print('✅ Kaggle API configured\n')

# ============================================================================
# CONFIGURATION
# ============================================================================

# Training config
TARGET_SIZE = (299, 299)
BATCH_SIZE = 32
FROZEN_EPOCHS = 10
FINETUNE_EPOCHS = 15

# Paths
WORKSPACE = '/content/training_data'
RAW_DIR = f'{WORKSPACE}/raw'
PROCESSED_DIR = f'{WORKSPACE}/processed'

# Clean workspace
if os.path.exists(WORKSPACE):
    shutil.rmtree(WORKSPACE)

os.makedirs(f'{RAW_DIR}/real', exist_ok=True)
os.makedirs(f'{RAW_DIR}/fake', exist_ok=True)
os.makedirs(f'{PROCESSED_DIR}/real', exist_ok=True)
os.makedirs(f'{PROCESSED_DIR}/fake', exist_ok=True)

print('✅ Workspace ready\n')

# ============================================================================
# DATA COLLECTION
# ============================================================================

print('=' * 70)
print('DOWNLOADING DATASETS')
print('=' * 70)

# Download Kaggle dataset
print('\n📦 Downloading Kaggle Real/Fake Faces...')
!kaggle datasets download -d xhlulu/140k-real-and-fake-faces
!unzip -q 140k-real-and-fake-faces.zip -d /content/faces/

# Copy initial batch
print('Copying dataset...')
!cp /content/faces/real_vs_fake/real/*.jpg {RAW_DIR}/real/ 2>/dev/null || true
!cp /content/faces/real_vs_fake/fake/*.jpg {RAW_DIR}/fake/ 2>/dev/null || true

# Download TPDNE GAN faces
print('\n🤖 Downloading GAN faces...')
url = 'https://thispersondoesnotexist.com/'
headers = {'User-Agent': 'Mozilla/5.0'}
for i in range(1500):
    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code == 200:
            with open(f'{RAW_DIR}/fake/tpdne_{i:04d}.jpg', 'wb') as f:
                f.write(r.content)
            if (i + 1) % 100 == 0:
                print(f'   {i+1}/1500')
        time.sleep(0.3)
    except:
        continue

# Count
real_count = len([f for f in os.listdir(f'{RAW_DIR}/real') if f.endswith(('.jpg', '.jpeg', '.png'))])
fake_count = len([f for f in os.listdir(f'{RAW_DIR}/fake') if f.endswith(('.jpg', '.jpeg', '.png'))])
print(f'\n✅ Downloaded: {real_count} real, {fake_count} fake\n')

# ============================================================================
# PREPROCESSING WITH VALIDATION
# ============================================================================

print('=' * 70)
print('PREPROCESSING')
print('=' * 70)

cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

def validate_image(img_path):
    """Check if image is valid and readable"""
    try:
        img = cv2.imread(img_path)
        if img is None or img.size == 0:
            return None
        return img
    except:
        return None

for label in ['real', 'fake']:
    src = f'{RAW_DIR}/{label}'
    dst = f'{PROCESSED_DIR}/{label}'
    
    # Get all valid images
    all_files = [f for f in os.listdir(src) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    files_to_process = all_files[:3000]  # Limit
    
    print(f'\n{label.upper()}: Processing {len(files_to_process)} images...')
    
    saved = 0
    skipped = 0
    
    for i, fname in enumerate(files_to_process):
        img_path = os.path.join(src, fname)
        
        # Validate image first
        img = validate_image(img_path)
        if img is None:
            skipped += 1
            continue
        
        try:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = cascade.detectMultiScale(gray, 1.1, 4, minSize=(80, 80))
            
            if len(faces) > 0:
                # Get largest face
                x, y, w, h = max(faces, key=lambda r: r[2]*r[3])
                
                # Add margin
                margin = int(w * 0.2)
                x = max(0, x - margin)
                y = max(0, y - margin)
                w = min(img.shape[1] - x, w + 2 * margin)
                h = min(img.shape[0] - y, h + 2 * margin)
                
                # Crop and resize
                face = img[y:y+h, x:x+w]
                face_resized = cv2.resize(face, TARGET_SIZE)
                
                # Save
                output_path = os.path.join(dst, fname)
                cv2.imwrite(output_path, face_resized)
                saved += 1
            else:
                skipped += 1
                
        except Exception as e:
            skipped += 1
            continue
        
        if (i + 1) % 500 == 0:
            print(f'   {i+1}/{len(files_to_process)} - {saved} saved, {skipped} skipped')
    
    print(f'✅ {label}: {saved} faces extracted, {skipped} skipped')

# Balance dataset
print('\n⚖️ BALANCING DATASET...')
real_files = [f for f in os.listdir(f'{PROCESSED_DIR}/real') if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
fake_files = [f for f in os.listdir(f'{PROCESSED_DIR}/fake') if f.lower().endswith(('.jpg', '.jpeg', '.png'))]

target = min(len(real_files), len(fake_files), 3000)

# Remove excess
for label, files in [('real', real_files), ('fake', fake_files)]:
    if len(files) > target:
        for f in files[target:]:
            os.remove(f'{PROCESSED_DIR}/{label}/{f}')

print(f'✅ Balanced: {target} per class, {target*2} total\n')

# VALIDATION: Check directory structure
print('🔍 VALIDATING DATA STRUCTURE...')
for label in ['real', 'fake']:
    dir_path = f'{PROCESSED_DIR}/{label}'
    count = len([f for f in os.listdir(dir_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))])
    print(f'   {label}: {count} images in {dir_path}')
    
    # Verify first image is readable
    files = os.listdir(dir_path)
    if files:
        test_img = cv2.imread(os.path.join(dir_path, files[0]))
        if test_img is not None:
            print(f'   ✅ {label} images are readable')
        else:
            print(f'   ❌ ERROR: {label} images cannot be read!')
print()

# ============================================================================
# TRAINING
# ============================================================================

print('=' * 70)
print('TRAINING')
print('=' * 70)

try:
    # Load datasets
    print('\nLoading training data...')
    train_ds = keras.utils.image_dataset_from_directory(
        PROCESSED_DIR,
        validation_split=0.2,
        subset='training',
        seed=123,
        image_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical',
        interpolation='bilinear'
    )
    
    print('Loading validation data...')
    val_ds = keras.utils.image_dataset_from_directory(
        PROCESSED_DIR,
        validation_split=0.2,
        subset='validation',
        seed=123,
        image_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical',
        interpolation='bilinear'
    )
    
    print('✅ Data loaded successfully\n')
    
except Exception as e:
    print(f'❌ DATA LOADING ERROR: {e}')
    print('\nDEBUG INFO:')
    print(f'PROCESSED_DIR exists: {os.path.exists(PROCESSED_DIR)}')
    print(f'Directory contents:')
    for root, dirs, files in os.walk(PROCESSED_DIR):
        print(f'  {root}: {len(files)} files')
    raise

# Preprocess
def preprocess(images, labels):
    return keras.applications.xception.preprocess_input(images), labels

train_ds = train_ds.map(preprocess).prefetch(tf.data.AUTOTUNE)
val_ds = val_ds.map(preprocess).prefetch(tf.data.AUTOTUNE)

# Build model
from keras import mixed_precision
from keras.applications import Xception
from keras.layers import Dense, GlobalAveragePooling2D, Dropout
from keras.models import Model
from keras.optimizers import Adam
from keras.callbacks import EarlyStopping, ReduceLROnPlateau

mixed_precision.set_global_policy('mixed_float16')

print('Building model...')
with tf.device('/GPU:0'):
    base = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base.trainable = False
    
    x = base.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu', dtype='float32')(x)
    x = Dropout(0.5)(x)
    out = Dense(2, activation='softmax', dtype='float32', name='predictions')(x)
    
    model = Model(base.input, out)

print(f'✅ Model built: {model.count_params():,} params\n')

# Phase 1: Frozen base
print(f'PHASE 1: Training head ({FROZEN_EPOCHS} epochs)')
model.compile(
    optimizer=Adam(1e-4),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

h1 = model.fit(
    train_ds,
    epochs=FROZEN_EPOCHS,
    validation_data=val_ds,
    callbacks=[
        EarlyStopping('val_loss', patience=3, restore_best_weights=True),
        ReduceLROnPlateau('val_loss', factor=0.5, patience=2)
    ]
)

phase1_acc = max(h1.history['val_accuracy'])
print(f'\n✅ Phase 1: {phase1_acc:.1%}\n')

# Phase 2: Fine-tuning
print(f'PHASE 2: Fine-tuning ({FINETUNE_EPOCHS} epochs)')
base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False

model.compile(
    optimizer=Adam(1e-5),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

h2 = model.fit(
    train_ds,
    epochs=FINETUNE_EPOCHS,
    validation_data=val_ds,
    callbacks=[
        EarlyStopping('val_loss', patience=2, restore_best_weights=True),
        ReduceLROnPlateau('val_loss', factor=0.3, patience=2)
    ]
)

final_acc = max(h2.history['val_accuracy'])
print(f'\n✅ Phase 2: {final_acc:.1%}\n')

# ============================================================================
# EXPORT
# ============================================================================

print('=' * 70)
print('EXPORTING TFLITE')
print('=' * 70)

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]

tflite_model = converter.convert()

with open('deepfake_net.tflite', 'wb') as f:
    f.write(tflite_model)

size_mb = len(tflite_model) / 1024 / 1024
print(f'\n✅ Exported: deepfake_net.tflite ({size_mb:.1f} MB)\n')

# Test
interpreter = tf.lite.Interpreter(model_path='deepfake_net.tflite')
interpreter.allocate_tensors()
test_img = np.random.rand(1, 299, 299, 3).astype(np.float32)
test_img = (test_img * 255 - 127.5) / 127.5
interpreter.set_tensor(interpreter.get_input_details()[0]['index'], test_img)
interpreter.invoke()
output = interpreter.get_tensor(interpreter.get_output_details()[0]['index'])
print(f'✅ TFLite test passed: {output[0]}\n')

# ============================================================================
# SUMMARY
# ============================================================================

print('=' * 70)
print('TRAINING COMPLETE!')
print('=' * 70)
print(f'\n📊 Dataset: {target*2} images ({target} real, {target} fake)')
print(f'🎯 Final Accuracy: {final_acc:.1%}')
print(f'📦 Model: deepfake_net.tflite ({size_mb:.1f} MB)')

if final_acc >= 0.90:
    print('\n🎉 SUCCESS! Accuracy >= 90%')
elif final_acc >= 0.85:
    print('\n✅ Good! Accuracy >= 85%')
else:
    print('\n⚠️ Accuracy < 85%. Consider retraining with more data.')

print('\n📥 Download deepfake_net.tflite from Files panel (left)')
print('=' * 70)
