# AR Port — Arxitektura hujjati (Architecture)

## 1. Umumiy tizim tuzilmasi

AR Port arxitekturasi ikkita asosiy qatlamdan iborat:

```
┌──────────────────────────────────────────────────────────────────┐
│                    Flutter UI Qobiq (Dart)                       │
│  - Splash ekrani                                                 │
│  - Qurilma sensorlarini tekshirish (Gyroscope, ARCore, GL ES)    │
│  - Boshlang'ich menyu (Stereo / Oddiy rejim tanlash)             │
│  - Qurilma sozlamalari (Kamera, Mik, Karnay, IPD)                │
│  - Ruxsatlar oqimi (Permissions)                                 │
└─────────────────────────────────┬────────────────────────────────┘
                                  │ MethodChannel / Intent
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│              Native Kotlin AR Dvigateli (arcore-engine)          │
│                                                                  │
│  ┌───────────────────────┐       ┌────────────────────────────┐  │
│  │   PassthroughRenderer │       │      TrackingManager       │  │
│  │  CameraX Preview →    │       │  Gyroscope / RotationVector│  │
│  │  GL_TEXTURE_EXTERNAL  │       │  ARCore 6DoF / Kalman /    │  │
│  │  OES (GPU fon kadr)   │       │  Predictive Tracking       │  │
│  └───────────┬───────────┘       └─────────────┬──────────────┘  │
│              │                                 │                 │
│              ▼                                 ▼                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │           OpenGL ES 3.0 Render Konveyeri                   │  │
│  │  - Fon kadrini chizish                                     │  │
│  │  - StereoRenderer (Chap / O'ng ko'z, IPD, Barrel dist.)   │  │
│  │  - GlassRenderer (Kawase/Gaussian Blur, Specular, Squircle)│  │
│  └───────────────────────────┬────────────────────────────────┘  │
│                              │                                   │
│  ┌───────────────────────────┴────────────────────────────────┐  │
│  │              WindowManager3D & SurfaceProvider             │  │
│  │  - Fazoviy oynalar (SpatialWindow: vec3, quat, 16:9/9:16)  │  │
│  │  - WebView offscreen Surface → GL tekstura                 │  │
│  │  - Z-tartib, Frustum culling, Focus, Uyqu siyosati         │  │
│  └───────────────────────────┬────────────────────────────────┘  │
│                              │                                   │
│  ┌───────────────────────────┴────────────────────────────────┐  │
│  │           HandTracker & GestureEngine                      │  │
│  │  - MediaPipe Tasks Vision (2 qo'l, 21 nuqta, GPU)          │  │
│  │  - One-Euro filtr (silliqlash)                             │  │
│  │  - Hand Ray, Nuqta, Aura/Glow                              │  │
│  │  - Pinch, Drag, Zoom, Palm-Up, Swipe, Fist                 │  │
│  │  - InputInjector (MotionEvent uzatish)                     │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

## 2. Modullar tavsifi

1. **`lib/` (Flutter):**
   - iOS dizayn tizimiga mos interfeys komponentlari (katta sarlavhalar, inset-grouped kartalar, spring animatsiyalar, dynamic color).
   - Ruxsatlar va giroskop mavjudligini tekshirish.
   - `ArActivity` ni ishga tushirish uchun konfiguratsiyani (rejim, kamera, audio, IPD) tayyorlash.

2. **`android/arcore-engine/` (Kotlin kutubxonasi):**
   - `tracking/`: Gyroscope 3DoF va ARCore 6DoF kuzatuvi, view/projection matritsalar.
   - `camera/`: CameraX past kechikishli video oqimi.
   - `render/`: OpenGL ES 3.0 konteksti va sahna boshqaruvi.
   - `glass/`: Liquid Glass shaderlari (shaffoflik, blur, chekka yorug'liklari).
   - `stereo/`: Ikkita ko'zli stereo viewport va linza distorsiyasi.
   - `hands/` & `gestures/`: MediaPipe Hand Landmarker integratsiyasi va fazoviy imo-ishoralar.
   - `windows/` & `surfaces/`: 3D fazodagi oynalar ierarxiyasi va offscreen render.
   - `web/` & `sandbox/`: Xavfsiz WebView muhiti, umumiy cookie/sessiya, tashqi intentlarni bloklash.
   - `registry/`: Dynamic ilovalar ro'yxati (`apps/`).

3. **`apps/` (Plagin tizimi):**
   - Har bir ilova mustaqil papka: `manifest.json`, piktogramma, assets va ixtiyoriy native kod.
   - Build vaqtida kod generatsiyasi orqali tizimga avtomatik ulanadi.

