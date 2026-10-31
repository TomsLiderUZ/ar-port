# Consumer ProGuard rules for arcore-engine (TZ 14 & TZ 17)

# Google ARCore
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**

# Google MediaPipe Tasks Vision & JNI native delegates
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**

# Android CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-dontwarn androidx.camera.**

# OpenGL ES & Native Surface
-keep class android.opengl.** { *; }
-keep class javax.microedition.khronos.** { *; }

# AR Port Engine Domain, Reflection & JSON Registry Models
-keep class uz.orbitx.arport.engine.registry.AppManifest** { *; }
-keep class uz.orbitx.arport.engine.registry.ARApp** { *; }
-keep class uz.orbitx.arport.engine.settings.ArSettings** { *; }
-keep class uz.orbitx.arport.engine.apps.** { *; }

# WebView JavaScript Interfaces & WebAppSurface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class android.webkit.** { *; }
-keep class uz.orbitx.arport.engine.web.WebAppSurface** { *; }

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
