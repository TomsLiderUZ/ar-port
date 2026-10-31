import 'package:flutter/material.dart';
import '../../../widgets/ios_glass_dialog.dart';

class HandCalibrationSheet {
  static Future<void> show(BuildContext context) async {
    await IosGlassDialog.show(
      context: context,
      title: 'Qo‘l kuzatuvi kalibratsiyasi',
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.pan_tool_rounded,
              size: 44,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          const SizedBox(height: 14),
          const Text(
            'AR Port qo‘llaringizni orqa kamera orqali 3D fazoda kuzatadi.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 10),
          const Text(
            '1. Qo‘lingizni kameradan 30–50 sm masofada, pastki o‘rta zonada ushlang.\n'
            '2. Ko‘rsatkich barmoq bilan nishon oling (Hand Ray).\n'
            '3. Bosh va ko‘rsatkich barmoqni chimchilab bosing (Pinch Tap).',
            textAlign: TextAlign.start,
            style: TextStyle(fontSize: 12.5, color: Colors.grey, height: 1.4),
          ),
        ],
      ),
      actions: [
        IosDialogAction(
          title: 'Tushundim',
          isDefault: true,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
    );
  }
}

