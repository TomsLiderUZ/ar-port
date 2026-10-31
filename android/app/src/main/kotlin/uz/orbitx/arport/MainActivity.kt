package uz.orbitx.arport

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import uz.orbitx.arport.engine.tracking.DeviceEvaluator

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL_CAPABILITY = "uz.orbitx.arport/device_capability"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_CAPABILITY).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkCapabilities" -> {
                    val caps = DeviceEvaluator.assessCapabilities(applicationContext)
                    result.success(caps)
                }
                else -> result.notImplemented()
            }
        }
    }
}
