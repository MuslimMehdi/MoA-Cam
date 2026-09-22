package org.moa.cam

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var streamInfo: TextView
    private lateinit var statusUrl: TextView
    private lateinit var manager: CameraManager
    private var cameraId = ""
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var request: CaptureRequest.Builder? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var recording = false
    private var aeLock = false
    private var lastJpeg = AtomicReference<ByteArray?>(null)
    private var streamServer: MjpegServer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var chars: CameraCharacteristics? = null
    private var selectedAudio: AudioDeviceInfo? = null

    private val shutterTimes = longArrayOf(0, 1_000_000_000L/24, 1_000_000_000L/30, 1_000_000_000L/48, 1_000_000_000L/50, 1_000_000_000L/60, 1_000_000_000L/120, 1_000_000_000L/240, 1_000_000_000L/500)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview)
        streamInfo = findViewById(R.id.streamInfo)
        statusUrl = findViewById(R.id.url)

        if (!hasPerm(Manifest.permission.CAMERA) || !hasPerm(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 42)
        } else init()

        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { if (camera == null) openCamera() }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun init() {
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = findCamera("")
        populateAudioDevices()
        streamServer = MjpegServer(8080, lastJpeg)
        streamServer!!.start()
        statusUrl.text = "http://${localIp()}:8080/stream"
        if (preview.isAvailable) openCamera()
        wireControls()
    }

    private fun wireControls() {
        val iso = findViewById<SeekBar>(R.id.iso)
        val isoLabel = findViewById<TextView>(R.id.isoLabel)
        iso.setOnSeekBarChangeListener(sb { v ->
            val r = chars?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return@sb
            if (v == 0) {
                request?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                isoLabel.text = "ISO  AUTO"
            } else {
                request?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val value = v.coerceIn(r.lower, r.upper)
                request?.set(CaptureRequest.SENSOR_SENSITIVITY, value)
                isoLabel.text = "ISO  $value"
            }
            push()
        })

        val shutter = findViewById<SeekBar>(R.id.shutter)
        val shutterLabel = findViewById<TextView>(R.id.shutterLabel)
        shutter.setOnSeekBarChangeListener(sb { v ->
            if (v == 0) {
                request?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                shutterLabel.text = "SHUTTER  AUTO"
            } else {
                request?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                request?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterTimes[v.coerceIn(1, shutterTimes.lastIndex)])
                shutterLabel.text = "SHUTTER  1/${(1_000_000_000.0 / shutterTimes[v]).roundToInt()}"
            }
            push()
        })

        findViewById<SeekBar>(R.id.ev).setOnSeekBarChangeListener(sb { v ->
            val r = chars?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return@sb
            request?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (r.lower + v - 12).coerceIn(r.lower, r.upper))
            push()
        })

        findViewById<Button>(R.id.aeLock).setOnClickListener {
            aeLock = !aeLock
            request?.set(CaptureRequest.CONTROL_AE_LOCK, aeLock)
            (it as Button).text = "AE LOCK  ${if (aeLock) "ON" else "OFF"}"
            push()
        }

        findViewById<SeekBar>(R.id.focus).setOnSeekBarChangeListener(sb { v ->
            val min = chars?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            if (v == 0 || min == null) {
                request?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                findViewById<TextView>(R.id.focusLabel).text = "FOCUS  AUTO"
            } else {
                request?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                request?.set(CaptureRequest.LENS_FOCUS_DISTANCE, min * (1f - v / 1000f))
                findViewById<TextView>(R.id.focusLabel).text = "FOCUS  MANUAL ${v / 10}%"
            }
            push()
        })

        findViewById<SeekBar>(R.id.wb).setOnSeekBarChangeListener(sb { v ->
            if (v == 0) {
                request?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                findViewById<TextView>(R.id.wbLabel).text = "WB  AUTO"
            } else {
                // A useful approximate Kelvin-to-gain curve for supported Camera2 manual color correction.
                val k = v.coerceIn(2000, 8000).toFloat()
                val red = (6500f / k).coerceIn(0.7f, 2.5f)
                val blue = (k / 6500f).coerceIn(0.7f, 2.5f)
                request?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                request?.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                request?.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(red, 1f, 1f, blue))
                findViewById<TextView>(R.id.wbLabel).text = "WB  ${k.roundToInt()}K"
            }
            push()
        })

        findViewById<Button>(R.id.record).setOnClickListener { if (recording) stopRecording() else startRecording() }
        findViewById<Button>(R.id.switchCamera).setOnClickListener {
            closeCamera()
            cameraId = findCamera(cameraId)
            openCamera()
        }
    }

    private fun populateAudioDevices() {
        val spinner = findViewById<Spinner>(R.id.audioDevice)
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val names = mutableListOf("Default / system selected")
        names += inputs.map { "${it.productName} • ${audioTypeName(it.type)}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedAudio = if (pos == 0) null else inputs.getOrNull(pos - 1)
            }
        }
    }

    private fun audioTypeName(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        else -> "Input"
    }

    private fun findCamera(current: String): String {
        val ids = manager.cameraIdList
        if (current.isNotEmpty()) {
            val old = manager.getCameraCharacteristics(current).get(CameraCharacteristics.LENS_FACING)
            val desired = if (old == CameraCharacteristics.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            ids.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == desired }?.let { return it }
        }
        return ids.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: ids.first()
    }

    private fun openCamera() {
        if (!hasPerm(Manifest.permission.CAMERA)) return
        chars = manager.getCameraCharacteristics(cameraId)
        val map = chars!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val sizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        val size = sizes.filter { it.width >= 1920 && it.height >= 1080 }.minByOrNull { kotlin.math.abs(it.width * it.height - 1920 * 1080) }
            ?: sizes.maxByOrNull { it.width * it.height } ?: android.util.Size(1920,1080)
        reader?.close()
        reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        reader!!.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { image ->
                val buf = image.planes[0].buffer
                val bytes = ByteArray(buf.remaining()); buf.get(bytes); lastJpeg.set(bytes)
            }
        }, cameraExecutor)
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(c: CameraDevice) { camera = c; createPreviewSession(size) }
            override fun onDisconnected(c: CameraDevice) { c.close() }
            override fun onError(c: CameraDevice, error: Int) { c.close(); streamInfo.text = "CAMERA ERROR $error" }
        }, null)
    }

    private fun createPreviewSession(size: android.util.Size) {
        val tex = preview.surfaceTexture ?: return
        tex.setDefaultBufferSize(size.width, size.height)
        val previewSurface = Surface(tex)
        val jpegSurface = reader!!.surface
        request = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(previewSurface); addTarget(jpegSurface)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
        camera!!.createCaptureSession(listOf(previewSurface, jpegSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) { session = s; push(); streamInfo.text = "LIVE • ${size.width}×${size.height} • MJPEG" }
            override fun onConfigureFailed(s: CameraCaptureSession) { streamInfo.text = "SESSION FAILED" }
        }, null)
    }

    private fun push() { try { session?.setRepeatingRequest(request!!.build(), null, cameraExecutor) } catch (_: Exception) {} }

    private fun startRecording() {
        if (camera == null || !hasPerm(Manifest.permission.RECORD_AUDIO)) return
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "MoACam_$stamp.mp4")
            val mr = MediaRecorder()
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setVideoEncodingBitRate(20_000_000)
            mr.setVideoFrameRate(30)
            mr.setVideoSize(1920,1080)
            mr.setAudioEncodingBitRate(256_000)
            mr.setAudioSamplingRate(48000)
            mr.setOutputFile(file.absolutePath)
            if (Build.VERSION.SDK_INT >= 28) selectedAudio?.let { mr.setPreferredDevice(it) }
            mr.prepare()
            val tex = preview.surfaceTexture ?: return
            val ps = Surface(tex)
            val rs = mr.surface
            val js = reader!!.surface
            camera!!.createCaptureSession(listOf(ps, rs, js), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    val b = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    b.addTarget(ps); b.addTarget(rs); b.addTarget(js)
                    b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    s.setRepeatingRequest(b.build(), null, cameraExecutor)
                    mr.start(); recorder = mr; recording = true
                    runOnUiThread { findViewById<Button>(R.id.record).text = "■  STOP • SAVES TO APP MOVIES"; streamInfo.text = "● REC • $file" }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { mr.release(); streamInfo.text = "RECORD SESSION FAILED" }
            }, null)
        } catch (e: Exception) { streamInfo.text = "REC ERROR: ${e.message}" }
    }

    private fun stopRecording() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
        recorder = null; recording = false
        closeCamera(); openCamera()
        streamInfo.text = "LIVE"
        findViewById<Button>(R.id.record).text = "●  RECORD"
    }

    private fun closeCamera() {
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        session = null; camera = null
    }

    private fun localIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "phone-ip"
        } catch (_: Exception) { "phone-ip" }
    }

    private fun sb(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) action(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc,p,g)
        if (rc == 42 && g.all { it == PackageManager.PERMISSION_GRANTED }) init()
    }

    override fun onDestroy() { try { streamServer?.stop() } catch (_: Exception) {}; closeCamera(); reader?.close(); cameraExecutor.shutdown(); super.onDestroy() }
}

private class MjpegServer(private val port: Int, private val frame: AtomicReference<ByteArray?>) {
    private var running = true
    private val executor = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    fun start() { executor.execute {
        try {
            server = ServerSocket(port)
            while (running) {
                val socket = server!!.accept()
                executor.execute { serve(socket) }
            }
        } catch (_: Exception) {}
    }}
    private fun serve(s: Socket) {
        s.use { socket ->
            socket.soTimeout = 5000
            val out = BufferedOutputStream(socket.getOutputStream())
            out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray()); out.flush()
            while (running && !socket.isClosed) {
                val data = frame.get()
                if (data == null) { Thread.sleep(20); continue }
                out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray())
                out.write(data); out.write("\r\n".toByteArray()); out.flush()
                Thread.sleep(33)
            }
        }
    }
    fun stop() { running = false; try { server?.close() } catch (_: Exception) {}; executor.shutdownNow() }
}
