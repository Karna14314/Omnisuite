# Contributing to OmniSuite

## Setup

1. Install JDK 17 and the Android SDK.
2. Clone and build: `./gradlew assembleDebug`.

## Ground rules

1. Minimal diffs. Touch only what the task needs.
2. Match existing style (Compose + Hilt + Room, MVVM).
3. No speculative features or refactors of unrelated code.
4. Never commit secrets: `*.jks`, `keystore.properties`, `play-store-key.json`.

## Workflow

1. Open an issue or pick an existing one.
2. Create a branch from `main`.
3. Make the change with a verify step:
   - UI/logic: `./gradlew testDebugUnitTest`
   - Compile check: `./gradlew compileDebugKotlin`
   - Full build: `./gradlew assembleDebug`
4. Open a PR against `main` describing what changed and how it was verified.

## Release

Maintainers only. Push to `main` runs `.github/workflows/deploy.yml`, which computes `versionCode = github.run_number + 2` and uploads the AAB to the Play Store internal track.
