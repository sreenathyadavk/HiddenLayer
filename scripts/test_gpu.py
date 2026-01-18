#!/usr/bin/env python3
import tensorflow as tf

print('=' * 60)
print('GPU VERIFICATION TEST')
print('=' * 60)

print(f'\nTensorFlow Version: {tf.__version__}')
print(f'Built with CUDA: {tf.test.is_built_with_cuda()}')

gpus = tf.config.list_physical_devices('GPU')
print(f'\nGPUs detected: {gpus}')

if len(gpus) > 0:
    print('\n✅ GPU READY FOR TRAINING!')
    print(f'   Device: {gpus[0].name}')
    
    # Test GPU computation
    with tf.device('/GPU:0'):
        a = tf.constant([[1.0, 2.0], [3.0, 4.0]])
        b = tf.constant([[1.0, 2.0], [3.0, 4.0]])
        c = tf.matmul(a, b)
    print(f'   Test computation successful on: {c.device}')
    print('\n🚀 READY TO START GPU TRAINING!')
else:
    print('\n❌ NO GPU DETECTED')
    print('   Training CANNOT proceed')
    print('   Fix CUDA/cuDNN installation')
    exit(1)
