# MoA Cam release package

MoA Cam is designed as a **Blackmagic-style camera app for Android**, with a second mode that turns the phone into a computer camera.

## Modes

### SOLO — phone is the camera
Use the Android app alone for filmmaking. Camera2 controls include auto/manual exposure, ISO, shutter, EV, focus, white balance, zoom, recording, and external-input discovery. The UI is designed around cinema controls rather than a normal phone camera.

### STUDIO — phone is the sensor
The Android phone supplies the sensor/lens and the desktop handles preview, processing, recording and virtual-camera output. The desktop app is designed for Windows and Linux.

### WEBCAM — system camera
The production target is `MoA Cam Camera`, a real OS-level virtual camera. On Windows 11 this should be implemented with Microsoft's Media Foundation virtual-camera API; that API creates software camera sources that other apps can discover as camera devices. Windows 11 is the minimum supported client for that API. citeturn0search0turn0search3

## Cinema feature direction

- Shutter speed / shutter angle
- ISO auto/manual and priority modes
- WB auto/manual, Kelvin and tint
- Focus assist / peaking
- Histogram / waveform / vectorscope / false color / zebra
- Lens switching and available sensor modes
- Open-gate-like full-sensor modes where exposed by the phone
- Anamorphic de-squeeze
- LUT preview / color processing
- Timelapse and off-speed recording
- Timecode and take/scene metadata
- External microphone / USB interface selection
- 48 kHz audio where supported
- Metering and monitoring
- Hardware/software gain separation

These are deliberately aligned with features documented by Blackmagic Camera for Android, while MoA Cam remains an independent implementation. Blackmagic's current Android technical specification documents shutter angle, ISO, manual Kelvin/tint, focus assist, external microphones, 48 kHz audio, anamorphic de-squeeze, timecode and other cinema-oriented features. citeturn0search7

Android 16 also adds Camera2 hybrid auto-exposure modes, including ISO-priority and exposure-time-priority style control, which MoA Cam can expose when the phone and OS support them. citeturn0search13turn0search14

## Important build note

This environment does not contain a Windows compiler or Android SDK, so **I am not claiming that an APK/EXE binary was compiled and tested here**. The repository includes reproducible Windows, Linux and Android build scripts plus a GitHub Actions workflow that builds the three artifacts on their native CI environments.
