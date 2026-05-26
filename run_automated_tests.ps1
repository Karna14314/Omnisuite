Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "      OMNISUITE QUALITY ASSURANCE AUTOMATED TESTS" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Stop"

# Step 1: Kotlin Syntax Compilation Check
Write-Host "[1/3] Running Kotlin Syntax Compilation check..." -ForegroundColor Yellow
& .\gradlew.bat compileDebugKotlin --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Kotlin compilation failed!" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: Kotlin compilation checks passed." -ForegroundColor Green
Write-Host ""

# Step 2: Run Unit Tests
Write-Host "[2/3] Executing JVM Unit Tests..." -ForegroundColor Yellow
& .\gradlew.bat testDebugUnitTest --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: JVM Unit Tests failed!" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: All Unit Tests passed successfully." -ForegroundColor Green
Write-Host ""

# Step 3: Assemble Debug APK
Write-Host "[3/3] Assembling Debug APK Binary..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: APK assembly failed!" -ForegroundColor Red
    exit 1
}
Write-Host "SUCCESS: Debug APK assembled at app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Green
Write-Host ""

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "             ALL AUTOMATED QUALITY CHECKS PASSED!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
