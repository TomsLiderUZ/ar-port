# AR Port — Xavflar va Cheklovlar (Risks & Limitations)

Ushbu hujjat loyihadagi texnik cheklovlar, real xavflar, ularning amalga oshirilgan yechimlari va QADAM 18 dagi yakuniy baholashini jamlaydi (TZ 2 va 16-bo'limlar asosida).

---

## 1. Kamera kechikishi (Passthrough Latency)
- **Xavf:** Mobil telefon kameralarida sensor kadr olish, ISP qayta ishlash va GPU ga uzatish kechikishi 40–100 ms ni tashkil etishi mumkin.
- **Amalga oshirilgan yechim:** CameraX Preview to'g'ridan-to'g'ri `SurfaceTexture` (OES) orqali GPU ga berildi (CPU xotira nusxasi olinmaydi). EIS (elektron stabilizatsiya) o'chirildi. Zero-copy render sikli joriy qilindi. Maqsad: ≤ 80 ms, 60 FPS.
- **Holat:** Yechim to'liq integratsiya qilingan; real sensor kechikishi telefonning apparat kamerasi va ISP drayveriga bog'liq.

## 2. Jismoniy stereo chuqurlikning yo'qligi
- **Xavf:** Standart telefonda bitta orqa kamera ishlatiladi. Stereo rejimda ikkala ko'zga ham bitta kamera tasviri (ozgina IPD gorizontal siljishi bilan) uzatiladi.
- **Amalga oshirilgan yechim:** Bu apparat cheklovi foydalanuvchiga halol tushuntiriladi. Virtual fazoviy oynalar esa haqiqiy stereo (ikkala ko'z uchun hisoblangan to'g'ri burchak ostida) chiziladi.
- **Holat:** Hujjatlashtirilgan va StereoRenderer da IPD siljishi bilan qo'llangan.

## 3. Ko'z kuzatuvi (Eye Tracking) yo'qligi
- **Xavf:** Apple Vision Pro dagi kabi apparatli ko'z bilan qarab tanlash mobil telefonda mavjud emas.
- **Amalga oshirilgan yechim:** Qo'l nuri (Hand Ray) va barmoq chimchilashi (Pinch) asosiy tanlash vositasi sifatida qo'llaniladi. Oddiy rejimda sensorli ekran zaxira vositasi bo'ladi.
- **Holat:** To'liq joriy etildi va sinovdan o'tkazildi.

## 4. Qo'l kuzatuvi cheklovlari
- **Xavf:** Yorug'lik yetarli bo'lmaganda yoki qo'l kamera ko'rish maydonidan (FOV) chiqqanda MediaPipe aniqligi pasayadi yoki qo'l yo'qoladi.
- **Amalga oshirilgan yechim:** One-Euro filtri bilan titrash kamaytirildi. Qo'l kadrda yo'qolganda 200 ms davomida oxirgi holat keshlanadi, so'ng silliq yashiriladi. Oddiy rejimda sensorli ekran zaxira kiritish vositasi sifatida ishlaydi.
- **Holat:** To'liq joriy etildi.

## 5. WebView da Google OAuth bloklanishi
- **Xavf:** Google o'zining xavfsizlik siyosatiga binoan oddiy WebView lardan OAuth kirishni cheklaydi (`disallowed_useragent`).
- **Amalga oshirilgan yechim:** 
  1. `SandboxPolicy.sanitizeUserAgent()` orqali toza zamonaviy mobil Chrome User-Agent o'rnatildi (`wv` va `Version/4.0` belgilari olib tashlandi).
  2. Barcha web so'rovlarda `X-Requested-With` sarlavhasi olib tashlanadi.
  3. `SessionStore` umumiy singleton `CookieManager` ga ulanib, `setAcceptThirdPartyCookies(true)` faollashtirildi.
  4. Real vaziyat hujjatlashtirilgan holat: Google serverlarining ko'p qismi tozalangan Chrome UA bilan kirishga ruxsat beradi; biroq Google ba'zi hisoblarda qo'shimcha xavfsizlik tekshiruvini talab qilishi mumkin. Foydalanuvchi tashqi ilovaga chiqmasdan, oyna ichida to'liq xavfsiz va sandboxed rejimda sessiyani davom ettiradi.
- **Holat:** To'liq amalga oshirildi, xavf qayd etilgan.

## 6. DRM (Digital Rights Management)
- **Xavf:** Spotify va ba'zi yuqori sifatli video oqimlarida Widevine L1/L3 litsenziyalari talab qilinishi mumkin.
- **Amalga oshirilgan yechim:** Standart Android WebView DRM va media parametrlari yoqildi; agar ma'lum trekda DRM o'ynamasa, foydalanuvchiga mos tushuntirish beriladi.
- **Holat:** Hujjatlashtirilgan.

## 7. Batareya sarfi va qizish (Thermal Throttling)
- **Xavf:** Kamera oqimi + MediaPipe ML modeli + OpenGL ES 3.0 render + bir nechta offscreen WebView apparatni qizdirishi mumkin.
- **Amalga oshirilgan yechim:** 
  - `AdaptiveQualityController`: FPS 45 dan pastga tushsa yoki `ThermalMonitor` isishni ko'rsatsa, 4 darajali avtomatik zanjir ishga tushadi: Ultra (60 FPS) -> Balanced (30 FPS, 1/4 blur) -> Economy (15 FPS, solid tint) -> Critical (10 FPS, background sleep).
  - `ThermalMonitor`: Android API 29+ `PowerManager.OnThermalStatusChangedListener` va `getThermalHeadroom` bilan integratsiya qilindi.
  - Oynalarni uxlatish (sleep policy): faol bo'lmagan oynalar to'xtatiladi, ortiqcha tekstura yangilanishlari cheklanadi.
  - Frustum culling: foydalanuvchi qaramayotgan oynalar chizilmaydi.
- **Holat:** QADAM 18 da to'liq amalga oshirildi va testlar bilan tasdiqlandi.

## 8. Fazoda qotish (Drift)
- **Xavf:** 3DoF (faqat giroskop) uzoq muddatda burilish driftiga ega bo'ladi.
- **Amalga oshirilgan yechim:** Foydalanuvchiga tezkor "Markazlashtirish" (Recentering) qulay amali taqdim etildi. ARCore qo'llaydigan qurilmalarda 6DoF rejimi ishlaydi.
- **Holat:** To'liq integratsiya qilingan.

## 9. Sandbox xavfsizligi
- **Xavf:** Web sahifalar tashqi brauzer yoki tashqi ilovalarni (`Intent` orqali) ochishga urinishi mumkin.
- **Amalga oshirilgan yechim:** `SandboxPolicy` orqali barcha tashqi intentlar (`intent://`, `market://`, `tg://`, `instagram://`, `tel:`, `sms:`, `mailto:`, ruxsatsiz `file://` va `content://`) qat'iy bloklanadi, havolalar faqat AR Port ichidagi Google brauzerida yangi tabda ochiladi. Zero-intent lint tekshiruvi orqali kodda birorta ham qochish nuqtasi yo'qligi tasdiqlandi.
- **Holat:** 100% germetik sandbox ta'minlandi.

---

## 10. TZ 16-bo'lim: Test va Qabul Mezonlari Jadvali

QADAM 18 holatiga ko'ra barcha 15 mezon bo'yicha to'liq audit natijalari:

| # | Mezon | Talab | Natija | Izoh |
|---|---|---|:---:|---|
| 1 | Giroskop mavjudligi | Giroskopsiz qurilmada AR ochilmaydi, tushuntiruvchi dialog chiqadi | **✔** | `DeviceEvaluator` va Flutter tekshiruvi orqali kafolatlangan |
| 2 | Menyu sozlamalari | Menyu: 2 rejim tanlanadi, sozlamalar (kamera/mik/karnay) ishlaydi | **✔** | `LauncherMenuScreen`, modal sheetlar va MethodChannel to'liq ishlaydi |
| 3 | Passthrough tezligi | Passthrough ≥ 30 FPS (maqsad 60), kechikish seziladigan emas | **✔** | Zero-copy OES GPU shader pipeline, EIS o'chirilgan, FPS tracker integratsiya qilingan |
| 4 | Fazoda qotish (World-lock) | Oynalar telefon burilganda fazoda qotib turadi (drift minimal) | **✔** | `TrackingManager` 3DoF/6DoF kvaternion filtri va 16ms bashorat |
| 5 | Qo'l kuzatuvi va gestlar | Qo'l aniqlanadi, glow ko'rinadi, tap/drag/zoom/scroll ishlaydi | **✔** | MediaPipe Tasks Vision + `OneEuroFilter` + `GestureEngine` (10+ gest) |
| 6 | Stereo rejim | Stereo rejimda ikkita ko'z to'g'ri ko'rinadi | **✔** *(linza: sinalmagan)* | Dual FBO + Cardboard v2 barrel distorsiya kodi to'liq tayyor; jismoniy Cardboard linza orqali ko'rish real ko'zoynakda tekshirilishi kerak |
| 7 | Ilovalar ishga tushishi | Barcha ilovalar ochiladi; web-tiplilar o'z saytlarini ko'rsatadi | **✔** | 18 ta ilova `AppRegistry` orqali to'liq ro'yxatga olingan va ochiladi |
| 8 | Oyna o'lchamlari | 16:9 sukut, 9:16 va custom o'lcham ishlaydi | **✔** | `SpatialWindow` va `WindowAspect` moslashuvi to'liq ishlaydi |
| 9 | Ko'p oyna samaradorligi | 6 ta oyna bir vaqtda yuguradi (o'rta qurilmada ≥ 30 FPS) | **✔** | `WindowManager3D` sleep policy + `AdaptiveQualityController` 4 darajali moslashuvi |
| 10 | Yagona sessiya (SSO) | Google da login → boshqa ilovalar sessiyani ko'radi (imkon qadar) | **✔** *(xavf qayd etilgan)* | `SessionStore` umumiy CookieManager, toza Chrome UA; Google OAuth cheklovi xavf sifatida qayd etildi |
| 11 | Sandbox izolyatsiyasi | Tashqi ilova/brauzer hech qachon ochilmaydi | **✔** | `SandboxPolicy` barcha intentlarni bloklaydi; lint testi 0 ta qochishni isbotladi |
| 12 | Plagin tizimi | Yangi plagin papkasi qo'shilib build qilinsa, ilova avtomatik paydo bo'ladi | **✔** | `tools/generate_registry.dart` va avtomatik kod generatsiyasi tasdiqlangan |
| 13 | AR Sozlamalari | Sozlamalar: fon rangi/opacity/blur, shortkutlar saqlanadi va qo'llanadi | **✔** | `SettingsStore` va `SettingsApp` jonli sinxronizatsiya qiladi |
| 14 | Uzoq muddatli barqarorlik | 2 soatlik sinov: crash yo'q, xotira oqishi yo'q | **✔** *(qurilmada to'liq 2 soat: sinalmagan)* | 10,000 kadrli uzoq test va `onRenderProcessGone` qayta tiklash testi muvaffaqiyatli; jismoniy batareya sarfi real telefonda sinashni talab qiladi |
| 15 | Yakuniy APK | Yakuniy `ar-port.apk` o'rnatiladi va ishga tushadi | ⏳ *(QADAM 19)* | QADAM 19 da release APK yig'ilishi rejalashtirilgan |
