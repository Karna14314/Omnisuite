# ⚠️ PORTING REFERENCE DOCUMENT ⚠️

**This document is exclusively for porting OmniSuite to Flutter Multiplatform.**
**It is NOT intended to help modify the current Android codebase.**
**All information here is for reference when building the new Flutter app.**

---

# OmniSuite → Flutter Multiplatform Porting Reference

**Version:** 1.0  
**Date:** 2026-09-02  
**Source:** OmniSuite Android (146 Kotlin files, 86+ tools)  
**Target:** Flutter (iOS, Android, Windows, Linux, Web, macOS)

---

## 1. Project Structure Mapping

### Android → Flutter Directory Structure

```
Android (Current)                    Flutter (Target)
─────────────────────────────────────────────────────────────────
app/src/main/java/...               lib/
├── core/                           ├── core/
│   ├── engine/                     │   ├── engine/
│   │   ├── document/               │   │   ├── pdf_engine.dart
│   │   ├── image/                  │   │   ├── office_engine.dart
│   │   └── utility/                │   │   └── image_engine.dart
│   ├── model/                      │   ├── model/
│   ├── repository/                 │   ├── repository/
│   └── util/                       │   └── di/
├── feature/                        ├── features/
│   ├── pdf_tools/                  │   ├── pdf_tools/
│   ├── viewer/                     │   ├── conversions/
│   ├── tools/                      │   ├── image_tools/
│   ├── utility/                    │   ├── archive_tools/
│   └── home/                       │   └── utility_tools/
├── di/                             └── ui/
├── ui/                                 ├── components/
│   ├── component/                      ├── theme/
│   ├── navigation/                     └── navigation/
│   └── theme/
└── MainActivity.dart                └── main.dart
```

---

## 2. Dependency Mapping

### Core Libraries

| Android Dependency | Flutter Package | Purpose |
|-------------------|-----------------|---------|
| `org.apache.poi:poi-ooxml` | `excel: ^4.0.0` | Excel read/write |
| `org.apache.poi:poi-scratchpad` | `docx_to_text: ^0.1.0` | Word text extraction |
| `com.tom-roush:pdfbox-android` | `pdf: ^3.10.0` | PDF creation |
| `com.tom-roush:pdfbox-android` | `syncfusion_flutter_pdf` | Advanced PDF ops |
| `com.google.zxing:core` | `qr_flutter: ^4.0.0` | QR generation |
| `com.google.mlkit:barcode-scanning` | `mobile_scanner: ^5.0.0` | Barcode scanning |
| `com.google.mlkit:text-recognition` | `google_ml_kit: ^0.16.0` | OCR |
| `androidx.camera:camera-*` | `camera: ^0.10.0` | Camera access |
| `net.lingala.zip4j:zip4j` | `archive: ^3.4.0` | ZIP/TAR operations |
| `androidx.room:room-*` | `hive: ^2.0.0` | Local database |
| `com.google.dagger:hilt-android` | `get_it: ^7.0.0` | Dependency injection |
| `androidx.datastore` | `shared_preferences: ^2.0.0` | Preferences |
| `io.coil-kt:coil-compose` | `cached_network_image: ^3.0.0` | Image loading |
| `androidx.navigation:navigation-compose` | `go_router: ^14.0.0` | Navigation |

### Web-Specific Libraries (JS Interop)

| Android Dependency | Web Library | Access Via |
|-------------------|-------------|------------|
| Apache POI | SheetJS (xlsx) | `dart:js_interop` |
| PDFBox | PDF.js | `dart:js_interop` |
| ML Kit OCR | Tesseract.js | `dart:js_interop` |
| CameraX | getUserMedia | `dart:html` |
| ZXing | jsQR | `dart:js_interop` |
| zip4j | JSZip | `dart:js_interop` |

---

## 3. Feature Porting Guide

### 3.1 PDF Tools (56 tools)

#### PDF Merge
```kotlin
// Android (Reference)
// PdfToolsRepository.kt
suspend fun mergePdfs(uris: List<Uri>): Result<Uri> {
    PDDocument.load(file).use { doc ->
        val merger = PDFMergerUtility()
        // merge logic
    }
}
```

