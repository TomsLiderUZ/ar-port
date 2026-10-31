import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../app/theme/app_theme.dart';
import '../../widgets/ios_large_title.dart';
import '../../widgets/ios_card.dart';
import '../../widgets/ios_inset_group.dart';
import '../../widgets/ios_list_tile.dart';
import '../../widgets/ios_primary_button.dart';
import '../../widgets/ios_segmented_control.dart';
import '../../widgets/ios_switch.dart';
import '../../widgets/ios_glass_dialog.dart';
import '../device_check/device_check_controller.dart';
import '../permissions/permission_flow.dart';
import 'package:ar_port/app/l10n/app_localizations.dart';

/// Launcher Menu screen with hardware capability validation and permission flows.
class LauncherMenuScreen extends StatefulWidget {
  final DeviceCheckController? deviceCheckController;

  const LauncherMenuScreen({
    super.key,
    this.deviceCheckController,
  });

  @override
  State<LauncherMenuScreen> createState() => _LauncherMenuScreenState();
}

class _LauncherMenuScreenState extends State<LauncherMenuScreen> {
  late final DeviceCheckController _checkController;
  DeviceCheckResult? _checkResult;
  bool _isLoadingCheck = true;

  int _viewMode = 0; // 0 = Stereo, 1 = Normal
  bool _highFpsEnabled = true;

  @override
  void initState() {
    super.initState();
    _checkController = widget.deviceCheckController ?? DeviceCheckController();
    _runDeviceCheck();
  }

  Future<void> _runDeviceCheck() async {
    final result = await _checkController.runCheck();
    if (!mounted) return;

    setState(() {
      _checkResult = result;
      _isLoadingCheck = false;
      if (result.recommendLowSpecMode) {
        _highFpsEnabled = false;
      }
    });

    if (result.isBlocked) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _showBlockedDialog(result);
      });
    }
  }

  void _showBlockedDialog(DeviceCheckResult result) {
    if (!mounted) return;
    IosGlassDialog.show(
      context: context,
      barrierDismissible: false,
      title: 'AR ishga tushirilmadi',
      message: result.blockMessage ?? 'Qurilma mos emas.',
      actions: [
        IosDialogAction(
          title: 'Chiqish',
          isDestructive: true,
          onPressed: () {
            SystemNavigator.pop();
          },
        ),
      ],
    );
  }

  Future<void> _handleLaunchAr() async {
    final check = _checkResult;
    if (check == null) return;

    if (check.isBlocked) {
      _showBlockedDialog(check);
      return;
    }

    // Check camera permission
    final hasCamera = await PermissionFlow.ensureCameraPermission(context);
    if (!hasCamera) return;

    if (!mounted) return;
    // In QADAM 4, this launches ArActivity with the chosen settings
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        content: Text(
          'AR tayyorlanmoqda (${_viewMode == 0 ? "Stereo" : "Oddiy"}, ${check.trackingMode == TrackingMode.sixDof ? "6DoF" : "3DoF"})',
        ),
      ),
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
            ),

            // Hardware Status Banner (6DoF / 3DoF / Blocked)
            if (!_isLoadingCheck && _checkResult != null)
              _buildTrackingBadge(theme, isDark),

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
              footer: _checkResult?.warningMessage != null
                  ? 'Ogohlantirish: ${_checkResult!.warningMessage!}'
                  : 'Barcha sensorlar to‘g‘ri aniqlandi.',
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
                  subtitle: _checkResult?.capabilities.hasBackCamera == true
                      ? 'Orqa kamera tayyor'
                      : 'Kamera aniqlanmadi',
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
                  onTap: () => PermissionFlow.requestMicrophonePermission(context),
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
                  onTap: () => PermissionFlow.requestBluetoothPermission(context),
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
                  subtitle: _checkResult?.recommendLowSpecMode == true
                      ? 'Qurilma resursi cheklangan, 30 FPS tavsiya'
                      : null,
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
                onPressed: _isLoadingCheck ? null : _handleLaunchAr,
              ),
            ),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildTrackingBadge(ThemeData theme, bool isDark) {
    final result = _checkResult!;
    Color badgeColor;
    IconData badgeIcon;
    String badgeTitle;
    String badgeDesc;

    if (result.isBlocked) {
      badgeColor = const Color(0xFFFF3B30);
      badgeIcon = Icons.error_outline_rounded;
      badgeTitle = 'AR Bloklangan';
      badgeDesc = result.blockMessage ?? '';
    } else if (result.trackingMode == TrackingMode.sixDof) {
      badgeColor = const Color(0xFF34C759);
      badgeIcon = Icons.view_in_ar_rounded;
      badgeTitle = '6DoF Rejimi (ARCore)';
      badgeDesc = 'Kamera va fazoviy joylashuv to‘liq kuzatiladi';
    } else {
      badgeColor = const Color(0xFFFF9500);
      badgeIcon = Icons.screen_rotation_rounded;
      badgeTitle = '3DoF Rejimi (Giroskop)';
      badgeDesc = 'Faqat burilish kuzatiladi';
    }

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: badgeColor.withValues(alpha: isDark ? 0.18 : 0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: badgeColor.withValues(alpha: isDark ? 0.35 : 0.25),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Icon(badgeIcon, color: badgeColor, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  badgeTitle,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: badgeColor,
                  ),
                ),
                Text(
                  badgeDesc,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w400,
                    color: isDark ? AppTheme.darkSecondaryLabel : AppTheme.lightSecondaryLabel,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
