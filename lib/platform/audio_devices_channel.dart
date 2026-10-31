import 'package:flutter/services.dart';

/// Information regarding an audio input or output device.
class AudioDeviceInfoModel {
  final int id;
  final String name;
  final int type;
  final bool isBluetooth;

  const AudioDeviceInfoModel({
    required this.id,
    required this.name,
    required this.type,
    required this.isBluetooth,
  });

  factory AudioDeviceInfoModel.fromMap(Map<dynamic, dynamic> map) {
    return AudioDeviceInfoModel(
      id: (map['id'] as num?)?.toInt() ?? 0,
      name: map['name'] as String? ?? 'Audio qurilma',
      type: (map['type'] as num?)?.toInt() ?? 0,
      isBluetooth: map['isBluetooth'] as bool? ?? false,
    );
  }
}

abstract class AudioDevicesProvider {
  Future<List<AudioDeviceInfoModel>> getInputDevices();
  Future<List<AudioDeviceInfoModel>> getOutputDevices();
  Future<bool> setCommunicationDevice(int deviceId);
}

class AudioDevicesChannel implements AudioDevicesProvider {
  static const MethodChannel _channel = MethodChannel('uz.orbitx.arport/audio_devices');

  const AudioDevicesChannel();

  @override
  Future<List<AudioDeviceInfoModel>> getInputDevices() async {
    try {
      final List<dynamic>? result = await _channel.invokeMethod<List<dynamic>>('getInputDevices');
      if (result != null) {
        return result.map((item) => AudioDeviceInfoModel.fromMap(item as Map<dynamic, dynamic>)).toList();
      }
    } catch (_) {
      // Fallback
    }
    return const [
      AudioDeviceInfoModel(id: 1, name: 'Telefon mikrofoni', type: 15, isBluetooth: false),
    ];
  }

  @override
  Future<List<AudioDeviceInfoModel>> getOutputDevices() async {
    try {
      final List<dynamic>? result = await _channel.invokeMethod<List<dynamic>>('getOutputDevices');
      if (result != null) {
        return result.map((item) => AudioDeviceInfoModel.fromMap(item as Map<dynamic, dynamic>)).toList();
      }
    } catch (_) {
      // Fallback
    }
    return const [
      AudioDeviceInfoModel(id: 2, name: 'Telefon karnayi', type: 2, isBluetooth: false),
    ];
  }

  @override
  Future<bool> setCommunicationDevice(int deviceId) async {
    try {
      final bool? success = await _channel.invokeMethod<bool>('setCommunicationDevice', {'deviceId': deviceId});
      return success ?? false;
    } catch (_) {
      return false;
    }
  }
}