```dart
// Flutter (Target)
// lib/core/engine/pdf_engine.dart
Future<Uint8List> mergePdfs(List<Uint8List> files) async {
  final pdfDoc = pw.Document();
  for (final file in files) {
    final pages = PdfDocument.openData(file);
    for (var i = 0; i < pages.pageCount; i++) {
      pdfDoc.addPage(pages.getPage(i + 1));
    }
  }
  return await pdfDoc.save();
}
```

#### PDF Split
```dart
// Flutter implementation
Future<List<Uint8List>> splitPdf(Uint8List input, List<List<int>> ranges) async {
  final document = PdfDocument.openData(input);
  final results = <Uint8List>[];
  
  for (final range in ranges) {
    final newDoc = pw.Document();
    for (var i = range[0]; i <= range[1]; i++) {
      newDoc.addPage(document.getPage(i + 1));
    }
    results.add(await newDoc.save());
  }
  return results;
}
```

#### PDF Compress
```dart
Future<Uint8List> compressPdf(Uint8List input, int quality) async {
  final document = PdfDocument.openData(input);
  // Re-encode images at lower quality
  // Rebuild PDF with compressed assets
  return await document.save();
}
```

#### PDF Encrypt
```dart
Future<Uint8List> encryptPdf(Uint8List input, String password) async {
  final document = PdfDocument.openData(input);
  // Apply AES-256 encryption
  final encrypted = await document.save(
    options: PdfEncryptOptions(
      password: password,
      algorithm: PdfEncryptionAlgorithm.aes256,
    ),
  );
  return encrypted;
}
```

#### PDF to Images
```dart
Future<List<Uint8List>> pdfToImages(Uint8List input) async {
  final document = PdfDocument.openData(input);
  final images = <Uint8List>[];
  
  for (var i = 0; i < document.pageCount; i++) {
    final page = document.getPage(i + 1);
    final image = await page.render(
      width: page.width * 2,
      height: page.height * 2,
    );
    images.add(await image.toPng());
  }
  return images;
}
```

#### PDF Watermark
```dart
Future<Uint8List> addWatermark(Uint8List input, String text) async {
  final document = PdfDocument.openData(input);
  
  for (var i = 0; i < document.pageCount; i++) {
    final page = document.getPage(i + 1);
    page.graphics.drawString(
      text,
      PdfStandardFont(PdfFontFamily.helvetica, 40),
      brush: PdfBrushes.gray,
      format: PdfStringFormat(alignment: PdfAlignment.center),
    );
  }
  return await document.save();
}
```

#### PDF Page Numbers
```dart
Future<Uint8List> addPageNumbers(Uint8List input, int startNumber) async {
  final document = PdfDocument.openData(input);
  
  for (var i = 0; i < document.pageCount; i++) {
    final page = document.getPage(i + 1);
    final pageNum = (startNumber + i).toString();
    page.graphics.drawString(
      pageNum,
      PdfStandardFont(PdfFontFamily.helvetica, 12),
      brush: PdfBrushes.black,
      bounds: Rect.fromLTWH(page.width / 2 - 10, page.height - 30, 20, 20),
    );
  }
  return await document.save();
}
```

---

### 3.2 Document Conversions

#### DOCX to PDF
```dart
Future<Uint8List> convertDocxToPdf(Uint8List input) async {
  // Extract text from DOCX
  final docxBytes = input;
  final archive = ZipDecoder().decodeBytes(docxBytes);
  final documentXml = archive.findFile('word/document.xml');
  final text = parseDocxXml(documentXml!.content as Uint8List);
  
  // Create PDF from extracted text
  final pdfDoc = pw.Document();
  pdfDoc.addPage(pw.Page(
    build: (context) => pw.Text(text),
  ));
  return await pdfDoc.save();
}
```

#### XLSX to PDF
```dart
Future<Uint8List> convertXlsxToPdf(Uint8List input) async {
  final bytes = input;
  final excel = Excel.decodeBytes(bytes);
  final pdfDoc = pw.Document();
  
  for (final table in excel.tables.keys) {
    final sheet = excel.tables[table]!;
    pdfDoc.addPage(pw.Page(
      build: (context) => pw.Table.fromTextArray(
        data: sheet.rows.map((row) => 
          row.map((cell) => cell?.value?.toString() ?? '').toList()
        ).toList(),
      ),
    ));
  }
  return await pdfDoc.save();
}
```

