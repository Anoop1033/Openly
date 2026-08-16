package com.openly.shared.data.model

import kotlinx.serialization.Serializable

/** Mirrors a `matches/{id}` document, created once both sides have approved. */
@Serializable
data class MatchInfo(
    val id: String = "",
    val uids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    // Denormalised copy of the newest message. Keeping it on the match doc means the chat list and
    // the unread badge need one listener over `matches` instead of a per-match messages listener.
    val lastMessageText: String = "",
    val lastMessageSenderUid: String = "",
    val lastMessageAtMillis: Long = 0L,
    /** MessageType of the newest message, so the chat list can show "📷 Photo" instead of a blank. */
    val lastMessageType: String = MessageType.TEXT.name,

    /**
     * Whoever is currently typing, and when they last said so. Both live on the match rather than
     * a subcollection so the existing single `matches` listener picks them up for free. Staleness
     * is decided on read (see TYPING_TIMEOUT_MILLIS) rather than by clearing on a timer, so a
     * client that dies mid-typing can't leave the indicator stuck on forever.
     */
    val typingUid: String = "",
    val typingAtMillis: Long = 0L
) {
    fun otherUid(selfUid: String): String = uids.firstOrNull { it != selfUid } ?: ""

    /** True when [uid] is the one typing and the signal is still fresh. */
    fun isTyping(uid: String, nowMillis: Long): Boolean =
        typingUid == uid && typingUid.isNotEmpty() &&
            nowMillis - typingAtMillis < TYPING_TIMEOUT_MILLIS

    companion object {
        /** Typing pings are re-sent every ~2s while typing, so 5s of silence means "stopped". */
        const val TYPING_TIMEOUT_MILLIS = 5_000L
    }
}
