<p align="center">
  <img src="app/src/main/ic_launcher-web.png" width="120" height="120" alt="OmniSuite">
</p>

<h1 align="center">OmniSuite</h1>

<p align="center">
  <strong>A privacy-first, offline document suite for Android</strong>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.karnadigital.omnisuite">
    <img src="https://img.shields.io/badge/Play%20Store-Download-green?logo=googleplay" alt="Play Store">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  </a>
  <a href="https://github.com/Karna14314/Omnisuite/stargazers">
    <img src="https://img.shields.io/github/stars/Karna14314/Omnisuite?style=flat&color=yellow" alt="GitHub Stars">
  </a>
  <a href="https://github.com/Karna14314/Omnisuite/forks">
    <img src="https://img.shields.io/github/forks/Karna14314/Omnisuite?style=flat&color=blue" alt="GitHub Forks">
  </a>
  <a href="https://github.com/Karna14314/Omnisuite/issues">
    <img src="https://img.shields.io/github/issues/Karna14314/Omnisuite?style=flat&color=red" alt="GitHub Issues">
  </a>
  <a href="https://github.com/Karna14314/Omnisuite/releases">
    <img src="https://img.shields.io/github/v/release/Karna14314/Omnisuite?include_prereleases" alt="Latest Release">
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android" alt="Platform">
  <img src="https://img.shields.io/github/last-commit/Karna14314/Omnisuite?style=flat&color=orange" alt="Last Commit">
</p>

---

## Get it on Android

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.karnadigital.omnisuite">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80" alt="Get it on Google Play">
  </a>
</p>

> Offline · Privacy-first · No account required

---

## Features

### Document Viewers

- **PDF Viewer** — High-performance rendering, text search, page jump, zoom
- **Word Viewer (`.docx`, `.doc`)** — In-memory parsing and styled rendering with Apache POI
- **Excel Viewer (`.xlsx`, `.xls`)** — Grid rendering with formula evaluation and cell editing
- **Slides Viewer (`.pptx`, `.ppt`)** — Slide layouts with presentation controls
- **Text Editor (`.txt`)** — Wrapped editing with standard file writes
- **CSV Editor** — Offline parsing (quotes, commas, line-breaks) with grid editing
- **Image Viewer** — Zoomable viewer (JPG, PNG, WEBP, GIF, BMP) with metadata inspection
- **Archive Viewer** — Browse ZIP contents without extracting

### PDF Toolkit

- **Merge & Split** — Combine files or extract page ranges
- **Compress** — Reduce file size while keeping quality
- **Encrypt & Decrypt** — Password-protect files with PDFBox
- **Watermark** — Text stamping with rotation
- **Header & Footer** — Page numbering and document branding
- **Forms** — Fill PDF forms on the go
- **Signatures** — Vector signature-pad capture and signing
- **Page Tools** — Reorder, rotate, extract, delete pages
- **PDF to Images / Text / Word** — Offline export pipelines

### Office Conversion

- **DOCX / XLSX / PPTX to PDF** — Offline A4 conversion
- **Text extraction** — DOCX, XLSX, PPTX to plain text
- **HTML / Markdown to PDF** — Direct document export
- **Images to PDF** — Layout-aware gallery export

### Image Lab & Scanning

- **OCR Text Recognition** — On-device ML Kit extraction from photos
- **Smart Scanner** — Edge detection with CameraX crop tools
- **Image Editor** — Compress, crop, format transcode (JPEG, PNG, WEBP)
- **QR & Barcode** — 11-payload QR generator plus gallery scanning

### File Management

- **4-tab workspace** — Home, Tools Hub, File Browser, History
- **Offline history logs** — Room-backed recent-file tracking
- **Storage Access Framework** — Scoped, sandbox-safe file access

---

## Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 100% |
| **UI Framework** | Jetpack Compose (Material Design 3) |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Dagger Hilt |
| **Database** | Room |
| **Preferences** | DataStore |
| **PDF Processing** | PdfBox-Android, Android PdfRenderer |
| **Office Documents** | Apache POI |
| **OCR & Scanning** | Google ML Kit, ZXing, CameraX |
| **Images** | Coil |
| **Archives** | Zip4j |
| **Async** | Coroutines & Flow |
| **Build** | Gradle + Kapt |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 30+

### Build

```bash
# Clone the repository
git clone https://github.com/Karna14314/Omnisuite.git
cd Omnisuite

# Debug APK
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Release AAB (version injected by CI)
./gradlew bundleRelease -PAPP_VERSION_CODE=3 -PAPP_VERSION_NAME=1.0.2

# Release APK (attached to GitHub Releases)
./gradlew assembleRelease -PAPP_VERSION_CODE=3 -PAPP_VERSION_NAME=1.0.2
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Download

| Platform | Link | Notes |
|----------|------|-------|
| Google Play | [Install](https://play.google.com/store/apps/details?id=com.karnadigital.omnisuite) | Internal track, auto-updates |
| GitHub Releases | [Download APK](https://github.com/Karna14314/Omnisuite/releases) | Signed release APK, manual install |

---

## Release Process

Push to `main` triggers `.github/workflows/deploy.yml`:

1. Computes `versionCode = github.run_number + 2`, `versionName = 1.0.{code}`
2. Builds signed release AAB + APK (secrets: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
3. Uploads the AAB to the Play Store internal track
4. Attaches the APK to a GitHub Release

Signing keys are never committed. See `.gitignore` and `CLAUDE.md`.

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request against `main`

See [open issues](https://github.com/Karna14314/Omnisuite/issues) for feature requests and bug reports.

---

## Maintainer

**Narisetti Chaitanya Naidu**
GitHub: [@Karna14314](https://github.com/Karna14314)

---

## License

Copyright (c) 2026 Karna Digital

Licensed under the MIT License.
See [LICENSE](LICENSE) for full text. Third-party attributions in [NOTICE](NOTICE).
