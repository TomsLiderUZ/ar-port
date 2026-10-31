# AR Port — Loyihaning Umumiy Holati va Bajarilgan Ishlar Hisoboti

**Hujjat yangilangan sana:** 2026-10-31  
**Loyiha:** AR Port — Spatial AR/VR Headset OS for Android  
**Paket nomi:** `uz.orbitx.arport`  
**Yakuniy mahsulot:** `dist/ar-port.apk`  
**Hozirgi umumiy progress:** **94.7%** (19 ta strategik qadamdan 18 tasi to'liq yakunlandi va sinovdan o'tdi)  
**Qolgan ish hajmi:** **5.3%** (1 ta yakuniy qadam: QADAM 19)

---

## 1. Loyiha Haqida Qisqacha (Executive Summary)

**AR Port** — bu oddiy Android smartfonlarini Google Cardboard v2 (yoki mos VR boks) ko'zoynaklari orqali to'laqonli fazoviy kompyuterga (Apple VisionOS / Meta Horizon OS darajasidagi spatial headset OS) aylantiruvchi tizim.

### Asosiy Texnologik Ustunlar:
1. **Kam kechikishli Passthrough (≤80ms):** Orqa kamera orqali real dunyoni 60 FPS tezlikda OpenGL ES 3.0 yordamida kechikishsiz uzatish.
2. **Fazoviy Barqarorlik (World-Lock):** 3DoF IMU giroskopik kvaternion sensori (driftni 1 tugma bilan to'g'rilovchi "Recenter") hamda 6DoF ARCore fazoviy bog'lanishi.
3. **Stereoskopik Optika (StereoRenderer):** Google Cardboard v2 optik pincushion distorsiyasini bartaraf etuvchi barrel distorsiyasi shader pass, IPD siljishi (55-75 mm), linza ajratgich chizig'i.
4. **Apple VisionOS Liquid Glass Estetikasi:** Kawase dual-pass real-vaqt xiralashtirish (blur), superellipse (squircle) burchaklar, 1px yorug'lik chegarasi, specular sheen nurlanishi, spring fizika animatsiyalari.
5. **Google MediaPipe Hand Tracking:** Kamera orqali 21 ta qo'l bo'g'inlarini 3D aniqlash, ko'rsatkich barmog'idan chiquvchi yo'naltiruvchi lazer nuri, masofaga qarab o'lchami o'zgaruvchi dinamik nishon (reticle).
6. **10+ Fazoviy Imo-ishoralar (GestureEngine):** Chimchilash (Pinch Tap), Lazer bilan ko'rsatish (Ray Point), Suzib yurish (Drag), Ikki qo'lli kattalashtirish (Two-Hand Zoom), Oynani yopish (Fist), Asosiy menyu (Palm-up), Qayta markazlashtirish (Peace V), 4 tomonlama svayp.
7. **Qat'iy Sandbox va SSO Sessiyalar Ombori:** Tizimdan tashqariga bitta ham havola yoki intent chiqib ketmaydi (`intent://`, `tg://` bloklangan), barcha havolalar ichki Google Brauzerda ochiladi; WebStorage va CookieManager orqali umumiy kirish (SSO).
8. **18 ta Ilova va O'yinlar:** 5 ta Web ilova, 5 ta Native ilova, 6 ta Oflayn 2D o'yin + AR Shaxmat, 2 ta 3D fazoviy o'yin.
9. **Samaradorlik va Adaptiv Sifat (QADAM 18):** 4 darajali dinamik sifat moslashuvi, termal monitor, xotira yetishmovchiligida oynalarni uxlatish (sleep policy), WebView qulashini qayta tiklash (crash recovery) va to'liq R8/ProGuard saqlash qoidalari.

---

## 2. Bajarilgan Qadamlar Tafsiloti (QADAM 1 – 18)

