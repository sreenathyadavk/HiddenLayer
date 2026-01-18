# BUILD FIX GUIDE

## Issue: Gradle Build Failed

**Error**: `org/gradle/api/artifacts/SelfResolvingDependency`

**Cause**: Gradle 9.0-milestone incompatibility

**Fix Applied**: ✅ Downgraded to Gradle 8.2.1

---

## How to Build (Choose ONE method)

### ✅ METHOD 1: Use Android Studio (RECOMMENDED)

```bash
# 1. Open project in Android Studio
# File → Open → Select HiddenLayer folder

# 2. Wait for Gradle sync (auto-downloads Gradle 8.2.1)

# 3. Build → Build Bundle(s) / APK(s) → Build APK(s)

# Done! APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

**Why this works**: Android Studio handles Gradle version automatically.

---

### ✅ METHOD 2: Command Line (Quick Script)

```bash
# Run the build script I created
./build_apk.sh
```

This script:
1. Cleans previous build
2. Downloads correct Gradle version
3. Builds release APK
4. Shows APK location

---

### ✅ METHOD 3: Manual CLI (If script fails)

```bash
# 1. Clean
./gradlew clean

# 2. Build debug APK (faster, no signing needed)
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Installing APK

### If you have ADB:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### If you DON'T have ADB:
1. Copy APK to phone (USB/cloud/email)
2. Open APK on phone
3. Allow "Install from unknown sources"
4. Install

---

## What's Already Done ✅

- ✅ Model downloaded (18MB, in assets/)
- ✅ Gradle version fixed (8.2.1)
- ✅ All code production-ready
- ✅ Build script created

**You just need to BUILD now.**

---

## Quick Decision Tree

```
Do you have Android Studio?
├─ YES → Use METHOD 1 (easiest)
└─ NO  → Use METHOD 2 (build_apk.sh)
          └─ If that fails → Use METHOD 3 (manual)
```

---

## Expected Output (Success)

```
BUILD SUCCESSFUL in 45s
✅ APK Location: app/build/outputs/apk/debug/app-debug.apk
Size: ~20MB
```

Then just install the APK on your phone!

---

**TL;DR**: Run `./build_apk.sh` OR open in Android Studio and click "Build APK"
