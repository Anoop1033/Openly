import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Upload-key credentials live in keystore.properties, which is untracked on purpose. The key is
// the one artifact in this repo that cannot be regenerated: Play ties the listing to it forever,
// so losing it means never shipping an update to existing installs again. Back up both the .jks
// and this file somewhere off this machine.
//
// The file being absent is not an error — release still configures, it just comes out unsigned so
// a fresh clone and CI can both build without secrets.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

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
        // Overridable so a release can be cut without a commit: -PopenlyVersionCode=2. Play rejects
        // a bundle whose versionCode it has already seen, so this has to go up on every upload.
        versionCode = (project.findProperty("openlyVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("openlyVersionName") as String?) ?: "1.0"

        // Declared here rather than only in debug so the symbol exists in every variant --
        // OpenlyApp reads it from inside an `if (BuildConfig.DEBUG)`, which still has to compile in
        // release. Left empty here so a shipped binary carries no developer LAN address; debug
        // overrides it below with the real one.
        buildConfigField("String", "EMULATOR_LAN_HOST", "\"\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // findByName rather than getByName: the config only exists when keystore.properties is
            // present, and an unsigned release build is better than a build that cannot configure.
            signingConfig = signingConfigs.findByName("release")
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
    // Not used directly -- this app has no fragments. It arrives transitively at 1.1.0, which
    // predates the ActivityResult APIs MainActivity registers, and lintVitalRelease fails the
    // release build over it (InvalidFragmentVersionForActivityResult). Forcing a modern version is
    // the fix; R8 strips what goes unused.
    implementation("androidx.fragment:fragment:1.8.5")
    // FCM is Android-native; the iOS side uses APNs directly (see README).
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
