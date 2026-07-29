package com.carbon.prolocker.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

class CameraCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "Moslemprolocker"
    }

    private val cameraMutex = Mutex()

    private class DummyLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        init {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry
    }

    suspend fun captureSelfie(outputFile: File): Boolean {
        Log.d(TAG, "INTRUDER_CAMERA_START file=${outputFile.name} thread=${Thread.currentThread().name}")
        val acquired = cameraMutex.tryLock()
        if (!acquired) {
            Log.w(TAG, "INTRUDER_CAMERA mutex locked — capture already in progress, skipping")
            return false
        }
        try {
            val result = withTimeoutOrNull(10_000L) {
                doCaptureSelfie(outputFile)
            }
            if (result == null) {
                Log.e(TAG, "INTRUDER_CAMERA_TIMEOUT after 10s")
                return false
            }
            Log.d(TAG, "INTRUDER_CAMERA_RESULT success=$result file.exists()=${outputFile.exists()} file.length()=${outputFile.length()}")
            return result
        } finally {
            cameraMutex.unlock()
        }
    }

    private suspend fun doCaptureSelfie(outputFile: File): Boolean {
        val hasFrontCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        Log.d(TAG, "INTRUDER_CAMERA_CHECK hasFrontCamera=$hasFrontCamera")
        if (!hasFrontCamera) return false

        val cameraPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
        Log.d(TAG, "INTRUDER_CAMERA_CHECK permission=${if (cameraPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")

        return suspendCancellableCoroutine { continuation ->
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        Log.d(TAG, "INTRUDER_CAMERA_PROVIDER received")

                        val hasFront = cameraProvider.availableCameraInfos.any {
                            it.lensFacing == CameraSelector.LENS_FACING_FRONT
                        }
                        Log.d(TAG, "INTRUDER_CAMERA frontAvailable=$hasFront")
                        if (!hasFront) {
                            if (continuation.isActive) continuation.resume(false)
                            return@addListener
                        }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        val lifecycleOwner = DummyLifecycleOwner()

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageCapture
                        )

                        if (camera == null) {
                            Log.e(TAG, "INTRUDER_CAMERA_BOUND bindToLifecycle returned null")
                            lifecycleOwner.destroy()
                            if (continuation.isActive) continuation.resume(false)
                            return@addListener
                        }
                        Log.d(TAG, "INTRUDER_CAMERA_BOUND success")

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    Log.d(TAG, "INTRUDER_CAMERA_CAPTURE_SUCCESS file=${outputFile.absolutePath} exists=${outputFile.exists()} size=${outputFile.length()}")
                                    lifecycleOwner.destroy()
                                    cameraProvider.unbindAll()
                                    if (continuation.isActive) continuation.resume(true)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(TAG, "INTRUDER_CAMERA_CAPTURE_FAILED code=${exception.imageCaptureError} msg=${exception.message}", exception)
                                    lifecycleOwner.destroy()
                                    cameraProvider.unbindAll()
                                    if (continuation.isActive) continuation.resume(false)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "INTRUDER_CAMERA exception in provider callback", e)
                        if (continuation.isActive) continuation.resume(false)
                    }
                }, ContextCompat.getMainExecutor(context))

                continuation.invokeOnCancellation {
                    Log.d(TAG, "INTRUDER_CAMERA coroutine cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "INTRUDER_CAMERA ProcessCameraProvider failed", e)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }
}
