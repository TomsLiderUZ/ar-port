# AR Port — Loyiha Jarayoni (Progress Tracker)

Ushbu hujjat har bir bosqich natijasini, qilingan ishlarni va ma'lum muammolarni qayd etadi.

---

## Qadamlar jadvali

| Qadam | Nomi | Holat | Qaydlar |
|---|---|---|---|
| **1** | Muhit, loyiha skeleti va hujjatlar | ✅ Bajarildi | Flutter 3.47.9, JDK 17, Android SDK 35/36/37 o'rnatildi, arcore-engine moduli ulandi |
| **2** | Flutter UI qobig'i: tema, iOS dizayn tizimi, Splash | ✅ Bajarildi | iOS UI komponentlari, Dynamic color/tizim temasi, Splash (≤1.2s), L10n (`uz`, `ru`, `en`) |
| **3** | Qurilma tekshiruvi (giroskop) va ruxsatlar oqimi | ✅ Bajarildi | Native `DeviceEvaluator`, MethodChannel, giroskop yo'qligida bloklash dialogi, 3DoF/6DoF qaror mantiqi, ruxsatlar oqimi |
| **4** | Boshlang'ich menyu (rejim + qurilma sozlamalari) | ✅ Bajarildi | Stereo/oddiy rejim, Camera/Audio/IPD/Lens sozlamalari, SharedPreferences, `ArActivity` ishga tushishi |
| **5** | AR dvigatel yadrosi: ArActivity, GL, kamera passthrough | ✅ Bajarildi | OpenGL ES 3.0, CameraX OES Zero-Copy rendering, past kechikish sozlamalari, FPS tracker, Liquid Glass HUD |
| **6** | Harakat kuzatuvi va fazoda qotirish (world-lock) | ⏳ Kutilmoqda | - |
| **7** | Stereo rejim | ⏳ Kutilmoqda | - |
| **8** | Fazoviy oyna menejeri va yuza (surface) tizimi | ⏳ Kutilmoqda | - |
| **9** | Liquid Glass renderer | ⏳ Kutilmoqda | - |
| **10** | Qo'l kuzatuvi (MediaPipe) va qo'l yoritilishi | ⏳ Kutilmoqda | - |
| **11** | Gest dvigateli va kiritishni oynaga uzatish | ⏳ Kutilmoqda | - |
| **12** | Home panel, dock, ilova reyestri va plagin tizimi | ⏳ Kutilmoqda | - |
| **13** | Web ilovalar, umumiy sessiya va sandbox | ⏳ Kutilmoqda | - |
| **14** | Native ilovalar: Kamera, Qaydlar, Soat, Kalendar | ⏳ Kutilmoqda | - |
| **15** | AR Sozlamalar ilovasi | ⏳ Kutilmoqda | - |
| **16** | O'yinlar I: 2D (Tetris, Dino, Flappy, Snake, 2048) + Chess.com | ⏳ Kutilmoqda | - |
| **17** | O'yinlar II: 3D Table Tennis (botlar) va 3D Shooter | ⏳ Kutilmoqda | - |
| **18** | Samaradorlik, barqarorlik, maxfiylik, tozalash | ⏳ Kutilmoqda | - |
| **19** | Yakuniy build: `ar-port.apk` | ⏳ Kutilmoqda | - |

---

## QADAM 1 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Dasturlash muhiti tayyorlandi: Eclipse Temurin OpenJDK 17 (`C:\Users\Toms\tools\jdk-17.0.20.1+1`), Android SDK platform 35 & 36 & 37, build-tools 35.0.0 & 36.0.0, platform-tools, Flutter 3.47.9 & Dart 3.13.7. Doimiy foydalanuvchi muhit o'zgaruvchilari sozlandi.
  2. Flutter loyihasi `uz.orbitx.arport` paket identifikatori va "AR Port" nomi bilan yaratildi.
  3. `android/app/build.gradle.kts` da `minSdk = 29`, `targetSdk = 35`, `compileSdk = 37`, `applicationId = "uz.orbitx.arport"` belgilandi.
  4. Kotlin native moduli `android/arcore-engine` yaratildi va `settings.gradle.kts` hamda `app/build.gradle.kts` ga library sifatida ulandi.
  5. Kutubxonalar qo'shildi:
     - Flutter: `dynamic_color`, `permission_handler`, `shared_preferences`, `flutter_localizations`.
     - Android: CameraX (core, camera2, lifecycle 1.4.1), ARCore 1.47.0, MediaPipe Tasks Vision 0.10.14, Kotlin Coroutines 1.8.1.
  6. TZ 15 bo'yicha to'liq papka tuzilmasi shakllantirildi.
  7. Hujjatlar: `docs/TZ.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROGRESS.md` yaratildi.
  8. Git repozitoriysi initsializatsiya qilindi.

