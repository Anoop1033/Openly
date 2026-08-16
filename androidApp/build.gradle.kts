import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.openly.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openly.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // Host of the Firebase Local Emulator Suite as seen from a *physical* device on the same
            // WiFi. Emulators use 10.0.2.2 instead, which only they can resolve.
            // Override per-machine with -PemulatorHost=<lan-ip> or in gradle.properties.
            buildConfigField(
                "String",
                "EMULATOR_LAN_HOST",
                "\"${project.findProperty("emulatorHost") ?: "192.168.1.41"}\""
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // FCM is Android-native; the iOS side uses APNs directly (see README).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
