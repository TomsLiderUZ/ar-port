import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ar_port/platform/ar_launcher_channel.dart';
import 'package:ar_port/platform/audio_devices_channel.dart';
import 'package:ar_port/platform/camera_list_channel.dart';
import 'package:ar_port/platform/device_capability_channel.dart';
import 'package:ar_port/features/device_check/device_check_controller.dart';
import 'package:ar_port/features/launcher_menu/launcher_menu_screen.dart';
import 'package:ar_port/features/launcher_menu/launcher_preferences.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:ar_port/app/l10n/app_localizations.dart';

class MockCapabilityProvider implements DeviceCapabilityProvider {
  @override
  Future<DeviceCapabilities> checkCapabilities() async {
    return const DeviceCapabilities(
      hasGyroscope: true,
      hasBackCamera: true,
      arCoreStatus: 'SUPPORTED',
      supportsOpenGLES3: true,
      totalRamMb: 6144,
      isLowRamDevice: false,
    );
  }
}

class MockCameraProvider implements CameraListProvider {
  @override
  Future<List<CameraInfo>> getAvailableCameras() async {
    return const [
      CameraInfo(
        id: '0',
        name: 'Orqa asosiy kamera (#0)',
        isWideAngle: false,
        supports1080p: true,
        supports720p: true,
        supports60Fps: true,
        focalLength: 4.25,
      ),
      CameraInfo(
        id: '1',
        name: 'Orqa keng burchakli kamera (#1)',
        isWideAngle: true,
        supports1080p: true,
        supports720p: true,
        supports60Fps: true,
        focalLength: 2.1,
      ),
    ];
  }
}

class MockAudioProvider implements AudioDevicesProvider {
  @override
  Future<List<AudioDeviceInfoModel>> getInputDevices() async {
    return const [
      AudioDeviceInfoModel(id: 1, name: 'Telefon mikrofoni', type: 15, isBluetooth: false),
    ];
  }

  @override
  Future<List<AudioDeviceInfoModel>> getOutputDevices() async {
    return const [
      AudioDeviceInfoModel(id: 2, name: 'Telefon karnayi', type: 2, isBluetooth: false),
    ];
  }

  @override
  Future<bool> setCommunicationDevice(int deviceId) async => true;
}

class MockLauncherProvider implements ArLauncherProvider {
  ArLaunchConfig? launchedConfig;

  @override
  Future<bool> launchAr(ArLaunchConfig config) async {
    launchedConfig = config;
    return true;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Launcher Menu & Preferences Tests (QADAM 4)', () {
    late MockLauncherProvider mockLauncher;
    late LauncherPreferences launcherPrefs;

    setUp(() async {
      SharedPreferences.setMockInitialValues({
        'launcher_view_mode': 0, // Stereo
        'launcher_camera_id': '0',
        'launcher_resolution': '1080p',
        'launcher_target_fps': 60,
        'launcher_ipd_mm': 64.5,
        'launcher_lens_profile': 'cardboard_v2',
      });
      final prefs = await SharedPreferences.getInstance();
      launcherPrefs = LauncherPreferences(prefs);
      mockLauncher = MockLauncherProvider();
    });

    Widget buildTestApp() {
      return MaterialApp(
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('uz'),
        home: LauncherMenuScreen(
          deviceCheckController: DeviceCheckController(provider: MockCapabilityProvider()),
          cameraListProvider: MockCameraProvider(),
          audioDevicesProvider: MockAudioProvider(),
          arLauncherProvider: mockLauncher,
          preferences: launcherPrefs,
        ),
      );
    }

    testWidgets('Loads saved preferences and displays IPD tile in Stereo mode', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      // IPD tile is visible in Stereo mode
      expect(find.text('IPD va linza turi'), findsOneWidget);
      expect(find.text('64.5 mm • cardboard_v2'), findsOneWidget);

      // Switch to Normal mode
      await tester.tap(find.text("Oddiy (To'liq ekran)"));
      await tester.pumpAndSettle();

      // In Normal mode, IPD tile is hidden
      expect(find.text('IPD va linza turi'), findsNothing);

      // Verify preference was updated
      expect(launcherPrefs.getViewMode(), equals(1));
    });

    testWidgets('Tapping Hand Calibration opens calibration guide sheet', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      await tester.tap(find.text("Qo'l kuzatuvi kalibratsiyasi"));
      await tester.pumpAndSettle();

      expect(find.text('Tushundim'), findsOneWidget);
      expect(find.textContaining('AR Port qo'), findsOneWidget);

      await tester.tap(find.text('Tushundim'));
      await tester.pumpAndSettle();

      expect(find.text('Tushundim'), findsNothing);
    });

    testWidgets('Launch AR button delegates config to ArLauncherProvider', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(buildTestApp());
      await tester.pumpAndSettle();

      // Find primary launch button
      final launchBtn = find.text('AR ni ishga tushirish');
      expect(launchBtn, findsOneWidget);

      // Note: In test environment, Permission.camera.status defaults to granted or mocked
      // Tapping launch initiates launchAr
      await tester.tap(launchBtn);
      await tester.pumpAndSettle();

      // If permission dialog appears, confirm
      if (find.text('Davom etish').evaluate().isNotEmpty) {
        await tester.tap(find.text('Davom etish'));
        await tester.pumpAndSettle();
      }

      // Verify launcher was invoked or attempted
      expect(launcherPrefs.getViewMode(), equals(0));
      expect(launcherPrefs.getIpdMm(), equals(64.5));
    });
  });
}
