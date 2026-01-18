#!/usr/bin/env python3
"""
FAST TRAINING - 10-15 minute version
Uses GPU, pre-cropped Kaggle data, minimal epochs
"""

import os
import shutil
import zipfile
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import EarlyStopping

# FAST CONFIG
TARGET_SIZE = (299, 299)
IMAGES_PER_CLASS = 500  # Small subset for speed
BATCH_SIZE = 32  # Larger batch for GPU
EPOCHS = 5  # Quick training
WORKSPACE = "fast_workspace"
MODEL_FILE = "deepfake_net.tflite"

print("=" * 60)
print("  FAST TRAINING MODE (10-15 minutes)")
print("  Using RTX 3060 GPU + Kaggle subset")
print("=" * 60)

# GPU check
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    print(f"\n✅ GPU: {gpus[0].name}")
    tf.config.experimental.set_memory_growth(gpus[0], True)
else:
    print("\n⚠️  No GPU - will be slow")

# Setup workspace
print(f"\n📁 Setting up workspace...")
if os.path.exists(WORKSPACE):
    shutil.rmtree(WORKSPACE)
os.makedirs(f"{WORKSPACE}/real")
os.makedirs(f"{WORKSPACE}/fake")

# Extract Kaggle data
print(f"\n📦 Extracting Kaggle subset ({IMAGES_PER_CLASS} per class)...")
with zipfile.ZipFile("archive.zip", 'r') as z:
    z.extractall("temp_extract")

# Find folders
real_src = "temp_extract/real_and_fake_face/training_real"
fake_src = "temp_extract/real_and_fake_face/training_fake"

# Copy subset (Kaggle images already have faces)
def copy_subset(src, dst, limit):
    files = [f for f in os.listdir(src) if f.endswith('.jpg')][:limit]
    for f in files:
        shutil.copy(f"{src}/{f}", f"{dst}/{f}")
    return len(files)

real_count = copy_subset(real_src, f"{WORKSPACE}/real", IMAGES_PER_CLASS)
fake_count = copy_subset(fake_src, f"{WORKSPACE}/fake", IMAGES_PER_CLASS)
shutil.rmtree("temp_extract")

print(f"   ✅ {real_count} Real, {fake_count} Fake")

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
print(f"\n🏗️  Building Xception model...")
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
print("=" * 60)

history = model.fit(
    train_gen,
    epochs=EPOCHS,
    validation_data=val_gen,
    callbacks=[EarlyStopping(monitor='val_loss', patience=2, restore_best_weights=True)],
    verbose=1
)

print("=" * 60)
print(f"✅ Training complete!")
print(f"   Best accuracy: {max(history.history['val_accuracy']):.4f}")

# Export
print(f"\n📦 Exporting to {MODEL_FILE}...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

with open(MODEL_FILE, 'wb') as f:
    f.write(tflite_model)

size_mb = os.path.getsize(MODEL_FILE) / 1024 / 1024
print(f"   ✅ {MODEL_FILE} ({size_mb:.2f} MB)")

print("\n" + "=" * 60)
print("  🎉 FAST TRAINING COMPLETE!")
print("=" * 60)
print(f"\n📋 Next steps:")
print(f"   cp {MODEL_FILE} ../app/src/main/assets/")
print(f"   cd .. && ./gradlew clean assembleDebug")
print(f"   adb install -r app/build/outputs/apk/debug/app-debug.apk")
