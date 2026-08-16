package com.openly.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.firebase.messaging.FirebaseMessaging
import com.openly.shared.ui.navigation.AppRoot
import com.openly.shared.ui.theme.OpenlyTheme
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // The shared MediaPicker can't register this itself — Activity-result contracts must be
    // registered before STARTED, which only a ComponentActivity can do. Same pattern as the
    // location permission launcher.
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            (application as OpenlyApp).container.mediaPicker.onResult(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as OpenlyApp
        app.container.permissionRequester.attachLauncher(requestLocationPermission)
        app.container.mediaPicker.attach(this, pickImage)
        app.container.voiceRecorder.attach(this)

        // Asked up front rather than at first tap: MediaRecorder.start() fails silently without it,
        // and a mid-recording permission dialog would lose the take.
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            OpenlyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        container = app.container,
                        onSignedIn = { uid ->
                            // Flush anything FCM handed us before the uid existed, then publish
                            // the current token — it can change on reinstall or data restore.
                            app.flushPendingPushToken(uid)
                            runCatching { FirebaseMessaging.getInstance().token.await() }
                                .getOrNull()
                                ?.let { app.container.profileRepository.updatePushToken(uid, it) }
                        }
                    )
                }
            }
        }
    }
}