#### PDF to DOCX
```dart
Future<Uint8List> convertPdfToDocx(Uint8List input) async {
  final document = PdfDocument.openData(input);
  final text = StringBuffer();
  
  for (var i = 0; i < document.pageCount; i++) {
    final page = document.getPage(i + 1);
    text.append(page.text);
  }
  
  // Create DOCX from text
  return await createDocxFromText(text.toString());
}
```

#### Markdown to PDF
```dart
Future<Uint8List> convertMarkdownToPdf(Uint8List input) async {
  final mdText = utf8.decode(input);
  final html = markdownToHtml(mdText);
  final pdfDoc = pw.Document();
  
  // Parse HTML and create PDF
  pdfDoc.addPage(pw.Page(
    build: (context) => pw.Markdown(mdText),
  ));
  return await pdfDoc.save();
}
```

---

### 3.3 Image Tools

#### Image Compress
```dart
Future<Uint8List> compressImage(Uint8List input, int quality) async {
  final image = img.decodeImage(input);
  if (image == null) throw Exception('Failed to decode image');
  
  final compressed = img.encodeJpg(image, quality: quality);
  return Uint8List.fromList(compressed);
}
```

#### Image Resize
```dart
Future<Uint8List> resizeImage(Uint8List input, int width, int height) async {
  final image = img.decodeImage(input);
  if (image == null) throw Exception('Failed to decode image');
  
  final resized = img.copyResize(image, width: width, height: height);
  return Uint8List.fromList(img.encodePng(resized));
}
```

#### Image Filter
```dart
Future<Uint8List> applyFilter(Uint8List input, String filterType) async {
  final image = img.decodeImage(input);
  if (image == null) throw Exception('Failed to decode image');
  
  img.Image filtered;
  switch (filterType) {
    case 'GRAYSCALE':
      filtered = img.grayscale(image);
      break;
    case 'SEPIA':
      filtered = img.sepia(image);
      break;
    case 'INVERT':
      filtered = img.invert(image);
      break;
    default:
      filtered = image;
  }
  return Uint8List.fromList(img.encodePng(filtered));
}
```

#### Image Stitch
```dart
Future<Uint8List> stitchImagesVertically(List<Uint8List> inputs) async {
  final images = inputs.map((b) => img.decodeImage(b)!).toList();
  final totalHeight = images.fold<int>(0, (sum, img) => sum + img.height);
  final maxWidth = images.fold<int>(0, (max, img) => img.width > max ? img.width : max);
  
  final stitched = img.Image(width: maxWidth, height: totalHeight);
  var yOffset = 0;
  
  for (final image in images) {
    img.compositeImage(stitched, image, dstY: yOffset);
    yOffset += image.height;
  }
  
  return Uint8List.fromList(img.encodeJpg(stitched));
}
```

---

### 3.4 Archive Tools

#### ZIP Create
```dart
Future<Uint8List> createZip(List<Uint8List> files, List<String> names) async {
  final archive = Archive();
  
  for (var i = 0; i < files.length; i++) {
    final file = ArchiveFile(names[i], files[i].length, files[i]);
    archive.addFile(file);
  }
  
  final zipData = ZipEncoder().encode(archive);
  return Uint8List.fromList(zipData!);
}
```

#### ZIP Extract
```dart
Future<List<ExtractedFile>> extractZip(Uint8List input) async {
  final archive = ZipDecoder().decodeBytes(input);
  final files = <ExtractedFile>[];
  
  for (final file in archive.files) {
    if (file.isFile) {
      files.add(ExtractedFile(
        name: file.name,
        data: file.content as Uint8List,
      ));
    }
  }
  return files;
}
```

#### Password ZIP
```dart
Future<Uint8List> createPasswordZip(
  List<Uint8List> files, 
  List<String> names,
  String password,
) async {
  // Use encrypt package for AES encryption
  final archive = Archive();
  for (var i = 0; i < files.length; i++) {
    archive.addFile(ArchiveFile(names[i], files[i].length, files[i]));
  }
  
  final zipData = ZipEncoder().encode(archive);
  final encrypted = await encryptData(Uint8List.fromList(zipData!), password);
  return encrypted;
}
```

---

### 3.5 Utility Tools

#### QR Generate
```dart
Future<Uint8List> generateQrCode(String content) async {
  final qrCode = QrCode.fromData(
    data: content,
    errorCorrectLevel: QrErrorCorrectLevel.M,
  );
  final qrImage = QrImageView(
    data: content,
    version: QrVersions.auto,
    size: 512,
  );
  // Convert to image bytes
  return await qrImage.toImageAsBytes();
}
```

