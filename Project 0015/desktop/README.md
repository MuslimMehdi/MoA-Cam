# MoA Cam Desktop
Cross-platform Windows/Linux Qt desktop controller and monitor.

It receives the Android prototype's MJPEG stream, shows the camera feed, provides production controls, can record the processed desktop feed, and can publish frames through pyvirtualcam when a supported virtual-camera backend is installed.

Run:
`pip install -r requirements.txt`
`python moa_cam.py`

## Distribution
Use `build/build-windows.ps1` on Windows or `build/build-linux.sh` on Linux. The desktop UI is intended to be the Studio/Webcam control surface; the native virtual-camera backend is a separate platform component.
