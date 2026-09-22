# MoA Cam
### A Masters Of All camera system for Android + Windows + Linux

**MoA Cam** turns a capable Android phone into a controllable cinema camera and, when connected to a computer, a system webcam. The phone supplies the camera sensor/lens; the desktop can handle monitoring, processing and virtual-camera output.

## What is implemented in this source drop

### Android
- Camera2 preview
- 1080p target with supported-size fallback
- Front/rear camera switching
- Auto/manual ISO
- Auto/manual shutter time
- EV compensation
- AE lock
- Auto/continuous/manual focus
- Focus distance
- Digital zoom
- Auto/manual white balance and Kelvin where supported
- External audio-device discovery
- Preferred input device selection on supported Android versions
- Standalone video recording with microphone selection
- MJPEG network stream for desktop/OBS prototype
- Dark, touch-friendly cinema UI with Masters Of All branding

### Desktop (Windows + Linux)
- Polished Qt/PySide6 control/monitoring application
- Phone connection over Wi-Fi using the Android MJPEG stream
- Live preview
- Camera status and production dashboard
- Virtual-camera output through `pyvirtualcam` where a supported virtual-camera backend is installed
- Processing controls for crop/fit/flip and output FPS
- Windows/Linux friendly codebase

## Important hardware reality
Camera2 exposes different controls on different phones. MoA Cam queries capabilities and only applies settings the device supports. Software cannot create sensor modes, manual gain or open-gate pixels that the phone camera HAL does not expose.

The network prototype uses MJPEG for simplicity and compatibility. For a production-grade USB implementation, the next transport should be hardware H.264/HEVC over ADB/direct USB, then feed decoded frames into a native Media Foundation virtual camera on Windows and V4L2 on Linux. The desktop UI is deliberately separated from the transport so that replacement is straightforward.

## Desktop install

```bash
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux: source .venv/bin/activate
pip install -r desktop/requirements.txt
python desktop/moa_cam.py
```

Enter the phone's displayed IP address and press **Connect**. The phone and PC need to be on the same network for this prototype.

### Virtual camera
`pyvirtualcam` needs an OS virtual-camera backend. On Linux this can be V4L2/v4l2loopback. On Windows use a supported pyvirtualcam backend/virtual-camera driver. If no backend is available, MoA Cam still previews and records/exports frames normally.

## Android build
Open `android/` in Android Studio and build the APK. The project uses Camera2 directly so the pro controls aren't hidden behind a high-level camera abstraction.

## Branding
The supplied Masters Of All logo is bundled as `moa_logo.png` and is used as the app maker logo.

## Product modes
**Solo** is the standalone Android cinema-camera experience. **Studio** uses the phone as the sensor and the computer as the processing/recording machine. **Webcam** exposes the processed feed as a normal camera to applications; the Windows production backend targets the Media Foundation virtual-camera API.

The Android feature set intentionally follows the capabilities actually exposed by each device. Blackmagic Camera's published Android specifications demonstrate that a modern phone can expose cinema-oriented controls such as shutter angle, ISO, Kelvin/tint, external microphones, timecode and focus assist. citeturn0search7