#### File Checksum
```dart
Future<String> calculateChecksum(Uint8List input, String algorithm) async {
  switch (algorithm) {
    case 'MD5':
      return md5.convert(input).toString();
    case 'SHA-1':
      return sha1.convert(input).toString();
    case 'SHA-256':
      return sha256.convert(input).toString();
    case 'SHA-512':
      return sha512.convert(input).toString();
    default:
      return sha256.convert(input).toString();
  }
}
```

#### File Encrypt (AES-256)
```dart
Future<Uint8List> encryptFile(Uint8List input, String password) async {
  final key = Key.fromUtf8(password.padRight(32, '0').substring(0, 32));
  final iv = IV.fromLength(16);
  final encrypter = Encrypter(AES(key, mode: AESMode.cbc));
  
  final encrypted = encrypter.encryptBytes(input, iv: iv);
  return encrypted.bytes;
}
```

#### Unit Converter
```dart
double convertUnit(double value, String from, String to, String category) {
  final converters = {
    'Length': _convertLength,
    'Weight': _convertWeight,
    'Temperature': _convertTemperature,
    'Area': _convertArea,
    'Volume': _convertVolume,
    'Speed': _convertSpeed,
    'Time': _convertTime,
    'Data': _convertData,
  };
  
  return converters[category]?.call(value, from, to) ?? value;
}
```

---

## 4. Web-Specific Implementations

### 4.1 PDF Processing (PDF.js)

```dart
// lib/web/js/pdf_js_processor.dart
@JS('pdfjsLib')
library pdf_js;

import 'package:js/js.dart';

@JS('getDocument')
external Promise<PdfJsDocument> getDocument(Uint8List data);

@JS('PdfJsDocument')
class PdfJsDocument {
  external int get numPages;
  external Promise<PdfJsPage> getPage(int num);
}

@JS('PdfJsPage')
class PdfJsPage {
  external Promise<PdfJsTextContent> getTextContent();
  external PdfJsViewport getViewport(double scale);
}

// Usage
Future<String> extractTextFromPdf(Uint8List input) async {
  final doc = await promiseToFuture(getDocument(input));
  final text = StringBuffer();
  
  for (var i = 1; i <= doc.numPages; i++) {
    final page = await promiseToFuture(doc.getPage(i));
    final content = await promiseToFuture(page.getTextContent());
    text.append(content.items.map((item) => item.str).join(' '));
  }
  
  return text.toString();
}
```

### 4.2 Excel Processing (SheetJS)

```dart
// lib/web/js/sheet_js_processor.dart
@JS('XLSX')
library sheet_js;

import 'package:js/js.dart';

@JS('read')
external JsObject read(Uint8List data, JsObject options);

@JS('utils.sheet_to_json')
external List<JsObject> sheetToJson(JsObject sheet);

// Usage
Future<List<List<dynamic>>> readExcel(Uint8List input) async {
  final data = read(input, jsify({'type': 'array'}));
  final sheetName = data['SheetNames'][0];
  final sheet = data['Sheets'][sheetName];
  final json = sheetToJson(sheet);
  
  return json.map((row) => List<dynamic>.from(row.toList())).toList();
}
```

### 4.3 OCR (Tesseract.js)

```dart
// lib/web/js/tesseract_processor.dart
@JS('Tesseract')
library tesseract;

import 'package:js/js.dart';

@JS('recognize')
external Promise<TesseractResult> recognize(dynamic image, String lang);

@JS('TesseractResult')
class TesseractResult {
  external String get data;
  external String get text;
}

// Usage
Future<String> performOcr(Uint8List imageBytes) async {
  final blob = Blob([imageBytes]);
  final result = await promiseToFuture(Tesseract.recognize(blob, 'eng'));
  return result.text;
}
```

### 4.4 Web Worker Integration

```dart
// lib/web/workers/pdf_worker.dart
@JS('Worker')
class Worker {
  external factory Worker(String script);
  external void postMessage(dynamic message);
  external set onmessage(Function(dynamic) callback);
  external void terminate();
}

// Usage
Future<Uint8List> processInWorker(Uint8List input) async {
  final worker = Worker('workers/pdf_worker.js');
  final completer = Completer<Uint8List>();
  
  worker.onmessage = allowInterop((e) {
    completer.complete(e.data as Uint8List);
    worker.terminate();
  });
  
  worker.postMessage(input);
  return completer.future;
}
```

