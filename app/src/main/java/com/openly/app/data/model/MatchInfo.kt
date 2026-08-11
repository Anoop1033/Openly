package com.openly.app.data.model

/** Mirrors a `matches/{id}` document, created once both sides have approved. */
data class MatchInfo(
    val id: String = "",
    val uids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    // Denormalised copy of the newest message. Keeping it on the match doc means the chat list and
    // the unread badge need one listener over `matches` instead of a per-match messages listener.
    val lastMessageText: String = "",
    val lastMessageSenderUid: String = "",
    val lastMessageAtMillis: Long = 0L
) {
    fun otherUid(selfUid: String): String = uids.firstOrNull { it != selfUid } ?: ""
}
