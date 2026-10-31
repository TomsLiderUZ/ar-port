import 'package:flutter/material.dart';
import '../../app/theme/app_theme.dart';
import '../../widgets/ios_large_title.dart';
import '../../widgets/ios_card.dart';
import '../../widgets/ios_inset_group.dart';
import '../../widgets/ios_list_tile.dart';
import '../../widgets/ios_primary_button.dart';
import '../../widgets/ios_segmented_control.dart';
import '../../widgets/ios_switch.dart';
import '../../widgets/ios_glass_dialog.dart';
import 'package:ar_port/app/l10n/app_localizations.dart';

/// Launcher Menu screen demonstrating iOS design components, theme adaptation, and localization.
class LauncherMenuScreen extends StatefulWidget {
  const LauncherMenuScreen({super.key});

  @override
  State<LauncherMenuScreen> createState() => _LauncherMenuScreenState();
}

class _LauncherMenuScreenState extends State<LauncherMenuScreen> {
  int _viewMode = 0; // 0 = Stereo, 1 = Normal
  bool _highFpsEnabled = true;

  void _showTestGlassDialog(BuildContext context, AppLocalizations l10n) {
    IosGlassDialog.show(
      context: context,
      title: l10n.gyroMissingTitle,
      message: l10n.gyroMissingMessage,
      actions: [
        IosDialogAction(
          title: l10n.btnExit,
          isDestructive: true,
          onPressed: () => Navigator.of(context).pop(),
        ),
        IosDialogAction(
          title: 'OK',
          isDefault: true,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.darkGroupedBackground : AppTheme.lightGroupedBackground,
      body: SafeArea(
        child: ListView(
          physics: const BouncingScrollPhysics(),
          children: [
            // iOS Large Title
            IosLargeTitle(
              title: l10n.appTitle,
              subtitle: 'Android Spatial AR/VR Headset',
              trailing: IconButton(
                icon: const Icon(Icons.info_outline_rounded),
                onPressed: () => _showTestGlassDialog(context, l10n),
              ),
            ),

            // Card 1: View Mode Selection
            IosCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primary.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(
                          Icons.visibility_rounded,
                          size: 20,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        l10n.viewModeTitle,
                        style: theme.textTheme.titleMedium,
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  IosSegmentedControl<int>(
                    items: [
                      IosSegmentItem(
                        value: 0,
                        title: l10n.modeStereo,
                        icon: const Icon(Icons.panorama_horizontal_select, size: 16),
                      ),
                      IosSegmentItem(
                        value: 1,
                        title: l10n.modeNormal,
                        icon: const Icon(Icons.crop_landscape, size: 16),
                      ),
                    ],
                    selectedValue: _viewMode,
                    onValueChanged: (val) => setState(() => _viewMode = val),
                  ),
                ],
              ),
            ),

            // Card 2: Settings Grouped List
            IosInsetGroup(
              header: l10n.deviceSettings,
              footer: 'Sensor va kamera parametrlari keyingi bosqichda ulanadi.',
              children: [
                IosListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: const Color(0xFF34C759),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.videocam_rounded, color: Colors.white, size: 20),
                  ),
                  title: l10n.camera,
                  subtitle: 'Orqa kamera (1080p, 60 FPS)',
                  showChevron: true,
                  onTap: () {},
                ),
                IosListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFF9500),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.mic_rounded, color: Colors.white, size: 20),
                  ),
                  title: l10n.microphone,
                  subtitle: 'Telefon mikrofoni',
                  showChevron: true,
                  onTap: () {},
                ),
                IosListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: const Color(0xFF5856D6),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.headphones_rounded, color: Colors.white, size: 20),
                  ),
                  title: l10n.audioOutput,
                  subtitle: 'Karnay',
                  showChevron: true,
                  onTap: () {},
                ),
                IosListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.speed_rounded, color: Colors.white, size: 20),
                  ),
                  title: '60 FPS Ultra-Low Latency',
                  trailing: IosSwitch(
                    value: _highFpsEnabled,
                    onChanged: (val) => setState(() => _highFpsEnabled = val),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 12),

            // Launch Button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: IosPrimaryButton(
                text: l10n.launchAr,
                icon: const Icon(Icons.play_arrow_rounded, color: Colors.white),
                onPressed: () {
                  _showTestGlassDialog(context, l10n);
                },
              ),
            ),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

