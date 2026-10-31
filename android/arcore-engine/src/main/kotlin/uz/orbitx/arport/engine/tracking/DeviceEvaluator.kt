package uz.orbitx.arport.engine.tracking

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.google.ar.core.ArCoreApk

/**
 * Evaluates device hardware capabilities for the AR engine.
 */
object DeviceEvaluator {
    fun assessCapabilities(ctx: Context): Map<String, Any> {
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasGyroscope = sensorManager?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null ||
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        } ?: false

        val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        var hasBackCamera = false
        cameraManager?.let { cm ->
            try {
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        hasBackCamera = true
                        break
                    }
                }
            } catch (_: Exception) {
                hasBackCamera = false
            }
        }

        var arCoreStatus = "NOT_SUPPORTED"
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(ctx)
            arCoreStatus = when {
                availability.isSupported -> "SUPPORTED"
                availability.isTransient -> "UNKNOWN_CHECKING"
                else -> "NOT_SUPPORTED"
            }
        } catch (_: Exception) {
            arCoreStatus = "NOT_SUPPORTED"
        }

        val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val configInfo = activityManager?.deviceConfigurationInfo
        val supportsOpenGLES3 = (configInfo?.reqGlEsVersion ?: 0) >= 0x00030000

        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val isLowRamDevice = activityManager?.isLowRamDevice ?: (totalRamMb < 4096)

        return mapOf(
            "hasGyroscope" to hasGyroscope,
            "hasBackCamera" to hasBackCamera,
            "arCoreStatus" to arCoreStatus,
            "supportsOpenGLES3" to supportsOpenGLES3,
            "totalRamMb" to totalRamMb,
            "isLowRamDevice" to isLowRamDevice
        )
    }
}

