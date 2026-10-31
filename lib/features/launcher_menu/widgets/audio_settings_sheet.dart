import 'package:flutter/material.dart';
import '../../../widgets/ios_glass_dialog.dart';
import '../../../platform/audio_devices_channel.dart';

class AudioSettingsSheet {
  static Future<void> show({
    required BuildContext context,
    required String title,
    required List<AudioDeviceInfoModel> devices,
    required int? selectedDeviceId,
    required void Function(AudioDeviceInfoModel device) onSelect,
  }) async {
    int currentId = selectedDeviceId ?? (devices.isNotEmpty ? devices.first.id : 0);

    await IosGlassDialog.show(
      context: context,
      title: title,
      content: StatefulBuilder(
        builder: (context, setModalState) {
          return SizedBox(
            width: double.maxFinite,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: devices.map((dev) {
                final isSelected = dev.id == currentId;
                return ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    dev.isBluetooth ? Icons.bluetooth_audio_rounded : Icons.volume_up_rounded,
                    color: isSelected ? Theme.of(context).colorScheme.primary : Colors.grey,
                  ),
                  title: Text(
                    dev.name,
                    style: TextStyle(
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                      color: isSelected ? Theme.of(context).colorScheme.primary : null,
                    ),
                  ),
                  trailing: isSelected
                      ? Icon(Icons.check, color: Theme.of(context).colorScheme.primary)
                      : null,
                  onTap: () {
                    setModalState(() => currentId = dev.id);
                  },
                );
              }).toList(),
            ),
          );
        },
      ),
      actions: [
        IosDialogAction(
          title: 'Bekor qilish',
          onPressed: () => Navigator.of(context).pop(),
        ),
        IosDialogAction(
          title: 'Tanlash',
          isDefault: true,
          onPressed: () {
            final chosen = devices.firstWhere(
              (d) => d.id == currentId,
              orElse: () => devices.first,
            );
            onSelect(chosen);
            Navigator.of(context).pop();
          },
        ),
      ],
    );
  }
}

