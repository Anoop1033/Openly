package com.openly.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "openly_prefs")

const val DEFAULT_RADAR_RADIUS_METERS = 200.0

class OpenlyPrefs(private val context: Context) {

    private val onboardedKey = booleanPreferencesKey("has_onboarded")
    private val radiusKey = doublePreferencesKey("radar_radius_meters")

    val hasOnboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    val radarRadiusMeters: Flow<Double> =
        context.dataStore.data.map { it[radiusKey] ?: DEFAULT_RADAR_RADIUS_METERS }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[onboardedKey] = value }
    }

    suspend fun setRadarRadiusMeters(value: Double) {
        context.dataStore.edit { it[radiusKey] = value }
    }

    // Read state is deliberately per-device rather than a Firestore field: "have *I* seen this"
    // is local by nature, and keeping it off the match doc avoids a write on every chat open.
    private fun lastReadKey(matchId: String) = longPreferencesKey("last_read_$matchId")

    /** Map of matchId -> the timestamp of the newest message this device has seen. */
    val lastReadByMatch: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("last_read_") }
            .mapNotNull { (key, value) ->
                (value as? Long)?.let { key.name.removePrefix("last_read_") to it }
            }
            .toMap()
    }

    suspend fun markMatchRead(matchId: String, upToMillis: Long) {
        context.dataStore.edit { prefs ->
            val existing = prefs[lastReadKey(matchId)] ?: 0L
            if (upToMillis > existing) prefs[lastReadKey(matchId)] = upToMillis
        }
    }
}
