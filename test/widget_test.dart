import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ar_port/main.dart';
import 'package:ar_port/features/splash/splash_screen.dart';
import 'package:ar_port/features/launcher_menu/launcher_menu_screen.dart';
import 'package:ar_port/widgets/ios_primary_button.dart';

void main() {
  testWidgets('Splash screen loads and transitions to LauncherMenuScreen', (WidgetTester tester) async {
    // Set a realistic phone size for testing
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 2.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(const ArPortApp());

    // Initially on Splash Screen
    expect(find.byType(SplashScreen), findsOneWidget);
    expect(find.text('Spatial Headset OS'), findsOneWidget);

    // Advance timer past splash duration (1000ms + animation 350ms)
    await tester.pump(const Duration(milliseconds: 1100));
    await tester.pumpAndSettle();

    // Now on Launcher Menu
    expect(find.byType(LauncherMenuScreen), findsOneWidget);
    expect(find.text('AR Port'), findsWidgets);
    expect(find.text('QURILMA SOZLAMALARI'), findsOneWidget);
    expect(find.text('Kamera'), findsOneWidget);
    expect(find.byType(IosPrimaryButton), findsOneWidget);
    expect(find.text('AR ni ishga tushirish'), findsOneWidget);
  });
}
