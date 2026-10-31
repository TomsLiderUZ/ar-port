pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val propFile = file("local.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { properties.load(it) }
        }
        properties.getProperty("flutter.sdk") ?: "C:/tools/flutter"
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "9.1.0" apply false
    id("com.android.library") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}

include(":app")
include(":arcore-engine")
