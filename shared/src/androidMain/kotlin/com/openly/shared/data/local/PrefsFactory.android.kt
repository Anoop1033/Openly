package com.openly.shared.data.local

import android.content.Context

/**
 * Set once from the Android app's Application.onCreate — the shared layer has no Context of its
 * own, and DataStore only needs the directory rather than the Context itself.
 */
internal lateinit var androidPrefsDirectory: String

fun initAndroidPrefs(context: Context) {
    androidPrefsDirectory = context.filesDir.absolutePath
}

internal actual fun prefsDirectoryPath(): String = androidPrefsDirectory
