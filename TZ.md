# AR Port — Texnik Topshiriq (TZ)

Versiya: 1.0 | Platforma: Android | Domen/identifikator: ar-port.orbitx.uz | Paket ID: `uz.orbitx.arport` | Chiqish fayli: `ar-port.apk`

---

## 0. Loyiha qisqacha

**AR Port** — Android telefonni "arzon AR/VR garnituraga" aylantiradigan dastur. Telefon kamerasi orqali real dunyo tasviri ekranga uzatiladi (passthrough), uning ustida Apple Vision Pro / Meta Quest 3 uslubidagi fazoviy (spatial) oynalar suzib turadi. Oynalar fazoda joyida qotib turadi (telefon burilganda ular siljimaydi, foydalanuvchi atrofga qaraydi). Boshqaruv — faqat qo'l bilan (kamera qo'lni kuzatadi). Dizayn — iOS uslubi va "Liquid Glass" (shaffof + blur).

Ikki ko'rinish rejimi:
1. **Stereo (2 ko'zli)** — ekran chap/o'ng yarmiga bo'linadi, telefon VR-korpusga (Cardboard turi) solinadi.
2. **Oddiy (to'liq ekran)** — bitta ko'rinish, telefonni qo'lda ushlab ishlatiladi.

---

## 1. Texnologiya tanlovi (qaror)

### 1.1 React Native yoki Flutter?
- **React Native** — bu loyiha uchun MOS EMAS. Sabab: bir nechta WebView ni 3D fazoda transformatsiya qilish, kamera kadrlarini real vaqtda GPU da qayta ishlash va stereo render RN ko'prigi (bridge) orqali kechikish beradi.
- **Flutter** — UI qobig'i (ishga tushish menyusi, sozlamalar, iOS-uslubdagi ekranlar) uchun mos. Lekin AR dvigatelining o'zi Flutter ichida yozilsa ham, 3D tekislikdagi WebView lar (platform view) to'g'ri ishlamaydi.

### 1.2 Yakuniy arxitektura qarori
**Flutter (UI qobiq) + Kotlin native AR dvigatel (plugin sifatida).**

| Qatlam | Texnologiya | Vazifa |
|---|---|---|
| UI qobiq | Flutter (Dart) | Boshlang'ich menyu, rejim tanlash, ruxsatlar, qurilma tekshiruvi |
| AR dvigatel | Kotlin + OpenGL ES 3.0 (GLSurfaceView) | Kamera passthrough, stereo render, oyna kompozitsiyasi, liquid glass shaderlari |
| Harakat kuzatuvi | ARCore (agar mavjud, 6DoF) → aks holda `TYPE_ROTATION_VECTOR`/gyro (3DoF) | Oynalarni fazoda qotirish |
| Qo'l kuzatuvi | MediaPipe Tasks — Hand Landmarker | 21 nuqta, gest aniqlash |
| Kamera | CameraX (Preview + ImageAnalysis) | Passthrough va qo'l tahlili |
| Web ilovalar | Android `WebView` (offscreen, GL teksturaga chizish) | YouTube, Telegram va h.k. |
| Saqlash | Umumiy cookie/storage, `SharedPreferences`/Room | Sozlamalar, login seanslari |

Flutter ↔ Kotlin aloqasi: `MethodChannel` (buyruqlar) va `EventChannel` (holat/hodisa). AR rejim ishga tushganda AR dvigatel **alohida native Activity** (`ArActivity`) sifatida ochiladi; Flutter faqat uni ishga tushiradi va sozlamalarni uzatadi.

> Eslatma AI uchun: React Native ga o'tma. Agar Flutter qismida muammo bo'lsa, ham baribir AR yadro Kotlin da qoladi.

### 1.3 Minimal talablar
- Android 10 (API 29) va undan yuqori; `minSdk 29`, `targetSdk 35`.
- Qurilmada kerak: orqa kamera, gyroscope yoki rotation-vector sensori, OpenGL ES 3.0.
- Tavsiya: Snapdragon 7-seriya yoki undan kuchli, 6 GB RAM.

---

## 2. Realistik cheklovlar va xavflar (AI bilishi shart)

Bular yashirilmaydi — foydalanuvchiga sozlamalar/"Haqida" bo'limida ham qisqa aytiladi:

1. **"Kechikishsiz" kamera** — telefon kamerasida passthrough kechikishi ~40–100 ms bo'lishi mumkin. Maqsad: ≤ 80 ms, 60 FPS. Mutlaq nol kechikish mumkin emas.
2. **Chuqurlik yo'q** — telefonda bitta kamera ishlatiladi; stereo rejimda ikkala ko'zga bir xil tasvir (ozgina IPD siljishi bilan) beriladi. Haqiqiy 3D chuqurlik yo'q.
3. **Ko'z kuzatuvi yo'q** — Vision Pro dagi "qarab tanlash" o'rniga **qo'l nuri (hand ray)** + chimchilash (pinch) ishlatiladi.
4. **Qo'l kuzatuvi** — yorug'lik yomon bo'lsa yoki qo'l kadrdan chiqsa aniqlik pasayadi. Kadr ichida qo'l bo'lishi shart (pastki o'rta zona).
5. **Google login WebView da bloklanadi** — Google OAuth ("disallowed_useragent") ichki WebView dan kirishni cheklaydi. Yechim bosqichlari (TZ 12.4): to'g'ri mobil Chrome user-agent, `X-Requested-With` sarlavhasini olib tashlash, va ishlamasa — foydalanuvchiga email/parol bilan kirish yoki QR/kod orqali kirish usulini taklif qilish. Bu mutlaq kafolatlanmaydi; Google siyosati o'zgarishi mumkin.
6. **DRM** — YouTube/Spotify web uchun Widevine kerak; ko'p qurilmalarda WebView orqali ishlaydi, lekin Spotify web ba'zan ruxsat bermaydi. Fallback: Spotify web ishlamasa xabar ko'rsatiladi.
7. **Batareya va qizish** — kamera + ML + GL + bir nechta WebView og'ir. Adaptiv sifat rejimi va "qizish ogohlantirishi" majburiy.
8. **Mualliflik/ToS** — Instagram, Telegram, YouTube, Spotify, Chess.com saytlari veb versiyada ochiladi (o'z kodimiz emas); ularning logotiplari faqat ilova nomini ko'rsatish uchun ishlatiladi. Chess.com — rasmiy sayt (chess.com) WebView da ochiladi; o'z shaxmat dvigatelini Chess.com nomi bilan chiqarmaymiz.
9. **Telefon qimirlaganda oynalarning qotishi** 3DoF da faqat burilishni kuzatadi (joy almashishni emas). 6DoF faqat ARCore qo'llaydigan qurilmalarda.

---

## 3. Umumiy arxitektura

```
ar-port/
├─ lib/ (Flutter)           → UI qobiq
├─ android/app/             → Flutter android runner
├─ android/arcore-engine/   → Kotlin modul: AR dvigatel
├─ apps/                    → AR ilovalar (plagin papkalari)
│   ├─ youtube/ telegram/ instagram/ spotify/ google/
│   ├─ camera/ notes/ settings/ clock/ calendar/
│   └─ games/ tetris/ dino/ flappy/ chess/ snake/ g2048/ pingpong3d/ shooter3d/
├─ tools/                   → generator skriptlar (plagin reyestri)
└─ docs/
```

### 3.1 Asosiy modullar (arcore-engine)
| Modul | Vazifa |
|---|---|
| `TrackingManager` | Sensor/ARCore dan kamera orientatsiyasi (kvaternion), pozitsiya (6DoF bo'lsa), filtrlash |
| `PassthroughRenderer` | CameraX kadrini OES teksturaga olib, fon sifatida chizish |
| `StereoRenderer` | Chap/o'ng ko'z viewport, IPD, linza distorsiyasi (barrel), oddiy rejimga o'tish |
| `WindowManager3D` | Fazoviy oynalar ro'yxati, joylashuv, o'lcham, z-tartib, fokus |
| `SurfaceProvider` | Har bir oyna uchun kontent manbai: WebView, native view yoki GL sahna → GL tekstura |
| `GlassRenderer` | Liquid Glass shaderi: blur (Kawase/Gaussian), shaffoflik, chekka yoritilishi (specular), tint |
| `HandTracker` | MediaPipe Hand Landmarker, 2 qo'l, 21 nuqta, silliqlash (One-Euro filtr) |
| `GestureEngine` | Landmarklar → gestlar (pinch, drag, pinch-zoom, palm-open, swipe, long-pinch) |
| `InputInjector` | Gestni oyna ichidagi `MotionEvent` ga aylantirish (WebView ga ham) |
| `AppRegistry` | Plagin manifestlarini o'qish, ilovalarni ro'yxatga olish |
| `SessionStore` | Umumiy cookie, localStorage, login holati |
| `SettingsStore` | Sozlamalar, qo'l shortkutlari, fon, blur |
| `AudioRouter` | Mikrofon, karnay/quloqchin tanlash |
| `SandboxPolicy` | Linklar va navigatsiya cheklovi |

### 3.2 Render konveyeri (har kadr)
1. Kamera kadri → OES tekstura (fon).
2. `TrackingManager` dan joriy kamera pozitsiyasi (view matrix).
3. Har bir ko'z uchun: fon chiziladi → oynalar orqadan oldinga (painter's algorithm) chiziladi → glass shader fonni blur qilib oyna orqasiga qo'yadi → qo'l kursori (hand ray + nuqta) → HUD.
4. Stereo rejimda linza distorsiyasi so'nggi o'tish sifatida.
5. Maqsad: 60 FPS (qurilma qo'llasa 90 emas, 60 yetarli).

---

## 4. Ishga tushish oqimi (Flutter qobiq)

### 4.1 Splash
- Qisqa (≤1.2 s) animatsiyali "AR Port" logotipi, iOS uslubida, fon — tizim temasiga mos.

### 4.2 Qurilma tekshiruvi (1-talab: giroskop)
Dastur ochilishi bilan avtomatik ketma-ket tekshiriladi:
1. **Giroskop / rotation-vector** sensori bormi?
2. Orqa kamera bormi?
3. ARCore qo'llab-quvvatlanadimi (6DoF uchun)? — majburiy emas.
4. OpenGL ES 3.0.
5. RAM / qurilma sinfi (past bo'lsa — "Yengil rejim" tavsiya).

Natija:
- **Giroskop YO'Q** → to'liq ekranli iOS uslubidagi dialog: "Qurilmangizda giroskop topilmadi. AR Port ishlashi uchun giroskop kerak." Tugma: "Chiqish". AR ishga tushirilmaydi.
- **Giroskop BOR, ARCore yo'q** → 3DoF rejimi, ogohlantirish (kichik): "Faqat burilish kuzatiladi".
- **Hammasi bor** → 6DoF rejimi.

### 4.3 Boshlang'ich menyu (2-talab)
iOS uslubidagi ekran (katta sarlavha, yumaloq kartalar, SF-ga o'xshash shrift — Inter yoki tizim shrifti):

**Karta 1: Ko'rinish rejimi** (segmentli tanlov)
- "2 ko'zli (Stereo)" — ikon: ikkita doira.
- "Oddiy (To'liq ekran)" — ikon: to'rtburchak.
- Tanlov saqlanadi, keyingi safar oldingi tanlov belgilangan turadi.

**Karta 2: Sozlamalar (qurilma)**
- **Kamera** — qaysi kamera (orqa asosiy / orqa keng burchakli, mavjud bo'lsa), sifat (720p/1080p), FPS (30/60).
- **Mikrofon** — kirish manbai tanlash (telefon mikrofoni, Bluetooth garnitura, USB). Ruxsat so'raladi.
- **Karnay / Quloqchin** — chiqish tanlash (telefon karnayi, simli, Bluetooth). `AudioManager.getDevices()` orqali ro'yxat.
- **Qo'l kuzatuvi kalibratsiyasi** (qisqa o'rgatish: qo'lni kadrga qo'yish).
- **IPD (ko'zlararo masofa)** va **linza turi** (faqat stereo).

**Tugma:** katta "AR ni ishga tushirish" (accent rangda).

Ruxsatlar (kamera, mikrofon, Bluetooth-connect) ushbu ekranda **kontekst bilan** so'raladi (nega kerakligi qisqa matn bilan).

### 4.4 Dizayn qoidalari (4-talab)
- iOS uslubi: katta yumaloq burchaklar (20–28 dp), guruhlangan ro'yxatlar (inset grouped), yupqa ajratgichlar, spring animatsiyalar, haptic (yengil) bosishlarda.
- **Accent rang** = qurilmaning dinamik rangi (Material You, `dynamic_color` paketi / Android 12+ `system_accent1`). Android 12 dan past bo'lsa — `#0A84FF` (iOS ko'k).
- **Tema** = tizim temasi (qora/och) avtomatik; o'zgarsa darhol moslashadi.
- Til: O'zbekcha (lotin) asosiy; keyin Ruscha va Inglizcha qo'shish oson bo'lsin (`l10n` ARB fayllari).

---

## 5. AR rejimi — dvigatel tafsilotlari

### 5.1 Kamera passthrough (3-talab)
- CameraX `Preview` → `SurfaceTexture` (GL_TEXTURE_EXTERNAL_OES), to'g'ridan-to'g'ri GPU ga — CPU nusxasiz.
- Qo'l tahlili uchun alohida `ImageAnalysis` oqimi (past ruxsat, 480×360 atrofida), `STRATEGY_KEEP_ONLY_LATEST` — kechikish to'planmasin.
- Avto-fokus uzluksiz, ekspozitsiya avtomatik. Stabilizatsiya (EIS) **o'chirilgan** (qo'shimcha kechikish beradi).
- Ekran yo'nalishi: landscape majburiy (stereo uchun), oddiy rejimda ham landscape default (portrait — keyingi versiya).
- Kamera aspekti ekranni to'ldiradi (center-crop), kengaytirilgan FOV uchun keng burchakli kamera tanlovi.

### 5.2 Fazoda qotirish (world-lock)
- Har bir oyna **dunyo koordinatasida** yashaydi (pozitsiya `vec3`, yo'nalish kvaternion, o'lcham metrda).
- Kamera harakati: kvaternion (rotation-vector) → view matrix. Oynalar o'zgarmaydi, kamera harakatlanadi → "joyida qotib turadi" effekti.
- Filtrlash: Sensor shovqini uchun yengil past chastotali filtr + predictive tracking (~16–20 ms oldinga) kechikish sezilmasligi uchun.
- Drift: gyro uzoq vaqtda siljiydi → "Markazlashtirish" amali (qo'l shortkuti/tugma) oynalarni hozirgi qarash yo'nalishiga qayta joylaydi.
- 6DoF (ARCore): kamera pozitsiyasi ham hisobga olinadi → foydalanuvchi yaqinlashsa oynalar kattalashib ko'rinadi.
- Boshlang'ich joylashuv: yangi oyna foydalanuvchi qarayotgan yo'nalishda, 1.5–2.0 m masofada, ko'z balandligida ochiladi. Mavjud oynalar bilan ustma-ust tushmaydi (yon tomonga siljitiladi).

### 5.3 Stereo rejim
- Ekran vertikal chiziq bo'yicha ikkiga bo'linadi. Har yarmi — alohida viewport, kamera pozitsiyasi ±IPD/2 siljitiladi (sukut IPD 63 mm).
- Barrel (linza) distorsiyasi: koeffitsientlar k1, k2 sozlanadi; "Cardboard v2" profili tayyor.
- Markaz chizig'i va linzalar orasidagi masofa sozlamalarda ko'rsatiladigan kalibratsiya ekrani bilan.
- HUD va qo'l kursori ikkala ko'zda ham chiziladi.

### 5.4 Oddiy rejim
- Bitta viewport, distorsiya yo'q, FOV ~ 75–90°, qolgani bir xil.

### 5.5 Pastki dock (Vision Pro "Home" ga o'xshash)
- Foydalanuvchi qo'lini ko'tarib kaftni yuqoriga ochganda (yoki bosh bilan pastga qaraganda) **Home** paneli ochiladi: ilovalar ikonkalari to'ri (glass karta), yuqorida soat/batareya/signal.
- Dock shuningdek doim ko'rinadigan kichik "boshqaruv paneli": Home, Markazlashtirish, Ovoz, Yorqinlik, Yopish (AR dan chiqish).

---

## 6. Qo'l kuzatuvi va gestlar

### 6.1 Texnik
- MediaPipe Hand Landmarker (`numHands=2`, delegate=GPU), 21 landmark/qo'l, 30 FPS tahlil.
- Landmarklar kadr koordinatasidan **AR koordinatasiga** aylantiriladi (kameraning FOV si bo'yicha taxminiy 3D nur).
- Silliqlash: One-Euro filtr (jitter kamaytirish, tez harakatda kechikishsiz).
- Qo'l yo'qolsa 200 ms davomida oxirgi holat ushlanadi, so'ng kursor yashiriladi.

### 6.2 "Qo'l usti yoritilishi" (3-talab)
- Qo'l konturi/landmarklar atrofida engil oq-ko'k **glow** (qo'l ustida nozik porlash) chiziladi: qo'lni ko'rsatuvchi yarim shaffof "aura" + barmoq uchlarida nuqtalar.
- Qo'l oynaga yaqinlashsa, oynaning shu nuqtasi yoritiladi (hover effekti, glass chekkasi yorqinlashadi).
- Qo'l oyna **orqasida** bo'lishi kerak emas — qo'l real tasvir sifatida passthrough ustida; yoritish overlay.

### 6.3 Gestlar jadvali
| Gest | Aniqlash | Amal |
|---|---|---|
| **Nishon (hover)** | Ko'rsatkich barmoq nuri oynaga tegadi | Elementni yoritish, kursor nuqtasi |
| **Bosish (tap)** | Bosh barmoq + ko'rsatkich uchlari yaqinlashadi (<~2.5 sm ekvivalent) va tez ajraladi (<350 ms) | Bosish (`ACTION_DOWN/UP`) |
| **Ushlab turish (hold)** | Chimchilash >500 ms | Kontekst menyu / uzoq bosish |
| **Sudrash (drag)** | Chimchilab turib qo'lni siljitish | Oynani siljitish (sarlavha chizig'idan), yoki oyna ichida scroll/drag |
| **Scroll** | Chimchilab vertikal/gorizontal siljitish (oyna kontenti ustida) | Sahifa aylantirish (inertsiya bilan) |
| **Zoom/o'lcham** | Ikki qo'l: har birida chimchilash, qo'llarni uzoqlashtirish/yaqinlashtirish | Oyna o'lchamini o'zgartirish (aspekt bloklash/ozod rejimi bilan) |
| **Burchak tortish** | Oyna burchak tutqichini chimchilab tortish | Custom o'lcham, 16:9 ↔ 9:16 ↔ erkin |
| **Oynani uzoqlashtirish/yaqinlashtirish** | Sarlavhadan chimchilab qo'lni oldinga/orqaga | Z (chuqurlik) masofasi |
| **Burish (rotate)** | Ikki qo'l chimchilab aylantirish | Oyna yo'nalishi (ixtiyoriy, sozlamada yoqiladi) |
| **Kaft ochiq (palm up)** | Barcha barmoqlar ochiq, kaft yuqoriga, 400 ms | Home panelni ochish/yopish |
| **Ikki barmoq sürish (swipe)** | Qo'l tez gorizontal harakat | Ilovalar orasida almashish / orqaga |
| **Musht (fist)** | Barcha barmoqlar yopiq 500 ms | Tanlangan oynani yopish tasdig'i (shortkut sozlanuvchi) |
| **Maxsus shortkutlar** | Foydalanuvchi belgilagan gest | Sozlamalar → Gestlar |

### 6.4 Qo'l orqali matn kiritish
- Fazoviy klaviatura (glass), ko'rsatkich barmoq bilan bosish, hamda "uchish" (swipe typing) variant keyinroq. Telefonning ovozli kiritishi (Speech) ham opsiya.

### 6.5 Xavfsizlik gestlari
- Tasodifiy yopishni oldini olish: yopish va "Chiqish" gestlari tasdiq talab qiladi.
- Qo'l aniqlanmasa — telefon sensorli ekrani ham zaxira boshqaruv sifatida ishlaydi (oddiy rejimda).

---

## 7. Oyna menejeri (fazoviy oynalar)

### 7.1 Oyna modeli
```
SpatialWindow {
  id, appId, title,
  position: vec3, orientation: quat,
  size: {w_m, h_m}  // metr
  aspect: Locked16x9 | Locked9x16 | Free
  state: Normal | Minimized | Pinned | Fullscreen
  opacity, glassIntensity
  z-order, focused
  content: ContentSurface (WebView | NativeView | GLScene)
}
```

### 7.2 Qoidalar
- Sukut: **16:9**, kenglik ≈ 1.2 m (1.5–2 m masofada).
- Foydalanuvchi o'lchamni **9:16** ga burishi yoki **istalgan custom** o'lchamga cho'zishi mumkin; "Aspektni qulflash" tugmasi bor.
- **Bir vaqtda juda ko'p oyna** (amaliy chegara sozlamalarda: 4/6/8/12; sukut 6). Chegaraga yetganda eng eski faolsiz oyna "uxlatiladi" (WebView pauza, tekstura bo'shatiladi, snapshot qoladi).
- Faol bo'lmagan oynalar kamroq FPS da yangilanadi (10–15 FPS), qaralmayotganlari yangilanmaydi (frustum culling).
- Har oynada: sarlavha paneli (ilova nomi, ikonka), pastki "tutqich" (siljitish), burchak tutqichlari (o'lcham), yopish/yig'ish/mahkamlash tugmalari.
- Fokus: oxirgi bosilgan oyna, uning chekkasi yengil yoritiladi; ovoz faqat fokusdagi oynadan (sozlamada o'zgartirsa bo'ladi).
- Oyna chetlari bir-biriga **magnit** (snap) bo'lishi mumkin (sozlama).
- Google brauzerida **bir ilova ichida ko'p tab** (7.4).

### 7.3 Tab (faqat Google/Brauzer)
- Google ilovasi (brauzer) oynasida tab paneli (glass), "+" yangi tab, tabni yopish, tablar orasida almashish. Har tab — alohida WebView, umumiy cookie.

### 7.4 Boshqa ilovalar
- Boshqa ilovalarda ko'p tab YO'Q; ammo bir ilovaning bir nechta **oynasini** ochish mumkin (sozlamada ruxsat).

---

## 8. Liquid Glass dizayn (5-talab)

AR rejimida **barcha** UI elementlari (dock, home, oyna ramkalari, sarlavhalar, klaviatura, dialoglar, tugmalar):
- **Blur:** fon (passthrough + orqadagi oynalar) ustiga Kawase blur (sozlanuvchi radius 0–40 dp ekvivalent).
- **Shaffoflik:** `glassOpacity` (0.15–0.85), tint rangi (accent yoki oq/qora), sozlanadi.
- **Chekka yoritilishi:** nozik 1 px yorug' chegara + yuqori chap burchakdan specular highlight; bosilganda "suyuq" deformatsiya (kichik refraksiya) animatsiyasi.
- **Burchak:** katta radius (28–36 dp ekvivalent), "squircle" (superellipse).
- **Soya:** yumshoq, kichik.
- **Animatsiyalar:** spring (damping ~0.8), ochilish/yopilish scale+fade, 60 FPS.
- **Qulaylik:** Sozlamalarda "Shaffoflikni kamaytirish" (Reduce Transparency) opsiyasi — blur o'chib, quyuq/och qattiq fon.
- **Yengil rejim:** past qurilmada blur bir marta kichik tekstura (1/4 o'lcham) da hisoblanadi.
- Matn: yuqori kontrast, glass ustida o'qilishi uchun matn soyasi/vibrancy.
- Web kontent (YouTube kabi) o'zi shaffof emas; faqat ramka/sarlavha/boshqaruvlar glass.

---

## 9. AR ichidagi ilovalar (har biri tafsilotda)

Umumiy: har bir ilova `manifest.json` bilan tavsiflanadi (11-bo'lim). Ikki tur:
- **Web-tipli** (`type: "web"`): URL ni WebView da ochadi (brauzer sifatida).
- **Native-tipli** (`type: "native"`): Kotlin/Compose-offscreen yoki GL da yozilgan (Kamera, Qaydlar, Sozlamalar, Soat, Kalendar).
- **Game-tipli** (`type: "game"`): ilova paketiga kiritilgan HTML5/JS o'yin (offline, assets ichida) yoki GL sahnasi (3D o'yinlar).

### 9.1 Web ilovalar (brauzer sifatida)
| Ilova | URL (boshlang'ich) | Izoh |
|---|---|---|
| YouTube | `https://m.youtube.com` (kengaytirilgan: desktop UA opsiyasi) | 16:9, to'liq ekran video, PiP o'xshash "kichik oyna" rejimi |
| Telegram | `https://web.telegram.org/a/` | QR yoki telefon raqami bilan kirish |
| Instagram | `https://www.instagram.com` | Mobil UA |
| Spotify | `https://open.spotify.com` | Fon ijrosi: oyna yig'ilganda ovoz davom etadi |
| Google (Brauzer) | `https://www.google.com` | **Ko'p tab**, manzil satri, qidiruv, ichki linklar shu yerda ochiladi |

Web ilovalar uchun umumiy xususiyatlar:
- Boshlang'ich 16:9 (~ 1280×720 virtual piksel viewport, DPR sozlanadi), 9:16 ga burilsa viewport mos ravishda 720×1280.
- Oyna o'lchami o'zgarsa WebView viewport **qayta o'lchanadi** (kontent o'zgaradi, cho'zilmaydi) — yuqori sifat uchun. Resize paytida (jarayonida) vaqtincha snapshot cho'ziladi, qo'yib yuborilganda qayta chiziladi.
- Navigatsiya: orqaga/oldinga/yangilash/uy tugmalari oyna ostki glass panelida.
- Linklar: **faqat AR Port ichida** (12-bo'lim).
- Fayl yuklab olish: ilovaning ichki sandbox papkasiga (tashqi "Downloads" ga emas); kerak bo'lsa "Fayllar" keyingi versiyada.

### 9.2 Native ilovalar
**Kamera**
- Kamera oynasi (alohida ilova): real kamera ko'rinishi, foto/video yozish, oldingi/orqa almashtirish, taymer. Saqlash: ilova sandbox galereyasi ("AR Port Media"). Qo'l bilan "tasvirga olish" (pinch) tugmasi.
- Skrinshot/ekran yozish: butun AR sahnasini yozish (opsiya).

**Qaydlar**
- Matnli qaydlar ro'yxati + muharrir (glass), qidirish, papka/teg, rangli kartalar. Saqlash: lokal DB (Room). Qo'l klaviaturasi/ovozli kiritish. Eksport .txt (sandbox ichiga).

**Sozlamalar** — 10-bo'lim.

**Soat**
- Dunyo soati, taymer, sekundomer, budilnik (budilnik AR da tovush + tizim bildirishnomasi). Katta analog/raqamli ko'rinish.

**Kalendar**
- Oy/hafta/kun ko'rinishi, hodisalar qo'shish/tahrirlash (lokal), eslatmalar. Tizim kalendari bilan sinxron — faqat foydalanuvchi ruxsat bersa (READ_CALENDAR), sukut: faqat lokal.

### 9.3 O'yinlar (AR ga moslangan)
Barcha o'yinlar qo'l bilan boshqariladi (pinch, drag, swipe), 16:9 oynada ochiladi, offline ishlaydi (assets ichida), eng yaxshi natija lokal saqlanadi.

| O'yin | Boshqaruv (qo'l) | Tafsilot |
|---|---|---|
| **Tetris** | Pinch+drag chap/o'ng — siljitish; tez pastga swipe — tushirish; ikki barmoq burish yoki tap — aylantirish | Klassik 10×20, daraja, ball, keyingi blok, "ushlab turish" |
| **Dino Runner** | Tap yoki yuqoriga swipe — sakrash; pastga swipe — egilish | Cheksiz yugurish, kunduz/tun, ball |
| **Flappy Bird** | Tap — ko'tarilish | Quvurlar, ball, rekord |
| **Chess (Chess.com)** | WebView orqali `https://www.chess.com/play` — pinch bilan donalarni sudrash | Web-tipli; Chess.com akkaunt bilan kirish umumiy sessiyada saqlanadi |
| **Snake** | Swipe yo'nalishlari | Devor/bez devor rejimlari, tezlik |
| **2048** | Swipe (4 yo'nalish) | 4×4, qaytarish (undo) 1 marta |
| **3D Table Tennis (botlar bilan)** | Qo'l = raketka (qo'l pozitsiyasi to'g'ridan-to'g'ri raketkaga map qilinadi); botlar 3 darajada (oson/o'rta/qiyin) | Fizika: to'p traektoriyasi, spin, to'r; GL 3D sahna (oynada yoki to'liq AR fazoda), ball 11 gacha |
| **3D Shooter** | Qo'l nuri = nishon, pinch = otish, boshqa qo'lda chimchilab siljitish = harakat/qayta o'qlash | Oddiy to'lqinli (wave) dushmanlar, 3 tur qurol, sog'liq/o'q-dori HUD, 3 daraja |

3D o'yinlar uchun dvigatel: o'zimizning yengil GL sahna (yoki Three.js ni WebView ichida `assets/` dan — qaysi biri barqaror bo'lsa; **Three.js + WebView** soddaroq, tavsiya etiladi).

> Chess.com: o'zimiz yozgan shaxmatni "Chess.com" deb atamaymiz. Tugma nomi: "Chess.com" — sayt ochiladi.

---

## 10. AR ichidagi "Sozlamalar" ilovasi

Barcha narsa sozlanadi. Bo'limlar (iOS "Settings" uslubida, chap ro'yxat + o'ng tafsilot):

1. **Umumiy:** til, versiya, "Haqida", sinxronlash, qayta ishga tushirish, zavod holatiga qaytarish.
2. **Ko'rinish (Display):**
   - Orqa fon: **passthrough (kamera)** / **bir rangli** / **gradient** / **rasm** (galereyadan, sandbox ichiga nusxa). Rang tanlagich (HSV + HEX + presetlar).
   - **Orqa fon shaffofligi (opacity)** 0–100% (passthrough ustiga rang qoplash).
   - **Orqa fon blur** 0–100% (passthrough ga).
   - Yorqinlik, kontrast, passthrough rang sozlamalari.
   - Glass: blur radiusi, shaffoflik, tint, "Reduce transparency".
   - Oyna: sukut o'lcham, sukut masofa, aspekt qulfi sukuti, magnit snap.
   - Rejim: stereo/oddiy almashtirish, IPD, linza distorsiyasi.
3. **Qo'l va gestlar:**
   - Gestlar ro'yxati, har birini yoqish/o'chirish, sezgirlik (pinch chegarasi), nur uzunligi.
   - **Qo'l shortkutlari (shortcuts):** gest → amal biriktirish. Amallar: Home ochish, Markazlashtirish, Ilovani ochish (ixtiyoriy ilova), Skrinshot, Oynani yopish, Barcha oynalarni yig'ish, Ovoz +/-, Kamera ochish va boshqalar. Yangi gest yozish (ikki qo'l/barmoq konfiguratsiyasi) — "o'rgatish" rejimi.
   - Qo'l yoritilishi (glow): yoqish, rang, intensivlik.
   - Qo'l kalibratsiyasi.
4. **Ovoz:** umumiy ovoz, mikrofon manbai, karnay/quloqchin, fazoviy ovoz (oynadan keladigandek).
5. **Kamera:** kamera tanlash, sifat, FPS, ekspozitsiya, "past kechikish rejimi".
6. **Ilovalar:** o'rnatilgan ilovalar ro'yxati, har biri uchun: tartib (home da), standart o'lcham, ruxsatlar, cache tozalash, sessiyani chiqarib tashlash. Plagin ro'yxati (faqat o'qish; yangi ilova qo'shish — build orqali, 11-bo'lim).
7. **Oynalar:** maksimal ochiq oynalar soni, fon oynalar FPS, uxlatish siyosati.
8. **Akkauntlar va sessiyalar:** umumiy login holati (kirgan saytlar ro'yxati), "Barcha sessiyalardan chiqish", cookie tozalash, saytlar bo'yicha o'chirish.
9. **Sandbox va xavfsizlik:** havola siyosati (faqat ichki), reklama/trekerlarni bloklash (ixtiyoriy), dastur lock (biometrik).
10. **Samaradorlik:** sifat sinfi (Past/O'rta/Yuqori/Avto), FPS limit, qizish ogohlantirishi, batareya tejash.
11. **Maxsus imkoniyatlar:** matn kattaligi, kontrast, harakat kamaytirish, ovozli kiritish.
12. **Haqida:** versiya, litsenziyalar (MediaPipe, ARCore, Three.js va h.k.), cheklovlar (TZ 2-bo'lim qisqacha).

Sozlamalar saqlash: `SettingsStore` (JSON, Room yoki DataStore), versiyalangan sxema, eksport/import (sandbox ichida).

---

## 11. Plagin tizimi — yangi ilova qo'shish (dynamic, "build orqali")

### 11.1 Maqsad
Ortiqcha amallarsiz: yangi ilova papkasini loyiha `apps/` ga tashlab, build qilish → ilova avtomatik AR Port ga qo'shiladi. Olib tashlash: papkani o'chirib, build.

### 11.2 Plagin tuzilmasi
```
apps/<app_id>/
├─ manifest.json
├─ icon.png            (1024×1024, glass ikon uchun avtomatik ishlov)
├─ assets/             (web/game bo'lsa: html, js, css, modellar)
├─ src/                (native bo'lsa: Kotlin fayllari, faqat ARApp interfeysini amalga oshiradi)
└─ README.md
```

### 11.3 manifest.json sxemasi
```json
{
  "id": "youtube",
  "name": {"uz": "YouTube", "en": "YouTube", "ru": "YouTube"},
  "version": "1.0.0",
  "type": "web",                 // web | native | game
  "category": "media",           // media | social | tools | games
  "entry": "https://m.youtube.com",   // web uchun URL; game uchun "assets/index.html"; native uchun sinf nomi
  "defaultAspect": "16:9",
  "defaultSize": {"w": 1.2},
  "allowTabs": false,
  "multiWindow": true,
  "userAgent": "mobile|desktop|custom:<string>",
  "permissions": ["camera","microphone","audio"],
  "allowedHosts": ["youtube.com","*.youtube.com","googlevideo.com","ytimg.com","google.com","accounts.google.com"],
  "sessionGroup": "google",      // umumiy login guruhi
  "minEngineVersion": "1.0",
  "order": 10
}
```

### 11.4 Reyestr generatsiyasi
- `tools/generate_registry.dart` (yoki Gradle task `generateAppRegistry`) build vaqtida `apps/*/manifest.json` larni o'qiydi, validatsiya qiladi (JSON Schema), `assets` ni APK ga qo'shadi va `AppRegistry` uchun `app_registry.g.json` + Kotlin `GeneratedApps.kt` hosil qiladi.
- Xato manifest bo'lsa — build **aniq xabar** bilan to'xtaydi (qaysi fayl, qaysi maydon).
- Native ilovalar `ARApp` interfeysini amalga oshiradi:
```kotlin
interface ARApp {
  val id: String
  fun createSurface(ctx: AppContext): ContentSurface
  fun onResize(w: Int, h: Int)
  fun onPointer(event: PointerEvent)   // qo'l kiritishi
  fun onPause(); fun onResume(); fun onDestroy()
}
```
- Hujjat: `docs/ADDING_AN_APP.md` — "yangi ilova qanday qo'shiladi" 1 sahifa qo'llanma + `apps/_template/`.
- Home ekrani va Sozlamalar → Ilovalar bo'limi ro'yxatni **faqat reyestrdan** oladi (qattiq kodlangan ro'yxat yo'q).

---

## 12. Sandbox, login va havolalar (muhim talab)

### 12.1 Umumiy qoida
AR Port ichidagi barcha narsa **dastur sandbox** idan chiqmaydi: tashqi brauzer, tashqi ilovalar (Chrome, Telegram ilovasi, YouTube ilovasi), tizim "Share" va intent orqali ochish **taqiqlangan**.

### 12.2 Umumiy sessiya (single sign-on o'xshash)
- Barcha WebView lar **bitta** `CookieManager` va bitta umumiy `WebStorage` dan foydalanadi (bitta jarayon, bitta data directory — `WebView.setDataDirectorySuffix` ishlatilmaydi yoki bitta).
- Google da login qilinganda boshqa web ilovalar (YouTube, Spotify "Google bilan kirish") shu sessiyani ko'radi.
- "Google bilan kirish" tugmasi bosilganda ochiladigan oyna/popup **AR Port ichidagi Google brauzer oynasida** (yoki yangi tab sifatida) ochiladi; natija (OAuth redirect) asl ilovaga qaytariladi (`window.opener` / `onCreateWindow` ni qo'llab-quvvatlash).
- Popup (`window.open`) va `target=_blank` — `WebChromeClient.onCreateWindow` orqali: Google brauzerida yangi tab, yoki shu ilovaning yangi oynasi.

### 12.3 Havolalar siyosati (`SandboxPolicy`)
- Har bir `shouldOverrideUrlLoading`:
  - `http/https` → ichkarida: agar host joriy ilovaning `allowedHosts` ichida bo'lsa — shu oynada; aks holda — **Google (Brauzer) ilovasida yangi tabda** ochiladi.
  - `intent://`, `market://`, `tel:`, `mailto:`, `sms:`, `tg://`, `file://` (assets dan tashqari), `content://` → **bloklanadi**, foydalanuvchiga "Bu havola AR Port ichida ochilmaydi" glass toast.
- `Intent` orqali tashqi ilovani ishga tushirish kodda umuman yo'q (lint qoidasi bilan tekshiriladi).
- Fayl tanlash (`<input type=file>`): faqat sandbox ichidagi fayl tanlagich (AR Port Media, Qaydlar eksporti).
- `WebView` sozlamalari: `allowFileAccess=false` (assets uchun faqat `file:///android_asset/` oq ro'yxat), `allowContentAccess=false`, `mixedContentMode=NEVER_ALLOW`, safe browsing yoqilgan, JS interfeyslari minimal va faqat o'z ilovalarimiz uchun.

### 12.4 Google login WebView da — rejalashtirilgan yechim
1. UA: haqiqiy mobil Chrome UA (versiyasi yangilanib turadigan konfiguratsiya orqali), `wv` belgisisiz.
2. `X-Requested-With` sarlavhasini yo'q qilish.
3. Agar Google baribir "Bu brauzer xavfsiz emas" desa → ilova foydalanuvchiga **muqobil** taklif qiladi: (a) Google bo'lmagan akkaunt/email-parol bilan kirish (Spotify, Telegram — QR/telefon), (b) keyingi versiyada **ichki Chrome Custom Tab** emas (u sandbox dan chiqadi) — shuning uchun qo'llab-quvvatlanmaydi; foydalanuvchiga halol xabar beriladi.
4. Bu element **xavf** sifatida hujjatlashtiriladi, sinovdan o'tkaziladi, natija README ga yoziladi.

### 12.5 Ma'lumotlar maxfiyligi
- Sessiya/cookie lar faqat qurilmada (ilova private storage), bulutga yuborilmaydi.
- Zaxira (backup) o'chirilgan (`android:allowBackup="false"`).
- Kamera tasviri va qo'l kadrlari **hech qayerga yuborilmaydi**, diskka yozilmaydi (faqat foydalanuvchi "Kamera" ilovasida rasm olsa).
- Tarmoq: faqat web ilovalar kerakli hostlarga; AR Port o'zi hech qanday analitika/telemetriya yubormaydi (sukut).

---

## 13. Ruxsatlar (AndroidManifest)

| Ruxsat | Nima uchun | Qachon so'raladi |
|---|---|---|
| `CAMERA` | Passthrough, qo'l kuzatuvi, Kamera ilovasi | Menyuda "AR ni ishga tushirish" dan oldin |
| `RECORD_AUDIO` | Mikrofon (Telegram qo'ng'iroq, ovozli kiritish, video yozish) | Mikrofon tanlanganda yoki kerak bo'lganda |
| `BLUETOOTH_CONNECT` (Android 12+) | Bluetooth quloqchin/mikrofon | Quloqchin tanlanganda |
| `MODIFY_AUDIO_SETTINGS` | Audio yo'naltirish | Oddiy |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Web ilovalar | Oddiy |
| `POST_NOTIFICATIONS` | Budilnik/eslatmalar | Kerak bo'lganda |
| `READ_CALENDAR` (ixtiyoriy) | Kalendar sinxroni | Foydalanuvchi yoqsa |
| `WAKE_LOCK` / ekran o'chmasligi | AR paytida ekran yoniq | Oddiy (FLAG_KEEP_SCREEN_ON) |

Ruxsat rad etilsa — tegishli funksiya o'chadi, tushuntiruvchi glass dialog va "Sozlamalarga o'tish" tugmasi.

---

## 14. Samaradorlik va barqarorlik

- Maqsad: 60 FPS, motion-to-photon ≤ 80 ms (taxminiy), qo'l kuzatuvi ≥ 30 FPS.
- Texnikalar: kamera OES to'g'ridan-to'g'ri GPU; qo'l ML alohida oqimda GPU delegate; WebView lar offscreen `Surface` ga; oyna teksturalari faqat o'zgarganda yangilanadi; blur past ruxsatda; frustum culling; oyna uxlatish.
- Adaptiv sifat: FPS 45 dan pastga tushsa — avtomatik: avval blur sifati, so'ng WebView FPS, so'ng teksturalar o'lchami kamayadi.
- Termal: `PowerManager.getThermalHeadroom` / `ThermalStatus` kuzatiladi; "isish" da ogohlantirish va sifatni tushirish.
- Xotira: bitta oynaga ~50–150 MB; limitdan oshsa eng eski uxlatiladi; OOM da xavfsiz yopish.
- Crash himoyasi: bitta WebView qulasa (`onRenderProcessGone`) faqat o'sha oyna qayta yuklanadi, AR sessiya davom etadi.
- Ekran: doim yoniq (`KEEP_SCREEN_ON`), yorqinlik avtomatik boshqarilmaydi.
- Log: faqat debug build da, shaxsiy ma'lumotsiz.

---

## 15. Papka va kod tuzilmasi (aniq)

```
ar-port/
├─ pubspec.yaml
├─ lib/
│  ├─ main.dart
│  ├─ app/ (theme, router, l10n)
│  ├─ features/
│  │   ├─ splash/ device_check/ launcher_menu/ permissions/ about/
│  ├─ platform/ (MethodChannel/EventChannel ko'priklari)
│  └─ widgets/ (iOS uslubidagi umumiy komponentlar)
├─ android/
│  ├─ app/ (Flutter runner, manifest, ArActivity registratsiyasi)
│  └─ arcore-engine/ (Kotlin kutubxona moduli)
│     └─ src/main/kotlin/uz/orbitx/arport/engine/
│        ├─ tracking/ camera/ render/ glass/ stereo/
│        ├─ hands/ gestures/ input/
│        ├─ windows/ surfaces/ web/ sandbox/
│        ├─ registry/ settings/ audio/ session/
│        └─ apps/ (native ilovalar)
├─ apps/ (plaginlar — 3.-bo'lim)
├─ tools/ (registry generator, manifest validator, schema)
├─ docs/ (TZ.md, ADDING_AN_APP.md, ARCHITECTURE.md, RISKS.md)
└─ test/ androidTest/
```

---

## 16. Test va qabul mezonlari

Har bosqich oxirida tekshiriladigan umumiy mezonlar:

| # | Mezon | Qabul |
|---|---|---|
| 1 | Giroskopsiz qurilmada AR ochilmaydi, dialog chiqadi | ✔ |
| 2 | Menyu: 2 rejim tanlanadi, sozlamalar (kamera/mik/karnay) ishlaydi | ✔ |
| 3 | Passthrough ≥ 30 FPS (maqsad 60), kechikish seziladigan emas | ✔ |
| 4 | Oynalar telefon burilganda fazoda qotib turadi (drift minimal) | ✔ |
| 5 | Qo'l aniqlanadi, glow ko'rinadi, tap/drag/zoom ishlaydi | ✔ |
| 6 | Stereo rejimda ikkita ko'z to'g'ri ko'rinadi | ✔ |
| 7 | Barcha ilovalar ochiladi; web-tiplilar o'z saytlarini ko'rsatadi | ✔ |
| 8 | 16:9 sukut, 9:16 va custom o'lcham ishlaydi | ✔ |
| 9 | 6 ta oyna bir vaqtda yuguradi (o'rta qurilmada ≥ 30 FPS) | ✔ |
| 10 | Google da login → boshqa ilovalar sessiyani ko'radi (imkon qadar) | ✔ / xavf qayd etiladi |
| 11 | Tashqi ilova/brauzer hech qachon ochilmaydi | ✔ |
| 12 | Yangi plagin papkasi qo'shilib build qilinsa, ilova avtomatik paydo bo'ladi | ✔ |
| 13 | Sozlamalar: fon rangi/opacity/blur, shortkutlar saqlanadi va qo'llanadi | ✔ |
| 14 | 2 soatlik sinov: crash yo'q, xotira oqishi yo'q | ✔ |
| 15 | Yakuniy `ar-port.apk` o'rnatiladi va ishga tushadi | ✔ |

---

## 17. Build va yetkazib berish

- Build: `flutter build apk --release` (yoki `--split-per-abi` o'chirilgan, universal APK) → chiqish fayli **`ar-port.apk`** deb qayta nomlanadi (`build/outputs/ar-port.apk`).
- Imzo: loyiha ichida `keystore` (test uchun) yaratiladi; haqiqiy chiqarish uchun foydalanuvchi o'z keystore ini qo'yadi (`docs/SIGNING.md`).
- ABI: `arm64-v8a` (+ ixtiyoriy `armeabi-v7a`).
- R8/ProGuard: yoqilgan, MediaPipe/ARCore qoidalari saqlangan.
- Versiya: `1.0.0 (1)`.
- APK hajmi hisoboti va README (o'rnatish, ruxsatlar, ma'lum cheklovlar).
- Agar muhitda Android SDK/Flutter bo'lmasa — AI o'rnatishga harakat qiladi; imkonsiz bo'lsa, aniq sababni va foydalanuvchi lokal build qilishi uchun buyruqlarni beradi (yolg'on "build tayyor" demaydi).

---

## 18. Yo'l xaritasi (keyingi versiyalar, hozir EMAS)
Portret rejimi, Rus/Ingliz tillari to'liq, qo'shimcha ilovalar (Fayllar, Xarita), ko'z kuzatuvi (old kamera orqali, tajribaviy), ovozli yordamchi, bulutli sozlamalar sinxroni.
