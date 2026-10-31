# AR Port — Loyiha Jarayoni (Progress Tracker)

Ushbu hujjat har bir bosqich natijasini, qilingan ishlarni va ma'lum muammolarni qayd etadi.

---

## Qadamlar jadvali

| Qadam | Nomi | Holat | Qaydlar |
|---|---|---|---|
| **1** | Muhit, loyiha skeleti va hujjatlar | ✅ Bajarildi | Flutter 3.47.9, JDK 17, Android SDK 35/36 o'rnatildi, arcore-engine kutubxonasi ulandi |
| **2** | Flutter UI qobig'i: tema, iOS dizayn tizimi, Splash | ⏳ Kutilmoqda | - |
| **3** | Qurilma tekshiruvi (giroskop) va ruxsatlar oqimi | ⏳ Kutilmoqda | - |
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
  1. Dasturlash muhiti tayyorlandi: Eclipse Temurin OpenJDK 17 (`C:\Users\Toms\tools\jdk-17.0.20.1+1`), Android SDK platform 35 & 36, build-tools 35.0.0 & 36.0.0, platform-tools, Flutter 3.47.9 & Dart 3.13.7. Doimiy foydalanuvchi muhit o'zgaruvchilari sozlandi.
  2. Flutter loyihasi `uz.orbitx.arport` paket identifikatori va "AR Port" nomi bilan yaratildi.
  3. `android/app/build.gradle.kts` da `minSdk = 29`, `targetSdk = 35`, `compileSdk = 35`, `applicationId = "uz.orbitx.arport"` belgilandi.
  4. Kotlin native moduli `android/arcore-engine` yaratildi va `settings.gradle.kts` hamda `app/build.gradle.kts` ga library sifatida ulandi.
  5. Kutubxonalar qo'shildi:
     - Flutter: `dynamic_color`, `permission_handler`, `shared_preferences`, `flutter_localizations`.
     - Android: CameraX (core, camera2, lifecycle 1.4.1), ARCore 1.47.0, MediaPipe Tasks Vision 0.10.14, Kotlin Coroutines 1.8.1.
  6. TZ 15 bo'yicha to'liq papka tuzilmasi shakllantirildi.
  7. Hujjatlar: `docs/TZ.md`, `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROGRESS.md` yaratildi.
  8. Git repozitoriysi initsializatsiya qilindi.

