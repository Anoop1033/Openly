package com.openly.shared.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * Directory the preferences file lives in — app-private storage, resolved per platform
 * (`Context.filesDir` on Android, the Documents directory on iOS).
 */
internal expect fun prefsDirectoryPath(): String

/**
 * DataStore's core is multiplatform, but nothing decides *where* the file goes — that part is
 * platform-specific, so only the directory is expect/actual.
 */
fun createOpenlyDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { "${prefsDirectoryPath()}/$PREFS_FILE_NAME".toPath() }
    )
