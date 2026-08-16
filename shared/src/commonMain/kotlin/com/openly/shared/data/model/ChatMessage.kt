package com.openly.shared.data.model

import kotlinx.serialization.Serializable

/** Mirrors a `matches/{matchId}/messages/{id}` document. */
@Serializable
data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val text: String = "",
    val sentAtMillis: Long = 0L,

    /**
     * When the recipient last opened the thread past this message. Written by the reader, not the
     * sender, which is why the rules let a participant update only these receipt fields on someone
     * else's message. 0 means unread.
     */
    val readAtMillis: Long = 0L,

    /** Set when the message has been edited; drives the "edited" marker under the bubble. */
    val editedAtMillis: Long = 0L,

    /**
     * "Delete for everyone" — the document is kept so the tombstone renders in place for both
     * sides (deleting the doc outright would silently reshuffle the thread for the other person).
     */
    val deletedForEveryone: Boolean = false,

    /** "Delete for me" — uids that have hidden this message locally-but-durably. */
    val deletedFor: List<String> = emptyList(),

    // Quoted message, denormalised so rendering a reply never needs a second fetch (and still
    // renders correctly if the original is later deleted).
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSenderUid: String = "",

    /** uid -> emoji. One reaction per person, same as WhatsApp. */
    val reactions: Map<String, String> = emptyMap(),

    // Attachment, empty for plain text messages. `type` is one of MessageType's values.
    val type: String = MessageType.TEXT.name,
    val mediaUrl: String = "",
    /** Voice-note length in seconds; 0 for other types. */
    val mediaDurationSeconds: Int = 0
) {
    val isDeleted: Boolean get() = deletedForEveryone

    /** A message is gone for [uid] if it was unsent for everyone or hidden by that person. */
    fun isHiddenFor(uid: String): Boolean = deletedForEveryone || deletedFor.contains(uid)

    val messageType: MessageType
        get() = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT)
}

enum class MessageType { TEXT, IMAGE, VOICE }
