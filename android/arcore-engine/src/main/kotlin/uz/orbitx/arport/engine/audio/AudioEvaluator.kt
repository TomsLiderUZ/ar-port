package uz.orbitx.arport.engine.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Enumerates and manages audio input and output devices.
 */
object AudioEvaluator {

    fun getInputDevices(context: Context): List<Map<String, Any>> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.map { dev ->
            mapOf(
                "id" to dev.id,
                "name" to getDeviceName(dev),
                "type" to dev.type,
                "isBluetooth" to isBluetoothDevice(dev.type)
            )
        }
    }

    fun getOutputDevices(context: Context): List<Map<String, Any>> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.map { dev ->
            mapOf(
                "id" to dev.id,
                "name" to getDeviceName(dev),
                "type" to dev.type,
                "isBluetooth" to isBluetoothDevice(dev.type)
            )
        }
    }

    fun setCommunicationDevice(context: Context, deviceId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val devices = audioManager.availableCommunicationDevices
            val target = devices.firstOrNull { it.id == deviceId }
            return if (target != null) {
                audioManager.setCommunicationDevice(target)
            } else {
                audioManager.clearCommunicationDevice()
                false
            }
        }
        return true
    }

    private fun getDeviceName(dev: AudioDeviceInfo): String {
        val productName = dev.productName.toString()
        if (productName.isNotBlank()) {
            return productName
        }
        return when (dev.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Telefon mikrofoni"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Telefon karnayi"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Simli quloqchin"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth garnitura"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            else -> "Audio qurilma (#${dev.id})"
        }
    }

    private fun isBluetoothDevice(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}

