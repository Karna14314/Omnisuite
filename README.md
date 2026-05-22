# OmniSuite

OmniSuite is a fully offline, high-performance productivity suite for Android. It supports advanced document viewing and editing (PDF, DOCX, XLSX, PPTX, TXT), local image manipulation tools, structural PDF tools, on-demand ML Kit OCR, and digital signing/watermarking utilities—operating 100% locally with a strict release budget of **under 30MB**.

---

## 🛠️ Project Setup & Gradle Configuration

To ensure smooth, consistent compilation without version mismatches or dependency issues:

### Portable Gradle Wrapper Standardization

Instead of relying on globally installed Gradle instances, OmniSuite uses a portable project-level Gradle wrapper. You can reuse this same standard configuration in any of your other sibling projects:

#### Step-by-Step Wrapper Setup:

1. **Navigate to your other project's root directory:**
   ```powershell
   cd C:\path\to\your\other\project
   ```

2. **Check for an existing wrapper:**
   - If `gradlew.bat` exists, skip to **Step 4**.
   - If not, proceed to **Step 3**.

3. **Generate the wrapper matching Gradle 8.11.1:**
   ```powershell
   gradle wrapper --gradle-version=8.11.1
   ```
   This generates the following files:
   - `gradle/wrapper/gradle-wrapper.jar`
   - `gradle/wrapper/gradle-wrapper.properties`
   - `gradlew` (Unix script)
   - `gradlew.bat` (Windows script)

4. **Verify the configuration:**
   ```powershell
   cat gradle\wrapper\gradle-wrapper.properties
   ```
   Ensure the distribution URL is targeted correctly:
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
   ```

5. **Run build tasks locally:**
   ```powershell
   .\gradlew.bat build
   ```
   Or:
   ```powershell
   gradlew build
   ```

---

