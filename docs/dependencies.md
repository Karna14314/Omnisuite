# OmniSuite — Dependency Reference

OmniSuite enforces strict size limits (aiming for under 30MB) and only allows offline libraries. This document maps out all third-party dependencies, versions, and associated compiler/minify keep rules.

---

## 📦 Dependency Catalog (`app/build.gradle.kts`)

All coordinates and versions are configured inside **[build.gradle.kts](file:///c:/Users/chait/Projects/Omnisuite/app/build.gradle.kts)**:

### 1. Document & PDF Engines
- **`org.apache.poi:poi-ooxml:5.2.5`**: Serves as the primary parser for Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) structures. `poi-ooxml` is used rather than standard `poi` to include schemas for modern XML formats.
- **`com.tom-roush:pdfbox-android:2.0.27.0`**: A lightweight port of Apache PDFBox specifically optimized for Android's custom JVM. Handles PDF page splitting, document merging, encryption/decryption, watermarking, and signature stamps.

### 2. Vision & Camera Engines
- **`com.google.zxing:core:3.5.3`**: Industry standard library for generating crisp QR codes and 1D barcode layouts offline.
- **`com.google.mlkit:barcode-scanning:17.2.0`**: Thin client for rapid QR/Barcode camera viewfinder scanning.
- **`com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`**: Provides perspective correction, document edge-detection, and scan enhancements natively.
- **`com.google.mlkit:text-recognition:16.0.1`**: Executes offline Optical Character Recognition (OCR) to extract text content from image sources.
- **`androidx.camera:camera-* (1.3.4)`**: Android Jetpack CameraX stack (core, camera2, lifecycle, view) providing unified hardware bindings and camera frames.

### 3. Core Android, Compose & UI Libraries
- **`androidx.compose (BOM 2024.06.00)`**: UI framework (material3, graphics, preview, tooling).
- **`androidx.navigation:navigation-compose:2.7.7`**: NavHost framework.
- **`io.coil-kt:coil-compose:2.6.0`**: Image loading and caching system.
- **`androidx.documentfile:documentfile:1.0.1`**: Simplifies SAF tree/folder traversals.

### 4. Injection & Database (Local Cache)
- **`com.google.dagger:hilt-android:2.51.1`**: Bootstrap injection framework.
- **`androidx.room:room-* (2.6.1)`**: Room SQLite database compiler and runtime bindings.

---

## 🛡️ ProGuard & R8 Optimization Rules (`proguard-rules.pro`)

Minification can aggressively strip reflection calls used inside Apache POI, PDFBox, or Room. The configurations in **[proguard-rules.pro](file:///c:/Users/chait/Projects/Omnisuite/app/proguard-rules.pro)** preserve these symbols:

### 1. Apache POI & XMLBeans
POI parses documents using reflection and dynamically generated XML classes. The following keep rules are required to prevent runtime crash loops:
```proguard
# Keep POI classes and their members
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }

# Keep openxmlformats schemas
-keep class org.openxmlformats.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# Ignore warnings for unresolved desktop/AWT classes not available on Android
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**
-dontwarn org.apache.xmlbeans.**
-dontwarn java.awt.**
```

### 2. PDFBox Android
Preserves fonts and resource loaders used to compile PDF canvas sheets:
```proguard
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

-keepclassmembers class com.tom_roush.pdfbox.pdmodel.font.PDFont {
    static <fields>;
}
```

### 3. Hilt & Room Database
Preserves generated classes, database callbacks, and dependency providers:
```proguard
# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
```
