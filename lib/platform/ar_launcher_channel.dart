import 'package:flutter/services.dart';

/// Runtime configuration bundled when transitioning into ArActivity.
class ArLaunchConfig {
  final String viewMode; // "stereo" | "normal"
  final String cameraId;
  final String resolution; // "1080p" | "720p"
  final int targetFps; // 60 | 30
  final double ipdMm; // e.g. 63.0
  final String lensProfile; // "cardboard_v2" | "daydream" | "custom"
  final String? audioInput;
  final String? audioOutput;

  const ArLaunchConfig({
    required this.viewMode,
    required this.cameraId,
    required this.resolution,
    required this.targetFps,
    this.ipdMm = 63.0,
    this.lensProfile = 'cardboard_v2',
    this.audioInput,
    this.audioOutput,
  });

  Map<String, dynamic> toMap() {
    return {
      'viewMode': viewMode,
      'cameraId': cameraId,
      'resolution': resolution,
      'targetFps': targetFps,
      'ipdMm': ipdMm,
      'lensProfile': lensProfile,
      'audioInput': audioInput,
      'audioOutput': audioOutput,
    };
  }
}

abstract class ArLauncherProvider {
  Future<bool> launchAr(ArLaunchConfig config);
}

class ArLauncherChannel implements ArLauncherProvider {
  static const MethodChannel _channel = MethodChannel('uz.orbitx.arport/launcher');

  const ArLauncherChannel();

  @override
  Future<bool> launchAr(ArLaunchConfig config) async {
    try {
      final bool? success = await _channel.invokeMethod<bool>('launchAr', config.toMap());
      return success ?? false;
    } on PlatformException {
      return false;
    }
  }
}

