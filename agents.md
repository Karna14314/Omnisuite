# Developer Agent Guidelines & Onboarding Manual

This document provides developer agents and engineers with setup guidelines for managing compilations, packaging, testing pipelines, dependency execution parameters, and navigation/viewer architectures within the OmniSuite project.

---

## 🛠️ Portable Gradle Wrapper Standardization
To maintain absolute consistency and reuse the standardized Gradle `8.11.1` configuration across sibling or downstream projects:

### Wrapper Configuration setup:
1. **Target Directory**: Root directory of the project.
2. **Verify Wrapper properties** in `gradle/wrapper/gradle-wrapper.properties`:
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
   ```
3. **Command Wrapper Execution**:
   - **Windows**: `.\gradlew.bat <TASK>`
   - **macOS/Linux**: `./gradlew <TASK>`

---

## 📂 Core Package Structure & Key Coordinates

Understanding the codebase directories:
- `com.karnadigital.omnisuite.core`: Core modules
  - `com.karnadigital.omnisuite.core.engine`: Contains `OfficeConverter.kt` (PDF transcoder using PDFBox-Android and Apache POI) and `DocumentSearchEngine.kt` (real-time in-document matches).
  - `com.karnadigital.omnisuite.core.util`: Contains `UriCacheUtils.kt` (SAF content streams cacher for sandboxing).
  - `com.karnadigital.omnisuite.core.repository`: SQLite DB logs mapping `RecentFile` elements.
- `com.karnadigital.omnisuite.feature`: User interfaces built in Jetpack Compose
  - `com.karnadigital.omnisuite.feature.home`: Contains `HomeScreen.kt` (4-tab bottom navigation) and `FileBrowserScreen.kt`.
  - `com.karnadigital.omnisuite.feature.tools`: Contains `AllToolsScreen.kt` (categorized 30+ planning tools).
  - `com.karnadigital.omnisuite.feature.viewer`: Contains `ViewerDispatcherScreen.kt` (automatic MIME cacher/type resolver), `ImageViewerScreen.kt` (Coil cinematic viewer), `XlsxViewerScreen.kt` and `XlsxViewerViewModel.kt` (dual XLSX + CSV editor).
  - `com.karnadigital.omnisuite.feature.utility`: Contains `QrGeneratorScreen.kt` (11-payload compiler and color model configurations) and `QrGeneratorViewModel.kt`.

---

## 🧪 Deployment & Deployment Automation Commands

To compile, verify, package, and deploy the application to a connected Android phone or active emulator in **one single command**, execute the automated deployment pipeline script in the project root:

### Windows Command:
```powershell
.\build_and_install.bat
```

### macOS/Linux Command:
```bash
./build_and_install.sh
```

### Single Command Under-the-Hood Sequence:
1. **Kotlin Syntax Compilation Check**:
   ```bash
   ./gradlew compileDebugKotlin --no-daemon
   ```
2. **Execute JVM Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
3. **Assemble Debug APK Binary**:
   ```bash
   ./gradlew assembleDebug --no-daemon
   ```
4. **ADB Device Installation**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Direct Intent Activity Boot**:
   ```bash
   adb shell am start -n com.karnadigital.omnisuite/.MainActivity
   ```

---

## 📝 Key Verification Specs (For Future Agents)
- **100% Offline Integrity**: Absolutely zero network adapters or cloud APIs are allowed. Document parser engines, ML Kit OCR, and QR generators run entirely in-device.
- **Persistent SAF Authority**: Persistent content URI locks across device reboots must call `context.contentResolver.takePersistableUriPermission(uri, takeFlags)` inside launchers.
- **Reflective R8 KeepRules**: Ensure that standard reflection calls utilized by Apache POI and PDFBox are fully detailed in `app/proguard-rules.pro` to prevent runtime `ClassNotFoundException` crashes upon R8 minification.

---

## 🤖 LLM Behavioral Guidelines & Mistake Reduction (CLAUDE.md)

### 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
- State assumptions explicitly. If uncertain, ask.
- Present multiple interpretations instead of picking silently.
- Push back when a simpler approach exists.
- Stop and ask if something is unclear.

### 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**
- No extra features beyond what is asked.
- No abstractions for single-use code.
- No unsolicited flexibility or configurability.
- Avoid error handling for impossible scenarios.
- Keep implementations as concise as possible.

### 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**
- Match existing style. Don't refactor or "improve" adjacent, working code.
- Remove only imports, variables, or functions that your own changes made unused.
- Do not delete pre-existing dead code unless explicitly requested.

### 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**
- Define verifiable success criteria and plan steps.
- Write tests/verifications to confirm correctness.
