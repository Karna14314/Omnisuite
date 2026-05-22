#!/usr/bin/env bash
# OmniSuite One-Click Build & Deployment Tool for Unix/macOS
# Compiles, verifies, packages, and deploys the app to a connected Android device or emulator.

set -e # Exit immediately on error

echo "-------------------------------------------------------------"
echo "[OmniSuite Build System] Preparing local environment..."
echo "-------------------------------------------------------------"

# 1. Compile and package the debug APK
echo "[BUILD] Triggering local Gradle assembler..."
chmod +x ./gradlew
./gradlew assembleDebug

echo "[SUCCESS] Debug APK assembled successfully."

# 2. Check for connected ADB devices
echo "[ADB] Checking for authenticated connected devices/emulators..."
DEVICES=$(adb devices | grep -v "List" | grep "device" || true)

if [ -z "$DEVICES" ]; then
    echo "[WARNING] No authorized, connected Android devices or emulators found."
    echo "Please connect your device with USB Debugging enabled or launch an emulator."
    exit 1
fi
echo "[SUCCESS] Connected device recognized."

# 3. Install the APK
echo "[DEPLOY] Installing debug APK onto device..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "[SUCCESS] APK installed successfully!"

# 4. Start the Application
echo "[LAUNCH] Initiating main launcher activity..."
adb shell am start -n com.karnadigital.omnisuite/.MainActivity
echo "-------------------------------------------------------------"
echo "[SUCCESS] OmniSuite is fully operational on your device!"
echo "-------------------------------------------------"
