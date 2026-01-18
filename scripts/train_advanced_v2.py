
import os
import shutil
import time
import requests
import zipfile
import cv2
import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam

# --- CONFIGURATION (LOCKED) ---
TARGET_SIZE = (299, 299)
TOTAL_IMAGES_PER_CLASS = 300  # Balanced: 300 Real, 300 Fake
EPOCHS = 8

# Directories
WORKSPACE = "advanced_workspace"
RAW_DIR = f"{WORKSPACE}/raw"
PROCESSED_DIR = f"{WORKSPACE}/processed"
MODEL_FILE = "deepfake_net.tflite"

# Sources
TPDNE_URL = "https://thispersondoesnotexist.com/"
LOCAL_ARCHIVES = ["archive.zip", "dataset.zip"]

def setup_dirs():
    if os.path.exists(WORKSPACE): shutil.rmtree(WORKSPACE)
    os.makedirs(f"{RAW_DIR}/real", exist_ok=True)
    os.makedirs(f"{RAW_DIR}/fake", exist_ok=True)
    os.makedirs(f"{PROCESSED_DIR}/real", exist_ok=True)
    os.makedirs(f"{PROCESSED_DIR}/fake", exist_ok=True)

def download_modern_ai_faces(count=100):
    print(f"\n🤖 Downloading {count} Modern AI Faces from thispersondoesnotexist.com...")
    headers = {'User-Agent': 'Mozilla/5.0'}
    success = 0
    for i in range(count):
        try:
            r = requests.get(TPDNE_URL, headers=headers, timeout=10)
            if r.status_code == 200:
                with open(f"{RAW_DIR}/fake/ai_gen_{i}.jpg", 'wb') as f:
                    f.write(r.content)
                success += 1
                if success % 10 == 0: print(f"   Downloaded {success}/{count}...")
            time.sleep(1.0) # Respect rate limits
        except Exception as e:
            print(f"   Error downloading image {i}: {e}")
    print(f"✅ Downloaded {success} AI faces.")

def extract_kaggle_subset(target_real=200, target_fake=100):
    print(f"\n📦 Extracting subset from local archives (Target: {target_real} Real, {target_fake} Fake)...")
    
    # Find archive
    archive = next((z for z in LOCAL_ARCHIVES if os.path.exists(z)), None)
    if not archive: 
        print("❌ No local archive found (archive.zip/dataset.zip). Skipping Kaggle source.")
        return

    temp_extract = f"{WORKSPACE}/temp_extract"
    with zipfile.ZipFile(archive, 'r') as z:
        z.extractall(temp_extract)

    # Locate folders
    real_src, fake_src = None, None
    for root, dirs, files in os.walk(temp_extract):
        for d in dirs:
            if 'real' in d.lower() and 'fake' not in d.lower(): real_src = os.path.join(root, d)
            if 'fake' in d.lower() and 'real' not in d.lower(): fake_src = os.path.join(root, d)

    if not real_src or not fake_src:
        print("❌ Could not locate Real/Fake folders in archive.")
        return

    # Copy Subset
    def copy_files(src, dst, limit):
        files = [f for f in os.listdir(src) if f.lower().endswith('.jpg')]
        count = 0
        for f in files[:limit]:
            shutil.copy(os.path.join(src, f), os.path.join(dst, f))
            count += 1
        return count

    r = copy_files(real_src, f"{RAW_DIR}/real", target_real)
    f = copy_files(fake_src, f"{RAW_DIR}/fake", target_fake)
    print(f"✅ Extracted {r} Real, {f} Fake from Kaggle.")

def crop_faces():
    print("\n✂️ Running MANDATORY Face Crop (OpenCV Haar Cascade)...")
    
    # Load Classifier
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    
    classes = ['real', 'fake']
    for label in classes:
        src_dir = f"{RAW_DIR}/{label}"
        dst_dir = f"{PROCESSED_DIR}/{label}"
        
        files = os.listdir(src_dir)
        print(f"   Processing {label}: {len(files)} images...")
        
        saved_count = 0
        for fname in files:
            img_path = os.path.join(src_dir, fname)
            img = cv2.imread(img_path)
            if img is None: continue
            
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, 1.1, 4)
            
            # If faces found, crop the largest one
            if len(faces) > 0:
                # Find largest face area
                largest_face = max(faces, key=lambda rect: rect[2] * rect[3])
                x, y, w, h = largest_face
                
                # Add margin (20%)
                margin = int(w * 0.2)
                x = max(0, x - margin)
                y = max(0, y - margin)
                w = min(img.shape[1] - x, w + 2*margin)
                h = min(img.shape[0] - y, h + 2*margin)
                
                face_img = img[y:y+h, x:x+w]
                
                # Resize to Model Input
                face_img = cv2.resize(face_img, TARGET_SIZE)
                
                # Save
                cv2.imwrite(os.path.join(dst_dir, fname), face_img)
                saved_count += 1
            else:
                # If no face detected, SKIP IT (Strict Rule)
                pass
        
        print(f"      Useable faces: {saved_count}/{len(files)}")

def train_model():
    print(f"\n🧠 Training Xception (Frozen Base) on {PROCESSED_DIR}...")
    
    train_datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.xception.preprocess_input,
        horizontal_flip=True,
        rotation_range=20,
        zoom_range=0.2,
        validation_split=0.2
    )
    
    train_gen = train_datagen.flow_from_directory(
        PROCESSED_DIR,
        target_size=TARGET_SIZE,
        batch_size=16,
        class_mode='categorical',
        classes=['real', 'fake'], # 0=Real, 1=Fake
        subset='training'
    )
    
    val_gen = train_datagen.flow_from_directory(
        PROCESSED_DIR,
        target_size=TARGET_SIZE,
        batch_size=16,
        class_mode='categorical',
        classes=['real', 'fake'],
        subset='validation'
    )

    # Architecture
    base_model = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base_model.trainable = False
    
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu')(x)
    x = Dropout(0.5)(x)
    predictions = Dense(2, activation='softmax')(x)
    
    model = Model(inputs=base_model.input, outputs=predictions)
    model.compile(optimizer=Adam(1e-4), loss='categorical_crossentropy', metrics=['accuracy'])
    
    callbacks = [tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=3, restore_best_weights=True)]
    
    model.fit(train_gen, epochs=EPOCHS, validation_data=val_gen, callbacks=callbacks)
    
    # Export
    print(f"\n💾 Exporting to {MODEL_FILE}...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16] # FP16 as requested
    tflite_model = converter.convert()
    
    with open(MODEL_FILE, 'wb') as f:
        f.write(tflite_model)
    print("✅ Done.")

def main():
    print("🚀 STARTING ADVANCED DATA PIPELINE (v2)")
    setup_dirs()
    
    # 1. Gather Data
    # Goal: ~300 Real, ~300 Fake total.
    # Source A: ThisPersonDoesNotExist (Fake) -> 150 images
    download_modern_ai_faces(50)
    
    # Source B: Kaggle (Real/Fake) -> 300 Real, 150 Fake (to balance the 150 AI ones)
    extract_kaggle_subset(target_real=350, target_fake=150) # Extra real to account for face crop failures
    
    # 2. Process
    crop_faces()
    
    # 3. Train
    train_model()

if __name__ == "__main__":
    main()
