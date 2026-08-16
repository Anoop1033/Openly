package com.openly.shared.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val DEFAULT_RADAR_RADIUS_METERS = 200.0
internal const val PREFS_FILE_NAME = "openly.preferences_pb"

/**
 * Local, per-device settings. The DataStore instance itself is created per platform (file paths
 * differ), but every key and read/write below is shared.
 */
class OpenlyPrefs(private val dataStore: DataStore<Preferences>) {

    private val onboardedKey = booleanPreferencesKey("has_onboarded")
    private val radiusKey = doublePreferencesKey("radar_radius_meters")

    val hasOnboarded: Flow<Boolean> =
        dataStore.data.map { it[onboardedKey] ?: false }

    val radarRadiusMeters: Flow<Double> =
        dataStore.data.map { it[radiusKey] ?: DEFAULT_RADAR_RADIUS_METERS }

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[onboardedKey] = value }
    }

    suspend fun setRadarRadiusMeters(value: Double) {
        dataStore.edit { it[radiusKey] = value }
    }

    // Read state is deliberately per-device rather than a Firestore field: "have *I* seen this"
    // is local by nature, and keeping it off the match doc avoids a write on every chat open.
    private fun lastReadKey(matchId: String) = longPreferencesKey("last_read_$matchId")

    /** Map of matchId -> the timestamp of the newest message this device has seen. */
    val lastReadByMatch: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("last_read_") }
            .mapNotNull { (key, value) ->
                (value as? Long)?.let { key.name.removePrefix("last_read_") to it }
            }
            .toMap()
    }

    suspend fun markMatchRead(matchId: String, upToMillis: Long) {
        dataStore.edit { prefs ->
            val existing = prefs[lastReadKey(matchId)] ?: 0L
            if (upToMillis > existing) prefs[lastReadKey(matchId)] = upToMillis
        }
    }
}
