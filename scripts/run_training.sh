#!/bin/bash
# Quick start script for production model training

echo "=========================================="
echo "  HiddenLayer Model Training"
echo "=========================================="
echo ""

# Activate venv
VENV_PATH="../venv"
if [ -d "$VENV_PATH" ]; then
    echo "🐍 Activating virtual environment..."
    source "$VENV_PATH/bin/activate"
    echo "   ✅ Using venv Python: $(which python3)"
else
    echo "⚠️  venv not found at $VENV_PATH, using system Python"
fi

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found"
    exit 1
fi

# Check dependencies
echo "📦 Checking dependencies..."
python3 -c "import tensorflow, cv2, requests, numpy" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "⚠️  Missing dependencies. Installing to venv..."
    pip3 install tensorflow opencv-python requests numpy
fi

echo "✅ Dependencies ready"
echo ""

# Check for Kaggle dataset
if [ ! -f "archive.zip" ]; then
    echo "⚠️  archive.zip not found in current directory"
    echo "   Please download Kaggle dataset and place archive.zip here"
    echo "   Or the script will skip Kaggle source"
    echo ""
fi

# Info about manual datasets
echo "📋 Dataset Info:"
echo "   • Kaggle: auto-detected from archive.zip"
echo "   • Modern AI: auto-downloaded from TPDNE"
echo "   • Celeb-DF v2: manual download required (optional)"
echo "   • FaceForensics++: manual download required (optional)"
echo ""
echo "   For manual datasets, see script output for instructions"
echo ""

# Run training
echo "🚀 Starting training..."
echo ""
python3 train_production_model.py

echo ""
echo "=========================================="
echo "  Training Complete!"
echo "=========================================="
