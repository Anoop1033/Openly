package com.openly.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.openly.shared.data.location.LocationTracker
import com.openly.shared.di.AppContainer
import com.openly.shared.notification.MessageNotifier
import com.openly.shared.ui.navigation.AppRoot
import com.openly.shared.ui.theme.OpenlyTheme
import platform.UIKit.UIViewController

/**
 * Swift's iOSApp.swift calls this to obtain the root UIViewController. Unlike Android there's no
 * Application-class dance: LocationTracker/MessageNotifier take no Context on iOS, and prefs
 * resolve their own directory via PrefsFactory.ios.kt, so the container can be built right here.
 *
 * NOTE: never compiled or run — iOS builds require macOS + Xcode. Verify on a Mac.
 */
fun MainViewController(): UIViewController {
    val container = AppContainer(
        locationTracker = LocationTracker(),
        messageNotifier = MessageNotifier()
    )
    container.messageNotifier.requestPermission()

    return ComposeUIViewController {
        OpenlyTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppRoot(container = container)
            }
        }
    }
}
