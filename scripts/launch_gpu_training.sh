#!/bin/bash
export VENV_DIR="/media/sreenath/kannaDisk/iitproject/HiddenLayer/venv"
source "$VENV_DIR/bin/activate"

# Use system libraries in /usr/lib/x86_64-linux-gnu
export LD_LIBRARY_PATH="/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH"
export LD_LIBRARY_PATH="/usr/lib/x86_64-linux-gnu/nvidia/current:$LD_LIBRARY_PATH"

# CUDA bin path
export PATH="/usr/local/cuda/bin:$PATH"

echo "=========================================="
echo "🚀 RTX 3060 GPU TRAINING (System Libs)"
echo "=========================================="

# Detailed GPU check
python3 -c "
import tensorflow as tf
import os

print(f'TensorFlow: {tf.__version__}')
print(f'LD_LIBRARY_PATH: {os.environ.get(\"LD_LIBRARY_PATH\", \"NOT SET\")[:100]}...')

try:
    gpus = tf.config.list_physical_devices('GPU')
    print(f'\\nGPUs Detected: {len(gpus)}')
    if len(gpus) > 0:
        for i, gpu in enumerate(gpus):
            print(f'  GPU {i}: {gpu}')
            # Try to allocate memory to verify it works
            with tf.device(f'/GPU:{i}'):
                a = tf.constant([[1.0, 2.0]])
                b = tf.constant([[3.0], [4.0]])
                c = tf.matmul(a, b)
            print(f'  ✅ GPU {i} VERIFIED (test computation successful)')
        exit(0)
    else:
        print('❌ NO GPU DETECTED')
        exit(1)
except Exception as e:
    print(f'❌ GPU ERROR: {e}')
    exit(1)
"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅✅✅ GPU READY - LAUNCHING TRAINING ✅✅✅"
    echo "=========================================="
    CUDA_VISIBLE_DEVICES=0 TF_FORCE_GPU_ALLOW_GROWTH=true python3 train_gpu_full_power.py
else
    echo ""
    echo "❌ CRITICAL: GPU NOT ACCESSIBLE"
    echo "   Your system requires cuDNN to be installed."
    echo "   Run: sudo apt-get install nvidia-cudnn"
    exit 1
fi
