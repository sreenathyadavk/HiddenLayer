# Add keep rules for TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep data models (used by reflection)
-keep class com.hiddenlayer.data.models.** { *; }
-keep class com.hiddenlayer.domain.models.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# General Android rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
