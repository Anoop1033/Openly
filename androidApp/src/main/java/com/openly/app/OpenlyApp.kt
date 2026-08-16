package com.openly.app

import android.app.Application
import android.os.Build
import com.openly.shared.data.local.initAndroidPrefs
import com.openly.shared.data.location.LocationTracker
import com.openly.shared.di.AppContainer
import com.openly.shared.notification.MessageNotifier

class OpenlyApp : Application() {

    val container: AppContainer by lazy {
        AppContainer(
            locationTracker = LocationTracker(this),
            messageNotifier = MessageNotifier(this)
        )
    }

    /**
     * FCM can hand us a token before anonymous sign-in has produced a uid, so the token is stashed
     * and flushed once the uid is known (see [flushPendingPushToken], called from MainActivity).
     */
    @Volatile
    private var pendingPushToken: String? = null

    suspend fun registerPushToken(token: String) {
        val uid = container.authRepository.currentUid
        if (uid == null) {
            pendingPushToken = token
            return
        }
        container.profileRepository.updatePushToken(uid, token)
    }

    suspend fun flushPendingPushToken(uid: String) {
        val token = pendingPushToken ?: return
        pendingPushToken = null
        container.profileRepository.updatePushToken(uid, token)
    }

    override fun onCreate() {
        super.onCreate()
        initAndroidPrefs(this)

        // Debug builds talk to the Firebase Local Emulator Suite (`firebase emulators:start`)
        // instead of a real project, so the whole app runs against a local backend with no cloud
        // project needed. The host differs by device: an Android emulator reaches the developer
        // machine through the magic alias 10.0.2.2, while a physical phone has to use that
        // machine's actual LAN address (baked in at build time, see androidApp/build.gradle.kts).
        if (BuildConfig.DEBUG) {
            val host = if (isRunningOnEmulator()) "10.0.2.2" else BuildConfig.EMULATOR_LAN_HOST
            container.useEmulators(host)
        }
    }

    private fun isRunningOnEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
}
