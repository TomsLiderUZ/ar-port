import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../app/theme/app_theme.dart';
import '../../widgets/ios_large_title.dart';
import '../../widgets/ios_card.dart';
import '../../widgets/ios_inset_group.dart';
import '../../widgets/ios_list_tile.dart';
import '../../widgets/ios_primary_button.dart';
import '../../widgets/ios_segmented_control.dart';
import '../../widgets/ios_glass_dialog.dart';
import '../../platform/ar_launcher_channel.dart';
import '../../platform/audio_devices_channel.dart';
import '../../platform/camera_list_channel.dart';
import '../device_check/device_check_controller.dart';
import '../permissions/permission_flow.dart';
import 'launcher_preferences.dart';
import 'widgets/audio_settings_sheet.dart';
import 'widgets/camera_settings_sheet.dart';
import 'widgets/hand_calibration_sheet.dart';
import 'widgets/ipd_lens_sheet.dart';
import 'package:ar_port/app/l10n/app_localizations.dart';

/// Full-featured iOS launcher menu for mode selection, device settings, and AR engine launch.
class LauncherMenuScreen extends StatefulWidget {
  final DeviceCheckController? deviceCheckController;
  final CameraListProvider? cameraListProvider;
  final AudioDevicesProvider? audioDevicesProvider;
  final ArLauncherProvider? arLauncherProvider;
  final LauncherPreferences? preferences;

  const LauncherMenuScreen({
    super.key,
    this.deviceCheckController,
    this.cameraListProvider,
    this.audioDevicesProvider,
    this.arLauncherProvider,
    this.preferences,
  });

  @override
  State<LauncherMenuScreen> createState() => _LauncherMenuScreenState();
}

class _LauncherMenuScreenState extends State<LauncherMenuScreen> {
  late final DeviceCheckController _checkController;
  late final CameraListProvider _cameraProvider;
  late final AudioDevicesProvider _audioProvider;
  late final ArLauncherProvider _launcherProvider;
  LauncherPreferences? _prefs;

  DeviceCheckResult? _checkResult;
  bool _isLoading = true;

  // Configuration State
  int _viewMode = 0; // 0 = Stereo, 1 = Normal
  String _selectedCameraId = '0';
  String _selectedResolution = '1080p';
  int _selectedTargetFps = 60;
  double _selectedIpdMm = 63.0;
  String _selectedLensProfile = 'cardboard_v2';
  int? _selectedAudioInputId;
  int? _selectedAudioOutputId;

  List<CameraInfo> _availableCameras = [];
  List<AudioDeviceInfoModel> _inputDevices = [];
  List<AudioDeviceInfoModel> _outputDevices = [];

  @override
  void initState() {
    super.initState();
    _checkController = widget.deviceCheckController ?? DeviceCheckController();
    _cameraProvider = widget.cameraListProvider ?? const CameraListChannel();
    _audioProvider = widget.audioDevicesProvider ?? const AudioDevicesChannel();
    _launcherProvider = widget.arLauncherProvider ?? const ArLauncherChannel();

    _initializeScreen();
  }

