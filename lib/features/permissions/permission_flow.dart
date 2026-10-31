import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../widgets/ios_glass_dialog.dart';

/// Contextual permission controller handling camera, microphone, and bluetooth permissions.
class PermissionFlow {
  /// Ensures mandatory camera permission is granted.
  /// Shows contextual explanation and guides to settings if denied.
  static Future<bool> ensureCameraPermission(BuildContext context) async {
    final status = await Permission.camera.status;

    if (status.isGranted) {
      return true;
    }

    if (!context.mounted) return false;

    if (status.isPermanentlyDenied) {
      await _showSettingsDialog(
        context: context,
        title: 'Kamera ruxsati kerak',
        message: 'AR Port real dunyo tasviri (passthrough) va qo‘l harakatini kuzatish uchun kameradan foydalanadi. Iltimos, sozlamalardan ruxsat bering.',
      );
      return (await Permission.camera.status).isGranted;
    }

    // Request with contextual dialog first if first time or denied
    final shouldProceed = await _showExplanationDialog(
      context: context,
      title: 'Kamera ruxsati',
      message: 'AR Port real dunyoni ko‘rish (passthrough) va qo‘l nuri bilan boshqarish uchun kamera ruxsatini talab qiladi.',
      actionTitle: 'Davom etish',
    );

    if (!shouldProceed) return false;

    final result = await Permission.camera.request();
    if (result.isGranted) {
      return true;
    }

    if (result.isPermanentlyDenied && context.mounted) {
      await _showSettingsDialog(
        context: context,
        title: 'Kamera ruxsati kerak',
        message: 'Kameraga ruxsatsiz AR rejim ishga tushmaydi. Ilovani sozlamalariga o‘tib ruxsatni yoqing.',
      );
    }
    return result.isGranted;
  }

  /// Contextually requests microphone permission.
  static Future<bool> requestMicrophonePermission(BuildContext context) async {
    final status = await Permission.microphone.status;
    if (status.isGranted) return true;

    if (!context.mounted) return false;

    if (status.isPermanentlyDenied) {
      await _showSettingsDialog(
        context: context,
        title: 'Mikrofon ruxsati',
        message: 'Ovozli kiritish va aloqa ilovalari uchun mikrofonga ruxsat kerak.',
      );
      return (await Permission.microphone.status).isGranted;
    }

    final result = await Permission.microphone.request();
    return result.isGranted;
  }

  /// Contextually requests Bluetooth Connect permission for Bluetooth headphones/mics.
  static Future<bool> requestBluetoothPermission(BuildContext context) async {
    final status = await Permission.bluetoothConnect.status;
    if (status.isGranted) return true;

    final result = await Permission.bluetoothConnect.request();
    return result.isGranted;
  }

  static Future<bool> _showExplanationDialog({
    required BuildContext context,
    required String title,
    required String message,
    required String actionTitle,
  }) async {
    bool proceed = false;
    await IosGlassDialog.show(
      context: context,
      title: title,
      message: message,
      actions: [
        IosDialogAction(
          title: 'Bekor qilish',
          onPressed: () => Navigator.of(context).pop(),
        ),
        IosDialogAction(
          title: actionTitle,
          isDefault: true,
          onPressed: () {
            proceed = true;
            Navigator.of(context).pop();
          },
        ),
      ],
    );
    return proceed;
  }

  static Future<void> _showSettingsDialog({
    required BuildContext context,
    required String title,
    required String message,
  }) async {
    await IosGlassDialog.show(
      context: context,
      title: title,
      message: message,
      actions: [
        IosDialogAction(
          title: 'Yopish',
          onPressed: () => Navigator.of(context).pop(),
        ),
        IosDialogAction(
          title: 'Sozlamalarga o‘tish',
          isDefault: true,
          onPressed: () {
            Navigator.of(context).pop();
            openAppSettings();
          },
        ),
      ],
    );
  }
}
