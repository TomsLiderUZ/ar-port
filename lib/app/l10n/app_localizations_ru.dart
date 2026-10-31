// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get appTitle => 'AR Port';

  @override
  String get launchAr => 'Запустить AR';

  @override
  String get viewModeTitle => 'Режим отображения';

  @override
  String get modeStereo => 'Стерео (2 глаза)';

  @override
  String get modeNormal => 'Обычный (полный экран)';

  @override
  String get deviceSettings => 'Настройки устройства';

  @override
  String get camera => 'Камера';

  @override
  String get microphone => 'Микрофон';

  @override
  String get audioOutput => 'Динамик / Наушники';

  @override
  String get handCalibration => 'Калибровка трекинга рук';

  @override
  String get ipdAndLens => 'IPD и тип линзы';

  @override
  String get gyroMissingTitle => 'Гироскоп не найден';

  @override
  String get gyroMissingMessage =>
      'На вашем устройстве нет гироскопа. Для работы AR Port необходим гироскоп.';

  @override
  String get btnExit => 'Выход';

  @override
  String get warning3Dof => 'Отслеживается только вращение (3DoF)';

  @override
  String get permissionCamera => 'Разрешение камеры';

  @override
  String get permissionMic => 'Разрешение микрофона';

  @override
  String get permissionBluetooth => 'Bluetooth гарнитура';

  @override
  String get settings => 'Настройки';

  @override
  String get about => 'О программе';
}