```javascript
// web/workers/pdf_worker.js
importScripts('https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js');

pdfjsLib.GlobalWorkerOptions.workerSrc = 
  'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

self.onmessage = async function(e) {
  const { action, payload } = e.data;
  
  switch (action) {
    case 'merge':
      const merged = await mergePdfs(payload.files);
      self.postMessage({ result: merged });
      break;
    case 'split':
      const split = await splitPdf(payload.file, payload.ranges);
      self.postMessage({ result: split });
      break;
    case 'extractText':
      const text = await extractText(payload.file);
      self.postMessage({ result: text });
      break;
  }
};
```

### 4.5 Web Storage (IndexedDB)

```dart
// lib/web/storage/indexed_db_storage.dart
@JS('indexedDB')
class IndexedDB {
  external static Promise<IDBDatabase> open(String name, int version);
}

@JS('IDBDatabase')
class IDBDatabase {
  external IDBObjectStore createObjectStore(String name);
  external Promise<IDBRequest> transaction(String store, String mode);
}

// Usage
class WebStorageService {
  static Future<void> saveFile(String name, Uint8List data) async {
    final db = await promiseToFuture(IndexedDB.open('omnisuite', 1));
    final tx = db.transaction('files', 'readwrite');
    final store = tx.objectStore('files');
    
    await promiseToFuture(store.put(jsify({
      'name': name,
      'data': data,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    })));
  }
  
  Future<Uint8List?> getFile(String name) async {
    final db = await promiseToFuture(IndexedDB.open('omnisuite', 1));
    final tx = db.transaction('files', 'readonly');
    final store = tx.objectStore('files');
    
    final result = await promiseToFuture(store.get(name));
    return result?['data'] as Uint8List?;
  }
}
```

### 4.6 File Download (Web)

```dart
// lib/web/utils/file_download.dart
void downloadFile(String filename, Uint8List data) {
  final blob = Blob([data]);
  final url = Url.createObjectUrlFromBlob(blob);
  
  final anchor = AnchorElement(href: url)
    ..setAttribute('download', filename)
    ..style.display = 'none';
  
  document.body!.children.add(anchor);
  anchor.click();
  anchor.remove();
  
  Url.revokeObjectUrl(url);
}
```

---

## 5. Platform Abstraction Layer

### 5.1 File System Abstraction

```dart
// lib/core/engine/platform/file_system.dart
abstract class FileSystemService {
  Future<Uint8List?> readFile(String path);
  Future<void> writeFile(String path, Uint8List data);
  Future<String> pickFile({List<String>? allowedExtensions});
  Future<String> pickSaveLocation(String suggestedName);
  Future<void> shareFile(String path, Uint8List data);
}

// Desktop implementation
class DesktopFileSystem implements FileSystemService {
  @override
  Future<String> pickFile({List<String>? allowedExtensions}) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: allowedExtensions,
    );
    return result?.files.single.path ?? '';
  }
}

// Web implementation
class WebFileSystem implements FileSystemService {
  @override
  Future<String> pickFile({List<String>? allowedExtensions}) async {
    final uploadInput = FileUploadInputElement();
    if (allowedExtensions != null) {
      uploadInput.accept = allowedExtensions.map((e) => '.$e').join(',');
    }
    uploadInput.click();
    
    final file = await uploadInput.firstFile;
    return file.name;
  }
}
```

### 5.2 Document Processor Abstraction

