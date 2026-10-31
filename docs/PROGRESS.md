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
| **6** | Harakat kuzatuvi va fazoda qotirish (world-lock) | ✅ Bajarildi | TrackingManager (3DoF/6DoF), kvaternion silliqlash va 16ms bashorat, View/Projection matritsalar, WorldObjectRenderer (fazoda qotuvchi 3D test oynasi), Markazlashtirish (recenter) |
| **7** | Stereo rejim | ✅ Bajarildi | StereoRenderer (dual FBO, Cardboard v2 barrel distortion, k1/k2, IPD siljishi, markaziy ajratuvchi chiziq, jonli kalibratsiya dialogi, real vaqtda Stereo ↔ Oddiy almashinuvi, dual stereoscopic HUD) |
| **8** | Fazoviy oyna menejeri va yuza (surface) tizimi | ✅ Bajarildi | SpatialWindow modeli, WindowManager3D, ContentSurface (offscreen canvas/texture), anti-overlap joylash, z-order, fokus, sleep siyosati, frustum culling, 3 ta sinov oynasi |
| **9** | Liquid Glass renderer | ✅ Bajarildi | GlassRenderer (Kawase/dual-filter blur, 1/2 va 1/4 FBO ping-pong), squircle superellipse burchaklar, chekka nuri, specular yaltirash, SpringAnimation (Apple fizika burchagi va taktil tebranish), kamaytirilgan shaffoflik rejimi |
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

---

