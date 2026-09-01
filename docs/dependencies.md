# OmniSuite — Dependency Reference

OmniSuite enforces strict size limits and only allows offline libraries. This document maps out all third-party dependencies, versions, and associated compiler/minify keep rules.

**Last Updated:** 2026-09-01
**Total Dependencies:** 35 declarations

---

## 📦 Dependency Catalog (`app/build.gradle.kts`)

All coordinates and versions are configured inside **`app/build.gradle.kts`**:

### 1. Document & PDF Engines
| Dependency | Version | Purpose |
|---|---|---|
| `org.apache.poi:poi-ooxml` | 5.2.5 | Primary parser for Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) structures |
| `org.apache.poi:poi-scratchpad` | 5.2.5 | Legacy Word (`.docx`) and other document format support |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | Lightweight PDFBox port for Android. Handles splitting, merging, encryption, watermarking, signatures |

### 2. Vision & Camera Engines
| Dependency | Version | Purpose |
|---|---|---|
| `com.google.zxing:core` | 3.5.3 | QR code and 1D barcode generation offline |
| `com.google.mlkit:barcode-scanning` | 17.2.0 | QR/Barcode camera viewfinder scanning |
| `com.google.android.gms:play-services-mlkit-document-scanner` | 16.0.0 | Document edge-detection and perspective correction |
| `com.google.android.gms:play-services-mlkit-text-recognition` | 19.0.0 | Offline OCR text extraction |
| `androidx.camera:camera-core` | 1.3.4 | CameraX core |
| `androidx.camera:camera-camera2` | 1.3.4 | Camera2 implementation |
| `androidx.camera:camera-lifecycle` | 1.3.4 | Lifecycle-aware camera |
| `androidx.camera:camera-view` | 1.3.4 | Camera preview view |

### 3. Core Android, Compose & UI Libraries
| Dependency | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | Kotlin extensions |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.3 | Lifecycle runtime |
| `androidx.activity:activity-compose` | 1.9.0 | Compose activity integration |
| `androidx.compose:compose-bom` | 2024.06.00 | Compose BOM |
| `androidx.compose.material3` | - | Material 3 design |
| `androidx.compose.material:material-icons-extended` | - | Extended Material icons |
| `androidx.navigation:navigation-compose` | 2.7.7 | Navigation framework |
| `io.coil-kt:coil-compose` | 2.6.0 | Image loading |
| `io.coil-kt:coil-svg` | 2.6.0 | SVG image loading |
| `androidx.documentfile:documentfile` | 1.0.1 | SAF tree traversal |

### 4. Injection & Database
| Dependency | Version | Purpose |
|---|---|---|
| `com.google.dagger:hilt-android` | 2.51.1 | Dependency injection |
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | Hilt + Navigation |
| `androidx.room:room-runtime` | 2.6.1 | SQLite database |
| `androidx.room:room-ktx` | 2.6.1 | Room Kotlin extensions |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Preferences storage |

### 5. ZIP Encryption
| Dependency | Version | Purpose |
|---|---|---|
| `net.lingala.zip4j:zip4j` | 2.11.5 | Password-protected ZIP creation and extraction |

### 6. Testing
| Dependency | Version | Purpose |
|---|---|---|
| `junit:junit` | 4.13.2 | Unit testing |
| `androidx.test.ext:junit` | 1.1.5 | Android test extensions |
| `androidx.test.espresso:espresso-core` | 3.5.1 | UI testing |

---

## 🛡️ ProGuard & R8 Optimization Rules (`proguard-rules.pro`)

Minification can aggressively strip reflection calls used inside Apache POI, PDFBox, or Room. The configurations preserve these symbols:

### 1. Apache POI & XMLBeans
```proguard
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn java.awt.**
```

### 2. PDFBox Android
```proguard
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
```

### 3. Hilt & Room Database
```proguard
-keep class * extends androidx.room.RoomDatabase
-keep class dagger.hilt.** { *; }
```
