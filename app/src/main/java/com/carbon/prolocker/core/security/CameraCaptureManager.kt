package com.carbon.prolocker.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
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

    @SuppressLint("MissingPermission")
    private suspend fun doCaptureSelfieWithCamera2(outputFile: File): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val frontCameraId = findFrontCameraId(cameraManager) ?: return false

        val characteristics = cameraManager.getCameraCharacteristics(frontCameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        
        val optimalSize = selectOptimalSize(jpegSizes)
        val imageReader = ImageReader.newInstance(optimalSize.width, optimalSize.height, ImageFormat.JPEG, 2)
        val handlerThread = HandlerThread("CameraCaptureThread").apply { start() }
        val backgroundHandler = Handler(handlerThread.looper)

        val isResumed = AtomicBoolean(false)
        var openedCameraDevice: CameraDevice? = null
        var activeSession: CameraCaptureSession? = null

        fun cleanup() {
            try { activeSession?.close() } catch (_: Exception) {}
            try { openedCameraDevice?.close() } catch (_: Exception) {}
            try { imageReader.close() } catch (_: Exception) {}
            try { handlerThread.quitSafely() } catch (_: Exception) {}
        }

        return suspendCancellableCoroutine { continuation ->
            fun safeResume(success: Boolean) {
                if (isResumed.compareAndSet(false, true)) {
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
            }

            continuation.invokeOnCancellation {
                cleanup()
            }

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(bytes)
                            fos.flush()
                        }
                        safeResume(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving captured selfie", e)
                        safeResume(false)
                    } finally {
                        image.close()
                    }
                } else {
                    safeResume(false)
                }
            }, backgroundHandler)

            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    openedCameraDevice = camera
                    try {
                        val surfaceList = listOf(imageReader.surface)
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(
                            surfaceList,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    activeSession = session
                                    try {
                                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                        captureBuilder.addTarget(imageReader.surface)
                                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                                        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                                        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)

                                        session.capture(
                                            captureBuilder.build(),
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {}

                                                override fun onCaptureFailed(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    failure: CaptureFailure
                                                ) {
                                                    safeResume(false)
                                                }
                                            },
                                            backgroundHandler
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Exception creating capture request", e)
                                        safeResume(false)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    safeResume(false)
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in createCaptureSession", e)
                        safeResume(false)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    safeResume(false)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    safeResume(false)
                }
            }

            try {
                cameraManager.openCamera(frontCameraId, stateCallback, backgroundHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Exception opening camera", e)
                safeResume(false)
            }
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
}