### ✅ QADAM 1: Ishlab Chiqish Muhiti va Bog'liqliklar
- Portativ vositalar sozlandi: OpenJDK 17 (`jdk-17.0.20.1+1`), Android SDK 37 (Build-Tools 36.0.0-rc4, Platform-Tools), Flutter 3.29.0, Git.
- Android NDK va CMake tayyorlandi, loyiha tuzilmasi shakllantirildi.

### ✅ QADAM 2: Flutter Shell va iOS Liquid Glass Dizayn Tizimi
- Apple VisionOS va iOS Liquid Glass interfeysi komponentlari yaratildi (`IosGlassContainer`, `IosSquircleCard`, `IosGlassButton`, `IosGlassSlider`, `IosGlassToggle`, `IosSegmentedControl`, `IosGlassDialog`).
- Kawase blur, 1px specular border, bahor (spring) fizikasi integratsiya qilindi.

### ✅ QADAM 3: Apparat Audit va Qurilma Mosligi Moduli
- Qurilma datchiklarini tekshirish zanjiri (`DeviceCheckController`):
  - **Giroskop yo'q bo'lsa:** AR rejimiga kirish qat'iy bloklanadi (TZ 4.2).
  - **ARCore yo'q bo'lsa:** Ogohlantirish bilan 3DoF (Giroskop) rejimiga avtomatik o'tkaziladi.
  - Kamera va OpenGL ES 3.0 mosligi tekshiriladi.
- Xavfsiz dialog oynalari va tushuntirish xabarlari kiritildi.

### ✅ QADAM 4: Launcher Menyusi va Foydalanuvchi Sozlamalari
- Asosiy ishga tushirish ekrani (`LauncherMenuScreen`):
  - Rejim tanlash: "👓 Stereo (Cardboard)" ↔ "📱 Oddiy (Bitta ekran)".
  - Datchik turi tanlash: "3DoF (Tezkor IMU)" ↔ "6DoF (ARCore fazoviy)".
  - IPD slayderi (55 mm dan 75 mm gacha, 64 mm sukut).
  - Past sifatli rejim (Low-spec mode) tugmasi.
  - "Qo'lni kalibratsiya qilish" yo'riqnomasi modal oynasi.
- Barcha sozlamalar Android `SharedPreferences` ga doimiy saqlanadi.

### ✅ QADAM 5: OpenGL ES 3.0 Passthrough Kamera Dvigateli
- `PassthroughRenderer.kt` va `ArEngineSurfaceView.kt` dvigatellari yaratildi.
- CameraX orqali orqa kamera oqimi bevosita OpenGL ES 3.0 teksturasiga (`GL_TEXTURE_2D`) minimal kechikish bilan (≤80ms) uzatiladi.
- 60 FPS uzluksiz render va `FpsTracker` orqali kechikish va kadr tezligi nazorat qilinadi.

### ✅ QADAM 6: Fazoviy Harakatni Kuzatish va World-Lock (3DoF/6DoF)
- `TrackingManager.kt`, pure-Kotlin `Quaternion.kt` va `Matrix4x4.kt` yaratildi.
- 3DoF IMU kvaternion filtri va 16ms bashoratlash (predictive tracking).
- "Markazlashtirish" (recenter) funksiyasi orqali drift bartaraf etildi.

### ✅ QADAM 7: Stereoskopik Ko'rish va Linzalar Distorsiyasi
- `StereoRenderer.kt` orqali Cardboard v2 linzalarining xromatik va barrel distorsiyasi shader pass yordamida to'g'rilandi.
- Ikki ko'z uchun alohida render buferlari va sozlanuvchi IPD siljishi.

### ✅ QADAM 8: 3D Fazoviy Oynalar Menejeri (WindowManager3D)
- `SpatialWindow.kt` va `WindowManager3D.kt`: fazoviy koordinatalar, anti-overlap joylashtirish algoritmi, z-order, fokus boshqaruvi, frustum culling va 6 ta faol oyna chegarasi.

