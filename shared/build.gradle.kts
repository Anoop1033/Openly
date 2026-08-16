import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Three iOS targets: device (arm64) plus both simulator flavours, so the framework builds
    // on Apple Silicon and Intel Macs alike.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // GitLive's Firebase SDK wraps the native Firebase iOS SDK rather than bundling it, so the
    // app has to link it via CocoaPods — this block generates shared/shared.podspec, which
    // iosApp/Podfile then pulls in (see iosApp/README). Never run on this machine: CocoaPods and
    // the pod-install/framework-sync tasks it adds require macOS. Verify on a Mac.
    cocoapods {
        version = "1.0"
        summary = "Openly shared KMP module"
        homepage = "https://github.com/Anoop1033/Openly"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "Shared"
            isStatic = true
        }

        pod("FirebaseCore")
        pod("FirebaseAuth")
        pod("FirebaseFirestore")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")

            // Firebase has no official Kotlin Multiplatform SDK; GitLive wraps the native
            // Android and iOS SDKs behind one common API.
            implementation("dev.gitlive:firebase-auth:2.6.0")
            implementation("dev.gitlive:firebase-firestore:2.6.0")
            implementation("dev.gitlive:firebase-storage:2.6.0")

            // Compose Multiplatform has no built-in network image loader; Coil 3 is the one that
            // supports iOS as well as Android. The ktor3 engine is what makes it work off-JVM.
            implementation("io.coil-kt.coil3:coil-compose:3.2.0")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.2.0")

            implementation("androidx.datastore:datastore-preferences-core:1.1.1")
            implementation("androidx.datastore:datastore-core-okio:1.1.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            implementation("com.squareup.okio:okio:3.10.2")
        }

        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.activity:activity-compose:1.9.0")
            // Fused location is Android-only; iOS uses CoreLocation via the actual in iosMain.
            implementation("com.google.android.gms:play-services-location:21.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.openly.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
