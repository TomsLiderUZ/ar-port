# AR Port — Spatial AR/VR Headset OS for Android

> **Android smartfoningizni Google Cardboard v2 (yoki mos VR boks) ko'zoynagi orqali to'laqonli fazoviy kompyuterga (Apple VisionOS darajasidagi Spatial OS) aylantiruvchi operatsion tizim qobig'i.**

---

## 📦 Yakuniy Chiqarilish Fayli (Release Build)

* **Fayl nomi:** `ar-port.apk`
* **Joylashuvi:** [`dist/ar-port.apk`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/dist/ar-port.apk)
* **Paket ID:** `uz.orbitx.arport`
* **Versiya:** `1.0.0` (Build `1`)
* **Hajmi:** **80.94 MB** (`84,868,139` bayt)
* **Target ABI:** `arm64-v8a` (+ `armeabi-v7a`, `x86_64`)
* **Minimal Android talabi:** Android 10 (API 29) yoki undan yuqori
* **Majburiy datchik:** Giroskop (Gyroscope sensor)
* **Imzo holati:** Release Keystore (`docs/SIGNING.md`) bilan to'liq imzolangan

### 🔐 SHA-256 Nazorat Summasi (Checksum)
```text
160072EC30F4395F5E85E96B9B2B51B506A06B29B4BD3CA1654D24291186F107
```

---

## 🚀 O'rnatish Yo'riqnomasi (Installation Guide)

### 1-usul: Kompyuterdan ADB orqali to'g'ridan-to'g'ri o'rnatish
1. Telefoningizda **USB Debugging** (Sozlamalar $\to$ Dasturchi parametrlari) ni yoqing.
2. Telefonni USB kabel orqali kompyuterga ulang.
3. Terminalda quyidagi buyruqni bajaring:
   ```bash
   adb install -r dist/ar-port.apk
   ```

### 2-usul: Telefonga tashlab o'rnatish
1. [`dist/ar-port.apk`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/dist/ar-port.apk) faylini telefoningizning *Downloads* yoki xotirasiga nusxalang.
2. Telefonda fayl menejeri orqali APK ni oching.
3. "Noma'lum manbalardan o'rnatish"ga ruxsat bering va o'rnatishni yakunlang.

---

## 👓 Foydalanish va Ishga Tushirish

1. **AR Port** ilovasini oching.
2. Boshlang'ich menyuda:
   - **Rejimni tanlang:** `👓 Stereo (Cardboard)` yoki `📱 Oddiy (Bitta ekran)`.
   - **IPD (ko'zlar orasidagi masofa):** Slayder orqali linzalaringizga moslang (sukut: 63 mm).
   - **Kamera ruxsati:** Kamera oqimiga ruxsat bering.
3. **"AR ni ishga tushirish"** tugmasini bosing va telefonni Cardboard ko'zoynagiga joylashtiring.

### ✋ Qo'l Imo-ishoralari va Fazoviy Boshqaruv (Spatial Control):
* **0° Toza Passthrough Ko'zgu:** Kamera giroskopga ulanmagan holda, 2 ta ko'zga 0° to'g'ridan-to'g'ri (direct passthrough) uzatiladi.
* **Ko'z Oldida Qotgan Statik Oynalar (Head-Locked HUD):** Asosiy menyu va Dock ko'z oldida statik qotgan holda turadi — bosh harakatlanganda qidirib yurish talab etilmaydi.
* **📌 Qotirish (Pin to World):** Dock-dagi yangi **📌 Qotirish** tugmasi orqali fokusdagi oynani jismoniy xona koordinatasiga mahkamlab qo'yish mumkin (World-Locked).
* **3D Xona Fazoviy Modeli (Room Mesh):** Orqa fonda ko'rinmas 3D pol, stol va devorlar maketi ishlab turadi; 3D o'yinlar (Stol tennisi, Shooter) to'qnashuvlari va nishonlar paydo bo'lishini ta'minlaydi.
* **Ikkala Qo'lni Kuzatish (Chap + O'ng):** MediaPipe orqali ikkala qo'lning 21 ta bo'g'ini fon oqimida (CPU XNNPACK) 60 FPS da kechikishsiz kuzatiladi.
* **Lazer nuri (Hand Ray):** Ko'rsatkich barmog'ingiz bilan fazodagi oynalar yoki elementlarga yo'naltiring.
* **Chimchilash (Pinch Tap):** Bosh barmoq va ko'rsatkich barmoq uchlarini tekkizib bosing (tanlash/bosish).
* **Suzib yurish (Drag):** Chimchilab ushlagan holda qo'lni fazoda siljiting (oynani surish).
* **Kattalashtirish (Two-Hand Zoom):** Ikkala qo'l bilan chimchilab oraliqni kengaytiring.
* **Home Dock (Palm-up):** Kaftingizni yuzingizga qaratib oching — VisionOS Home paneli ochiladi.
* **Oynani yopish (Fist):** Musht qilib buring yoki oyna burchagidagi qizil tugmaga bosing.

---

## 📱 18 ta O'rnatilgan Ilova va O'yinlar