```dart
// lib/core/engine/platform/document_processor.dart
abstract class DocumentProcessor {
  Future<Uint8List> mergePdfs(List<Uint8List> files);
  Future<List<Uint8List>> splitPdf(Uint8List file, List<List<int>> ranges);
  Future<Uint8List> compressPdf(Uint8List file, int quality);
  Future<Uint8List> encryptPdf(Uint8List file, String password);
  Future<List<Uint8List>> pdfToImages(Uint8List file);
  Future<String> extractTextFromPdf(Uint8List file);
}

// Desktop implementation (uses syncfusion/native)
class DesktopDocumentProcessor implements DocumentProcessor {
  @override
  Future<Uint8List> mergePdfs(List<Uint8List> files) async {
    final document = PdfDocument();
    for (final file in files) {
      final source = PdfDocument(inputBytes: file);
      document.pages.addCount(source.pages.count);
      for (var i = 0; i < source.pages.count; i++) {
        document.pages[i].graphics.drawPdfTemplate(
          source.pages[i].createTemplate(),
          Offset.zero,
        );
      }
    }
    return document.saveSync();
  }
}

// Web implementation (uses PDF.js)
class WebDocumentProcessor implements DocumentProcessor {
  @override
  Future<Uint8List> mergePdfs(List<Uint8List> files) async {
    return await processInWorker(jsify({
      'action': 'merge',
      'payload': {'files': files},
    }));
  }
}
```

---

## 6. State Management (Riverpod)

```dart
// lib/core/di/providers.dart
final pdfEngineProvider = Provider<PdfEngine>((ref) {
  if (kIsWeb) {
    return WebPdfEngine();
  } else {
    return DesktopPdfEngine();
  }
});

final fileSystemProvider = Provider<FileSystemService>((ref) {
  if (kIsWeb) {
    return WebFileSystem();
  } else {
    return DesktopFileSystem();
  }
});

// lib/features/pdf_tools/providers/merge_provider.dart
final mergeProvider = StateNotifierProvider<MergeNotifier, MergeState>((ref) {
  return MergeNotifier(ref.read(pdfEngineProvider));
});

class MergeNotifier extends StateNotifier<MergeState> {
  final PdfEngine _pdfEngine;
  
  MergeNotifier(this._pdfEngine) : super(MergeState.initial());
  
  Future<void> mergePdfs(List<Uint8List> files) async {
    state = state.copyWith(isProcessing: true);
    try {
      final result = await _pdfEngine.mergePdfs(files);
      state = state.copyWith(
        isProcessing: false,
        result: result,
        isSuccess: true,
      );
    } catch (e) {
      state = state.copyWith(
        isProcessing: false,
        error: e.toString(),
      );
    }
  }
}
```

---

## 7. Navigation (GoRouter)

```dart
// lib/ui/navigation/app_router.dart
final goRouter = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const HomeScreen(),
    ),
    GoRoute(
      path: '/tools',
      builder: (context, state) => const ToolsScreen(),
      routes: [
        GoRoute(
          path: 'pdf',
          builder: (context, state) => const PdfToolsScreen(),
        ),
        GoRoute(
          path: 'conversions',
          builder: (context, state) => const ConversionsScreen(),
        ),
        GoRoute(
          path: 'image',
          builder: (context, state) => const ImageToolsScreen(),
        ),
        GoRoute(
          path: 'archive',
          builder: (context, state) => const ArchiveToolsScreen(),
        ),
        GoRoute(
          path: 'utility',
          builder: (context, state) => const UtilityToolsScreen(),
        ),
      ],
    ),
    GoRoute(
      path: '/tool/:id',
      builder: (context, state) {
        final toolId = state.pathParameters['id']!;
        return ToolDetailScreen(toolId: toolId);
      },
    ),
  ],
);
```

---

## 8. PWA Configuration

### web/manifest.json
```json
{
  "name": "OmniSuite Tools",
  "short_name": "OmniSuite",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#1a1a2e",
  "theme_color": "#16213e",
  "orientation": "portrait-primary",
  "icons": [
    {
      "src": "icons/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "icons/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

### web/index.html
```html
<!DOCTYPE html>
<html>
<head>
  <base href="$FLUTTER_BASE_HREF">
  <meta charset="UTF-8">
  <meta content="IE=Edge" http-equiv="X-UA-Compatible">
  <meta name="description" content="OmniSuite Tools - Offline Document Processing">
  
  <!-- iOS meta tags -->
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-status-bar-style" content="black">
  <meta name="apple-mobile-web-app-title" content="OmniSuite">
  
  <!-- PWA manifest -->
  <link rel="manifest" href="manifest.json">
  
  <!-- PDF.js for web -->
  <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js"></script>
  
  <!-- SheetJS for Excel -->
  <script src="https://cdn.sheetjs.com/xlsx-0.20.0/package/dist/xlsx.full.min.js"></script>
  
  <!-- Tesseract.js for OCR -->
  <script src="https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js"></script>
  
  <title>OmniSuite Tools</title>
