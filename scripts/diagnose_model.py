#!/usr/bin/env python3
"""
DIAGNOSTIC SCRIPT - Check why model outputs constant 0.5

Checks:
1. Model head weights (detect collapsed head)
2. Class indices mapping
3. Raw model output on test images
"""

import tensorflow as tf
import numpy as np
from PIL import Image
import sys

# Load the model
print("=" * 70)
print("  DIAGNOSTIC CHECK: Model Output Analysis")
print("=" * 70)

# Check if model exists
model_path = "deepfake_net.tflite"
try:
    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"\n✅ Model loaded: {model_path}")
    print(f"Input shape: {input_details[0]['shape']}")
    print(f"Output shape: {output_details[0]['shape']}")
    
except Exception as e:
    print(f"\n❌ Failed to load model: {e}")
    sys.exit(1)

# Test inference with a simple pattern
print("\n" + "=" * 70)
print("  TEST 1: Inference with Solid Color Images")
print("=" * 70)

def test_inference(image_array, label):
    """Run inference on a test image"""
    # Normalize to [-1, 1] (Xception preprocessing)
    normalized = (image_array / 127.5) - 1.0
    
    # Add batch dimension
    input_data = np.expand_dims(normalized, axis=0).astype(np.float32)
    
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"\n{label}:")
    print(f"  Input range: [{normalized.min():.3f}, {normalized.max():.3f}]")
    print(f"  Raw output: {output[0]}")
    print(f"  Real prob: {output[0][0]:.6f}")
    print(f"  Fake prob: {output[0][1]:.6f}")
    print(f"  Sum: {output[0].sum():.6f} (should be ~1.0 for softmax)")
    
    return output[0]

# Test 1: All black image (should give some prediction)
black_img = np.zeros((299, 299, 3), dtype=np.uint8)
out1 = test_inference(black_img, "Black image (all zeros)")

# Test 2: All white image (should give different prediction)
white_img = np.ones((299, 299, 3), dtype=np.uint8) * 255
out2 = test_inference(white_img, "White image (all 255)")

# Test 3: Random noise
random_img = np.random.randint(0, 256, (299, 299, 3), dtype=np.uint8)
out3 = test_inference(random_img, "Random noise")

# Analysis
print("\n" + "=" * 70)
print("  ANALYSIS")
print("=" * 70)

# Check if outputs are always the same
outputs = np.array([out1, out2, out3])
variance = outputs.var(axis=0)

print(f"\nOutput variance across different inputs:")
print(f"  Real prob variance: {variance[0]:.8f}")
print(f"  Fake prob variance: {variance[1]:.8f}")

if variance.max() < 0.001:
    print("\n❌ PROBLEM DETECTED: Model outputs are constant!")
    print("   This indicates:")
    print("   1. Head weights may have collapsed to ~0")
    print("   2. Model didn't learn during training")
    print("   3. Outputs are stuck at initialization values")
else:
    print("\n✅ Model outputs vary with different inputs")

# Check if outputs are always 0.5
if np.allclose(outputs, 0.5, atol=0.01):
    print("\n❌ CRITICAL: All outputs ≈ 0.5!")
    print("   Model is not making predictions - just guessing")
elif all(np.abs(out[0] - out[1]) < 0.01 for out in outputs):
    print("\n⚠️  WARNING: Real and fake probabilities are equal")
    print("   Model is uncertain about everything")

# Check softmax normalization
sums = outputs.sum(axis=1)
print(f"\nSoftmax sums: {sums}")
if not np.allclose(sums, 1.0, atol=0.01):
    print("❌ WARNING: Outputs don't sum to 1.0! Not proper softmax?")
else:
    print("✅ Outputs sum to 1.0 (proper softmax)")

print("\n" + "=" * 70)
print("  RECOMMENDATION")
print("=" * 70)

if variance.max() < 0.001:
    print("\nThe model head likely collapsed during training.")
    print("Next step: Check training script for:")
    print("  1. Final layer weights (should NOT be all ~0)")
    print("  2. Class indices (confirm real=0, fake=1)")
    print("  3. Learning rate (may be too low)")
else:
    print("\nModel outputs vary - issue may be elsewhere:")
    print("  1. Check Android processing pipeline")
    print("  2. Verify class index mapping")
    print("  3. Check if averaging is causing 0.5")
