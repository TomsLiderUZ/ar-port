# Flutter and Application ProGuard / R8 Rules (TZ 14 & TZ 17)

# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-dontwarn io.flutter.**

# Preserve AR Port Application & ArActivity
-keep class uz.orbitx.arport.** { *; }
-keepclassmembers class uz.orbitx.arport.** { *; }

# Google ARCore & MediaPipe
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Suppress compiler annotation processor warnings from auto-value / guava
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**

# Prohibit Intent escaping or reflection of dangerous components
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
}
