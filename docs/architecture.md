# OmniSuite — Architectural Deep-Dive

OmniSuite is designed around clean architecture principles and a strict **100% offline-first** execution model. It segregates logic into distinct layers to isolate complex Java-based file parsing engines (Apache POI/PDFBox) from Jetpack Compose UI rendering layers.

---

## 🏗️ Layered Architecture Overview

The app follows a unidirectional data flow conforming to MVVM (Model-View-ViewModel):

```mermaid
graph TD
    UI[Jetpack Compose UI Layer] --> VM[ViewModels]
    VM --> Repo[RecentFileRepository]
    Repo --> DAO[RecentFileDao]
    DAO --> DB[(Room SQLite Database)]
    
    VM -.-> Engines[Core Engines Layer]
    Engines --> Cache[UriCacheUtils Cache]
    Engines --> Output[FileOutputManager Storage]
```

### 1. The Core Layer (`com.karnadigital.omnisuite.core`)
The engine room of the application, containing zero UI dependencies.
- **`core/engine/`**: Wrappers and configurations for heavy processing engines:
  - **Word/Excel/Slides converter engines** utilizing Apache POI.
  - **PDF operations engine** built on top of the PDFBox Android port.
  - **Barcode and QR code builders** using ZXing.
- **`core/repository/` & `core/model/`**: Local Room database caching file operations and view histories.
- **`core/util/`**: Low-level platform helpers for sandboxing, zoom capabilities, and theme preferences.

### 2. The Dependency Injection Layer (`com.karnadigital.omnisuite.di`)
Bootstrapped via Dagger Hilt:
- **`DatabaseModule.kt`** binds singletons for `OmniDatabase`, `RecentFileDao`, and `RecentFileRepository`.

### 3. The Feature Layer (`com.karnadigital.omnisuite.feature`)
Coordinated MVVM sub-packages separated by application modules:
- Each sub-package typically contains a Compose screen file (`*Screen.kt`) and its associated lifecycle coordinator (`*ViewModel.kt`).

### 4. Shared UI System (`com.karnadigital.omnisuite.ui`)
Contains global style configurations and reusable design blocks:
- **`ui/theme/`**: Theme tokens, typography, and AccentGlow colors.
- **`ui/component/`**: Toolkit layouts, card systems, top app bars, and switches.
- **`ui/navigation/`**: Central NavGraph routing configurations.

---

## 🔒 The Storage Access Framework (SAF) Sandboxing Cache Pattern

Since Android 11, apps run inside sandboxed Scoped Storage. This creates a friction point:
- Heavyweight document engines like **Apache POI** and **PDFBox** require literal `java.io.File` paths or standard `FileInputStream` instances to seek and write files correctly.
- Android system intents and file pickers return virtual `content://` URIs via the Storage Access Framework (SAF).

OmniSuite bypasses this restriction securely using a **Temp Cache Sandboxing Flow**:

```mermaid
sequenceDiagram
    participant UI as Compose UI / File Browser
    participant VM as ViewModel (IO Dispatcher)
    participant Cache as UriCacheUtils
    participant Eng as Document / POI Engine
    participant Out as FileOutputManager
    
    UI->>VM: Select File URI (content://...)
    VM->>Cache: cacheUri(context, uri)
    Note over Cache: Reads content stream,<br/>writes copy to context.cacheDir
    Cache-->>VM: Returns java.io.File path
    VM->>Eng: Process File (e.g., DocToPdf)
    Note over Eng: Performs offline transcode
    Eng-->>VM: Returns output byte array
    VM->>Out: saveToDefault(bytes, filename)
    Note over Out: Inserts into MediaStore Documents
    Out-->>VM: Returns new saved content:// URI
    VM->>UI: Update UI with success state
```

### Sandbox Utilities
- **[UriCacheUtils.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/UriCacheUtils.kt)**:
  - `cacheUri(context, uri)`: Safely streams bytes from the SAF Content Resolver and dumps them into a temporary file in `context.cacheDir`.
  - `clearCache(context)`: Housekeeps the cache directory to prevent storage bloat.
- **[FileOutputManager.kt](file:///c:/Users/chait/Projects/Omnisuite/app/src/main/java/com/karnadigital/omnisuite/core/util/FileOutputManager.kt)**:
  - `saveToDefault(context, bytes, filename, mimeType, subfolder)`: Uses MediaStore to insert the finalized output into `Documents/OmniSuite/<subfolder>` and returns the target `Uri`.

---

## ⚡ Threading & State Flow Architecture

All document parsing and file compression workloads are heavy on CPU and memory. OmniSuite uses strict coroutine boundaries to maintain a 60 FPS UI:

1. **ViewModels** expose state reactively using `StateFlow` (e.g., `PdfLoadState`, `ZipMakerState`).
2. **Dispatchers.IO** is explicitly invoked for all heavy I/O operations:
   ```kotlin
   suspend fun processFile(uri: Uri) = withContext(Dispatchers.IO) {
       val cachedFile = UriCacheUtils.cacheUri(context, uri)
       // perform POI or PDFBox work here
   }
   ```
3. **WeakReferences** and aggressive garbage collection triggers (`bitmap.recycle()`) are run inside views to keep memory within low-RAM device constraints.
