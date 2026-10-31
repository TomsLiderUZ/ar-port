# AR Port — Loyiha Jarayoni (Progress Tracker)

Ushbu hujjat har bir bosqich natijasini, qilingan ishlarni va ma'lum muammolarni qayd etadi.

---

## Qadamlar jadvali

| Qadam | Nomi | Holat | Qaydlar |
|---|---|---|---|
| **1** | Muhit, loyiha skeleti va hujjatlar | ✅ Bajarildi | Flutter 3.47.9, JDK 17, Android SDK 35/36/37 o'rnatildi, arcore-engine moduli ulandi |
| **2** | Flutter UI qobig'i: tema, iOS dizayn tizimi, Splash | ✅ Bajarildi | iOS UI komponentlari, Dynamic color/tizim temasi, Splash (≤1.2s), L10n (`uz`, `ru`, `en`) |
| **3** | Qurilma tekshiruvi (giroskop) va ruxsatlar oqimi | ✅ Bajarildi | Native `DeviceEvaluator`, MethodChannel, giroskop yo'qligida bloklash dialogi, 3DoF/6DoF qaror mantiqi, ruxsatlar oqimi |
| **4** | Boshlang'ich menyu (rejim + qurilma sozlamalari) | ⏳ Kutilmoqda | - |
| **5** | AR dvigatel yadrosi: ArActivity, GL, kamera passthrough | ⏳ Kutilmoqda | - |
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
  2. Qayta ishlatiladigan to'liq iOS komponentlar to'plami yaratildi:
     - `IosLargeTitle`: iOS uslubidagi katta sarlavha.
     - `IosCard`: 22 dp burchakli yumaloq karta.
     - `IosInsetGroup`: Guruhlangan ro'yxat, yuqori sarlavha, pastki izoh va avtomatik 0.5 dp ajratgichlar.
     - `IosListTile`: Yengil haptic va bosilish animatsiyasiga ega qator.
     - `IosPrimaryButton`: Spring bosilish animatsiyasi va haptic feedbackli asosiy tugma.
     - `IosSegmentedControl`: Silliq harakatlanuvchi pill va spring animatsiyali segmentli tanlov.
     - `IosSwitch` & `IosSlider`: iOS standartidagi o'chirib-yoqish va slayder boshqaruvlari.
     - `IosGlassDialog`: `BackdropFilter` (sigma 28), frosted blur va iOS tugmalariga ega oyna.
  3. Splash ekrani yaratildi (`≤1.2 s`, 1000 ms animatsiyali spatial logo + matn, avtomatik Launcher ekraniga o'tish).
  4. Lokalizatsiya tizimi (`l10n.yaml`) ulandi; O'zbekcha (`uz` asosiy), ruscha (`ru`), inglizcha (`en`) ARB lug'atlari yaratildi.
  5. Widget testlari yozildi va `flutter test` orqali to'liq tasdiqlandi.

---

## QADAM 3 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Native apparat tahlilchisi `DeviceEvaluator` (`arcore-engine` moduli) yaratildi:
     - Giroskop / Rotation Vector (`SensorManager`)
     - Orqa kamera (`CameraManager`)
     - ARCore 6DoF mavjudligi (`ArCoreApk.checkAvailability`)
     - OpenGL ES 3.0+ qo'llab-quvvatlanishi (`reqGlEsVersion >= 0x00030000`)
     - RAM hajmi va past xotirali qurilma tekshiruvi (`ActivityManager.MemoryInfo`).
  2. `MainActivity.kt` da `uz.orbitx.arport/device_capability` MethodChannel kanali ulandi.
  3. Dart qatlamida `DeviceCapabilities`, `DeviceCapabilityChannel`, hamda TZ 4.2 talablariga to'liq mos keluvchi `DeviceCheckController` va `DeviceCheckResult` yaratildi:
     - Giroskop yo'q bo'lsa: `isBlocked = true`, `BlockReason.gyroscopeMissing`, xabar va 'Chiqish' tugmali dialog.
     - Giroskop bor, lekin ARCore yo'q bo'lsa: 3DoF rejimi, kichik ogohlantirish ("Faqat burilish kuzatiladi (3DoF)").
     - ARCore qo'llansa: 6DoF rejimi.
     - Kam RAM: yengil sifat rejimi tavsiyasi.
  4. Ruxsatlar oqimi (`PermissionFlow`) yaratildi: Kamera (majburiy, rad etilsa sozlamalarga yo'naltiruvchi dialog), Mikrofon va Bluetooth (Android 12+) kontekstli so'rovlari.
  5. LauncherMenuScreen interfeysiga real apparat holati bannerlari (6DoF / 3DoF / Bloklangan) va ruxsat tekshiruvi bog'landi.
  6. To'liq birlik va widget testlari (`test/device_check_test.dart`) yozildi va barchasi (9 ta test) muvaffaqiyatli o'tdi.
