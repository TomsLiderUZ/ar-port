# AR Port — Xavflar va Cheklovlar (Risks & Limitations)

Ushbu hujjat loyihadagi texnik cheklovlar, real xavflar va ularning rejalashtirilgan yechimlarini jamlaydi (TZ 2-bo'lim asosida).

---

## 1. Kamera kechikishi (Passthrough Latency)
- **Xavf:** Mobil telefon kameralarida sensor kadr olish, ISP qayta ishlash va GPU ga uzatish kechikishi 40–100 ms ni tashkil etishi mumkin.
- **Yechim:** CameraX Preview to'g'ridan-to'g'ri `SurfaceTexture` (OES) orqali GPU ga beriladi (CPU xotira nusxasi olinmaydi). EIS (elektron stabilizatsiya) o'chiriladi. Maqsad: ≤ 80 ms, 60 FPS.

## 2. Jismoniy stereo chuqurlikning yo'qligi
- **Xavf:** Telefonda bitta orqa kamera ishlatiladi. Stereo rejimda ikkala ko'zga ham bitta kamera tasviri (ozgina IPD gorizontal siljishi bilan) uzatiladi.
- **Yechim:** Bu apparat cheklovi foydalanuvchiga halol tushuntiriladi. Virtual fazoviy oynalar esa haqiqiy stereo (ikkala ko'z uchun hisoblangan to'g'ri burchak ostida) chiziladi.

## 3. Ko'z kuzatuvi (Eye Tracking) yo'qligi
- **Xavf:** Apple Vision Pro dagi kabi ko'z bilan qarab tanlash mobil telefonda mavjud emas.
- **Yechim:** Qo'l nuri (Hand Ray) va barmoq chimchilashi (Pinch) asosiy tanlash vositasi sifatida qo'llaniladi.

## 4. Qo'l kuzatuvi cheklovlari
- **Xavf:** Yorug'lik yetarli bo'lmaganda yoki qo'l kamera ko'rish maydonidan (FOV) chiqqanda MediaPipe aniqligi pasayadi yoki qo'l yo'qoladi.
- **Yechim:** One-Euro filtri bilan titrash kamaytiriladi. Qo'l kadrda yo'qolganda 200 ms davomida oxirgi holat keshlanadi, so'ng silliq yashiriladi. Oddiy rejimda sensorli ekran zaxira kiritish vositasi bo'ladi.

## 5. WebView da Google OAuth bloklanishi
- **Xavf:** Google o'zining xavfsizlik siyosatiga binoan oddiy WebView lardan OAuth kirishni cheklaydi (`disallowed_useragent`).
- **Yechim:** 
  1. Toza zamonaviy mobil Chrome User-Agent o'rnatish (`wv` va `Version/4.0` belgilari olib tashlanadi).
  2. `X-Requested-With` sarlavhasini olib tashlash.
  3. Agar bloklanish davom etsa: foydalanuvchiga to'g'ridan-to'g'ri email/parol, telefon raqami yoki QR-kod orqali kirish imkoniyatlari tavsiya etiladi.

## 6. DRM (Digital Rights Management)
- **Xavf:** Spotify va ba'zi yuqori sifatli video oqimlarida Widevine L1/L3 litsenziyalari talab qilinishi mumkin.
- **Yechim:** Standart Android WebView DRM parametrlarini yoqish; agar ma'lum servisda DRM o'ynamasa, foydalanuvchiga mos tushuntirish beriladi.

## 7. Batareya sarfi va qizish (Thermal Throttling)
- **Xavf:** Kamera oqimi + MediaPipe ML modeli + OpenGL ES 3.0 render + bir nechta offscreen WebView apparatni qizdirishi mumkin.
- **Yechim:** 
  - Adaptiv sifat tizimi: kadrlar soni (FPS) 45 dan tushsa yoki `PowerManager.getThermalHeadroom` isishni ko'rsatsa, avval blur sifati, so'ng WebView FPS va tekstura o'lchamlari kamaytiriladi.
  - Oynalarni uxlatish (sleep policy): faol bo'lmagan oynalar to'xtatiladi.
  - Frustum culling: foydalanuvchi qaramayotgan oynalar chizilmaydi.

## 8. Fazoda qotish (Drift)
- **Xavf:** 3DoF (faqat giroskop) uzoq muddatda burilish driftiga ega bo'ladi.
- **Yechim:** Foydalanuvchiga tezkor "Markazlashtirish" (Recentering) qulay amali taqdim etiladi. ARCore qo'llaydigan qurilmalarda 6DoF rejimi ishlaydi.

## 9. Sandbox xavfsizligi
- **Xavf:** Web sahifalar tashqi brauzer yoki tashqi ilovalarni (`Intent` orqali) ochishga urinishi mumkin.
- **Yechim:** `SandboxPolicy` orqali barcha tashqi intentlar qat'iy bloklanadi, havolalar faqat AR Port ichidagi Google brauzerida yoki tegishli oynada ochiladi.

