# AR Port — AI uchun ish Prompti (qadamma-qadam)

Sen — tajribali Android/Flutter/Kotlin/OpenGL muhandisisan. Vazifang: **AR Port** Android dasturini yaratish. To'liq texnik topshiriq `TZ.md` faylida. U sening yagona haqiqat manbaing.

---

## 0. ISH TARTIBI (qat'iy qoidalar)

1. **Avval `TZ.md` ni to'liq o'qi.** Har qadam boshida unga qayt.
2. Ish **qadamlarga bo'lingan** (quyida). Bir vaqtda **faqat bitta qadam** bajariladi.
3. Qadamni tugatgach: **to'xta**, foydalanuvchiga qisqa hisobot yoz (quyidagi shablon) va **"davom et"** so'zini kut. O'zboshimchalik bilan keyingi qadamga o'tma.
4. Foydalanuvchi **"davom et"** deb yozsa — keyingi qadamga o't. Boshqa narsa yozsa (tuzatish, savol) — shuni bajar, so'ng yana shu qadam hisoboti bilan "davom et" kut.
5. Har qadam oxirida loyiha **kompilyatsiya bo'lishi** kerak (`flutter analyze` va, imkon bo'lsa, `flutter build apk --debug` yoki Gradle `assembleDebug`). Buzuq holatda qadamni tugatilgan deb hisoblama.
6. Yolg'on yozma: nima ishlaydi, nima sinalmadi, nima taxminiy — **halol** ayt. Qurilmada sinay olmasang — "qurilmada sinalmagan" deb yoz.
7. TZ da yo'q narsani o'ylab topma; muammo yoki ikkilanish bo'lsa, hisobotda "Savollar" bo'limida so'ra. Kichik texnik qarorlarni o'zing qabul qil va hisobotda qayd et.
8. Kodni **tartibli, izohli** yoz; katta fayllarni bo'lib yoz. Har modul uchun kamida asosiy birlik testlari.
9. **Taqiqlar:** React Native ga o'tma; tashqi brauzer/intent ochma (TZ 12-bo'lim); telemetriya qo'shma; o'zing yozgan shaxmatni "Chess.com" deb atama; brend logotiplarini rasmiy deb ko'rsatma.
10. Har qadam yangi `docs/PROGRESS.md` faylini yangilaydi (qadam raqami, nima bajarildi, ma'lum muammolar).
11. Hisobot tili — O'zbekcha (lotin). Kod, o'zgaruvchi nomlari — inglizcha.

### Qadam oxiri hisobot shabloni
```
✅ QADAM N tugadi: <nom>
Nima qilindi: (3–6 qisqa band)
Yangi/o'zgargan fayllar: (asosiylari)
Tekshiruv: (nima ishga tushirildi, natija; sinalmaganlar)
Ma'lum muammolar / xavflar:
Savollar (bo'lsa):
Keyingi qadam: QADAM N+1 — <nom>
👉 Davom etish uchun "davom et" deb yozing.
```

---

## QADAMLAR

### QADAM 1 — Muhit, loyiha skeleti va hujjatlar
- Muhitni tekshir: Flutter, Dart, JDK 17, Android SDK (API 35), NDK, Gradle. Yo'q narsani o'rnatishga harakat qil; bo'lmasa hisobotga yoz.
- `flutter create` bilan `ar-port` loyihasi: paket `uz.orbitx.arport`, ilova nomi "AR Port", `minSdk 29`, `targetSdk 35`, faqat Android.
- Papka tuzilmasini TZ 15-bo'lim bo'yicha yarat; `android/arcore-engine` ni Kotlin kutubxona moduli sifatida ulang (Gradle).
- Kerakli bog'liqliklarni qo'sh (Flutter: `dynamic_color`, `permission_handler`, `shared_preferences`, `flutter_localizations`; Android: CameraX, ARCore, MediaPipe tasks-vision, Kotlin coroutines).
- `TZ.md` ni `docs/` ga nusxala; `docs/ARCHITECTURE.md`, `docs/RISKS.md`, `docs/PROGRESS.md` yarat.
- Git init, `.gitignore`, birinchi commit.
- **Qabul:** bo'sh loyiha debug rejimida yig'iladi; modullar ulangan.

### QADAM 2 — Flutter UI qobig'i: tema, iOS dizayn tizimi, Splash
- Tizim temasi (qora/och) bilan avtomatik moslashuv; **accent = qurilmaning dinamik rangi** (`dynamic_color`), zaxira `#0A84FF`.
- Qayta ishlatiladigan iOS uslubidagi komponentlar: katta sarlavha, inset-grouped ro'yxat, segmentli tanlov, switch, slayder, yumaloq karta, asosiy tugma, glass dialog (Flutter qobig'i uchun `BackdropFilter`), spring animatsiyalar, yengil haptic.
- Splash ekrani (≤1.2 s).
- Lokalizatsiya skeleti (`uz` asosiy, `ru`, `en` bo'sh ARB fayllar).
- **Qabul:** splash → bo'sh bosh ekran, tema va accent to'g'ri almashadi.

### QADAM 3 — Qurilma tekshiruvi (giroskop) va ruxsatlar oqimi
- Native `DeviceCapabilityPlugin` (Kotlin, MethodChannel): giroskop/rotation-vector, orqa kamera, ARCore holati (`ArCoreApk.checkAvailability`), OpenGL ES 3.0, RAM sinfi.
- Dart tomonda natija modeli va TZ 4.2 dagi mantiq: **giroskop yo'q → AR bloklanadi va dialog**, ARCore yo'q → 3DoF rejimi ogohlantirishi.
- Ruxsat oqimi (kamera, mikrofon, Bluetooth-connect) — kontekstli tushuntirish matni bilan, rad etilganda yo'l-yo'riq.
- Birlik testlari: qaror mantiqi.
- **Qabul:** giroskopli qurilmada o'tadi, giroskopsiz (emulyator/mock) holatda blok dialog chiqadi.

### QADAM 4 — Boshlang'ich menyu (rejim + qurilma sozlamalari)
- TZ 4.3 bo'yicha ekran: 2 ko'zli / oddiy rejim tanlash, Kamera, Mikrofon, Karnay/Quloqchin, kalibratsiya, IPD va linza (stereo uchun), "AR ni ishga tushirish" tugmasi.
- Native `AudioDevicesPlugin`: kirish/chiqish qurilmalari ro'yxati va tanlash (`AudioManager`, `setCommunicationDevice`/`setPreferredDevice`).
- Native `CameraListPlugin`: mavjud kameralar, sifat/FPS variantlari.
- Tanlovlarni saqlash va keyingi safar tiklash.
- Tugma `ArActivity` ni (hozircha bo'sh qora ekran) tanlovlar bilan ishga tushiradi.
- **Qabul:** barcha tanlovlar saqlanadi, `ArActivity` konfiguratsiyani oladi.

### QADAM 5 — AR dvigatel yadrosi: ArActivity, GL, kamera passthrough
- `ArActivity` (landscape, to'liq ekran, immersive, ekran yoniq).
- `GLSurfaceView` + OpenGL ES 3.0 render sikli, FPS hisoblagich (debug HUD).
- CameraX Preview → OES tekstura, GPU da fon sifatida chizish; EIS o'chiq, past kechikish sozlamalari; kamera/sifat/FPS konfiguratsiyasini qo'llash.
- Oddiy (to'liq ekran) rejim ko'rinishi, chiqish tugmasi (zaxira).
- Kechikishni o'lchash uslubi (kadr vaqt belgisi) va natijani hisobotga yoz.
- **Qabul:** AR ochiladi, kamera tasviri to'liq ekran, ≥30 FPS (maqsad 60).

### QADAM 6 — Harakat kuzatuvi va fazoda qotirish (world-lock)
- `TrackingManager`: rotation-vector → kvaternion, filtr va yengil bashorat (predictive); ARCore bo'lsa 6DoF pozitsiya.
- View/projection matritsalari; demo: fazoda qotib turgan sinov to'rtburchagi (oyna o'rniga).
- "Markazlashtirish" funksiyasi (drift tuzatish).
- 3DoF/6DoF holati menyudagi tekshiruv bilan mos.
- **Qabul:** telefon burilganda sinov obyekti joyida qotib turadi, titrash minimal.

### QADAM 7 — Stereo rejim
- `StereoRenderer`: ikki viewport, IPD siljishi, barrel distorsiya (k1,k2), "Cardboard" profili, kalibratsiya ekrani.
- Passthrough ikkala ko'zda; sinov obyekti ikkala ko'zda to'g'ri.
- Menyudagi IPD/linza sozlamalari ulanadi; rejimni (stereo/oddiy) real almashtirish.
- **Qabul:** stereo rejimda ko'z-ko'z mosligi, oddiy rejimga qaytish ishlaydi.

### QADAM 8 — Fazoviy oyna menejeri va yuza (surface) tizimi
- `SpatialWindow` modeli, `WindowManager3D`: yaratish, joylash (qarash yo'nalishida 1.5–2 m), ustma-ust tushmaslik, z-tartib, fokus, uxlatish (sleep) siyosati, maksimal oyna soni.
- `SurfaceProvider`/`ContentSurface`: offscreen `Surface` → GL tekstura; yangilash faqat o'zgarganda; frustum culling.
- Oyna chizish: sarlavha, tutqichlar, yopish/yig'ish/mahkamlash (hozircha oddiy rang bilan, glass keyingi qadamda).
- 16:9 sukut, 9:16 va erkin o'lcham modeli; o'lcham o'zgarganda kontent viewport qayta o'lchanadi (rezolyutsiya qayta bo'linadi).
- Sinov: 3 ta test-oyna (oddiy rang/matn).
- **Qabul:** bir nechta oyna fazoda turadi, fokus/yopish ishlaydi.

### QADAM 9 — Liquid Glass renderer
- `GlassRenderer`: orqa fonni (passthrough + oldingi oynalar) past ruxsatli teksturaga olib Kawase/Gaussian blur; oyna ustida shaffof tint, chekka yorug'ligi, specular, squircle burchak, yumshoq soya.
- Parametrlar (radius, opacity, tint, reduce-transparency) Uniformlar orqali; yengil rejim (1/4 tekstura).
- Spring animatsiyalar (ochilish/yopilish/bosilish "suyuq" effekt).
- Barcha UI (oyna ramkasi, dock, tugmalar) glass ga o'tadi.
- **Qabul:** glass ko'rinishi, FPS ≥45 o'rta qurilmada; parametrlar jonli o'zgaradi.

### QADAM 10 — Qo'l kuzatuvi (MediaPipe) va qo'l yoritilishi
- `HandTracker`: ImageAnalysis oqimi, MediaPipe Hand Landmarker (2 qo'l, GPU delegate), One-Euro silliqlash, kadr→AR nur o'tkazish.
- Qo'l kursori (hand ray + nuqta), qo'l usti glow (landmarklar atrofida), hover yoritishi oyna ustida.
- Kalibratsiya ekrani (menyudan), qo'l yo'qolish mantiqi, aniqlik/FPS ko'rsatkichi (debug).
- **Qabul:** qo'l barqaror kuzatiladi (≥30 FPS), glow ko'rinadi, nur oynaga tegishi hover beradi.

### QADAM 11 — Gest dvigateli va kiritishni oynaga uzatish
- `GestureEngine`: tap, hold, drag, scroll, ikki qo'l zoom/resize, burchak tutqichi, oynani siljitish va Z masofa, palm-up (Home), swipe, fist (tasdiq bilan). Chegaralar sozlanadi; false-positive kamaytirish (histerezis).
- `InputInjector`: gestni oyna ichidagi `MotionEvent` ga aylantirish (WebView ham qabul qiladi), scroll inertsiyasi.
- Oyna boshqaruvlari qo'l bilan: siljitish, o'lcham (16:9/9:16/erkin, aspekt qulfi), yopish, yig'ish.
- Fazoviy glass klaviatura (asosiy variant).
- Gest sozlamalari modeli (shortkutlar uchun tayyor ma'lumot tuzilmasi).
- **Qabul:** barcha asosiy gestlar sinov oynalarida ishlaydi; qo'lsiz zaxira: ekran sensori (oddiy rejimda).

### QADAM 12 — Home panel, dock, ilova reyestri va plagin tizimi
- Plagin tizimi (TZ 11): `manifest.json` JSON Schema, `tools/generate_registry` (Gradle task + Dart skript), `AppRegistry`, `ARApp` interfeysi, `apps/_template/`, `docs/ADDING_AN_APP.md`.
- Build vaqtida validatsiya va aniq xato xabarlari; assets avtomatik APK ga qo'shiladi.
- Home panel (glass ilova to'ri, soat/batareya), dock, "Markazlashtirish", ovoz/yorqinlik, AR dan chiqish (tasdiq bilan); palm-up gesti bilan ochish.
- Sinov plagini qo'shib va o'chirib build qilib dynamic ishlashini isbotla.
- **Qabul:** yangi papka qo'shilsa ilova home da paydo bo'ladi, o'chirilsa yo'qoladi; kod o'zgartirishsiz.

### QADAM 13 — Web ilovalar, umumiy sessiya va sandbox
- `WebAppSurface`: WebView → offscreen → GL tekstura, 16:9 viewport, resize da qayta viewport, UA rejimlari, navigatsiya paneli (glass).
- Umumiy `CookieManager`/storage, `sessionGroup`.
- `SandboxPolicy` (TZ 12): havolalarni ichkarida ochish, `onCreateWindow` → Google brauzer yangi tab, bloklangan sxemalar, WebView xavfsizlik sozlamalari, tashqi intent lint qoidasi.
- Google (Brauzer): ko'p tab paneli, manzil satri, qidiruv.
- Plaginlar: **YouTube, Telegram, Instagram, Spotify, Google** (manifestlar bilan).
- Google login masalasi (TZ 12.4): UA va sarlavha chora-tadbirlarini qo'lla, natijani haqqoniy hujjatla (`docs/RISKS.md`).
- `onRenderProcessGone` qayta tiklash; fayl yuklash sandbox ichida.
- **Qabul:** besh web ilova ochiladi, linklar faqat ichkarida, umumiy sessiya ishlaydi (imkon qadar), tashqi ilova hech qachon ochilmaydi.

### QADAM 14 — Native ilovalar: Kamera, Qaydlar, Soat, Kalendar
- Har biri `ARApp` plagini (`type: native`), glass UI, qo'l bilan boshqarish, o'lcham moslashuvi.
- **Kamera:** foto/video, kamera almashtirish, taymer, sandbox galereyasi.
- **Qaydlar:** Room DB, ro'yxat/muharrir/qidiruv, eksport (sandbox).
- **Soat:** dunyo soati, taymer, sekundomer, budilnik.
- **Kalendar:** oy/hafta/kun, hodisalar (lokal), ixtiyoriy tizim sinxroni.
- **Qabul:** to'rttala ilova ishlaydi, ma'lumotlar saqlanadi.

### QADAM 15 — AR Sozlamalar ilovasi
- TZ 10 dagi barcha bo'limlar: Display (fon rangi/rasm/gradient/passthrough, **opacity**, **blur**), glass parametrlari, qo'l va gestlar (**shortkut o'rnatish va "o'rgatish" rejimi**), ovoz, kamera, ilovalar, oynalar (maksimal soni), akkauntlar/sessiyalar, sandbox/xavfsizlik, samaradorlik, maxsus imkoniyatlar, haqida.
- `SettingsStore` (versiyalangan sxema, eksport/import), o'zgarishlar **jonli** qo'llanadi.
- Shortkutlar `GestureEngine` ga ulanadi.
- **Qabul:** sozlamalar qayta ishga tushirishdan keyin ham saqlanadi va real ta'sir qiladi.

### QADAM 16 — O'yinlar I: 2D (Tetris, Dino Runner, Flappy Bird, Snake, 2048) + Chess.com
- Har bir o'yin `type: game` plagini (HTML5/JS assets, offline), qo'l gestlari bilan boshqaruv (TZ 9.3 jadvali), rekordlar lokal.
- Chess — `type: web`, `https://www.chess.com/play`, umumiy sessiya.
- Barcha o'yinlar 16:9 da, resize da to'g'ri moslashadi, pauza/qayta boshlash.
- **Qabul:** hammasi qo'l bilan o'ynaladi, tashqi resurs so'ramaydi (Chess.com dan tashqari).

### QADAM 17 — O'yinlar II: 3D Table Tennis (botlar) va 3D Shooter
- Three.js (assets ichida, offline) yoki mos GL sahna; qo'l pozitsiyasi → raketka / nishon nuri; fizika, botlar (3 daraja), HUD, ovozlar, ball.
- Shooter: to'lqinlar, 3 qurol, qayta o'qlash, sog'liq/o'q-dori, 3 daraja.
- Samaradorlik: 60 FPS maqsadi, adaptiv sifat.
- **Qabul:** ikkala o'yin qo'l bilan to'liq o'ynaladi.

### QADAM 18 — Samaradorlik, barqarorlik, maxfiylik, tozalash
- Adaptiv sifat (FPS<45 zanjiri), termal kuzatuv, xotira limiti va oyna uxlatish, WebView crash tiklash.
- `allowBackup=false`, ProGuard/R8 qoidalari, lint (tashqi intent taqiqi), ruxsatlar audit.
- 6 ta oynali stress-test, uzoq (≥30 daqiqa) ishlash testi, xotira oqishi tekshiruvi.
- Xatolar ro'yxatini tuzat; `docs/RISKS.md` ni yakunla.
- **Qabul:** TZ 16-bo'limdagi mezonlar jadvali to'ldirilgan (✔ / ✖ / sinalmagan).

### QADAM 19 — Yakuniy build: `ar-port.apk`
- Release konfiguratsiya: versiya 1.0.0 (1), imzolash (test keystore + `docs/SIGNING.md`), R8, arm64-v8a (+ixtiyoriy v7a).
- `flutter build apk --release` → natija **`ar-port.apk`** nomi bilan loyiha ildizidagi `dist/` ga.
- APK hajmi, SHA-256, o'rnatish yo'riqnomasi, ma'lum cheklovlar bilan `README.md`.
- Build imkonsiz bo'lsa (SDK/internet yo'q) — **aniq sabab** va foydalanuvchi lokal ishga tushiradigan buyruqlar; "tayyor" deb yolg'on yozma.
- Oxirgi hisobot: TZ mezonlari bo'yicha yakuniy holat, sinalmagan joylar, keyingi tavsiyalar.
- `ar-port.apk` faylini foydalanuvchiga yetkaz.

---

## BOSHLASH
Hozir faqat **QADAM 1** ni bajar. Tugatgach hisobot shablonini yoz va **"davom et"** kut.
