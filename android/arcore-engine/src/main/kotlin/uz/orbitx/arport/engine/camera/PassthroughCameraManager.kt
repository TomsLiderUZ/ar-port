package uz.orbitx.arport.engine.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Manages CameraX passthrough stream optimized for ultra-low latency.
 */
class PassthroughCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraId: String,
    private val resolution: String,
    private val targetFps: Int,
    private val onPreviewSizeKnown: (width: Int, height: Int) -> Unit,
    private val onRotationDegreesKnown: ((Int) -> Unit)? = null
) {
    companion object {
        private const val TAG = "ARPort_CameraMgr"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    var imageAnalysis: ImageAnalysis? = null
    private var surface: Surface? = null

    fun startCamera(surfaceTexture: SurfaceTexture) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(surfaceTexture)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(surfaceTexture: SurfaceTexture) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // 1. Target resolution
        val targetSize = if (resolution == "720p") {
            Size(1280, 720)
        } else {
            Size(1920, 1080)
        }
        onPreviewSizeKnown(targetSize.width, targetSize.height)

        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_90
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_90
        }

        // 2. Camera2 low-latency capture settings (TZ 5.1)
        val previewBuilder = Preview.Builder()
            .setTargetResolution(targetSize)
            .setTargetRotation(displayRotation)

        val camera2Extender = Camera2Interop.Extender(previewBuilder)
        // Continuous auto-focus
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        // Automatic exposure
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        // Disable stabilization (EIS) to eliminate latency buffer
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        )
        // Target FPS range: query camera characteristics to lock solid 60 FPS if supported
        val cManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        val characteristics = try {
            cManager?.getCameraCharacteristics(cameraId)
        } catch (_: Exception) { null }
        val availableRanges = characteristics?.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)

        val fpsRange = if (targetFps >= 60) {
            availableRanges?.find { it.lower >= 60 && it.upper >= 60 }
                ?: availableRanges?.find { it.upper >= 60 }
                ?: Range(30, 60)
        } else {
            availableRanges?.find { it.upper == 30 }
                ?: Range(15, 30)
        }
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            fpsRange
        )
        Log.i(TAG, "Selected Camera AE Target FPS Range: $fpsRange")

        preview = previewBuilder.build()

        // 3. Attach surface provider to OES texture
        preview?.setSurfaceProvider { request ->
            surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            onPreviewSizeKnown(request.resolution.width, request.resolution.height)

            request.setTransformationInfoListener(ContextCompat.getMainExecutor(context)) { info ->
                Log.i(TAG, "TransformationInfo: rotationDegrees=${info.rotationDegrees}, cropRect=${info.cropRect}")
                onRotationDegreesKnown?.invoke(info.rotationDegrees)
            }

            surface?.release()
            surface = Surface(surfaceTexture)
            request.provideSurface(surface!!, ContextCompat.getMainExecutor(context)) {
                // Surface released callback
            }
        }

        // 4. Camera selector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { cameraInfos ->
                val matching = cameraInfos.filter { info ->
                    try {
                        val c2Info = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
                        c2Info.cameraId == cameraId
                    } catch (_: Exception) {
                        false
                    }
                }
                if (matching.isNotEmpty()) matching else cameraInfos
            }
            .build()

        try {
            val useCases = mutableListOf<UseCase>()
            preview?.let { useCases.add(it) }
            imageAnalysis?.let { useCases.add(it) }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            Log.i(TAG, "Camera bound successfully with ${useCases.size} use cases. ID: $cameraId, Res: $resolution, Target FPS: $targetFps")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ${e.message}", e)
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        surface?.release()
        surface = null
        preview = null
        camera = null
    }
}
