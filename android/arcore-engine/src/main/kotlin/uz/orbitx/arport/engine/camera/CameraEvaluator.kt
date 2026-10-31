package uz.orbitx.arport.engine.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Size

/**
 * Discovers and inspects available rear cameras for low-latency AR passthrough.
 */
object CameraEvaluator {

    fun getAvailableCameras(context: Context): List<Map<String, Any>> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val result = mutableListOf<Map<String, Any>>()

        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // We only care about rear-facing cameras for world passthrough
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focalLength = focalLengths?.firstOrNull() ?: 0.0f
                    val isWideAngle = focalLength > 0.0f && focalLength < 3.0f

                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val previewSizes = map?.getOutputSizes(ImageFormat.PRIVATE) ?: emptyArray()

                    val supports1080p = previewSizes.any { it.width >= 1920 && it.height >= 1080 }
                    val supports720p = previewSizes.any { it.width >= 1280 && it.height >= 720 }

                    val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
                    val supports60Fps = fpsRanges.any { it.upper >= 60 }

                    val name = if (isWideAngle) "Orqa keng burchakli kamera (#$id)" else "Orqa asosiy kamera (#$id)"

                    result.add(
                        mapOf(
                            "id" to id,
                            "name" to name,
                            "isWideAngle" to isWideAngle,
                            "supports1080p" to supports1080p,
                            "supports720p" to (supports720p || previewSizes.isNotEmpty()),
                            "supports60Fps" to supports60Fps,
                            "focalLength" to focalLength.toDouble()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return empty list on hardware camera service error
        }

        return result
    }
}

