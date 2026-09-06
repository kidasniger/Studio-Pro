# Studio Pro

Studio Pro is an Android audio visualizer and lyric-video application with synchronized lyrics, audio editing, effects and video export.

## Current release

- Version: 2.9.3
- Version code: 30
- Application ID: `com.kidas.studiopro`
- Compile/target SDK: 35
- Java: 17
- Gradle used by CI: 9.1.0

## Build and release

The repository uses a single GitHub Actions workflow for validation and release. The workflow performs unit tests, a debug build, a signed release build, APK verification, artifact upload, then creates or updates the `v2.9.3` GitHub release for the exact commit being built.

Release builds never fall back to the Android debug key. GitHub Actions must provide either `STUDIO_PRO_KEYSTORE_BASE64` or `STUDIO_PRO_KEYSTORE`, together with `STUDIO_PRO_KEYSTORE_PASSWORD`, `STUDIO_PRO_KEY_ALIAS` and `STUDIO_PRO_KEY_PASSWORD`.

The source package and `applicationId` are intentionally `com.kidas.studiopro`.

## Tests

Current JVM tests cover LRC parsing, word-level Whisper timing, translated word mapping and natural-language command routing. Android-specific audio processing is validated through the release build and APK checks in CI.