</head>
<body>
  <script src="flutter_bootstrap.js" async></script>
</body>
</html>
```

---

## 9. Testing Strategy

```dart
// test/core/engine/pdf_engine_test.dart
void main() {
  group('PdfEngine', () {
    late PdfEngine pdfEngine;
    
    setUp(() {
      pdfEngine = DesktopPdfEngine();
    });
    
    test('mergePdfs combines multiple PDFs', () async {
      final file1 = await File('test/fixtures/sample1.pdf').readAsBytes();
      final file2 = await File('test/fixtures/sample2.pdf').readAsBytes();
      
      final result = await pdfEngine.mergePdfs([file1, file2]);
      
      expect(result, isNotEmpty());
      expect(result.length, greaterThan(file1.length));
    });
    
    test('splitPdf creates correct number of files', () async {
      final input = await File('test/fixtures/sample.pdf').readAsBytes();
      final ranges = [[0, 2], [3, 5]];
      
      final results = await pdfEngine.splitPdf(input, ranges);
      
      expect(results.length, equals(2));
    });
  });
}
```

---

## 10. Build & Deploy

### Build Commands
```bash
# Web
flutter build web --release

# Windows
flutter build windows --release

# Linux
flutter build linux --release

# iOS
flutter build ios --release

# macOS
flutter build macos --release

# All platforms
flutter build web --release && \
flutter build windows --release && \
flutter build linux --release && \
flutter build macos --release
```

### Web Deployment
```bash
# Build for web
flutter build web --release --dart-define=FLUTTER_WEB_USE_SKIA=true

# Deploy to Firebase
firebase deploy --only hosting

# Deploy to GitHub Pages
flutter build web --release --base-href="/omnisuite/"
cp -r build/web/* docs/
```

---

## 11. Porting Checklist

### Phase 1: Foundation (Weeks 1-3)
- [ ] Set up Flutter project with multiplatform targets
- [ ] Configure web dependencies (PDF.js, SheetJS, Tesseract.js)
- [ ] Implement platform abstraction layer
- [ ] Set up Riverpod state management
- [ ] Configure GoRouter navigation
- [ ] Implement theme system
- [ ] Set up dependency injection (GetIt)

### Phase 2: PDF Tools (Weeks 4-6)
- [ ] Port PDF merge functionality
- [ ] Port PDF split functionality
- [ ] Port PDF compress functionality
- [ ] Port PDF encrypt/decrypt
- [ ] Port PDF watermark
- [ ] Port PDF page numbers
- [ ] Port PDF to images conversion
- [ ] Port remaining PDF tools

### Phase 3: Conversions (Weeks 7-9)
- [ ] Port DOCX to PDF
- [ ] Port XLSX to PDF
- [ ] Port PPTX to PDF
- [ ] Port PDF to DOCX
- [ ] Port PDF to XLSX
- [ ] Port Markdown to PDF
- [ ] Port remaining conversions

### Phase 4: Image & Archive (Weeks 10-11)
- [ ] Port image compress/resize/crop
- [ ] Port image filters
- [ ] Port image stitch
- [ ] Port ZIP create/extract
- [ ] Port password ZIP
- [ ] Port file encryption

### Phase 5: Utilities (Weeks 12-13)
- [ ] Port QR/barcode generation
- [ ] Port OCR
- [ ] Port file checksum
- [ ] Port unit converter
- [ ] Port text compare

### Phase 6: Web & Polish (Weeks 14-15)
- [ ] Implement web-specific processors
- [ ] Add Web Worker support
- [ ] Implement PWA
- [ ] Cross-platform testing
- [ ] Performance optimization

---

## 12. Key Differences: Android vs Flutter

| Aspect | Android (Current) | Flutter (Target) |
|--------|-------------------|------------------|
| Language | Kotlin | Dart |
| UI Framework | Jetpack Compose | Flutter Widgets |
| State Management | StateFlow/MutableStateFlow | Riverpod/Bloc |
| DI | Hilt | GetIt/Koin |
| Database | Room | Hive/SharedPreferences |
| File I/O | java.io.File | dart:io / dart:html |
| Async | Coroutines | Future/Stream |
| Navigation | Navigation Compose | GoRouter |
| Web Support | N/A | dart:js_interop |

---

**END OF PORTING REFERENCE DOCUMENT**
