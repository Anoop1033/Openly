package com.openly.app.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/** Mirrors the `users/{uid}` Firestore document. Every field needs a default for Firestore's POJO mapping. */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val avatarEmoji: String = "🙂",
    val bio: String = "",
    // Firestore's Java mapper strips "is" from boolean getters (isOpen() -> field "open") when
    // inferring the field name, which would silently desync from the literal "isOpen" field this
    // app writes elsewhere. Pin both directions explicitly so read and write agree.
    @get:PropertyName("isOpen") @set:PropertyName("isOpen")
    var isOpen: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastActiveMillis: Long = 0L
) {
    @get:Exclude
    val hasLocation: Boolean get() = lat != null && lng != null
}