| Turi | Ilova | Tavsif |
|---|---|---|
| **Web** | 🌐 **Google (Brauzer)** | Ko'p tabli ichki brauzer, manzil satri va sandboxed navigatsiya |
| **Web** | 📺 **YouTube** | Katta ekranli kinoteatr rejimi, HD video oqimi |
| **Web** | ✈️ **Telegram** | WebK/WebA fazoviy messenjer, bildirishnomalar |
| **Web** | 📷 **Instagram** | Lenta va Stories ko'rish |
| **Web** | 🎵 **Spotify** | Fazoviy fon musiqasi va pleylistlar |
| **Native** | 📸 **Kamera** | Foto/video suratga olish, taymer, old/orqa kamera almashish, sandbox galereyasi |
| **Native** | 📝 **Qaydlar** | SQLite doimiy xotirasi, qidiruv, eksport, shisha klaviatura |
| **Native** | ⏰ **Soat** | Dunyo soati, Taymer, Sekundomer oraliqlari va Budilnik |
| **Native** | 📅 **Kalendar** | Oy/hafta/kun ko'rinishlari, lokal hodisalar |
| **Native** | ⚙️ **Sozlamalar** | Fon, shaffoflik, blur, imo-ishoralar, ovoz, xavfsizlik va termal boshqaruv |
| **O'yin 2D** | 🧱 **Tetris** | Klassik oflayn bloklar, 4-tomonlama svayp, hisoblar xotirasi |
| **O'yin 2D** | 🦖 **Dino Runner** | Sakrash va egilish, kaktuslar, kundan-kunga tezlashuvchi chopish |
| **O'yin 2D** | 🐦 **Flappy Bird** | Chimchilash orqali qanot qoqish, trubalardan o'tish |
| **O'yin 2D** | 🐍 **Snake** | Klassik iloncha, mevalar, to'qnashuv fizikasi |
| **O'yin 2D** | 🔢 **2048** | Shisha plitkalar, birlashish animatsiyalari, Undo imkoniyati |
| **O'yin AR** | ♟️ **AR Shaxmat** | 3D shisha doska, Minimax AI (3 daraja) + Chess.com onlayn |
| **O'yin 3D** | 🏓 **3D Table Tennis** | ITTF 3D stol fizikasi, to'p aylanmasi (Magnus), 3 darajali AI bot, 11 ochkolik o'yin |
| **O'yin 3D** | 🎯 **3D Target Shooter** | 10 ta to'lqin, 4 dushman, 3 qurol (Lazer, Plazma, Raketa AoE), zarrachalar |

---

## 🔒 Xavfsizlik, Maxfiylik va Ruxsatlar

* **`CAMERA`:** Kamera passthrough va MediaPipe qo'l kuzatuvi uchun (kadrlar hech qayerga yuborilmaydi).
* **`RECORD_AUDIO`:** Ovozli kiritish, video yozish va Telegram qo'ng'iroqlari uchun.
* **`INTERNET` & `ACCESS_NETWORK_STATE`:** Faqat ruxsat etilgan web ilovalar uchun.
* **`allowBackup="false"`:** Qurilmadagi shaxsiy sessiyalar zaxira nusxalariga ko'chirilmaydi.
* **Germetik Sandbox:** `intent://`, `market://`, `tg://` kabi tashqi ilovalarni chaqirish qat'iy bloklangan; barcha tashqi havolalar faqat ichki brauzerda ochiladi (statik kod tahlilida 0 ta intent qochishi isbotlangan).

---

## ⚠️ Ma'lum Texnik Cheklovlar va Xavflar (docs/RISKS.md)

1. **Jismoniy Stereo Chuqurlik:** Standart telefonlarda bitta orqa kamera mavjudligi sababli, real fon ikkala ko'zga IPD siljishi bilan beriladi. Virtual oynalar va 3D obyektlar esa haqiqiy stereo burchaklar ostida chiziladi.
2. **Google OAuth WebView Cheklovi:** Tozalangan Chrome User-Agent va `SessionStore` umumiy CookieManager qo'llangan. Google xavfsizlik filtrlari ba'zi hisoblarda qo'shimcha tasdiqlash talab qilishi mumkin.
3. **Qurilma Isishi (Thermal Throttling):** Kamera + MediaPipe GPU + OpenGL render uzoq muddatda telefonni qizdirishi mumkin. Tizimda buning uchun 4 darajali `AdaptiveQualityController` va `ThermalMonitor` integratsiya qilingan bo'lib, harorat ko'tarilganda render sifati va orqa fon oynalari avtomatik tejamkor rejimga o'tkaziladi.

---

## 🧪 Sinov va Tekshirish Ko'rsatkichlari

* **JVM Unit Testlar:** **103 / 103** muvaffaqiyatli o'tdi (100% yashil).
* **Flutter Widget Testlar:** **12 / 12** muvaffaqiyatli o'tdi (100% yashil).
* **Flutter Analyze:** **0 ta ogohlantirish, 0 ta xatolik** (`No issues found`).
* **Release APK:** ProGuard / R8 optimallashtirilgan, release keystore bilan imzolangan.

---

## 📄 Hujjatlar Mundarijasi

* [`docs/TZ.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/TZ.md) — To'liq Texnik Topshiriq (18 bo'lim)
* [`docs/ARCHITECTURE.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/ARCHITECTURE.md) — Dvigatel va UI arxitekturasi
* [`docs/RISKS.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/RISKS.md) — Xavflar, cheklovlar va TZ 16-bo'lim qabul mezonlari
* [`docs/PROGRESS.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/PROGRESS.md) — Qadam-baqadam ishlab chiqish hisobotlari
* [`docs/SIGNING.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/SIGNING.md) — APK kalitini yaratish va imzolash qo'llanmasi
* [`docs/ADDING_AN_APP.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/docs/ADDING_AN_APP.md) — Yangi ilova yoki o'yin qo'shish qo'llanmasi
* [`All progres/LOYIHA_PROGRESI_VA_HOLATI.md`](file:///C:/ItzToms%20(all%20files)/ALL%20CODES/Demo%20Productions/ar-port/All%20progres/LOYIHA_PROGRESI_VA_HOLATI.md) — Umumiy holat va progress