### ✅ QADAM 9: Liquid Glass Shader Dvigateli (GlassRenderer)
- Real vaqtda Kawase dual-pass xiralashtirish, orqa fon sinishi (refraction), 1px specular bevel, squircle burchaklar va Spring dinamik fizikasi.

### ✅ QADAM 10: Google MediaPipe Qo'l Kuzatuvi (HandTracker)
- 21 ta 3D bo'g'inlarni aniqlash, One-Euro adaptiv titrash filtri, yo'naltiruvchi lazer nuri, hover nurlanishi.

### ✅ QADAM 11: Fazoviy Imo-ishoralar Dvigateli (GestureEngine)
- Chimchilash (Pinch), Tortish (Drag), Kattalashtirish (Zoom), Svayplar, Kaftni ochish (Palm-up), Musht (Fist), va `SpatialKeyboard` shisha klaviaturasi.

### ✅ QADAM 12: Home Panel, Dock va Plaginlar Tizimi
- Apple VisionOS uslubidagi dock, ilovalar to'ri, `AppRegistry` va `tools/generate_registry.dart` orqali manifestlar integratsiyasi.

### ✅ QADAM 13: Web Ilovalar, Umumiy Sessiya (SSO) va Germetik Sandbox
- `WebAppSurface.kt` orqali offscreen WebView larni OpenGL teksturalariga render qilish, tozalangan Chrome UA, `SessionStore` umumiy CookieManager, tashqi intentlar va xavfli sxemalarni bloklovchi `SandboxPolicy`.

### ✅ QADAM 14: Native Ilovalar (Kamera, Qaydlar, Soat, Kalendar)
- `CameraApp.kt` (foto/video, taymer, galereya), `NotesApp.kt` (SQLite bazasi, qidiruv), `ClockApp.kt` (Dunyo soati, Budilnik, Sekundomer, Taymer), `CalendarApp.kt` (oy/hafta/kun, voqealar).

### ✅ QADAM 15: AR Sozlamalar Ilovasi (SettingsApp)
- Barcha tizim parametrlarini (displey, fon, shaffoflik, blur, imo-ishoralar, audio, xavfsizlik) real vaqtda jonli boshqarish va saqlash.

### ✅ QADAM 16: O'yinlar I (2D Oflayn O'yinlar + AR Shaxmat)
- 6 ta to'liq oflayn HTML5/JS/Web Audio o'yinlar: Tetris, Dino Runner, Flappy Bird, Snake, 2048, hamda Minimax AI boti va Chess.com onlayn o'yiniga ega AR Shaxmat.

### ✅ QADAM 17: O'yinlar II (3D Stol Tennisi va 3D Fazoviy Mergan)
- **3D Table Tennis (`TableTennisApp.kt`):** ITTF 3D stol geometriyasi, Magnus effekti to'p fizikasi, 3 darajali AI bot, 11 ochkolik o'yin qoidalari, qo'l nuri bilan boshqaruv.
- **3D Shooter (`Shooter3DApp.kt`):** 10 ta to'lqin, 4 xil dushman turi, Lazer/Plazma/Raketa qurollari, AoE maydoniy portlash, lock-on nishon, 3D zarrachalar.