  Future<void> _initializeScreen() async {
    _prefs = widget.preferences ?? await LauncherPreferences.getInstance();

    // Restore saved settings
    if (_prefs != null) {
      _viewMode = _prefs!.getViewMode();
      _selectedCameraId = _prefs!.getCameraId();
      _selectedResolution = _prefs!.getResolution();
      _selectedTargetFps = _prefs!.getTargetFps();
      _selectedIpdMm = _prefs!.getIpdMm();
      _selectedLensProfile = _prefs!.getLensProfile();
      _selectedAudioInputId = _prefs!.getAudioInputId();
      _selectedAudioOutputId = _prefs!.getAudioOutputId();
    }

    // Check device capabilities
    final check = await _checkController.runCheck();
    final cameras = await _cameraProvider.getAvailableCameras();
    final inputs = await _audioProvider.getInputDevices();
    final outputs = await _audioProvider.getOutputDevices();

    if (!mounted) return;

    setState(() {
      _checkResult = check;
      _availableCameras = cameras;
      _inputDevices = inputs;
      _outputDevices = outputs;
      _isLoading = false;

      if (check.recommendLowSpecMode) {
        _selectedTargetFps = 30;
      }
    });

    if (check.isBlocked) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _showBlockedDialog(check);
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
          onPressed: () => SystemNavigator.pop(),
        ),
      ],
    );
  }

  void _onViewModeChanged(int newMode) {
    setState(() => _viewMode = newMode);
    _prefs?.setViewMode(newMode);
  }

  Future<void> _openCameraSettings() async {
    await CameraSettingsSheet.show(
      context: context,
      availableCameras: _availableCameras,
      selectedCameraId: _selectedCameraId,
      selectedResolution: _selectedResolution,
      selectedFps: _selectedTargetFps,
      onSave: (camId, res, fps) {
        setState(() {
          _selectedCameraId = camId;
          _selectedResolution = res;
          _selectedTargetFps = fps;
        });
        _prefs?.setCameraId(camId);
        _prefs?.setResolution(res);
        _prefs?.setTargetFps(fps);
      },
    );
  }

  Future<void> _openAudioInputSettings() async {
    final granted = await PermissionFlow.requestMicrophonePermission(context);
    if (!granted && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mikrofon ruxsati berilmadi.')),
      );
      return;
    }
    if (!mounted) return;

    await AudioSettingsSheet.show(
      context: context,
      title: 'Mikrofon tanlash',
      devices: _inputDevices,
      selectedDeviceId: _selectedAudioInputId,
      onSelect: (dev) {
        setState(() => _selectedAudioInputId = dev.id);
        _prefs?.setAudioInputId(dev.id);
        _audioProvider.setCommunicationDevice(dev.id);
      },
    );
  }

  Future<void> _openAudioOutputSettings() async {
    await PermissionFlow.requestBluetoothPermission(context);
    if (!mounted) return;

    await AudioSettingsSheet.show(
      context: context,
      title: 'Karnay / Quloqchin tanlash',
      devices: _outputDevices,
      selectedDeviceId: _selectedAudioOutputId,
      onSelect: (dev) {
        setState(() => _selectedAudioOutputId = dev.id);
        _prefs?.setAudioOutputId(dev.id);
        _audioProvider.setCommunicationDevice(dev.id);
      },
    );
  }

  Future<void> _openIpdLensSettings() async {
    await IpdLensSheet.show(
      context: context,
      initialIpdMm: _selectedIpdMm,
      initialLensProfile: _selectedLensProfile,
      onSave: (ipd, profile) {
        setState(() {
          _selectedIpdMm = ipd;
          _selectedLensProfile = profile;
        });
        _prefs?.setIpdMm(ipd);
        _prefs?.setLensProfile(profile);
      },
    );
  }

  Future<void> _handleLaunchAr() async {
    final check = _checkResult;
    if (check == null) return;

    if (check.isBlocked) {
      _showBlockedDialog(check);
      return;
    }

    // Enforce camera permission
    final hasCamera = await PermissionFlow.ensureCameraPermission(context);
    if (!hasCamera) return;

    final config = ArLaunchConfig(
      viewMode: _viewMode == 0 ? 'stereo' : 'normal',
      cameraId: _selectedCameraId,
      resolution: _selectedResolution,
      targetFps: _selectedTargetFps,
      ipdMm: _selectedIpdMm,
      lensProfile: _selectedLensProfile,
      audioInput: _selectedAudioInputId?.toString(),
      audioOutput: _selectedAudioOutputId?.toString(),
    );

    await _launcherProvider.launchAr(config);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final l10n = AppLocalizations.of(context)!;

    final selectedCamera = _availableCameras.firstWhere(
      (c) => c.id == _selectedCameraId,
      orElse: () => const CameraInfo(
        id: '0',
        name: 'Orqa asosiy kamera',
        isWideAngle: false,
        supports1080p: true,
        supports720p: true,
        supports60Fps: true,
        focalLength: 4.25,
      ),
    );

    final selectedInput = _inputDevices.firstWhere(
      (d) => d.id == _selectedAudioInputId,
      orElse: () => const AudioDeviceInfoModel(id: 1, name: 'Telefon mikrofoni', type: 15, isBluetooth: false),
    );

    final selectedOutput = _outputDevices.firstWhere(
      (d) => d.id == _selectedAudioOutputId,
      orElse: () => const AudioDeviceInfoModel(id: 2, name: 'Telefon karnayi', type: 2, isBluetooth: false),
    );

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
            if (!_isLoading && _checkResult != null)
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
                    onValueChanged: _onViewModeChanged,
                  ),
                ],
              ),
            ),

            // Card 2: Settings Grouped List
            IosInsetGroup(
              header: l10n.deviceSettings,
              footer: _viewMode == 0
                  ? 'Stereo rejim uchun telefonni VR korpusga soling. Sukut IPD: 63.0 mm.'
                  : 'Oddiy to‘liq ekran rejimi (telefonni qo‘lda tutib ishlatish uchun).',
              children: [
                // Camera Setting
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
                  subtitle: '${selectedCamera.name} ($_selectedResolution, ${_selectedTargetFps}fps)',
                  showChevron: true,
                  onTap: _openCameraSettings,
                ),

                // Microphone Setting
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
                  subtitle: selectedInput.name,
                  showChevron: true,
                  onTap: _openAudioInputSettings,
                ),

                // Audio Output Setting
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
                  subtitle: selectedOutput.name,
                  showChevron: true,
                  onTap: _openAudioOutputSettings,
                ),

                // Stereo Only: IPD and Lens Profile
                if (_viewMode == 0)
                  IosListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                        color: const Color(0xFF007AFF),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.tune_rounded, color: Colors.white, size: 20),
                    ),
                    title: l10n.ipdAndLens,
                    subtitle: '${_selectedIpdMm.toStringAsFixed(1)} mm • $_selectedLensProfile',
                    showChevron: true,
                    onTap: _openIpdLensSettings,
                  ),

                // Hand Calibration Tile
                IosListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFF2D55),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.pan_tool_rounded, color: Colors.white, size: 20),
                  ),
                  title: l10n.handCalibration,
                  subtitle: 'Qo‘l kursorini kalibrlash',
                  showChevron: true,
                  onTap: () => HandCalibrationSheet.show(context),
                ),
              ],
            ),

            const SizedBox(height: 12),

            // Big Launch AR Button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: IosPrimaryButton(
                text: l10n.launchAr,
                icon: const Icon(Icons.play_arrow_rounded, color: Colors.white),
                onPressed: _isLoading ? null : _handleLaunchAr,
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
