package uz.orbitx.arport

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import uz.orbitx.arport.engine.ArActivity
import uz.orbitx.arport.engine.audio.AudioEvaluator
import uz.orbitx.arport.engine.camera.CameraEvaluator
import uz.orbitx.arport.engine.tracking.DeviceEvaluator

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL_CAPABILITY = "uz.orbitx.arport/device_capability"
        const val CHANNEL_CAMERA = "uz.orbitx.arport/camera_list"
        const val CHANNEL_AUDIO = "uz.orbitx.arport/audio_devices"
        const val CHANNEL_LAUNCHER = "uz.orbitx.arport/launcher"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        // Hardware capabilities
        MethodChannel(messenger, CHANNEL_CAPABILITY).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkCapabilities" -> {
                    val caps = DeviceEvaluator.assessCapabilities(applicationContext)
                    result.success(caps)
                }
                else -> result.notImplemented()
            }
        }

        // Camera list
        MethodChannel(messenger, CHANNEL_CAMERA).setMethodCallHandler { call, result ->
            when (call.method) {
                "getAvailableCameras" -> {
                    val cameras = CameraEvaluator.getAvailableCameras(applicationContext)
                    result.success(cameras)
                }
                else -> result.notImplemented()
            }
        }

        // Audio devices
        MethodChannel(messenger, CHANNEL_AUDIO).setMethodCallHandler { call, result ->
            when (call.method) {
                "getInputDevices" -> {
                    val inputs = AudioEvaluator.getInputDevices(applicationContext)
                    result.success(inputs)
                }
                "getOutputDevices" -> {
                    val outputs = AudioEvaluator.getOutputDevices(applicationContext)
                    result.success(outputs)
                }
                "setCommunicationDevice" -> {
                    val deviceId = call.argument<Int>("deviceId") ?: -1
                    val success = AudioEvaluator.setCommunicationDevice(applicationContext, deviceId)
                    result.success(success)
                }
                else -> result.notImplemented()
            }
        }

        // AR Launcher
        MethodChannel(messenger, CHANNEL_LAUNCHER).setMethodCallHandler { call, result ->
            when (call.method) {
                "launchAr" -> {
                    val viewMode = call.argument<String>("viewMode") ?: "stereo"
                    val cameraId = call.argument<String>("cameraId") ?: "0"
                    val resolution = call.argument<String>("resolution") ?: "1080p"
                    val targetFps = call.argument<Int>("targetFps") ?: 60
                    val ipdMm = call.argument<Double>("ipdMm") ?: 63.0
                    val lensProfile = call.argument<String>("lensProfile") ?: "cardboard_v2"
                    val audioInput = call.argument<String>("audioInput")
                    val audioOutput = call.argument<String>("audioOutput")

                    val intent = ArActivity.createIntent(
                        this,
                        viewMode = viewMode,
                        cameraId = cameraId,
                        resolution = resolution,
                        targetFps = targetFps,
                        ipdMm = ipdMm,
                        lensProfile = lensProfile,
                        audioInput = audioInput,
                        audioOutput = audioOutput
                    )
                    startActivity(intent)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }
    }
}
