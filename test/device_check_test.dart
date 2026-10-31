import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ar_port/platform/audio_devices_channel.dart';
import 'package:ar_port/platform/camera_list_channel.dart';
import 'package:ar_port/platform/device_capability_channel.dart';
import 'package:ar_port/features/device_check/device_check_controller.dart';
import 'package:ar_port/features/launcher_menu/launcher_menu_screen.dart';
import 'package:ar_port/features/launcher_menu/launcher_preferences.dart';
import 'package:ar_port/widgets/ios_glass_dialog.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:ar_port/app/l10n/app_localizations.dart';

class MockCapabilityProvider implements DeviceCapabilityProvider {
  final DeviceCapabilities capabilities;

  MockCapabilityProvider(this.capabilities);

  @override
  Future<DeviceCapabilities> checkCapabilities() async => capabilities;
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late LauncherPreferences launcherPrefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    launcherPrefs = LauncherPreferences(prefs);
  });

  group('DeviceCheckController Decision Logic Unit Tests', () {
    test('Device without gyroscope is blocked (TZ 4.2)', () {
      const caps = DeviceCapabilities(
        hasGyroscope: false,
        hasBackCamera: true,
        arCoreStatus: 'NOT_SUPPORTED',
        supportsOpenGLES3: true,
        totalRamMb: 6144,
        isLowRamDevice: false,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.isBlocked, isTrue);
      expect(result.blockReason, equals(BlockReason.gyroscopeMissing));
      expect(result.blockMessage, contains('giroskop topilmadi'));
    });

    test('Device without back camera is blocked', () {
      const caps = DeviceCapabilities(
        hasGyroscope: true,
        hasBackCamera: false,
        arCoreStatus: 'SUPPORTED',
        supportsOpenGLES3: true,
        totalRamMb: 6144,
        isLowRamDevice: false,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.isBlocked, isTrue);
      expect(result.blockReason, equals(BlockReason.cameraMissing));
    });

    test('Device without OpenGL ES 3.0 is blocked', () {
      const caps = DeviceCapabilities(
        hasGyroscope: true,
        hasBackCamera: true,
        arCoreStatus: 'SUPPORTED',
        supportsOpenGLES3: false,
        totalRamMb: 6144,
        isLowRamDevice: false,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.isBlocked, isTrue);
      expect(result.blockReason, equals(BlockReason.openGlMissing));
    });

    test('Device with gyroscope but no ARCore activates 3DoF with warning (TZ 4.2)', () {
      const caps = DeviceCapabilities(
        hasGyroscope: true,
        hasBackCamera: true,
        arCoreStatus: 'NOT_SUPPORTED',
        supportsOpenGLES3: true,
        totalRamMb: 6144,
        isLowRamDevice: false,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.isBlocked, isFalse);
      expect(result.trackingMode, equals(TrackingMode.threeDof));
      expect(result.warningMessage, equals('Faqat burilish kuzatiladi (3DoF)'));
    });

    test('Device with gyroscope and ARCore activates 6DoF without warning (TZ 4.2)', () {
      const caps = DeviceCapabilities(
        hasGyroscope: true,
        hasBackCamera: true,
        arCoreStatus: 'SUPPORTED',
        supportsOpenGLES3: true,
        totalRamMb: 6144,
        isLowRamDevice: false,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.isBlocked, isFalse);
      expect(result.trackingMode, equals(TrackingMode.sixDof));
      expect(result.warningMessage, isNull);
    });

    test('Device with low RAM recommends low spec mode', () {
      const caps = DeviceCapabilities(
        hasGyroscope: true,
        hasBackCamera: true,
        arCoreStatus: 'SUPPORTED',
        supportsOpenGLES3: true,
        totalRamMb: 2048,
        isLowRamDevice: true,
      );

      final result = DeviceCheckResult.evaluate(caps);

      expect(result.recommendLowSpecMode, isTrue);
    });
  });

  group('LauncherMenuScreen Device Check Widget Tests', () {
    Widget buildTestApp(DeviceCapabilityProvider provider) {
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
          deviceCheckController: DeviceCheckController(provider: provider),
          cameraListProvider: MockCameraProvider(),
          audioDevicesProvider: MockAudioProvider(),
          preferences: launcherPrefs,
        ),
      );
    }

    testWidgets('Missing gyroscope surfaces block glass dialog with Chiqish', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final mockProvider = MockCapabilityProvider(
        const DeviceCapabilities(
          hasGyroscope: false,
          hasBackCamera: true,
          arCoreStatus: 'NOT_SUPPORTED',
          supportsOpenGLES3: true,
          totalRamMb: 4096,
          isLowRamDevice: false,
        ),
      );

      await tester.pumpWidget(buildTestApp(mockProvider));
      await tester.pumpAndSettle();

      // Blocking dialog should be visible
      expect(find.byType(IosGlassDialog), findsOneWidget);
      expect(find.text('AR ishga tushirilmadi'), findsOneWidget);
      expect(find.text('Chiqish'), findsOneWidget);
    });

    testWidgets('6DoF capable device shows 6DoF badge and no block dialog', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final mockProvider = MockCapabilityProvider(
        const DeviceCapabilities(
          hasGyroscope: true,
          hasBackCamera: true,
          arCoreStatus: 'SUPPORTED',
          supportsOpenGLES3: true,
          totalRamMb: 8192,
          isLowRamDevice: false,
        ),
      );

      await tester.pumpWidget(buildTestApp(mockProvider));
      await tester.pumpAndSettle();

      // No block dialog
      expect(find.byType(IosGlassDialog), findsNothing);
      expect(find.text('6DoF Rejimi (ARCore)'), findsOneWidget);
    });
  });
}
