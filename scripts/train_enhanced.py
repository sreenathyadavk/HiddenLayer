#!/usr/bin/env python3
"""
ENHANCED FAST TRAINING
Kaggle + Modern AI Faces (200 images from TPDNE)
Per user specifications: 2 datasets, small subsets
"""

import os
import shutil
import zipfile
import time
import requests
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import EarlyStopping

# CONFIG (LOCKED - DO NOT CHANGE)
TARGET_SIZE = (299, 299)
KAGGLE_REAL = 500
KAGGLE_FAKE = 300  # Less to make room for AI faces
AI_FACES = 200      # MANDATORY per user spec
BATCH_SIZE = 32
EPOCHS = 5
WORKSPACE = "enhanced_workspace"
MODEL_FILE = "deepfake_net.tflite"

print("=" * 70)
print("  ENHANCED TRAINING: Kaggle + Modern AI Faces")
print("  Datasets: 2 (as specified)")
print("=" * 70)

# GPU check
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    print(f"\n✅ GPU: {gpus[0].name}")
    tf.config.experimental.set_memory_growth(gpus[0], True)
else:
    print("\n⚠️  No GPU - using CPU")

# Setup
print(f"\n📁 Setting up workspace...")
if os.path.exists(WORKSPACE):
    shutil.rmtree(WORKSPACE)
os.makedirs(f"{WORKSPACE}/real")
os.makedirs(f"{WORKSPACE}/fake")

# Dataset 1: Kaggle
print(f"\n📦 [1/2] Kaggle Real vs Fake ({KAGGLE_REAL} real, {KAGGLE_FAKE} fake)")
with zipfile.ZipFile("archive.zip", 'r') as z:
    z.extractall("temp_extract")

real_src = "temp_extract/real_and_fake_face/training_real"
fake_src = "temp_extract/real_and_fake_face/training_fake"

def copy_subset(src, dst, limit):
    files = [f for f in os.listdir(src) if f.endswith('.jpg')][:limit]
    for f in files:
        shutil.copy(f"{src}/{f}", f"{dst}/{f}")
    return len(files)

real_count = copy_subset(real_src, f"{WORKSPACE}/real", KAGGLE_REAL)
fake_kaggle = copy_subset(fake_src, f"{WORKSPACE}/fake", KAGGLE_FAKE)
shutil.rmtree("temp_extract")
print(f"   ✅ {real_count} real, {fake_kaggle} fake from Kaggle")

# Dataset 2: Modern AI Faces (MANDATORY)
print(f"\n🤖 [2/2] Modern AI Faces (MANDATORY - {AI_FACES} images)")
print("   Source: ThisPersonDoesNotExist.com (StyleGAN2)")

headers = {'User-Agent': 'Mozilla/5.0'}
ai_count = 0
for i in range(AI_FACES):
    try:
        r = requests.get("https://thispersondoesnotexist.com/", headers=headers, timeout=10)
        if r.status_code == 200:
            with open(f"{WORKSPACE}/fake/ai_{i:03d}.jpg", 'wb') as f:
                f.write(r.content)
            ai_count += 1
            if (i + 1) % 50 == 0:
                print(f"   Progress: {i+1}/{AI_FACES} ({(i+1)/AI_FACES*100:.0f}%)")
        time.sleep(0.8)  # Rate limit
    except:
        continue

print(f"   ✅ {ai_count} AI-generated faces downloaded")

total_real = real_count
total_fake = fake_kaggle + ai_count
print(f"\n📊 Final Dataset: {total_real} real, {total_fake} fake")

# Data generators
print(f"\n🔄 Creating data generators...")
datagen = ImageDataGenerator(
    preprocessing_function=tf.keras.applications.xception.preprocess_input,
    horizontal_flip=True,
    validation_split=0.2
)

train_gen = datagen.flow_from_directory(
    WORKSPACE,
    target_size=TARGET_SIZE,
    batch_size=BATCH_SIZE,
    class_mode='categorical',
    classes=['real', 'fake'],
    subset='training'
)

val_gen = datagen.flow_from_directory(
    WORKSPACE,
    target_size=TARGET_SIZE,
    batch_size=BATCH_SIZE,
    class_mode='categorical',
    classes=['real', 'fake'],
    subset='validation'
)

# Build model
print(f"\n🏗️  Building Xception (LOCKED architecture)...")
base_model = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
base_model.trainable = False

x = base_model.output
x = GlobalAveragePooling2D()(x)
x = Dense(1024, activation='relu')(x)
x = Dropout(0.5)(x)
predictions = Dense(2, activation='softmax')(x)

model = Model(inputs=base_model.input, outputs=predictions)
model.compile(
    optimizer=Adam(learning_rate=1e-4),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

print(f"   Total params: {model.count_params():,}")

# Train
print(f"\n🏋️  Training ({EPOCHS} epochs)...")
print("=" * 70)

history = model.fit(
    train_gen,
    epochs=EPOCHS,
    validation_data=val_gen,
    callbacks=[EarlyStopping(monitor='val_loss', patience=2, restore_best_weights=True)],
    verbose=1
)

print("=" * 70)
print(f"✅ Training complete!")
print(f"   Final val_accuracy: {history.history['val_accuracy'][-1]:.4f}")

# Export (LOCKED - DO NOT CHANGE)
print(f"\n📦 Exporting to TFLite (FP16 quantization)...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

with open(MODEL_FILE, 'wb') as f:
    f.write(tflite_model)

size_mb = os.path.getsize(MODEL_FILE) / 1024 / 1024
print(f"   ✅ {MODEL_FILE} ({size_mb:.2f} MB)")

print("\n" + "=" * 70)
print("  🎉 TRAINING COMPLETE - 2 DATASETS")
print("=" * 70)
print(f"\n✅ Datasets used:")
print(f"   1. Kaggle Real vs Fake: {real_count} real, {fake_kaggle} fake")
print(f"   2. Modern AI Faces (MANDATORY): {ai_count} fake")
print(f"\n📋 Next steps:")
print(f"   cp {MODEL_FILE} ../app/src/main/assets/")
print(f"   cd .. && ./gradlew clean assembleDebug")
print(f"   adb install -r app/build/outputs/apk/debug/app-debug.apk")
