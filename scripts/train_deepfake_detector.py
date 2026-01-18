
import os
import shutil
import zipfile
import glob
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D, Dropout
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import Adam

# --- CONFIGURATION ---
# The script will look for these files in the CURRENT directory
LOCAL_ZIPS = ["archive.zip", "dataset.zip"] 
LOCAL_FOLDERS = ["dataset", "data", "Real_and_Fake_Image_Classsification"]

DATASET_SOURCE_KAGGLE = "ciplab/real-and-fake-face-detection" 

# Output
MODEL_FILENAME = "deepfake_net.tflite"

def find_image_folders(base_path):
    """Recursively find folders named 'real' and 'fake'."""
    real_dir = None
    fake_dir = None
    print(f"   🔍 Searching in: {base_path}")
    for root, dirs, files in os.walk(base_path):
        for d in dirs:
            d_lower = d.lower()
            if 'real' in d_lower and 'fake' not in d_lower:
                check = os.path.join(root, d)
                if len(os.listdir(check)) > 5: real_dir = check
            if 'fake' in d_lower and 'real' not in d_lower:
                check = os.path.join(root, d)
                if len(os.listdir(check)) > 5: fake_dir = check
    return real_dir, fake_dir

def main():
    print(f"🚀 TensorFlow Version: {tf.__version__}")
    
    # GPU Check
    gpus = tf.config.list_physical_devices('GPU')
    if gpus:
        print(f"✅ GPU Detected: {gpus}")
        tf.config.experimental.set_memory_growth(gpus[0], True)
    else:
        print("⚠️ No GPU detected. Running on CPU (5900X).")

    # 1. Acquire Data
    dataset_path = None
    
    # Priority A: Check for already extracted folders
    for folder in LOCAL_FOLDERS:
        if os.path.exists(folder):
            print(f"✅ Found local folder: {folder}")
            dataset_path = folder
            break
            
    # Priority B: Check for Zip files
    if not dataset_path:
        for z in LOCAL_ZIPS:
            if os.path.exists(z):
                print(f"✅ Found local zip: {z}")
                print("   Extracting...")
                with zipfile.ZipFile(z, 'r') as zip_ref:
                    zip_ref.extractall("extracted_dataset")
                dataset_path = "extracted_dataset"
                break
    
    # Priority C: Kaggle Download
    if not dataset_path:
        print("⚠️ No local data found. Trying KaggleHub...")
        try:
            import kagglehub
            dataset_path = kagglehub.dataset_download(DATASET_SOURCE_KAGGLE)
            print(f"   Downloaded to: {dataset_path}")
        except Exception as e:
            print(f"❌ Failed to download: {e}")
            print("❌ Please place 'archive.zip' or 'dataset.zip' in this folder and try again.")
            return

    # 2. Organize Data
    real_img_dir, fake_img_dir = find_image_folders(dataset_path)

    if not real_img_dir or not fake_img_dir:
        print("❌ Could not auto-detect 'real' and 'fake' image folders.")
        print(f"   Checked inside: {dataset_path}")
        return

    print(f"   ✅ Real Images: {real_img_dir} ({len(os.listdir(real_img_dir))} files)")
    print(f"   ✅ Fake Images: {fake_img_dir} ({len(os.listdir(fake_img_dir))} files)")

    # Create workspace
    workspace = "training_workspace"
    if os.path.exists(workspace): shutil.rmtree(workspace)
    os.makedirs(f"{workspace}/real")
    os.makedirs(f"{workspace}/fake")

    # Limit for speed (Optional: remove [:LIMIT] for full training)
    # Using 2000 images per class for a good balance on your RTX 3060
    LIMIT = 2000 
    print(f"\n📂 Formatting workspace (Using {LIMIT} images/class)...")
    
    def copy_images(src, dst):
        count = 0
        valid_exts = ('.jpg', '.jpeg', '.png')
        files = [f for f in os.listdir(src) if f.lower().endswith(valid_exts)]
        for f in files[:LIMIT]:
            shutil.copy(os.path.join(src, f), os.path.join(dst, f))
            count += 1
        return count

    copy_images(real_img_dir, f"{workspace}/real")
    copy_images(fake_img_dir, f"{workspace}/fake")

    # 3. Model Setup
    print("\n🏗️ Building Xception...")
    IMG_SIZE = (299, 299)
    BATCH_SIZE = 16

    datagen = ImageDataGenerator(
        preprocessing_function=tf.keras.applications.xception.preprocess_input,
        horizontal_flip=True,
        validation_split=0.2
    )

    train_gen = datagen.flow_from_directory(
        workspace, target_size=IMG_SIZE, batch_size=BATCH_SIZE,
        classes=['real', 'fake'], subset='training' # 0=Real, 1=Fake
    )
    val_gen = datagen.flow_from_directory(
        workspace, target_size=IMG_SIZE, batch_size=BATCH_SIZE,
        classes=['real', 'fake'], subset='validation'
    )

    base_model = Xception(weights='imagenet', include_top=False, input_shape=(299, 299, 3))
    base_model.trainable = False

    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(1024, activation='relu')(x)
    x = Dropout(0.5)(x)
    predictions = Dense(2, activation='softmax')(x)

    model = Model(inputs=base_model.input, outputs=predictions)
    model.compile(optimizer=Adam(1e-4), loss='categorical_crossentropy', metrics=['accuracy'])

    # 4. Train
    print("\n🏋️ Training (5 Epochs)...")
    model.fit(train_gen, epochs=5, validation_data=val_gen)

    # 5. Export
    print(f"\n📦 Converting to {MODEL_FILENAME}...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(MODEL_FILENAME, 'wb') as f:
        f.write(tflite_model)
    
    print("-" * 50)
    print(f"✅ MODEL GENERATED: {os.path.abspath(MODEL_FILENAME)}")
    print(f"👉 Move this file to 'app/src/main/assets/'")
    print("-" * 50)

if __name__ == "__main__":
    main()
