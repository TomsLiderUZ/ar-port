# AR Port — Loyihaning Umumiy Holati va Bajarilgan Ishlar Hisoboti

**Hujjat yaratilgan sana:** 2026-10-31  
**Loyiha:** AR Port — Spatial AR/VR Headset OS for Android  
**Paket nomi:** `uz.orbitx.arport`  
**Yakuniy mahsulot:** `dist/ar-port.apk`  
**Hozirgi umumiy progress:** **84.2%** (19 ta strategik qadamdan 16 tasi to'liq yakunlandi va sinovdan o'tdi)  
**Qolgan ish hajmi:** **15.8%** (3 ta qadam: QADAM 17, 18, 19)

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

---

## 2. Bajarilgan Qadamlar Tafsiloti (QADAM 1 – 16)

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
- `TrackingManager.kt`, `ImuTracker.kt`, pure-Kotlin `Quaternion.kt` va `Matrix4x4.kt` yaratildi.
- Telefon bosh harakatlari bilan birga 3D virtual sahnaning dunyoda qotib turishi (world-lock) ta'minlandi.
- "🎯 Markazlashtirish (Recenter)" funksiyasi orqali boshlang'ich qarash yo'nalishi bir zumda qayta sozlanadi (drift yo'qotiladi).

### ✅ QADAM 7: StereoRenderer va Optik Kalibratsiya
- `StereoRenderer.kt` va `LensProfile.kt` yaratildi.
- Google Cardboard v2 ($k_1 = 0.34, k_2 = 0.55$), Cardboard v1 va distorsiyasiz profillar.
- Chap va o'ng ko'z uchun alohida FBO, barrel pincushion egrilikni to'g'rilash pass, IPD linza siljishi va markaziy ajratuvchi chiziq (Cardboard divider).
- Ko'z zo'riqishining (diplopia) oldini oluvchi Dual Stereoscopic HUD.

### ✅ QADAM 8: WindowManager3D va ContentSurface (Fazoviy Oynalar)
- 3D suzuvchi oynalar boshqaruvchisi (`WindowManager3D.kt`, `SpatialWindow.kt`, `ContentSurface.kt`).
- Oyna holatlari: Normal, Minimized, Pinned, Fullscreen; 16:9, 9:16 va Free aspektlari.
- 3D nur bilan kesishish (ray-plane hit testing), sarlavha, yopish, yig'ish, pin va burchak o'lchamlari tutqichlari.
- Oynalarning bir-birini to'sib qo'ymasligi uchun anti-overlap (gorizontal va chuqurlik bo'yicha siljish) joylashtirish algoritmi.

### ✅ QADAM 9: GlassRenderer (Shader Shaffofligi va Estetika)
- Kawase dual-pass real-vaqt xiralashtirish (ping-pong FBO downsample).
- Squircle superellipse geometric shader, 1px specular rim highlight, yumshoq orqa soya.
- Oynalar uchun bahor (spring physics, damping ~0.8) tebranish animatsiyalari.

### ✅ QADAM 10: Google MediaPipe Hand Tracking & HandRenderer
- Google MediaPipe Tasks Vision (`hand_landmarker.task`) integratsiya qilindi.
- `HandTracker.kt` kameradan olingan kadrlarni tahlil qilib 21 ta 3D bo'g'in koordinatalarini aniqlaydi.
- `HandRenderer.kt` fazoda neon ko'k/oq rangda qo'l suyaklari skeletini, lazer nurini va masofaga qarab qisqaruvchi dinamik nishonni (reticle) 60 FPS da chizadi.

### ✅ QADAM 11: GestureEngine va Kirish Inyeksiyasi
- `GestureEngine.kt` va `InputInjector.kt`:
  - 10+ ta fazoviy imo-ishorani aniqlash: Pinch Tap, Index Point, Pinch Drag, Two-Hand Zoom, Fist Close, Palm-Up Home, Peace V Recenter, Thumbs Up Switcher, 4 tomonlama svayp.
  - Histerezis chegaralari (jitter va noto'g'ri signallarni bartaraf etish).
  - Virtual teginish tebranishlari (haptic feedback).

### ✅ QADAM 12: Suzuvchi Fazoviy Klaviatura va Ilovalar Boshqaruvi
- `SpatialKeyboard.kt`: 3D fazoda suzuvchi shisha klaviatura (UZ, EN, RU harf terish layoutlari, Raqamlar, Emojilar, Shift, Caps Lock, Space, Backspace, Enter).
- `SpatialDock.kt`: Apple VisionOS uslubidagi pastki suzuvchi glass dok-panel.
- `SpatialHomePanel.kt`: O'rnatilgan barcha ilovalar to'plamini ko'rsatuvchi 3D ilovalar oynasi.
- `AppRegistry.kt`: 18 ta ilovani avtomatik boshqaruvchi markaziy registr.

### ✅ QADAM 13: WebAppSurface, Xavfsiz Sandbox va SSO Sessiyalar
- `WebAppSurface.kt`: Android `WebView` ni offscreen OpenGL teksturasiga render qilish, fazoviy teginishlarni `MotionEvent` ga aylantirish.
- `SessionStore.kt`: 3-tomon cookie fayllari va umumiy SSO sessiyalari sinxronizatsiyasi.
- `SandboxPolicy.kt`: Tashqi intentlar (`intent://`, `tg://`, `instagram://`, `market://` va h.k.) 100% bloklandi; ruxsat etilmagan havolalar ichki Google Brauzerda ochiladi; Google login uchun toza User-Agent ta'minlandi.

### ✅ QADAM 14: Native Ilovalar (Kamera, Qaydlar, Soat, Kalendar)
- **Kamera (`CameraApp.kt`):** Foto/video yozish, old/orqa almashtirish, taymer, sandboxed galereya (`context.filesDir/ar_port_photos/`).
- **Qaydlar (`NotesApp.kt`, `NotesRepository.kt`):** SQLite/Room bazasi, toifalar, jonli matnli qidiruv, fazoviy klaviatura bilan yozish, `.txt` eksport.
- **Soat (`ClockApp.kt`):** Dunyo soati (Toshkent, London, Nyu-York, Tokio analog/raqamli disklari), Budilniklar, Millisoniyali Sekundomer (oraliq laptar bilan), Taymer (Pomodoro va boshqalar).
- **Kalendar (`CalendarApp.kt`):** Oy/hafta/kun ko'rinishlari, oylararo navigatsiya, hodisalar yaratish/o'chirish paneli.

### ✅ QADAM 15: AR Sozlamalar Ilovasi va Dinamik Xotira Ombori
- `SettingsStore.kt` va `SettingsApp.kt`:
  - VisionOS uslubidagi chap toifalar ro'yxati va o'ng batafsil boshqaruv paneli.
  - Displey (passthrough fon, xiralashtirish, shaffoflik, yorqinlik), Imo-ishoralar (shortkutlar xaritasi, sezgirlik, "Gest o'rgatish" rejimi), Oynalar (maksimal 1-8 oyna limiti, uyqu siyosati), Unumdorlik (30/60/90 FPS), Maxfiylik va Sandbox (sessiyalarni tozalash, sozlamalarni eksport/import).
  - O'zgarishlar bevosita real vaqtda render dvigateliga tatbiq etiladi.

### ✅ QADAM 16: Oflayn 2D O'yinlar To'plami va AR Shaxmat
- **Tetris (`tetris/index.html`):** 10x20 katak, neon tetraminolar, hold/next, chiziq yo'qotish animatsiyasi, Web Audio tovushlari, rekord.
- **Dino Runner (`dino/index.html`):** 16:9 keng format, kun/tun sikli, kaktus va pterodaktillar, sakrash/egilish.
- **Flappy Bird (`flappy/index.html`):** Neon qushcha, aniq impuls fizikasi, shisha quvurlar, medallar tizimi.
- **Snake (`snake/index.html`):** Zumrad iloncha, devorli/devorsiz rejimlar, tezlik bosqichlari, olma va oltin yulduz ovqatlari.
- **2048 (`g2048/index.html`):** Rangli shisha plitkalar, birlashish, 1 marta orqaga qaytarish (Undo) tugmasi (TZ 9.3).
- **AR Shaxmat + Chess.com (`chess/index.html`):** Qimmatbaho shisha doska, FIDE qoidalari, Minimax AI boti (3 daraja), yurishlar tarixi, urib olingan donalar; `"🌐 Chess.com (Onlayn)"` tugmasi orqali jonli Chess.com o'yinlariga o'tish.
- Barcha o'yinlar `window.onArGesture` fazoviy imo-ishoralari (Pinch, Tap, 4-tomonlama svayp) bilan boshqariladi.

---

## 3. Qolgan Ishlar Rejasi (15.8% — Yakuniy Bosqich)

AR Port to'liq yakunlanishi uchun oldimizda faqatgina **3 ta qadam** qoldi:

```
[========================================------] 84.2% TAYYOR
```

### ⏳ QADAM 17 — O'yinlar II: 3D Table Tennis (botlar) va 3D Shooter (~5.3%)
- **3D Table Tennis (Stol Tennisi):**
  - Foydalanuvchining qo'l pozitsiyasi to'g'ridan-to'g'ri 3D raketkaga aylanadi.
  - To'p fizikasi: trayektoriya, spin, stol va to'rga urilish elastikligi.
  - 3 darajadagi AI bot raqibi (Oson, O'rta, Qiyin), 11 ochkogacha hisob yuritish.
- **3D Shooter (Fazoviy Mergan):**
  - Lazer nuri = nishonga olish, chimchilash (Pinch) = otish.
  - To'lqinli (waves) fazoviy nishonlar, 3 xil qurol turi, qayta o'qlash mexanikasi.
  - Sog'liq va o'q-dori shisha HUD paneli, 3 qiyinchilik darajasi.
  - 60 FPS uzluksiz kadr tezligi.

### ⏳ QADAM 18 — Samaradorlik, Barqarorlik, Maxfiylik va Tozalash (~5.3%)
- **Adaptiv Sifat Nazorati:** Kadrlar tezligi 45 FPS dan pastga tushsa, render rezolyutsiyasi va Kawase blur sifatini avtomatik moslashuvchan pasaytirish.
- **Termal va Resurslar Nazorati:** Batareya va protsessor qizishini monitoring qilish, fondagi oynalarni avtomatik "uxlatish" (Sleep policy).
- **Xavfsizlik va R8 Audit:** `allowBackup=false`, tashqi intentlar yo'qligini qat'iy tekshirish, ProGuard / R8 qoidalarini tozalash.
- **Stress Sinovlari:** Bir vaqtning o'zida 6 ta oyna bilan 30+ daqiqalik uzluksiz barqarorlik testi, xotira oqishlarini (memory leaks) tozalash.
- **TZ 16-bo'lim:** Loyiha talablari qabul mezonlari jadvalini to'ldirish.

### ⏳ QADAM 19 — Yakuniy Ishlab Chiqarish Buildi: `ar-port.apk` (~5.2%)
- Release imzolash konfiguratsiyasi (Test keystore va `docs/SIGNING.md`).
- R8 / ProGuard optimallashtirish, `arm64-v8a` arxitekturasi uchun yakuniy build.
- `flutter build apk --release` orqali yaratilgan APK ni loyiha ildizidagi **`dist/ar-port.apk`** ga joylash.
- APK faylining SHA-256 nazorat summasini, aniq hajmini hisoblash.
- To'liq o'rnatish va foydalanish bo'yicha yakuniy `README.md` hujjatini taqdim etish.

---

## 4. Loyihaning Joriy Texnik Holati va Ko'rsatkichlari

| Mezon | Natija | Holat |
|---|---|---|
| **JVM Unit Testlar** | **69 ta test** muvaffaqiyatli o'tdi | ✅ 100% Yashil (`BUILD SUCCESSFUL in 35s`) |
| **Flutter Widget / Unit Testlar** | **12 ta test** muvaffaqiyatli o'tdi | ✅ 100% Yashil (`All tests passed`) |
| **Flutter Statik Tahlil (`analyze`)** | **0 ogohlantirish, 0 xatolik** | ✅ Ideal toza (`No issues found`) |
| **Debug APK Yig'ilishi** | `build\app\outputs\flutter-apk\app-debug.apk` | ✅ Yig'ilgan (215.90 MB, exit code 0) |
| **Dvigatellar integratsiyasi** | CameraX + GLES 3.0 + MediaPipe + WebAppSurface | ✅ To'liq bog'langan |
| **Oflayn resurslar** | 6 ta o'yin HTML5/JS/Web Audio assets ichida | ✅ 100% Oflayn ishlaydi |
| **Git Repozitoriy** | `master` shoxi, oxirgi kommit: `9088ecc` | ✅ Barcha fayllar saqlangan |

---

## 5. Xulosa va Keyingi Qadam

Loyiha eng qiyin va mas'uliyatli qismlaridan muvaffaqiyatli o'tdi: arxitektura, passthrough kamera, stereo linzalar, optik shaderlar, MediaPipe qo'l kuzatuvi, suzuvchi oynalar, xavfsiz sandbox, native ilovalar va 6 ta to'liq o'yin tayyor holatda.

Foydalanuvchi keyingi bosqichga o'tish uchun **"davom et"** buyrug'ini berganida, to'g'ridan-to'g'ri **QADAM 17 (3D Table Tennis va 3D Shooter)** ga kirishiladi.
