# MoA Cam engineering roadmap

## Current source drop
Android Camera2 + standalone recording + external input discovery + MJPEG stream; PySide6 Windows/Linux monitor/controller + processing hooks + pyvirtualcam output.

## Production transport
Replace MJPEG with H.264/HEVC hardware encoding on Android and USB/ADB transport. Decode with Media Foundation hardware acceleration on Windows and FFmpeg/GStreamer VAAPI/NVDEC where available on Linux.

## Native virtual camera
Windows: Media Foundation virtual camera media source. Linux: V4L2/v4l2loopback or PipeWire camera portal integration.

## Cinema processing
Add GPU shader pipeline for crop/reframe, stabilization, denoise, sharpening, LUT preview, 10-bit/Log preservation where source supports it, scopes, false color and HDR handling.

## Audio
For studio mode, stream synchronized PCM/AAC from the phone's selected AudioDeviceInfo input. For solo mode, Android MediaRecorder/AudioRecord handles external USB interfaces where the device exposes them. Hardware preamp gain is only controllable when the Android audio HAL/interface exposes it; otherwise MoA Cam uses a clearly-labelled digital gain stage.
