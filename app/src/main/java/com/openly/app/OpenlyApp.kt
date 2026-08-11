package com.openly.app

import android.app.Application
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.openly.app.data.local.OpenlyPrefs
import com.openly.app.data.location.LocationTracker
import com.openly.app.data.repository.AuthRepository
import com.openly.app.data.repository.ChatRepository
import com.openly.app.data.repository.InterestRepository
import com.openly.app.data.repository.ProfileRepository
import com.openly.app.data.repository.RadarRepository
import com.openly.app.notification.MessageNotifier

class OpenlyApp : Application() {

    val prefs: OpenlyPrefs by lazy { OpenlyPrefs(this) }
    val locationTracker: LocationTracker by lazy { LocationTracker(this) }
    val messageNotifier: MessageNotifier by lazy { MessageNotifier(this) }

    val authRepository: AuthRepository by lazy { AuthRepository(FirebaseAuth.getInstance()) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(FirebaseFirestore.getInstance()) }
    val radarRepository: RadarRepository by lazy { RadarRepository(FirebaseFirestore.getInstance()) }
    val interestRepository: InterestRepository by lazy { InterestRepository(FirebaseFirestore.getInstance()) }
    val chatRepository: ChatRepository by lazy { ChatRepository(FirebaseFirestore.getInstance()) }

    override fun onCreate() {
        super.onCreate()
        // Debug builds talk to the Firebase Local Emulator Suite (`firebase emulators:start`)
        // instead of a real project, so the whole app runs against a local backend with no cloud
        // project needed. The host differs by device: an Android emulator reaches the developer
        // machine through the magic alias 10.0.2.2, while a physical phone has to use that
        // machine's actual LAN address (baked in at build time, see app/build.gradle.kts).
        if (BuildConfig.DEBUG) {
            val host = if (isRunningOnEmulator()) "10.0.2.2" else BuildConfig.EMULATOR_LAN_HOST
            FirebaseAuth.getInstance().useEmulator(host, 9099)
            FirebaseFirestore.getInstance().useEmulator(host, 8080)
        }
    }

    private fun isRunningOnEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
}
