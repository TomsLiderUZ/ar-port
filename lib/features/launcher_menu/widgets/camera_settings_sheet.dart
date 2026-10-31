import 'package:flutter/material.dart';
import '../../../widgets/ios_glass_dialog.dart';
import '../../../widgets/ios_segmented_control.dart';
import '../../../platform/camera_list_channel.dart';

class CameraSettingsSheet {
  static Future<void> show({
    required BuildContext context,
    required List<CameraInfo> availableCameras,
    required String selectedCameraId,
    required String selectedResolution,
    required int selectedFps,
    required void Function(String cameraId, String resolution, int fps) onSave,
  }) async {
    String currentCameraId = selectedCameraId;
    String currentResolution = selectedResolution;
    int currentFps = selectedFps;

    await IosGlassDialog.show(
      context: context,
      title: 'Kamera sozlamalari',
      content: StatefulBuilder(
        builder: (context, setModalState) {
          final camera = availableCameras.firstWhere(
            (c) => c.id == currentCameraId,
            orElse: () => availableCameras.isNotEmpty
                ? availableCameras.first
                : const CameraInfo(
                    id: '0',
                    name: 'Orqa kamera',
                    isWideAngle: false,
                    supports1080p: true,
                    supports720p: true,
                    supports60Fps: true,
                    focalLength: 4.2,
                  ),
          );

          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Kamera tanlash:',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              if (availableCameras.length > 1) ...[
                IosSegmentedControl<String>(
                  items: availableCameras.map((c) {
                    return IosSegmentItem(
                      value: c.id,
                      title: c.isWideAngle ? 'Keng burchak' : 'Asosiy',
                    );
                  }).toList(),
                  selectedValue: currentCameraId,
                  onValueChanged: (val) => setModalState(() => currentCameraId = val),
                ),
              ] else ...[
                Text(
                  camera.name,
                  style: const TextStyle(fontSize: 14, color: Colors.grey),
                ),
              ],
              const SizedBox(height: 14),
              const Text(
                'Sifat (Ruxsat):',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              IosSegmentedControl<String>(
                items: const [
                  IosSegmentItem(value: '1080p', title: '1080p Full HD'),
                  IosSegmentItem(value: '720p', title: '720p HD'),
                ],
                selectedValue: currentResolution,
                onValueChanged: (val) => setModalState(() => currentResolution = val),
              ),
              const SizedBox(height: 14),
              const Text(
                'Kadrlar tezligi (FPS):',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              IosSegmentedControl<int>(
                items: const [
                  IosSegmentItem(value: 60, title: '60 FPS (Tavsiya)'),
                  IosSegmentItem(value: 30, title: '30 FPS'),
                ],
                selectedValue: currentFps,
                onValueChanged: (val) => setModalState(() => currentFps = val),
              ),
            ],
          );
        },
      ),
      actions: [
        IosDialogAction(
          title: 'Bekor qilish',
          onPressed: () => Navigator.of(context).pop(),
        ),
        IosDialogAction(
          title: 'Saqlash',
          isDefault: true,
          onPressed: () {
            onSave(currentCameraId, currentResolution, currentFps);
            Navigator.of(context).pop();
          },
        ),
      ],
    );
  }
}

