import '../../platform/device_capability_channel.dart';

enum TrackingMode {
  threeDof,
  sixDof,
}

enum BlockReason {
  none,
  gyroscopeMissing,
  cameraMissing,
  openGlMissing,
}

/// Evaluated result of device hardware assessment per TZ 4.2 specifications.
class DeviceCheckResult {
  final bool isBlocked;
  final BlockReason blockReason;
  final TrackingMode trackingMode;
  final bool recommendLowSpecMode;
  final String? warningMessage;
  final String? blockMessage;
  final DeviceCapabilities capabilities;

  const DeviceCheckResult({
    required this.isBlocked,
    required this.blockReason,
    required this.trackingMode,
    required this.recommendLowSpecMode,
    this.warningMessage,
    this.blockMessage,
    required this.capabilities,
  });

  /// Evaluates hardware capabilities according to TZ 4.2 rules.
  factory DeviceCheckResult.evaluate(DeviceCapabilities caps) {
    if (!caps.hasGyroscope) {
      return DeviceCheckResult(
        isBlocked: true,
        blockReason: BlockReason.gyroscopeMissing,
        trackingMode: TrackingMode.threeDof,
        recommendLowSpecMode: caps.isLowRamDevice || caps.totalRamMb < 4096,
        blockMessage: 'Qurilmangizda giroskop topilmadi. AR Port ishlashi uchun giroskop kerak.',
        capabilities: caps,
      );
    }

    if (!caps.hasBackCamera) {
      return DeviceCheckResult(
        isBlocked: true,
        blockReason: BlockReason.cameraMissing,
        trackingMode: TrackingMode.threeDof,
        recommendLowSpecMode: caps.isLowRamDevice || caps.totalRamMb < 4096,
        blockMessage: 'Orqa kamera topilmadi. AR Port uchun kamera passthrough zarur.',
        capabilities: caps,
      );
    }

    if (!caps.supportsOpenGLES3) {
      return DeviceCheckResult(
        isBlocked: true,
        blockReason: BlockReason.openGlMissing,
        trackingMode: TrackingMode.threeDof,
        recommendLowSpecMode: caps.isLowRamDevice || caps.totalRamMb < 4096,
        blockMessage: 'OpenGL ES 3.0 qo‘llab-quvvatlanmaydi.',
        capabilities: caps,
      );
    }

    // Gyroscope is present and core hardware matches
    final is6DoF = caps.is6DoFSupported;
    return DeviceCheckResult(
      isBlocked: false,
      blockReason: BlockReason.none,
      trackingMode: is6DoF ? TrackingMode.sixDof : TrackingMode.threeDof,
      recommendLowSpecMode: caps.isLowRamDevice || caps.totalRamMb < 4096,
      warningMessage: is6DoF ? null : 'Faqat burilish kuzatiladi (3DoF)',
      capabilities: caps,
    );
  }
}

/// Controller coordinating device checking with native platform.
class DeviceCheckController {
  final DeviceCapabilityProvider provider;

  DeviceCheckController({this.provider = const DeviceCapabilityChannel()});

  Future<DeviceCheckResult> runCheck() async {
    final caps = await provider.checkCapabilities();
    return DeviceCheckResult.evaluate(caps);
  }
}