### ✅ QADAM 18: Samaradorlik, Barqarorlik, Maxfiylik va Tozalash
- **Adaptiv Sifat Nazorati (`AdaptiveQualityController.kt`):** Kadrlar tezligiga qarab 4 darajali sifat zanjiri (Ultra 60 FPS -> Balanced 30 FPS -> Economy 15 FPS -> Critical 10 FPS).
- **Uskuna Termal Monitori (`ThermalMonitor.kt`):** Android API 29+ `PowerManager.OnThermalStatusChangedListener` va `getThermalHeadroom` bilan integratsiya, qizishda avtomatik quvvatni tejash va ogohlantirish toastlari.
- **Xotira Bosimi va Oynalarni Uxlatish Siyosati:** 6 tadan ortiq oyna ochilganda yoki `onTrimMemory` signali kelganda nofaol orqa fondagi oynalar avtomatik uxlatiladi (`isSleeping = true`), teksturalar qayta yuklanishi cheklanadi.
- **WebView Crash Recovery:** `onRenderProcessGone` orqali web render jarayoni qulaganda yoki tizim OOM tufayli to'xtatganda AR sessiyani yiqitmasdan xavfsiz qayta tiklash.
- **Maxfiylik va Xavfsizlik:** `allowBackup="false"`, barcha tashqi intent sxemalari (`intent://`, `market://`, `tg://`, `instagram://`, `tel:`, `sms:`) bloklangan, statik kod lint tekshiruvi orqali kodda 0 ta noqonuniy intent ochilishi isbotlandi.
- **ProGuard / R8 Qoidalari:** MediaPipe, ARCore, CameraX, GLES va JavaScript interfeyslari uchun to'liq saqlash qoidalari o'rnatildi.
- **103 ta Unit Test:** Barcha testlar 100% yashil o'tdi (`BUILD SUCCESSFUL in 54s`).
- **TZ 16-bo'lim:** Barcha 15 ta qabul mezonlari bo'yicha to'liq audit yakunlandi.

---

## 3. Qolgan Ishlar Rejasi (5.3% — Yakuniy Bosqich)

AR Port to'liq yakunlanishi uchun oldimizda faqatgina **1 ta qadam** qoldi:

```
[==============================================--] 94.7% TAYYOR
```

### ⏳ QADAM 19 — Yakuniy Ishlab Chiqarish Buildi: `ar-port.apk` (~5.3%)
- Release imzolash konfiguratsiyasi (Test keystore va `docs/SIGNING.md`).
- R8 / ProGuard optimallashtirish, `arm64-v8a` arxitekturasi uchun yakuniy build.
- `flutter build apk --release` orqali yaratilgan APK ni loyiha ildizidagi **`dist/ar-port.apk`** ga joylash.
- APK faylining SHA-256 nazorat summasini, aniq hajmini hisoblash.
- To'liq o'rnatish va foydalanish bo'yicha yakuniy `README.md` hujjatini taqdim etish.

---

## 4. Loyihaning Joriy Texnik Holati va Ko'rsatkichlari

| Mezon | Natija | Holat |
|---|---|---|
| **JVM Unit Testlar** | **103 ta test** muvaffaqiyatli o'tdi | ✅ 100% Yashil (`BUILD SUCCESSFUL in 54s`) |
| **Flutter Widget / Unit Testlar** | **12 ta test** muvaffaqiyatli o'tdi | ✅ 100% Yashil (`All tests passed`) |
| **Flutter Statik Tahlil (`analyze`)** | **0 ogohlantirish, 0 xatolik** | ✅ Ideal toza (`No issues found`) |
| **Dvigatellar integratsiyasi** | CameraX + GLES 3.0 + MediaPipe + WebAppSurface + 3D Native Apps | ✅ To'liq bog'langan |
| **Adaptiv sifat va termal boshqaruv** | AdaptiveQualityController + ThermalMonitor + Window sleep policy | ✅ To'liq integratsiya qilingan |
| **Sandbox xavfsizligi** | Zero-Intent lint tekshiruvi: 0 ta qochish | ✅ 100% Germetik |
| **Git Repozitoriy** | `main` shoxi, 103 ta unit test bilan | ✅ Barcha fayllar saqlangan |

---

## 5. Xulosa va Keyingi Qadam

Loyiha tizimli, barqaror, xavfsiz va samaradorlik talablariga to'liq javob beruvchi holatga keltirildi.

Foydalanuvchi keyingi bosqichga o'tish uchun **"davom et"** buyrug'ini berganida, yakuniy **QADAM 19 (Yakuniy build: `ar-port.apk`)** ga kirishiladi.
