# OmniSuite

OmniSuite is a **fully offline, high-performance, premium productivity suite** built natively for Android using Jetpack Compose and modern architecture principles. It operates 100% locally on the user's device, ensuring zero dependency on external network APIs or cloud servers, safeguarding absolute data privacy.

---

## 💎 Features & Capabilities

OmniSuite delivers desktop-grade utility across a lightweight mobile framework:

### 1. Unified Sandbox Document Viewers
- **PDF Viewer**: High-performance rendering, text search, page jump, and zoom controls.
- **Word Viewer (`.docx`, `.doc`)**: In-memory parsing, style rendering, and text editing using Apache POI.
- **Excel Viewer (`.xlsx`, `.xls`)**: Normalized grid-view tabular visual spreadsheet rendering. Supports active formulas calculations and inline cell updating.
- **Slides Viewer (`.pptx`, `.ppt`)**: Slides layouts presentation controls.
- **Text Editor (`.txt`)**: Text-wrapped editor, support for standard file writes.
- **Image Viewer**: Cinematic zoomable viewer supporting JPG, PNG, WEBP, GIF, and BMP. Features double-tap reset and full metadata inspection.
- **CSV Editor**: Full offline parser handling embedded double-quotes, commas, and line-breaks. Allows spreadsheet grid editing and exports back to standard formatted CSV.

### 2. PDF toolkit (Offline-First)
- **Merge & Split**: Combine multiple files or extract targeted page ranges.
- **Encrypt**: Secure files with password locks using PDFBox.
- **Signature Pad**: High-fidelity vector digital canvas capture and signing.
- **Watermark overlay**: Extended graphics state rotation stamping.
- **Office Transcoders**: Dynamic offline conversion of docx/xlsx elements to landscapes/A4 PDF documents.

### 3. Image Lab & ML Kit Utilities
- **OCR Text Recognition**: Extract Latin-model text offline from imported pictures or target photos.
- **Smart Edge Scanner**: Automated edge detection and page camera crop tools.
- **Image Editor**: Compress, interactively crop, and format transcode (JPEG, PNG, WEBP, BMP).

### 4. 11-Payload QR Generator & Customization
Dynamically structures and compiles QR codes offline for **11 different payload formats** (exceeding standard requirements):
- **URL**, **Plain Text**, **WiFi Credentials**, **Contact (vCard 3.0)**, **Email Links**, **SMS Links**, **Phone Shortcuts**, **GPS Coordinates (Geo)**, **Event Calendars (vCalendar 2.0)**, **WhatsApp pre-fills**, and **Play Store shortcuts**.
- **Accent Customizations**: Allows customizing foreground accent colors (Black, Emerald, Royal Blue, Indigo, and Deep Red) and high-fidelity PNG downloads to device gallery folders.

### 5. Categorized All-Tools Hub
- Restructured navigation into a beautiful **4-tab dashboard** (Home Workspace, Tools Hub, File Browser, and History Logs) inspired by premium mockups.
- Categorized 30+ tools under PDF, Documents, Image, and Utilities tabs inside a clean scroll-safe Compose grid.

---

## 🛠️ Architecture & Build Guidelines

OmniSuite uses a modern clean architecture with Dagger-Hilt for dependency injection, Room database for offline history logs, CameraX hardware abstractions, Apache POI for document indexing, PDFBox-Android for graphics, and Coil for images.

### Portable Gradle Wrapper Standardization

To maintain absolute build consistency across workstations, pipelines, and developer environments:
- Standardized Gradle Version: **Gradle 8.11.1**
- Java Platform: **JDK 17** (compatible with toolchains up to Java 21)

---

## 🚀 One-Command Compile & Deployment

To build, verify, package, and deploy OmniSuite to your connected device in **one single command**, run the custom deployment script in your project root:

### For Windows:
```powershell
.\build_and_install.bat
```

### For macOS / Linux:
```bash
./build_and_install.sh
```

### Script Execution Parameters:
1. **Compiles & Packages** the optimized debug APK: `.\gradlew assembleDebug`
2. **Scans & Verifies** connected, authorized USB-debugging devices/emulators via `adb devices`.
3. **Deploys & Upgrades** the compiled binary directly on the device: `adb install -r ...`
4. **Starts & Launches** the premium splash viewport: `adb shell am start -n com.karnadigital.omnisuite/.MainActivity`

---

## 🧪 Automated CI Workflow
Every commit and Pull Request triggers a comprehensive automated test build in our GitHub Actions pipeline (`.github/workflows/ci.yml`):
- Performs static syntax compilation verification (`compileDebugKotlin`).
- Runs target unit testing suites evaluating payload compilers (`testDebugUnitTest`).
- Assembles debugging structures (`assembleDebug`).
