# Developer Agent Guidelines & Gradle Setup

This document provides developer agents and engineers with setup guidelines for managing compiling, packaging, and dependency execution parameters.

---

## 🛠️ Portable Gradle Wrapper Reuse & Standardization

To maintain consistency and reuse the standardized Gradle 8.11.1 configuration in sibling or downstream projects:

### Step-by-Step Wrapper Setup:

1. **Navigate to the target project's root directory:**
   ```powershell
   cd C:\path\to\your\other\project
   ```

2. **Check if a Gradle wrapper exists:**
   - If `gradlew.bat` is already present, skip to **Step 4**.
   - If not, proceed to **Step 3**.

3. **Generate the wrapper with the standard version (8.11.1):**
   ```powershell
   gradle wrapper --gradle-version=8.11.1
   ```
   This generates the portable wrapper assets:
   - `gradle-wrapper.jar` (in `gradle/wrapper/`)
   - `gradle-wrapper.properties` (in `gradle/wrapper/`)
   - `gradlew` (Linux/Mac script)
   - `gradlew.bat` (Windows script)

4. **Verify the wrapper points to Gradle 8.11.1:**
   ```powershell
   cat gradle\wrapper\gradle-wrapper.properties
   ```
   Confirm that the `distributionUrl` line matches:
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
   ```

5. **Execute commands via the local wrapper:**
   ```powershell
   .\gradlew.bat build
   ```
   Or:
   ```powershell
   gradlew build
   ```

### Benefits of the Wrapper Strategy:
- **Portable & Isolated:** Each project maintains its own isolated wrapper, removing local path dependencies or globally installed Gradle mismatches.
- **Cache-Optimized:** Reuses cached distributions under `~/.gradle/wrapper/dists/` to avoid redundant downloads.
- **Consistent Execution:** Guarantees that compilation behaves identically across local machines, build systems, and pipelines.