---

## QADAM 2 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Tizim temasi (qora/och) va `dynamic_color` (Material You) bilan avtomatik moslashuvchi, zaxirada `#0A84FF` (iOS ko'k) rangidan foydalanuvchi to'liq iOS mavzusi (`AppTheme`) yaratildi.
  2. Qayta ishlatiladigan to'liq iOS komponentlar to'plami yaratildi (`IosLargeTitle`, `IosCard`, `IosInsetGroup`, `IosListTile`, `IosPrimaryButton`, `IosSegmentedControl`, `IosSwitch`, `IosSlider`, `IosGlassDialog`).
  3. Splash ekrani yaratildi (`≤1.2 s`, 1000 ms animatsiyali spatial logo + matn, avtomatik Launcher ekraniga o'tish).
  4. Lokalizatsiya tizimi (`l10n.yaml`) ulandi; O'zbekcha (`uz` asosiy), ruscha (`ru`), inglizcha (`en`) ARB lug'atlari yaratildi.
  5. Widget testlari yozildi va `flutter test` orqali to'liq tasdiqlandi.

---

## QADAM 3 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Native apparat tahlilchisi `DeviceEvaluator` yaratildi (giroskop, kamera, ARCore 6DoF, GLES 3.0, RAM).
  2. `MainActivity.kt` da MethodChannel bog'landi.
  3. Dart qatlamida `DeviceCapabilities`, `DeviceCapabilityChannel`, hamda TZ 4.2 talablariga to'liq mos keluvchi `DeviceCheckController` va `DeviceCheckResult` yaratildi.
  4. Ruxsatlar oqimi (`PermissionFlow`) yaratildi (Kamera majburiy, mikrofon, bluetooth).
  5. `LauncherMenuScreen` ekraniga real apparat holati bannerlari bog'landi.
  6. Testlar (`test/device_check_test.dart`) yozildi va tasdiqlandi.

---

## QADAM 4 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Native apparat tahlilchilari (`CameraEvaluator`, `AudioEvaluator`) yaratildi.
  2. Native `ArActivity` (landscape, to'liq ekranli immersive, `FLAG_KEEP_SCREEN_ON`) yaratildi va `AndroidManifest.xml` ga kiritildi; `MainActivity.kt` da Intent extras orqali parametrlar uzatildi.
  3. Dart qatlamida `CameraListChannel`, `AudioDevicesChannel`, `ArLauncherChannel` hamda `LauncherPreferences` yaratildi.
  4. iOS interaktiv modal sheetlari (`CameraSettingsSheet`, `AudioSettingsSheet`, `IpdLensSheet`, `HandCalibrationSheet`) yaratildi.
  5. `LauncherMenuScreen` to'liq sozlamalar va rejimlar bilan boyitildi, barcha testlar muvaffaqiyatli o'tdi.

---

## QADAM 5 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. OpenGL ES 3.0 shader va dastur boshqaruvi (`GLHelper`): `GL_TEXTURE_EXTERNAL_OES` teksturasi, vertex/fragment shader kompilyatsiyasi va bog'lanishi.
  2. Zero-Copy GPU Passthrough Renderer (`PassthroughRenderer`): CameraX `SurfaceTexture` dan kadrni to'g'ridan-to'g'ri OES teksturasiga olib, to'liq ekranli to'rtburchakka center-crop aspekt nisbati bilan chizadi.
  3. Past kechikishli CameraX boshqaruvchisi (`PassthroughCameraManager`): uzluksiz avtofokus (`CONTROL_AF_MODE_CONTINUOUS_PICTURE`), avto-ekspozitsiya, EIS o'chirilgan (`CONTROL_VIDEO_STABILIZATION_MODE_OFF`), 30/60 FPS diapazoni.
  4. Kadr kechikishi va FPS o'lchovchi tizim (`FpsTracker`): `SurfaceTexture.timestamp` (sensor vaqti) va GPU render vaqti orasidagi kechikishni (motion-to-frame arrival latency) eksponensial o'rtacha bilan hisoblaydi.
  5. `ArActivity` render sikli (`ArEngineRenderer`): `GLSurfaceView` EGL 3.0, `RENDERMODE_WHEN_DIRTY`, stereo va oddiy ko'rinishlarni chizish, Liquid Glass uslubidagi jonli Debug HUD (FPS, kechikish, rejim ko'rsatkichlari) va zaxira chiqish tugmasi (`✕`).
