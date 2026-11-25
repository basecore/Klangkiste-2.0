# Fairphone 5 / iodéOS test plan — not yet executed

- Build/install debug APK; verify app starts without Google Play Services.
- Import MP3/M4A, multi-file folders, covers and a real PWA backup; test both SAF reference and managed-copy modes.
- Check Media3 background/lock-screen controls, Bluetooth/audio focus, progress restoration, CUE navigation and distorted/speeded audio.
- Pair real NFC tags; test known/unknown tags and note that foreground reader mode requires an awake, unlocked app.
- Run sleep timer through pause/resume; verify audio stops, local goodnight sound plays, sleep view becomes black after 60 seconds.
- Test immersive display, five-tap parent exit and Android App-Pinning. System gestures are not fully blocked by a normal app; Device Owner may be needed for kiosk mode.
- Test RHVoice English-only configuration: app must report missing German voice and must not speak German text with English voice.
- Measure battery use with keep-screen-on and face-down overlay enabled/disabled.
