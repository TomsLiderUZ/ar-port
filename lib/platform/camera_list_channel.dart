import 'package:flutter/services.dart';

/// Information regarding an available rear camera.
class CameraInfo {
  final String id;
  final String name;
  final bool isWideAngle;
  final bool supports1080p;
  final bool supports720p;
  final bool supports60Fps;
  final double focalLength;

  const CameraInfo({
    required this.id,
    required this.name,
    required this.isWideAngle,
    required this.supports1080p,
    required this.supports720p,
    required this.supports60Fps,
    required this.focalLength,
  });

  factory CameraInfo.fromMap(Map<dynamic, dynamic> map) {
    return CameraInfo(
      id: map['id']?.toString() ?? '0',
      name: map['name'] as String? ?? 'Kamera',
      isWideAngle: map['isWideAngle'] as bool? ?? false,
      supports1080p: map['supports1080p'] as bool? ?? true,
      supports720p: map['supports720p'] as bool? ?? true,
      supports60Fps: map['supports60Fps'] as bool? ?? false,
      focalLength: (map['focalLength'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

abstract class CameraListProvider {
  Future<List<CameraInfo>> getAvailableCameras();
}

class CameraListChannel implements CameraListProvider {
  static const MethodChannel _channel = MethodChannel('uz.orbitx.arport/camera_list');

  const CameraListChannel();

  @override
  Future<List<CameraInfo>> getAvailableCameras() async {
    try {
      final List<dynamic>? result = await _channel.invokeMethod<List<dynamic>>('getAvailableCameras');
      if (result != null) {
        return result.map((item) => CameraInfo.fromMap(item as Map<dynamic, dynamic>)).toList();
      }
    } catch (_) {
      // Fallback
    }
    return const [
      CameraInfo(
        id: '0',
        name: 'Orqa asosiy kamera (#0)',
        isWideAngle: false,
        supports1080p: true,
        supports720p: true,
        supports60Fps: true,
        focalLength: 4.25,
      ),
    ];
  }
}
