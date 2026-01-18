#!/bin/bash
# Download and prepare EfficientNet-Lite0 for Android

echo "🔧 Downloading EfficientNet-Lite0 TFLite model..."

# Create assets directory
mkdir -p ../app/src/main/assets/

# Download pretrained model from TensorFlow Hub
# Using EfficientNet-Lite0 classification model (already TFLite format)
wget -O ../app/src/main/assets/efficientnet_lite0_fp16.tflite \
  "https://tfhub.dev/tensorflow/lite-model/efficientnet/lite0/fp32/2?lite-format=tflite"

if [ $? -eq 0 ]; then
    echo "✅ Model downloaded successfully"
    ls -lh ../app/src/main/assets/efficientnet_lite0_fp16.tflite
else
    echo "⚠️  Direct download failed. Using alternative approach..."
    
    # Alternative: Download via Python
    python3 << 'EOF'
import urllib.request
import os

url = "https://storage.googleapis.com/tfhub-lite-models/tensorflow/lite-model/efficientnet/lite0/fp32/2.tflite"
output_path = "../app/src/main/assets/efficientnet_lite0_fp16.tflite"

os.makedirs(os.path.dirname(output_path), exist_ok=True)

print("Downloading model...")
urllib.request.urlretrieve(url, output_path)
print(f"✅ Model saved to {output_path}")
print(f"Size: {os.path.getsize(output_path) / 1024 / 1024:.1f} MB")
EOF
fi

echo "✅ Model setup complete"
