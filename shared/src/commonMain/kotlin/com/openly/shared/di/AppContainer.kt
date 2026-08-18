package com.openly.shared.di

import com.openly.shared.data.local.OpenlyPrefs
import com.openly.shared.data.local.createOpenlyDataStore
import com.openly.shared.data.location.LocationPermissionRequester
import com.openly.shared.data.location.LocationTracker
import com.openly.shared.data.repository.AccountRepository
import com.openly.shared.data.repository.AuthRepository
import com.openly.shared.data.repository.ChatRepository
import com.openly.shared.data.repository.InterestRepository
import com.openly.shared.data.repository.MediaRepository
import com.openly.shared.data.repository.ProfileRepository
import com.openly.shared.data.repository.RadarRepository
import com.openly.shared.media.MediaPicker
import com.openly.shared.media.VoicePlayer
import com.openly.shared.media.VoiceRecorder
import com.openly.shared.notification.MessageNotifier
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage

/**
 * Manual DI, shared by both platforms.
 *
 * Screens depend on this rather than on an Android `Application` subclass, which is what lets the
 * whole UI layer live in commonMain. Platform-specific pieces (location, notifications) are handed
 * in by each host app.
 */
class AppContainer(
    val locationTracker: LocationTracker,
    val messageNotifier: MessageNotifier,
    val mediaPicker: MediaPicker = MediaPicker(),
    val voiceRecorder: VoiceRecorder = VoiceRecorder(),
    val voicePlayer: VoicePlayer = VoicePlayer()
) {
    val permissionRequester: LocationPermissionRequester by lazy { LocationPermissionRequester() }
    val prefs: OpenlyPrefs by lazy { OpenlyPrefs(createOpenlyDataStore()) }

    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val storage by lazy { Firebase.storage }

    val authRepository: AuthRepository by lazy { AuthRepository(auth) }
    val accountRepository: AccountRepository by lazy {
        AccountRepository(firestore, authRepository, prefs)
    }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(firestore) }
    val radarRepository: RadarRepository by lazy { RadarRepository(firestore) }
    val interestRepository: InterestRepository by lazy { InterestRepository(firestore) }
    val chatRepository: ChatRepository by lazy { ChatRepository(firestore) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(storage) }

    /**
     * Point Auth, Firestore and Storage at the Firebase Local Emulator Suite. Hosts call this in
     * debug builds only; see the README for the ports and the per-platform address quirks.
     */
    fun useEmulators(host: String) {
        auth.useEmulator(host, 9099)
        firestore.useEmulator(host, 8080)
        storage.useEmulator(host, 9199)
    }
}
