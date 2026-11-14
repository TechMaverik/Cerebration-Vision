package com.example.cerebration_vision.stream

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors

/** Owns the camera pipeline and the HTTP server, and exposes their state to the UI. */
class StreamController(private val activity: ComponentActivity) {

    // UI state (written on the main thread only)
    var streaming by mutableStateOf(false)
        private set
    var port by mutableIntStateOf(MjpegServer.DEFAULT_PORT)
        private set
    var clients by mutableIntStateOf(0)
        private set
    var fps by mutableIntStateOf(0)
        private set
    var frameSize by mutableStateOf("—")
        private set
    var useFrontCamera by mutableStateOf(false)
        private set
    var highDetail by mutableStateOf(false)
        private set
    var torchOn by mutableStateOf(false)
        private set
    var hasTorch by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val hub = FrameHub()
    private val server = MjpegServer(hub)
    private val main = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var analysis: ImageAnalysis? = null

    @Volatile private var live = false
    @Volatile private var targetRotation = Surface.ROTATION_0

    // analyzer-thread state
    private var frameCount = 0
    private var windowStart = 0L
    private val jpegBuffer = ByteArrayOutputStream(512 * 1024)

    /** Keeps streamed frames upright however the phone is held; the UI itself stays portrait. */
    private val orientationListener = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (rotation != targetRotation) {
                targetRotation = rotation
                analysis?.targetRotation = rotation
            }
        }
    }

    private val statsTicker = object : Runnable {
        override fun run() {
            clients = server.clientCount
            main.postDelayed(this, 1000)
        }
    }

    fun onStart() {
        orientationListener.enable()
        main.post(statsTicker)
    }

    fun onStop() {
        orientationListener.disable()
        main.removeCallbacks(statsTicker)
    }

    fun release() {
        stopStreaming()
        analysisExecutor.shutdown()
    }

    fun attachPreview(view: PreviewView) {
        previewView = view
        bindCamera()
    }

    fun toggleStreaming() = if (streaming) stopStreaming() else startStreaming()

    private fun startStreaming() {
        try {
            port = server.start()
            hub.clear()
            live = true
            streaming = true
            error = null
        } catch (e: Exception) {
            Log.e(TAG, "Could not start server", e)
            error = "Could not start the stream server: ${e.message}"
        }
    }

    private fun stopStreaming() {
        live = false
        streaming = false
        server.stop()
        hub.clear()
        clients = 0
        fps = 0
    }

    fun switchCamera() {
        useFrontCamera = !useFrontCamera
        bindCamera()
    }

    fun toggleDetail() {
        highDetail = !highDetail
        bindCamera()
    }

    fun toggleTorch() {
        val cam = camera ?: return
        if (!hasTorch) return
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
    }

    private fun bindCamera() {
        val view = previewView ?: return
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                val provider = future.get()
                // 4:3 uses the full sensor, so the inspection machine sees the widest field of view.
                val target = if (highDetail) Size(1920, 1440) else Size(1280, 960)
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .build()
                preview.setSurfaceProvider(view.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            )
                            .build()
                    )
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(targetRotation)
                    .build()
                imageAnalysis.setAnalyzer(analysisExecutor, ::analyze)

                val selector =
                    if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                val bound = provider.bindToLifecycle(activity, selector, preview, imageAnalysis)
                camera = bound
                analysis = imageAnalysis
                hasTorch = bound.cameraInfo.hasFlashUnit()
                torchOn = false
                if (error?.startsWith("Camera") == true) error = null
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                error = "Camera could not be opened: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!live) return
            val rotation = image.imageInfo.rotationDegrees
            val raw = image.toBitmap()
            val upright = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
            } else raw
            jpegBuffer.reset()
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpegBuffer)
            val w = upright.width
            val h = upright.height
            upright.recycle()
            hub.publish(jpegBuffer.toByteArray())
            server.frameWidth = w
            server.frameHeight = h

            frameCount++
            val now = System.currentTimeMillis()
            if (windowStart == 0L) windowStart = now
            if (now - windowStart >= 1000) {
                val measured = (frameCount * 1000L / (now - windowStart)).toInt()
                frameCount = 0
                windowStart = now
                server.fps = measured
                main.post {
                    if (streaming) {
                        fps = measured
                        frameSize = "$w×$h"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame dropped", e)
        } finally {
            image.close()
        }
    }

    companion object {
        private const val TAG = "CerebrationVision"
        private const val JPEG_QUALITY = 85

        /** The phone's IPv4 address on the local network (Wi-Fi or its own hotspot), if any. */
        fun localIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nif -> nif.inetAddresses.asSequence().map { nif.name to it } }
                .filter { (_, addr) -> addr is Inet4Address && addr.isSiteLocalAddress }
                // prefer Wi-Fi / hotspot interfaces over anything else (e.g. carrier-NAT mobile data)
                .sortedBy { (name, _) -> if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) 0 else 1 }
                .map { (_, addr) -> addr.hostAddress }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
