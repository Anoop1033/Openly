package com.openly.app.data.model

/** Mirrors a `matches/{matchId}/messages/{id}` document. */
data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val text: String = "",
    val sentAtMillis: Long = 0L
)
