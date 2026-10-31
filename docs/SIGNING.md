# AR Port — APK Imzolash Qo'llanmasi (Signing Guide)

Ushbu hujjat AR Port Android ilovasi (`uz.orbitx.arport`) uchun chiqarish (release) kaliti va imzolash konfiguratsiyasini tushuntiradi.

---

## 1. Test Chiqarish Kaliti (Lokal Keystore)

Dastlabki sinov va avtomatlashtirilgan release build uchun loyihada vaqtinchalik test kaliti yaratilgan:

- **Fayl joylashuvi:** `android/app/release.keystore`
- **Konfiguratsiya fayli:** `android/key.properties`
- **Kalit taxallusi (Alias):** `arport`
- **Algoritm:** RSA 2048-bit (SHA256withRSA)
- **Amal qilish muddati:** 10,000 kun (~27 yil)
- **Parol:** `arport123`
- **Tashkilot:** `CN=OrbitX, OU=Spatial, O=ARPort, L=Tashkent, ST=Tashkent, C=UZ`

---

## 2. O'z Shaxsiy Ishlab Chiqarish Kalitingizni Yaratish (Production Keystore)

Google Play Store yoki haqiqiy ochiq tarqatish uchun o'zingizning xavfsiz shaxsiy kalitingizni yaratishingiz tavsiya etiladi:

```bash
keytool -genkey -v -keystore android/app/my-upload-key.keystore \
        -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
```

So'ng `android/key.properties` fayliga o'z parollaringizni kiriting:

```properties
storePassword=sizning_maxfiy_parolingiz
keyPassword=sizning_maxfiy_parolingiz
keyAlias=my-key-alias
storeFile=my-upload-key.keystore
```

---

## 3. `android/app/build.gradle.kts` Sozlamalari

Release build vaqtida Gradle `android/key.properties` mavjudligini avtomatik tekshiradi:

```kotlin
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

signingConfigs {
    create("release") {
        keyAlias = keystoreProperties["keyAlias"] as String? ?: "arport"
        keyPassword = keystoreProperties["keyPassword"] as String? ?: "arport123"
        storeFile = file(keystoreProperties["storeFile"] as String? ?: "release.keystore")
        storePassword = keystoreProperties["storePassword"] as String? ?: "arport123"
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

> [!CAUTION]
> Haqiqiy ishlab chiqarish uchun `key.properties` va `*.keystore` fayllarini ochiq git repozitoriysiga (.gitignore orqali) yuklamang.

