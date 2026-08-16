package com.openly.shared.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Mirrors the `users/{uid}` Firestore document. Defaults keep partial documents decodable. */
@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val avatarEmoji: String = "🙂",
    val bio: String = "",
    // kotlinx.serialization uses the property name verbatim, so this stays "isOpen" on the wire.
    // (The Firebase *Java* mapper used to strip the "is" prefix off the getter and silently read
    // back a different field — that whole class of bug goes away with the multiplatform SDK.)
    val isOpen: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastActiveMillis: Long = 0L,
    /**
     * Per-install push token, read by the Cloud Function that fans out new-message notifications.
     * Anonymous auth is per-install, so one uid maps to exactly one device and a single token is
     * enough — a real multi-device account would need a `tokens` subcollection instead.
     */
    val fcmToken: String = ""
) {
    @Transient
    val hasLocation: Boolean = lat != null && lng != null
}
