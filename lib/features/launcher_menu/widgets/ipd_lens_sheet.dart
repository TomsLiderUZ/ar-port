import 'package:flutter/material.dart';
import '../../../widgets/ios_glass_dialog.dart';
import '../../../widgets/ios_slider.dart';
import '../../../widgets/ios_segmented_control.dart';

class IpdLensSheet {
  static Future<void> show({
    required BuildContext context,
    required double initialIpdMm,
    required String initialLensProfile,
    required void Function(double ipdMm, String lensProfile) onSave,
  }) async {
    double currentIpd = initialIpdMm;
    String currentProfile = initialLensProfile;

    await IosGlassDialog.show(
      context: context,
      title: 'IPD va Linza sozlamalari',
      content: StatefulBuilder(
        builder: (context, setModalState) {
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'IPD (Ko‘zlararo masofa):',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                  ),
                  Text(
                    '${currentIpd.toStringAsFixed(1)} mm',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              IosSlider(
                value: currentIpd,
                min: 55.0,
                max: 75.0,
                divisions: 40, // 0.5 mm steps
                onChanged: (val) {
                  setModalState(() => currentIpd = val);
                },
              ),
              const Center(
                child: Text(
                  'Standart o‘rtacha qiymat: 63.0 mm',
                  style: TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Linza profili (Distorsiya):',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              IosSegmentedControl<String>(
                items: const [
                  IosSegmentItem(value: 'cardboard_v2', title: 'Cardboard v2'),
                  IosSegmentItem(value: 'daydream', title: 'Daydream'),
                  IosSegmentItem(value: 'custom', title: 'Erkin'),
                ],
                selectedValue: currentProfile,
                onValueChanged: (val) {
                  setModalState(() => currentProfile = val);
                },
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
            onSave(currentIpd, currentProfile);
            Navigator.of(context).pop();
          },
        ),
      ],
    );
  }
}

