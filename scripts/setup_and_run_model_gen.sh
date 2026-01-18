#!/bin/bash
set -e

# 1. Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# 2. Activate venv
source venv/bin/activate

# 3. Upgrade pip
pip install --upgrade pip

# 4. Install dependencies
echo "Installing dependencies (this may take a while)..."
# Installing CPU versions of torch/tensorflow to save bandwidth/time if possible, 
# but standard install is safer for compatibility.
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install tensorflow-cpu tensorflow-probability tf_keras # lighter than full tensorflow
pip install onnx==1.15.0 onnx-tf timm pillow

# 5. Run the script
echo "Running get_xception_model.py..."
python3 scripts/get_xception_model.py

# 6. Copy the model if successful
if [ -f "deepfake_net.tflite" ]; then
    echo "Copying model to assets..."
    cp deepfake_net.tflite app/src/main/assets/
    echo "✅ DONE! Model installed."
else
    echo "❌ Error: deepfake_net.tflite was not generated."
    exit 1
fi
