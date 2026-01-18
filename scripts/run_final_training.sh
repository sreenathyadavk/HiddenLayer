#!/bin/bash
# Quick start script for FINAL production model training
# Trains ONE model to detect ALL fake types (GAN + Diffusion + Deepfakes)

set -e

cd "$(dirname "$0")"

echo "=========================================="
echo "  FINAL PRODUCTION MODEL TRAINING"
echo "  Multi-Source Deepfake Detection"
echo "=========================================="
echo ""

# Check if archive.zip exists
if [ ! -f "archive.zip" ]; then
    echo "⚠️  WARNING: archive.zip not found"
    echo "   Download from Kaggle: Real vs Fake Faces"
    echo "   This provides ~2000 real + 800 fake images"
    echo ""
    read -p "Continue without Kaggle dataset? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled. Please add archive.zip and try again."
        exit 1
    fi
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
    echo "⚠️  Missing dependencies. Installing..."
    pip3 install tensorflow opencv-python requests numpy
fi

echo "✅ Dependencies ready"
echo ""

# Collect diffusion images info
echo "📋 Data Collection Checklist:"
echo ""
echo "Required (Auto):"
echo "  ✓ Kaggle: archive.zip (if present)"
echo "  ✓ TPDNE GAN: auto-download 600 images"
echo ""
echo "Optional (Manual, for >90% accuracy):"
if [ -d "gemini_images" ]; then
    COUNT=$(ls gemini_images/*.{jpg,jpeg,png} 2>/dev/null | wc -l)
    echo "  ✓ Gemini: $COUNT images found"
else
    echo "  ⏭ Gemini: folder not found (target: 800)"
fi

if [ -d "grok_images" ]; then
    COUNT=$(ls grok_images/*.{jpg,jpeg,png} 2>/dev/null | wc -l)
    echo "  ✓ Grok: $COUNT images found"
else
    echo "  ⏭ Grok: folder not found (target: 600)"
fi

if [ -d "midjourney_images" ]; then
    COUNT=$(ls midjourney_images/*.{jpg,jpeg,png} 2>/dev/null | wc -l)
    echo "  ✓ Midjourney: $COUNT images found"
else
    echo "  ⏭ Midjourney: folder not found (target: 700)"
fi

if [ -d "sdxl_images" ]; then
    COUNT=$(ls sdxl_images/*.{jpg,jpeg,png} 2>/dev/null | wc -l)
    echo "  ✓ SDXL: $COUNT images found"
else
    echo "  ⏭ SDXL: folder not found (target: 700)"
fi

if [ -d "celeb_df" ]; then
    echo "  ✓ Celeb-DF: videos found"
else
    echo "  ⏭ Celeb-DF: folder not found (target: 500 frames)"
fi

if [ -d "faceforensics" ]; then
    echo "  ✓ FaceForensics++: videos found"
else
    echo "  ⏭ FaceForensics++: folder not found (target: 500 frames)"
fi

echo ""
echo "See TRAINING_INSTRUCTIONS.md for data collection guide"
echo ""

# Estimate training time
echo "⏱️  Estimated Training Time:"
if python3 -c "import tensorflow as tf; exit(0 if tf.config.list_physical_devices('GPU') else 1)" 2>/dev/null; then
    echo "   GPU detected: ~2-3 hours"
else
    echo "   CPU only: ~6-12 hours (SLOW!)"
    echo "   Consider using Google Colab for GPU training"
fi

echo ""
read -p "Start training now? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

# Run training
echo ""
echo "🚀 Starting training..."
echo "   Output will be logged to training.log"
echo ""

python3 train_final_production.py 2>&1 | tee training.log

# Check if successful
if [ -f "deepfake_net.tflite" ]; then
    echo ""
    echo "=========================================="
    echo "  ✅ TRAINING COMPLETE"
    echo "=========================================="
    echo ""
    echo "📋 Next Steps:"
    echo "   1. cp deepfake_net.tflite ../app/src/main/assets/"
    echo "   2. cd .. && ./gradlew assembleDebug"
    echo "   3. adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo "   4. Test with Gemini/Grok images!"
    echo ""
else
    echo ""
    echo "❌ Training failed or incomplete"
    echo "   Check training.log for details"
    exit 1
fi
