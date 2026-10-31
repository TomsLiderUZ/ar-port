import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_uz.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ru'),
    Locale('uz'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In uz, this message translates to:
  /// **'AR Port'**
  String get appTitle;

  /// No description provided for @launchAr.
  ///
  /// In uz, this message translates to:
  /// **'AR ni ishga tushirish'**
  String get launchAr;

  /// No description provided for @viewModeTitle.
  ///
  /// In uz, this message translates to:
  /// **'Ko\'rinish rejimi'**
  String get viewModeTitle;

  /// No description provided for @modeStereo.
  ///
  /// In uz, this message translates to:
  /// **'2 ko\'zli (Stereo)'**
  String get modeStereo;

  /// No description provided for @modeNormal.
  ///
  /// In uz, this message translates to:
  /// **'Oddiy (To\'liq ekran)'**
  String get modeNormal;

  /// No description provided for @deviceSettings.
  ///
  /// In uz, this message translates to:
  /// **'Qurilma sozlamalari'**
  String get deviceSettings;

  /// No description provided for @camera.
  ///
  /// In uz, this message translates to:
  /// **'Kamera'**
  String get camera;

  /// No description provided for @microphone.
  ///
  /// In uz, this message translates to:
  /// **'Mikrofon'**
  String get microphone;

  /// No description provided for @audioOutput.
  ///
  /// In uz, this message translates to:
  /// **'Karnay / Quloqchin'**
  String get audioOutput;

  /// No description provided for @handCalibration.
  ///
  /// In uz, this message translates to:
  /// **'Qo\'l kuzatuvi kalibratsiyasi'**
  String get handCalibration;

  /// No description provided for @ipdAndLens.
  ///
  /// In uz, this message translates to:
  /// **'IPD va linza turi'**
  String get ipdAndLens;

  /// No description provided for @gyroMissingTitle.
  ///
  /// In uz, this message translates to:
  /// **'Giroskop topilmadi'**
  String get gyroMissingTitle;

  /// No description provided for @gyroMissingMessage.
  ///
  /// In uz, this message translates to:
  /// **'Qurilmangizda giroskop topilmadi. AR Port ishlashi uchun giroskop kerak.'**
  String get gyroMissingMessage;

  /// No description provided for @btnExit.
  ///
  /// In uz, this message translates to:
  /// **'Chiqish'**
  String get btnExit;

  /// No description provided for @warning3Dof.
  ///
  /// In uz, this message translates to:
  /// **'Faqat burilish kuzatiladi (3DoF)'**
  String get warning3Dof;

  /// No description provided for @permissionCamera.
  ///
  /// In uz, this message translates to:
  /// **'Kamera ruxsati'**
  String get permissionCamera;

  /// No description provided for @permissionMic.
  ///
  /// In uz, this message translates to:
  /// **'Mikrofon ruxsati'**
  String get permissionMic;

  /// No description provided for @permissionBluetooth.
  ///
  /// In uz, this message translates to:
  /// **'Bluetooth garnitura'**
  String get permissionBluetooth;

  /// No description provided for @settings.
  ///
  /// In uz, this message translates to:
  /// **'Sozlamalar'**
  String get settings;

  /// No description provided for @about.
  ///
  /// In uz, this message translates to:
  /// **'Haqida'**
  String get about;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ru', 'uz'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ru':
      return AppLocalizationsRu();
    case 'uz':
      return AppLocalizationsUz();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
