// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Uzbek (`uz`).
class AppLocalizationsUz extends AppLocalizations {
  AppLocalizationsUz([String locale = 'uz']) : super(locale);

  @override
  String get appTitle => 'AR Port';

  @override
  String get launchAr => 'AR ni ishga tushirish';

  @override
  String get viewModeTitle => 'Ko\'rinish rejimi';

  @override
  String get modeStereo => '2 ko\'zli (Stereo)';

  @override
  String get modeNormal => 'Oddiy (To\'liq ekran)';

  @override
  String get deviceSettings => 'Qurilma sozlamalari';

  @override
  String get camera => 'Kamera';

  @override
  String get microphone => 'Mikrofon';

  @override
  String get audioOutput => 'Karnay / Quloqchin';

  @override
  String get handCalibration => 'Qo\'l kuzatuvi kalibratsiyasi';

  @override
  String get ipdAndLens => 'IPD va linza turi';

  @override
  String get gyroMissingTitle => 'Giroskop topilmadi';

  @override
  String get gyroMissingMessage =>
      'Qurilmangizda giroskop topilmadi. AR Port ishlashi uchun giroskop kerak.';

  @override
  String get btnExit => 'Chiqish';

  @override
  String get warning3Dof => 'Faqat burilish kuzatiladi (3DoF)';

  @override
  String get permissionCamera => 'Kamera ruxsati';

  @override
  String get permissionMic => 'Mikrofon ruxsati';

  @override
  String get permissionBluetooth => 'Bluetooth garnitura';

  @override
  String get settings => 'Sozlamalar';

  @override
  String get about => 'Haqida';
}
