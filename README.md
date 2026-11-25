# KlangKiste 2.0 — native Android

Kotlin + Jetpack Compose + Room + Media3 Android Studio project. Not a WebView/PWA wrapper and not a MuPiBox port. No Google Play Services dependency.

## Included
- Native child player/library and parent administration.
- Room metadata, settings, NFC bindings, progress and statistics.
- SAF smart/flat and individual-file import preview, age/search filters, mass selection, file reference or app-private copy.
- PWA `FULL_BACKUP_V1`/legacy JSON migration and export; missing media can be relinked.
- Media3 background player/MediaSession, progress restoration, playlists/CUE seek, sleep timer.
- OHRKA online metadata catalog and separate audio relink; optional online examples.
- NFC reader mode, TTS German voice check, immersive mode and App-Pinning entry point.
- Supplied artwork retained. Sleep background runs as a WebP derivative; original PNG is in `design/source-assets/`.

See `FEATURE-MATRIX.md`, `docs/DATA-MIGRATION.md` and `docs/FAIRPHONE-5-TESTPLAN.md`.

## Build
Requirements: Android Studio with JDK 17, Android SDK platform 35 and build-tools 35.0.0. Open this root directory and sync Gradle. From a shell run `./gradlew :app:assembleDebug`. The bootstrap script uses installed Gradle or downloads Gradle 8.10.2. First download requires network access.

`.github/workflows/android-debug.yml` installs JDK 17, Gradle 8.10.2, `platform-tools`, platform 35 and build-tools 35.0.0, runs `assembleDebug` and uploads `app-debug.apk` as an Actions artifact. It sets `android-actions/setup-android@v3` with `packages: 'platform-tools'` and installs the platform/build-tools separately.

No signing key is included. Release signing must use protected GitHub Secrets. **No successful build is claimed until Android Studio or Actions has actually built it.**

## Safety and offline use
No broad storage permission is requested. SAF references depend on the saved URI grant; managed copies work offline and are removed on uninstall. A normal app cannot block every Android system gesture. App-Pinning is only a user-visible safeguard; Lock Task kiosk can require Device Owner configuration.

TTS checks for a German voice at runtime. If only English RHVoice is installed, the app reports that German speech is unavailable and does not use the English voice for German text.
