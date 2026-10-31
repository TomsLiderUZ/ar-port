import 'package:flutter/services.dart';

/// Data class representing hardware assessment from the native platform.
class DeviceCapabilities {
  final bool hasGyroscope;
  final bool hasBackCamera;
  final String arCoreStatus; // 'SUPPORTED', 'NOT_SUPPORTED', 'UNKNOWN_CHECKING'
  final bool supportsOpenGLES3;
  final int totalRamMb;
  final bool isLowRamDevice;

  const DeviceCapabilities({
    required this.hasGyroscope,
    required this.hasBackCamera,
    required this.arCoreStatus,
    required this.supportsOpenGLES3,
    required this.totalRamMb,
    required this.isLowRamDevice,
  });

  bool get is6DoFSupported => arCoreStatus == 'SUPPORTED';

  factory DeviceCapabilities.fromMap(Map<dynamic, dynamic> map) {
    return DeviceCapabilities(
      hasGyroscope: map['hasGyroscope'] as bool? ?? false,
      hasBackCamera: map['hasBackCamera'] as bool? ?? false,
      arCoreStatus: map['arCoreStatus'] as String? ?? 'NOT_SUPPORTED',
      supportsOpenGLES3: map['supportsOpenGLES3'] as bool? ?? false,
      totalRamMb: (map['totalRamMb'] as num?)?.toInt() ?? 0,
      isLowRamDevice: map['isLowRamDevice'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'hasGyroscope': hasGyroscope,
      'hasBackCamera': hasBackCamera,
      'arCoreStatus': arCoreStatus,
      'supportsOpenGLES3': supportsOpenGLES3,
      'totalRamMb': totalRamMb,
      'isLowRamDevice': isLowRamDevice,
    };
  }
}

/// Interface for hardware capability assessment.
abstract class DeviceCapabilityProvider {
  Future<DeviceCapabilities> checkCapabilities();
}

/// MethodChannel implementation communicating with native Android DeviceCapabilityPlugin.
class DeviceCapabilityChannel implements DeviceCapabilityProvider {
  static const MethodChannel _channel = MethodChannel('uz.orbitx.arport/device_capability');

  const DeviceCapabilityChannel();

  @override
  Future<DeviceCapabilities> checkCapabilities() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('checkCapabilities');
      if (result != null) {
        return DeviceCapabilities.fromMap(result);
      }
    } on PlatformException {
      // Fallback in case of platform error
    }
    return const DeviceCapabilities(
      hasGyroscope: false,
      hasBackCamera: false,
      arCoreStatus: 'NOT_SUPPORTED',
      supportsOpenGLES3: false,
      totalRamMb: 0,
      isLowRamDevice: true,
    );
  }
}

