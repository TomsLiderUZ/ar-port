import 'package:flutter/material.dart';

/// iOS-style theme definition supporting Dynamic Color and Light/Dark modes.
class AppTheme {
  // Default iOS system blue fallback
  static const Color defaultAccent = Color(0xFF0A84FF);

  // iOS System Grays & Surfaces (Light)
  static const Color lightBackground = Color(0xFFF2F2F7);
  static const Color lightGroupedBackground = Color(0xFFF2F2F7);
  static const Color lightCardBackground = Color(0xFFFFFFFF);
  static const Color lightSeparator = Color(0x333C3C43);
  static const Color lightLabel = Color(0xFF000000);
  static const Color lightSecondaryLabel = Color(0x993C3C43);
  static const Color lightTertiaryLabel = Color(0x4D3C3C43);

  // iOS System Grays & Surfaces (Dark)
  static const Color darkBackground = Color(0xFF000000);
  static const Color darkGroupedBackground = Color(0xFF1C1C1E);
  static const Color darkCardBackground = Color(0xFF2C2C2E);
  static const Color darkSeparator = Color(0x38545458);
  static const Color darkLabel = Color(0xFFFFFFFF);
  static const Color darkSecondaryLabel = Color(0x99EBEBF5);
  static const Color darkTertiaryLabel = Color(0x4DEBEBF5);

  /// Radius constants matching iOS 20-28 dp squircle specs
  static const double cardRadius = 22.0;
  static const double buttonRadius = 18.0;
  static const double dialogRadius = 28.0;
  static const double controlRadius = 14.0;

  static ThemeData lightTheme(ColorScheme? dynamicColorScheme) {
    final accent = dynamicColorScheme?.primary ?? defaultAccent;
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: lightBackground,
      colorScheme: ColorScheme.light(
        primary: accent,
        secondary: dynamicColorScheme?.secondary ?? const Color(0xFF5E5CE6),
        surface: lightCardBackground,
        onSurface: lightLabel,
        outline: lightSeparator,
      ),
      dividerColor: lightSeparator,
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 34,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.8,
          color: lightLabel,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.5,
          color: lightLabel,
        ),
        titleLarge: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.4,
          color: lightLabel,
        ),
        titleMedium: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.4,
          color: lightLabel,
        ),
        bodyLarge: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w400,
          letterSpacing: -0.4,
          color: lightLabel,
        ),
        bodyMedium: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w400,
          letterSpacing: -0.2,
          color: lightSecondaryLabel,
        ),
        labelMedium: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w500,
          letterSpacing: -0.1,
          color: lightTertiaryLabel,
        ),
      ),
      cardTheme: CardThemeData(
        color: lightCardBackground,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(cardRadius),
        ),
      ),
    );
  }

  static ThemeData darkTheme(ColorScheme? dynamicColorScheme) {
    final accent = dynamicColorScheme?.primary ?? defaultAccent;
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: darkBackground,
      colorScheme: ColorScheme.dark(
        primary: accent,
        secondary: dynamicColorScheme?.secondary ?? const Color(0xFF5E5CE6),
        surface: darkCardBackground,
        onSurface: darkLabel,
        outline: darkSeparator,
      ),
      dividerColor: darkSeparator,
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 34,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.8,
          color: darkLabel,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.5,
          color: darkLabel,
        ),
        titleLarge: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.4,
          color: darkLabel,
        ),
        titleMedium: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.4,
          color: darkLabel,
        ),
        bodyLarge: TextStyle(
          fontSize: 17,
          fontWeight: FontWeight.w400,
          letterSpacing: -0.4,
          color: darkLabel,
        ),
        bodyMedium: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w400,
          letterSpacing: -0.2,
          color: darkSecondaryLabel,
        ),
        labelMedium: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w500,
          letterSpacing: -0.1,
          color: darkTertiaryLabel,
        ),
      ),
      cardTheme: CardThemeData(
        color: darkCardBackground,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(cardRadius),
        ),
      ),
    );
  }
}

