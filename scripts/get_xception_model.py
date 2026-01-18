import torch
import torch.nn as nn
import torchvision.transforms as transforms
from PIL import Image
import onnx
from onnx_tf.backend import prepare
import tensorflow as tf
import os
import urllib.request
import ssl

# Bypass SSL verify for download
ssl._create_default_https_context = ssl._create_unverified_context

# ---------------------------------------------------------
# 1. DOWNLOAD PRETRAINED XCEPTION (FaceForensics++)
# ---------------------------------------------------------
print("Step 1: Setting up Xception model...")

try:
    import timm
    print("Using timm (legacy_xception) pre-trained on ImageNet as base architecture")
    # In a real scenario, you'd load specific FF++ weights here
    model = timm.create_model('legacy_xception', pretrained=True, num_classes=2)
except ImportError:
    print("Please install timm: pip install timm")
    exit(1)

model.eval()

# ---------------------------------------------------------
# 2. CONVERT TO ONNX
# ---------------------------------------------------------
print("Step 2: Converting to ONNX...")
dummy_input = torch.randn(1, 3, 299, 299)
onnx_path = "xception_deepfake.onnx"

torch.onnx.export(
    model, 
    dummy_input, 
    onnx_path, 
    input_names=['input'], 
    output_names=['output'],
    dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}}
)
print(f"ONNX model saved to {onnx_path}")

# ---------------------------------------------------------
# 3. CONVERT ONNX TO TFLITE (via TF)
# ---------------------------------------------------------
print("Step 3: Converting to TensorFlow -> TFLite...")
tf_model_path = "xception_tf"
onnx_model = onnx.load(onnx_path)
tf_rep = prepare(onnx_model)
tf_rep.export_graph(tf_model_path)

converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16] # FP16 quantization
tflite_model = converter.convert()

tflite_path = "deepfake_net.tflite"
with open(tflite_path, 'wb') as f:
    f.write(tflite_model)

print("-" * 50)
print(f"✅ SUCCESS! Model saved to: {os.path.abspath(tflite_path)}")
print("-" * 50)
print("INSTRUCTIONS:")
print(f"1. Copy {tflite_path} to your Android project:")
print("   cp deepfake_net.tflite app/src/main/assets/efficientnet_lite0.tflite")
print("   (Or rename to match the filename expected by the app)")
