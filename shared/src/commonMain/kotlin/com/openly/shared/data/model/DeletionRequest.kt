package com.openly.shared.data.model

import kotlinx.serialization.Serializable

/**
 * A user's request to be erased. Deliberately tiny and write-once: it is a signal to the purge
 * function in functions/index.js, not a record worth keeping. The function deletes it last, once
 * the cascade has finished, so its presence doubles as "this cascade has not completed yet".
 */
@Serializable
data class DeletionRequest(
    val uid: String = "",
    val requestedAtMillis: Long = 0L
)
