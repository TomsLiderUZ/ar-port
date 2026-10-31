// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'AR Port';

  @override
  String get launchAr => 'Launch AR';

  @override
  String get viewModeTitle => 'Display Mode';

  @override
  String get modeStereo => '2-Eye (Stereo)';

  @override
  String get modeNormal => 'Normal (Fullscreen)';

  @override
  String get deviceSettings => 'Device Settings';

  @override
  String get camera => 'Camera';

  @override
  String get microphone => 'Microphone';

  @override
  String get audioOutput => 'Speaker / Headphones';

  @override
  String get handCalibration => 'Hand Tracking Calibration';

  @override
  String get ipdAndLens => 'IPD & Lens Profile';

  @override
  String get gyroMissingTitle => 'Gyroscope Not Found';

  @override
  String get gyroMissingMessage =>
      'No gyroscope was found on your device. AR Port requires a gyroscope to operate.';

  @override
  String get btnExit => 'Exit';

  @override
  String get warning3Dof => 'Only rotation is tracked (3DoF)';

  @override
  String get permissionCamera => 'Camera Permission';

  @override
  String get permissionMic => 'Microphone Permission';

  @override
  String get permissionBluetooth => 'Bluetooth Headset';

  @override
  String get settings => 'Settings';

  @override
  String get about => 'About';
}
