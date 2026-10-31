package uz.orbitx.arport.engine.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
    private val onPreviewSizeKnown: (width: Int, height: Int) -> Unit
) {
    companion object {
        private const val TAG = "ARPort_CameraMgr"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
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

        // 2. Camera2 low-latency capture settings (TZ 5.1)
        val previewBuilder = Preview.Builder()
            .setTargetResolution(targetSize)

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
        // Target FPS range
        val fpsRange = if (targetFps >= 60) Range(30, 60) else Range(15, 30)
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            fpsRange
        )

        preview = previewBuilder.build()

        // 3. Attach surface provider to OES texture
        preview?.setSurfaceProvider { request ->
            surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            onPreviewSizeKnown(request.resolution.width, request.resolution.height)

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
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            Log.i(TAG, "Camera bound successfully. ID: $cameraId, Res: $resolution, Target FPS: $targetFps")
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

