# AR Port — Yangi Ilova Qo'shish Qo'llanmasi (Adding an App)

AR Port ilovalar ekotizimi dinamik plagin arxitekturasiga asoslangan. Har bir ilova mustaqil katalogda joylashadi va `manifest.json` orqali tizimga ro'yxatdan o'tadi.

---

## 1. Ilova katalogining tuzilishi

Yangi ilova yaratish uchun `apps/<app_id>/` papkasini oching (yoki `apps/_template/` dan nusxa oling):

```
apps/<app_id>/
├── manifest.json       # Ilova metama'lumotlari, ruxsatlari va o'lchamlari
├── icon.png            # 512x512 yoki 1024x1024 pikselli iOS squircle ikonka
├── assets/             # Web yoki 2D/3D o'yinlar uchun: index.html, JS, CSS, modellar
└── README.md           # Ilova tavsifi va foydalanish yo'riqnomasi
```

---

## 2. manifest.json maydonlari

Har bir `manifest.json` quyidagi tuzilishga ega bo'lishi shart:

```json
{
  "id": "my_app",
  "name": {
    "uz": "Mening Ilovam",
    "en": "My App",
    "ru": "Мое Приложение"
  },
  "icon": "📱",
  "version": "1.0.0",
  "type": "web",
  "category": "tools",
  "entry": "https://myapp.example.com",
  "defaultAspect": "16:9",
  "defaultSize": {
    "w": 1.0,
    "h": 0.5625
  },
  "multiWindow": false,
  "permissions": ["audio"],
  "allowedHosts": ["myapp.example.com"],
  "sessionGroup": "default",
  "minEngineVersion": "1.0",
  "order": 50
}
```

### Maydonlar izohi:
- `id` (String): Unikal identifikator (kichik harflar va pastki chiziq).
- `name` (Obyekt): `uz`, `en`, `ru` tillaridagi nomlar.
- `icon` (String): Emojili belgi yoki `icon.png` fayli yo'li.
- `type` (String): `"web"` | `"native"` | `"game2d"` | `"game3d"`.
- `category` (String): `"media"` | `"social"` | `"tools"` | `"games"` | `"system"`.
- `entry` (String): Web uchun to'liq URL (`https://...`), o'yinlar uchun nisbiy html (`assets/index.html`), native uchun sinf nomi.
- `defaultAspect` (String): `"16:9"` | `"9:16"` | `"free"`.
- `defaultSize` (Obyekt): Dastlabki kenglik va balandlik (metrlarda).
- `multiWindow` (Boolean): Bir vaqtda bir nechta nusxada ochila olishi.
- `allowedHosts` (Massiv): Tashqi havolalar xavfsizlik filtri (Sandbox).
- `order` (Int): Home ekrani va reyestrdagi tartib raqami.

---

## 3. Reyestrni generatsiya qilish

Ilova fayllarini joylashtirgandan so'ng, quyidagi buyruqni ishga tushiring:

```bash
dart run tools/generate_registry.dart
```

Ushbu buyruq barcha manifestlarni tekshiradi, `apps/app_registry.g.json` ni hosil qiladi va Android aktivlariga (`android/arcore-engine/src/main/assets/registry/app_registry.json`) nusxalaydi.

