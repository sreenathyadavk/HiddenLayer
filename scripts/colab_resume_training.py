"""
🚀 RESUME TRAINING (Dataset Already Downloaded)
Use this if data is already preprocessed
"""

import tensorflow as tf
import os
import cv2
import numpy as np
from tensorflow import keras

print('=' * 70)
print('GPU CHECK')
print('=' * 70)
gpus = tf.config.list_physical_devices('GPU')
if not gpus:
    raise SystemExit('❌ Enable GPU!')
print(f'✅ GPU Ready: {gpus}\n')

# ============================================================================
# PATHS - CHECK EXISTING DATA
# ============================================================================

PROCESSED_DIR = '/content/training_data/processed'

print('=' * 70)
print('CHECKING EXISTING DATA')
print('=' * 70)

# Check if processed data exists
if not os.path.exists(PROCESSED_DIR):
    print(f'❌ ERROR: {PROCESSED_DIR} not found!')
    print('\nTrying alternative paths...')
    
    # Check common alternatives
    alternatives = [
        '/content/training_data/processed',
        '/content/processed',
        '/content/faces',
    ]
    
    for path in alternatives:
        if os.path.exists(path):
            print(f'✅ Found data at: {path}')
            PROCESSED_DIR = path
            break
    else:
        raise SystemExit('❌ No processed data found. Run preprocessing first.')

# Verify structure
print(f'\nUsing: {PROCESSED_DIR}')
for label in ['real', 'fake']:
    label_dir = os.path.join(PROCESSED_DIR, label)
    if os.path.exists(label_dir):
        count = len([f for f in os.listdir(label_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))])
        print(f'  {label}: {count} images')
    else:
        print(f'  ❌ {label}: directory not found!')

print()

# ============================================================================
# TRAINING
# ============================================================================

TARGET_SIZE = (299, 299)
BATCH_SIZE = 32
FROZEN_EPOCHS = 10
FINETUNE_EPOCHS = 15

print('=' * 70)
print('LOADING DATA')
print('=' * 70)

try:
    train_ds = keras.utils.image_dataset_from_directory(
        PROCESSED_DIR,
        validation_split=0.2,
        subset='training',
        seed=123,
        image_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical'
    )
    
    val_ds = keras.utils.image_dataset_from_directory(
        PROCESSED_DIR,
        validation_split=0.2,
        subset='validation',
        seed=123,
        image_size=TARGET_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical'
    )
    
    print('✅ Data loaded!\n')
    
except Exception as e:
    print(f'❌ ERROR: {e}')
    print('\nDEBUG:')
    for root, dirs, files in os.walk(PROCESSED_DIR):
        level = root.replace(PROCESSED_DIR, '').count(os.sep)
        indent = ' ' * 2 * level
        print(f'{indent}{os.path.basename(root)}/ ({len(files)} files)')
    raise

# Preprocess
def preprocess(images, labels):
    return keras.applications.xception.preprocess_input(images), labels

train_ds = train_ds.map(preprocess).prefetch(tf.data.AUTOTUNE)
val_ds = val_ds.map(preprocess).prefetch(tf.data.AUTOTUNE)

print('=' * 70)
print('BUILDING MODEL')
print('=' * 70)

from keras import mixed_precision
from keras.applications import Xception
from keras.layers import Dense, GlobalAveragePooling2D, Dropout
from keras.models import Model
from keras.optimizers import Adam
from keras.callbacks import EarlyStopping, ReduceLROnPlateau

mixed_precision.set_global_policy('mixed_float16')

with tf.device('/GPU:0'):
    base = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base.trainable = False
    
    x = base.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu', dtype='float32')(x)
    x = Dropout(0.5)(x)
    out = Dense(2, activation='softmax', dtype='float32')(x)
    
    model = Model(base.input, out)

print(f'✅ Model: {model.count_params():,} params\n')

# ============================================================================
# PHASE 1: FROZEN BASE
# ============================================================================

print('=' * 70)
print(f'PHASE 1: HEAD TRAINING ({FROZEN_EPOCHS} epochs)')
print('=' * 70)

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

# ============================================================================
# PHASE 2: FINE-TUNING
# ============================================================================

print('=' * 70)
print(f'PHASE 2: FINE-TUNING ({FINETUNE_EPOCHS} epochs)')
print('=' * 70)

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
print(f'✅ Exported: deepfake_net.tflite ({size_mb:.1f} MB)\n')

# Test
interpreter = tf.lite.Interpreter(model_path='deepfake_net.tflite')
interpreter.allocate_tensors()
test_img = np.random.rand(1, 299, 299, 3).astype(np.float32)
test_img = (test_img * 255 - 127.5) / 127.5
interpreter.set_tensor(interpreter.get_input_details()[0]['index'], test_img)
interpreter.invoke()
output = interpreter.get_tensor(interpreter.get_output_details()[0]['index'])
print(f'✅ TFLite test: {output[0]}\n')

# ============================================================================
# SUMMARY
# ============================================================================

print('=' * 70)
print('TRAINING COMPLETE!')
print('=' * 70)
print(f'\n🎯 Final Accuracy: {final_acc:.1%}')
print(f'📦 Model: deepfake_net.tflite ({size_mb:.1f} MB)')

if final_acc >= 0.90:
    print('\n🎉 SUCCESS! >=90%')
elif final_acc >= 0.85:
    print('\n✅ Good! >=85%')
else:
    print('\n⚠️ <85%')

print('\n📥 Download deepfake_net.tflite from Files panel')
print('=' * 70)
