package com.carbon.prolocker.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class CameraCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraCaptureManager"

        private const val MIN_WARMUP_MS = 300L
        private const val MAX_WARMUP_MS = 1500L
        private const val MIN_WARMUP_WIDTH = 320

        /** Android docs formula for CaptureRequest.JPEG_ORIENTATION. */
        internal fun computeJpegOrientation(
            sensorOrientation: Int,
            displayRotationDegrees: Int,
            isFrontFacing: Boolean
        ): Int {
            var deviceOrientation = (360 - displayRotationDegrees) % 360
            if (isFrontFacing) {
                deviceOrientation = -deviceOrientation
            }
            return (sensorOrientation + deviceOrientation + 360) % 360
        }
    }

    private val cameraMutex = Mutex()

    suspend fun captureSelfie(outputFile: File): Boolean {
        val acquired = cameraMutex.tryLock()
        if (!acquired) {
            return false
        }

        try {
            return withTimeoutOrNull(8_000L) {
                doCaptureSelfieWithCamera2(outputFile)
            } ?: false
        } finally {
            cameraMutex.unlock()
        }
    }

    private suspend fun doCaptureSelfieWithCamera2(outputFile: File): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val frontCameraId = findFrontCameraId(cameraManager) ?: return false

        val capture = try {
            val characteristics = cameraManager.getCameraCharacteristics(frontCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            SelfieCapture(
                cameraManager = cameraManager,
                cameraId = frontCameraId,
                characteristics = characteristics,
                jpegSize = selectOptimalSize(jpegSizes),
                warmupSize = selectWarmupSize(map?.getOutputSizes(ImageFormat.YUV_420_888)),
                outputFile = outputFile
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reading camera characteristics", e)
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            capture.start(continuation)
        }
    }

    /**
     * One selfie attempt. There is no preview UI, so the camera first runs a tiny YUV repeating
     * request until auto exposure settles (a single cold still comes out dark), then takes the JPEG.
     * All camera callbacks run on one [HandlerThread]; [finish] resumes the continuation exactly once
     * and [release] frees everything on every path.
     */
    private inner class SelfieCapture(
        private val cameraManager: CameraManager,
        private val cameraId: String,
        private val characteristics: CameraCharacteristics,
        private val jpegSize: Size,
        private val warmupSize: Size?,
        private val outputFile: File
    ) {
        private val finished = AtomicBoolean(false)
        private val stillStarted = AtomicBoolean(false)
        @Volatile private var continuation: CancellableContinuation<Boolean>? = null
        @Volatile private var handlerThread: HandlerThread? = null
        @Volatile private var handler: Handler? = null
        @Volatile private var jpegReader: ImageReader? = null
        @Volatile private var warmupReader: ImageReader? = null
        @Volatile private var camera: CameraDevice? = null
        @Volatile private var session: CameraCaptureSession? = null

        private val stillDeadline = Runnable {
            session?.let { captureStill(it) }
        }

        @SuppressLint("MissingPermission")
        fun start(cont: CancellableContinuation<Boolean>) {
            continuation = cont
            cont.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    release()
                }
            }
            if (!cont.isActive) return

            try {
                val thread = HandlerThread("CameraCaptureThread").apply { start() }
                handlerThread = thread
                val cameraHandler = Handler(thread.looper)
                handler = cameraHandler

                jpegReader = ImageReader.newInstance(
                    jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2
                ).also { it.setOnImageAvailableListener({ reader -> onJpegAvailable(reader) }, cameraHandler) }

                warmupReader = warmupSize?.let { size ->
                    ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also {
                        it.setOnImageAvailableListener({ reader -> discardLatestImage(reader) }, cameraHandler)
                    }
                }

                cameraManager.openCamera(cameraId, stateCallback, cameraHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Exception opening camera", e)
                finish(false)
                return
            }

            // Cancelled while the objects above were still being created.
            if (finished.get()) {
                release()
            }
        }

        private val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                if (finished.get()) {
                    closeQuietly(device)
                    return
                }
                createSession(withWarmup = warmupReader != null)
            }

            override fun onDisconnected(device: CameraDevice) {
                closeQuietly(device)
                finish(false)
            }

            override fun onError(device: CameraDevice, error: Int) {
                closeQuietly(device)
                finish(false)
            }
        }

        private fun createSession(withWarmup: Boolean) {
            val device = camera
            val cameraHandler = handler
            val stillSurface = jpegReader?.surface
            if (device == null || cameraHandler == null || stillSurface == null || finished.get()) {
                finish(false)
                return
            }
            val surfaces = mutableListOf(stillSurface)
            if (withWarmup) {
                val warmupSurface = warmupReader?.surface
                if (warmupSurface == null) {
                    createSession(withWarmup = false)
                    return
                }
                surfaces.add(warmupSurface)
            }

            try {
                @Suppress("DEPRECATION")
                device.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            session = configured
                            if (finished.get()) {
                                closeQuietly(configured)
                                return
                            }
                            if (withWarmup) startWarmup(configured) else captureStill(configured)
                        }

                        override fun onConfigureFailed(failed: CameraCaptureSession) {
                            closeQuietly(failed)
                            if (withWarmup && !finished.get()) {
                                Log.w(TAG, "Two-surface session failed, capturing without warm-up")
                                createSession(withWarmup = false)
                            } else {
                                finish(false)
                            }
                        }
                    },
                    cameraHandler
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception in createCaptureSession", e)
                if (withWarmup && !finished.get()) {
                    createSession(withWarmup = false)
                } else {
                    finish(false)
                }
            }
        }

        private fun startWarmup(activeSession: CameraCaptureSession) {
            val device = camera
            val cameraHandler = handler
            val warmupSurface = warmupReader?.surface
            if (device == null || cameraHandler == null || warmupSurface == null) {
                captureStill(activeSession)
                return
            }
            try {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(warmupSurface)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                applyContinuousAutofocus(builder)

                val startedAt = SystemClock.elapsedRealtime()
                activeSession.setRepeatingRequest(
                    builder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            if (finished.get() || stillStarted.get()) return
                            if (SystemClock.elapsedRealtime() - startedAt < MIN_WARMUP_MS) return
                            if (isExposureSettled(result.get(CaptureResult.CONTROL_AE_STATE))) {
                                captureStill(activeSession)
                            }
                        }
                    },
                    cameraHandler
                )
                cameraHandler.postDelayed(stillDeadline, MAX_WARMUP_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Warm-up failed, capturing immediately", e)
                captureStill(activeSession)
            }
        }

        private fun isExposureSettled(aeState: Int?): Boolean {
            // null: LEGACY devices do not report an AE state.
            return aeState == null ||
                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                aeState == CaptureResult.CONTROL_AE_STATE_LOCKED
        }

        private fun captureStill(activeSession: CameraCaptureSession) {
            if (finished.get() || !stillStarted.compareAndSet(false, true)) return
            val device = camera
            val cameraHandler = handler
            val stillSurface = jpegReader?.surface
            if (device == null || cameraHandler == null || stillSurface == null) {
                finish(false)
                return
            }
            cameraHandler.removeCallbacks(stillDeadline)
            try {
                try {
                    activeSession.stopRepeating()
                } catch (_: Exception) {
                    // Not repeating (fallback path) or session already closing.
                }

                val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(stillSurface)
                applyContinuousAutofocus(captureBuilder)
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                val isFront = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
                captureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    computeJpegOrientation(sensorOrientation, displayRotationDegrees(), isFront)
                )

                activeSession.capture(
                    captureBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure
                        ) {
                            finish(false)
                        }
                    },
                    cameraHandler
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating capture request", e)
                finish(false)
            }
        }

        private fun applyContinuousAutofocus(builder: CaptureRequest.Builder) {
            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
        }

        private fun onJpegAvailable(reader: ImageReader) {
            if (finished.get()) {
                discardLatestImage(reader)
                return
            }
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring captured selfie", e)
                null
            }
            if (image == null) {
                finish(false)
                return
            }
            var saved = false
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                saved = true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving captured selfie", e)
            } finally {
                image.close()
            }
            finish(saved)
        }

        private fun discardLatestImage(reader: ImageReader) {
            try {
                reader.acquireLatestImage()?.close()
            } catch (_: Exception) {
            }
        }

        private fun finish(success: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            release()
            val cont = continuation
            if (cont != null && cont.isActive) {
                cont.resume(success)
            }
        }

        private fun release() {
            try { handler?.removeCallbacksAndMessages(null) } catch (_: Exception) {}
            closeQuietly(session)
            closeQuietly(camera)
            try { jpegReader?.close() } catch (_: Exception) {}
            try { warmupReader?.close() } catch (_: Exception) {}
            try { handlerThread?.quitSafely() } catch (_: Exception) {}
        }

        private fun closeQuietly(target: AutoCloseable?) {
            try { target?.close() } catch (_: Exception) {}
        }
    }

    private fun displayRotationDegrees(): Int {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            when (displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun findFrontCameraId(cameraManager: CameraManager): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                }
            }
            return cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating camera IDs", e)
            return null
        }
    }

    private fun selectOptimalSize(sizes: Array<Size>): Size {
        if (sizes.isEmpty()) return Size(640, 480)
        val candidates = sizes.filter { it.width <= 1280 && it.height <= 1280 }
        return candidates.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(640, 480)
    }

    /** Smallest YUV size that is at least [MIN_WARMUP_WIDTH] wide, or the smallest available. */
    private fun selectWarmupSize(sizes: Array<Size>?): Size? {
        if (sizes.isNullOrEmpty()) return null
        return sizes.filter { it.width >= MIN_WARMUP_WIDTH }.minByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
    }
}
