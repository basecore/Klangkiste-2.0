# KlangKiste 2.0 – Feature-Matrix

Native implementation status means source code is present, not that it has passed an Android Studio/Actions build or device test.

| PWA feature | Native project status | Notes |
|---|---|---|
| Offline library | Room metadata; SAF URI references or app-private copies | Audio is not a Room BLOB |
| PWA backup | FULL_BACKUP_V1 and legacy JSON import/export | Binary audio/cover/background and audited PWA CUE data are not in the PWA JSON; relink media |
| Safe/manual import | Compose preview, SAF individual/multi-file picker, filters, candidate selection | Long imports need device testing |
| Smart/flat folders and selected files | SAF tree/multi-file scanning and grouping | Test nested folders and duplicate names |
| Covers/library | Compose grid/list, cover, info, age/genre/recent filters | Original KlangKiste assets retained |
| Player/resume | Media3 service, progress storage, playlist/CUE seeking | No playback-speed control; Fairphone audio test pending |
| Sleep | Service timer, sleep view, black overlay | Visual overlay is not a device power-off |
| OHRKA | HTTPS metadata catalog and file relinking | Audio is a separate offline import |
| Statistics | Room events, day/week/all, recent timeline | Test crash and pause accounting |
| NFC | Android reader mode and UID binding | Foreground/unlocked behavior; physical tags pending |
| Child/parent | Immersive view, five-tap exit, App-Pinning entry point | Not a guaranteed kiosk; Device Owner may be needed |
| TTS | German voice availability check | No English voice is used for German announcements |
| GitHub Actions | Debug APK build and artifact upload workflow | Not run yet; no successful build is claimed |
| Pi/GPIO/MuPiHAT | Not included | Not smartphone functions |
