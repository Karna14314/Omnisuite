@echo off
:: OmniSuite One-Click Build & Deployment Tool for Windows
:: Compiles, verifies, packages, and deploys the app to a connected Android device or emulator.

echo -------------------------------------------------------------
echo [OmniSuite Build System] Preparing local environment...
echo -------------------------------------------------------------

:: 1. Compile and package the debug APK
echo [BUILD] Triggering local Gradle assembler...
call .\gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Gradle compilation or packaging failed! Exiting...
    exit /b %ERRORLEVEL%
)
echo [SUCCESS] Debug APK assembled successfully.

:: 2. Check for connected ADB devices
echo [ADB] Checking for authenticated connected devices/emulators...
adb devices > temp_devices.txt
findstr /R /C:"[a-zA-Z0-9].*device$" temp_devices.txt > nul
if %ERRORLEVEL% neq 0 (
    echo [WARNING] No authorized, connected Android devices or emulators found.
    echo Please connect your device with USB Debugging enabled or launch an emulator.
    del temp_devices.txt
    exit /b 1
)
del temp_devices.txt
echo [SUCCESS] Connected device recognized.

:: 3. Install the APK
echo [DEPLOY] Installing debug APK onto device...
adb install -r app/build/outputs/apk/debug/app-debug.apk
if %ERRORLEVEL% neq 0 (
    echo [ERROR] ADB installation failed! Exiting...
    exit /b %ERRORLEVEL%
)
echo [SUCCESS] APK installed successfully!

:: 4. Start the Application
echo [LAUNCH] Initiating main launcher activity...
adb shell am start -n com.karnadigital.omnisuite/.MainActivity
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to start application on device!
    exit /b %ERRORLEVEL%
)
echo -------------------------------------------------------------
echo [SUCCESS] OmniSuite is fully operational on your device!
echo -------------------------------------------------------------
