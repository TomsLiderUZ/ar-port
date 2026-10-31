pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            val propFile = file("local.properties")
            if (propFile.exists()) {
                propFile.inputStream().use { properties.load(it) }
            }
            properties.getProperty("flutter.sdk")
        }

    if (flutterSdkPath != null && file("$flutterSdkPath/packages/flutter_tools/gradle").exists()) {
        includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val hasFlutter = run {
    val properties = java.util.Properties()
    val propFile = file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { properties.load(it) }
    }
    val path = properties.getProperty("flutter.sdk")
    path != null && file("$path/packages/flutter_tools/gradle").exists()
}

plugins {
    id("com.android.application") version "9.1.0" apply false
    id("com.android.library") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}

if (hasFlutter) {
    apply {
        plugin("dev.flutter.flutter-plugin-loader")
    }
    include(":app")
}
include(":arcore-engine")