## QADAM 6 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Yuqori aniqlikdagi kvaternion matematikasi (`Quaternion.kt`): ko'paytirish, normallashtirish, teskari (inverse), slerp (sferik chiziqli interpolyatsiya), toRotationMatrix, fromRotationMatrix, yaw burchagini hisoblash va axis-angle konstruktori.
  2. Harakat kuzatuvi menejeri (`TrackingManager.kt`):
     - `Sensor.TYPE_ROTATION_VECTOR` (va `TYPE_GAME_ROTATION_VECTOR` zaxira) datchigini `SENSOR_DELAY_FASTEST` bilan tinglash.
     - Landshaft ekran aylanishi bo'yicha koordinatalarni to'g'ri qayta xaritalash (`SensorManager.remapCoordinateSystem`).
     - Titrashni yo'qotuvchi past chastotali kvaternion SLERP filtrlash (`alpha = 0.65`).
     - Kechikishni kompensatsiya qiluvchi prediktiv bashorat (`lookahead = 16 ms`, 60 FPS da bitta kadr oldinga surish).
     - 6DoF (ARCore Pose) va 3DoF datchik holatlarini qo'llab-quvvatlash.
     - "Markazlashtirish" (Recenter / drift correction): datchik dreyfini nolga tenglab, foydalanuvchining hozirgi qarash yo'nalishini dunyo bo'yicha oldinga (`-Z`) yo'naltiruvchi mexanizm.
     - View Matrix ($V = R^{-1} \cdot T^{-1}$) va Perspective Projection Matrix generatsiyasi; Stereo rejim uchun mahalliy ko'z siljishi ($\pm IPD/2$).
  3. Fazoda qotib turuvchi 3D sinov obyekti renderer (`WorldObjectRenderer.kt`):
     - Dunyo koordinatasida 1.5 metr masofada ($(0, 0, -1.5)$) qotib turuvchi 16:9 spatial karta (0.85m x 0.48m).
     - OpenGL ES 3.0 shaderlari: iOS Liquid Glass chekka nuri (`#0A84FF`), nozik ichki spatial panjara va nishonga olish retikulasi, chuqurlik buferi (depth buffer) va alfa aralashuvi (alpha blending).
  4. Render sikli integratsiyasi (`ArEngineRenderer.kt`):
     - Fon sifatida passthrough chiziladi (depth write o'chiq).
     - Chuqurlik buferi yoqilib, Stereo rejimda chap va o'ng ko'zlar uchun IPD burchagi bilan, Oddiy rejimda bitta markaziy ko'z bilan 3D dunyo obyekti chiziladi.
     - Uzluksiz 60 FPS yangilanishi (`RENDERMODE_CONTINUOUSLY`).
  5. Foydalanuvchi interfeysi va boshqaruv (`ArActivity.kt`):
     - Yuqorida doimiy "🎯 Markazlashtirish" frosted glass tugmasi (bosilganda haptic feedback bilan driftni qayta sozlaydi).
     - Jonli Debug HUD datchik holatini ("3DoF (Giroskop)" yoki "6DoF (ARCore)") aks ettiradi.
  6. Tekshiruv va sinovlar:
     - JVM unit testlari (`QuaternionTest.kt` da 7 ta test: identity, normalize, multiplication, inverse/conjugate, slerp, yaw/recenter, matrix round-trip) to'liq muvaffaqiyatli o'tdi (`BUILD SUCCESSFUL`).
     - Flutter testlari (12 ta test) va Flutter statik tahlili (`flutter analyze` — 0 ogohlantirish) to'liq o'tdi.
     - Debug APK (`flutter build apk --debug`) muvaffaqiyatli yig'ildi (`build\app\outputs\flutter-apk\app-debug.apk`, 215.8 MB).

---

## QADAM 7 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Optik linza profillari modeli (`LensProfile.kt`): Google Cardboard v2 ($k_1 = 0.34, k_2 = 0.55$), Google Cardboard v1 ($k_1 = 0.441, k_2 = 0.156$) va tekis distorsiyasiz ($k_1 = 0, k_2 = 0$) profillar tayyorlandi.
  2. Stereo renderlash tizimi (`StereoRenderer.kt`):
     - Chap va o'ng ko'zlar uchun alohida offscreen FBO (Color Texture + Depth Renderbuffer).
     - Barrel distorsiyasi (pincushion optik egrilikni yo'qotuvchi shader pass: $r' = r \cdot (1 + k_1 r^2 + k_2 r^4)$).
     - Optik chegaradan tashqariga chiqqan piksellarni vinetkalash (vignette black).
     - IPD bo'yicha linza markazi siljishi ($uLensCenter$).
     - Ikki ko'z orasidagi optik o'tishni to'suvchi vertikal markaziy ajratuvchi chiziq (Cardboard divider).
  3. Render sikli integratsiyasi (`ArEngineRenderer.kt`):
     - Stereo rejimda chap ko'z sahnasini chap FBO ga, o'ng ko'z sahnasini o'ng FBO ga chizib, so'ng `StereoRenderer.finishAndDistort()` orqali ekranga barrel distorsiya bilan uzatadi.
     - Oddiy rejimda FBO lar aylanib o'tilib, to'g'ridan-to'g'ri to'liq ekranga (single viewport) render qilinadi.
     - Jonli sozlamalar yangilanishi (`updateStereoSettings`).
  4. Foydalanuvchi interfeysi va boshqaruv (`ArActivity.kt`):
     - "👓 Stereo" ↔ "📱 Oddiy" tezkor rejim almashtirish tugmasi.
     - "⚙️ Kalibratsiya" frosted glass modal dialogi: jonli IPD slayderi (55 mm – 75 mm) va linza profili tanlovi (Cardboard v2, Cardboard v1, Distorsiyasiz).
     - Dual Stereoscopic HUD: stereo rejimda ko'z zo'riqishi (diplopia) ning oldini olish maqsadida ma'lumotlar ikkala ko'z oynasida bir xil nisbatda chiziladi (TZ 5.3).
  5. Tekshiruv va sinovlar:
     - JVM unit testlari (`StereoTest.kt` va `QuaternionTest.kt` bo'yicha 10 ta test) muvaffaqiyatli o'tdi (`BUILD SUCCESSFUL`).
     - Flutter testlari (12 ta test) va Flutter statik tahlili (`flutter analyze` — 0 ogohlantirish) to'liq o'tdi.
     - Debug APK (`flutter build apk --debug`) muvaffaqiyatli yig'ildi (`build\app\outputs\flutter-apk\app-debug.apk`, 215.8 MB).

---

## QADAM 8 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Fazoviy oyna modeli (`SpatialWindow.kt`):
     - `position` ($\text{vec3}$), `orientation` (kvaternion), `size` ($W, H$ metrda), `aspect` (16:9 sukut, 9:16 portrait, Free), `state` (Normal, Minimized, Pinned, Fullscreen).
     - Model matritsasi generatsiyasi ($M = T \cdot R \cdot S$) `Matrix4x4` yordamida.
     - 3D nur bilan kesishishni tekshirish (ray-plane hit testing) va oyna elementlarini (Close, Minimize, Pin, Title bar, Bottom handle, Resize handle) aniqlash.
  2. Offscreen yuza tizimi (`ContentSurface.kt`):
     - Android Canvas va 2D bitmapni to'g'ridan-to'g'ri OpenGL `GL_TEXTURE_2D` ga yuklash tizimi.
     - Faqat o'zgarganda yangilanuvchi `isDirty` mexanizmi.
  3. Fazoviy oyna boshqaruvchisi (`WindowManager3D.kt`):
     - Oynalarni yaratish, yopish, yig'ish (minimize), tiklash va z-tartib bo'yicha fokusni boshqarish.
     - Qarash yo'nalishida (1.6 metr masofada) ustma-ust tushmaydigan (anti-overlap horizontal/depth stagger) joylashtirish algoritmi.
     - Maksimal oyna chegarasi (sukut 6 ta) oshganda eng eski faol bo'lmagan oynani "uxlatish" (sleep policy) qoidasi.
     - Frustum culling: kamera ko'rish maydonidan tashqarida bo'lgan oynalarni renderlashdan va yangilashdan chiqarish.
     - QADAM 8 talabi bo'yicha 3 ta sinov oynasi:
       - 1-oyna (16:9): "Tizim Monitori" (FPS, kechikish, datchik holati jonli metrikalari).
       - 2-oyna (9:16): "Fazoviy Eslatmalar" (iOS ro'yxat va vazifalar).
       - 3-oyna (16:9): "Media Pleer" (musiqa muqovasi va pleyer boshqaruvi).
  4. OpenGL ES 3.0 oyna rendereri (`WindowRenderer3D.kt`):
     - Z-tartib bo'yicha orqadan oldinga chizish (alfa aralashuvi to'g'ri ishlashi uchun).
     - Squircle yumaloq burchaklarni qirqish shaderi (`discard`).
     - Fokusdagi oyna uchun jonli `#0A84FF` ko'k nurli ramka, nofaol oynalar uchun shaffof kulrang ramka.
  5. Render sikli va foydalanuvchi interfeysi (`ArEngineRenderer.kt` va `ArActivity.kt`):
     - Stereo va oddiy rejimlarda `WindowRenderer3D` integratsiyasi.
     - Yuqorida yangi oynalar qo'shuvchi "+ Oyna" tugmasi.
     - Oddiy rejimda ekranni bosish orqali 3D fazodagi oynalarni fokuslash, yopish va o'lchamini almashtirish (touch raycasting).
  6. Tekshiruv va sinovlar:
     - JVM unit testlari (`WindowTest.kt`, `StereoTest.kt`, `QuaternionTest.kt` bo'yicha jami 18 ta test) to'liq muvaffaqiyatli o'tdi (`BUILD SUCCESSFUL in 27s`).
     - Flutter testlari (12 ta test) va Flutter tahlili (`flutter analyze` — 0 ogohlantirish) to'liq o'tdi.
     - Debug APK (`flutter build apk --debug`) muvaffaqiyatli yig'ildi (`build\app\outputs\flutter-apk\app-debug.apk`, 215.8 MB).

---

## QADAM 9 Batafsil Hisoboti
- **Sana:** 2026-10-31
- **Bajarildi:**
  1. Kawase / Dual-filtering blur tizimi (`GlassRenderer.kt`):
     - Kamera passthrough sahnasini 1/2 yoki 1/4 (yengil rejim) ruxsatga tushirish (downsampling).
     - Ping-pong FBO quvuri orqali diagonal 4-tap Kawase filtri (Gaussian blurdan 3 barobar tezroq, minimal kechikish).
  2. Liquid Glass shader va vizual qatlamlar (`WindowRenderer3D.kt`):
     - Ekran koordinatasi bo'yicha orqa fonni nozik optik sindirish (refraction) bilan xiralashtirib ko'rsatish (`uBlurredBackground`).
     - Squircle superellipse burchak formulasi ($x^4 + y^4 \le 1.0$) bo'yicha haqiqiy iOS shaklida qirqish (`discard`).
     - iOS qorong'i glass tinti (`#1C1C1E`, alpha = 0.65) va och rejim tinti (`#F2F2F7`).
     - Chekka nuri (Edge highlight) — oynaning burchaklarida nozik yorug'lik hoshiyasi (fokusda `#0A84FF`, boshqalarida yarim shaffof oq-kulrang).
     - Specular yaltirash (yuqori chekkadagi yorug'lik nuri).
  3. Apple uslubidagi jismoniy prujina animatsiyalari (`SpringAnimation.kt`):
     - Damping ratio $\zeta = 0.82$, burchak chastotasi $\omega = 24.0$ rad/s.
     - Oyna ochilishida organik sakrash ($0.0 \to 1.0$), bosilganda taktil tebranish ($0.96 \to 1.0$), yopilish/yig'ilishda silliq kichrayish.
  4. Optimallashtirish va moslashuvchan boshqaruv (`ArActivity.kt`):
     - "Kamaytirilgan shaffoflik" (Reduce Transparency) rejimi: blur o'chirilib, qattiqroq rangli fon qo'yiladi (GPU energiyasini tejash).
     - "Yengil rejim" (Low Spec 1/4 blur): o'rta va past toifali qurilmalarda FPS $\ge 45$ barqarorligini ta'minlash.
     - Kalibratsiya dialogida Liquid Glass parametrlarini jonli almashtirish.
  5. Tekshiruv va sinovlar:
     - JVM unit testlari (`SpringTest.kt`, `WindowTest.kt`, `StereoTest.kt`, `QuaternionTest.kt` bo'yicha jami 22 ta test) to'liq muvaffaqiyatli o'tdi (`BUILD SUCCESSFUL in 59s`).
     - Flutter testlari (12 ta test) va Flutter tahlili (`flutter analyze` — 0 ogohlantirish) to'liq o'tdi.
     - Debug APK (`flutter build apk --debug`) muvaffaqiyatli yig'ildi (`build\app\outputs\flutter-apk\app-debug.apk`, 215.8 MB).
