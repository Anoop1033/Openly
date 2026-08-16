package com.openly.shared.data.repository

import com.openly.shared.data.model.ChatMessage
import com.openly.shared.data.model.MatchInfo
import com.openly.shared.data.model.MessageType
import com.openly.shared.data.remote.FirestorePaths
import com.openly.shared.platform.nowMillis
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val firestore: FirebaseFirestore) {

    private val matches get() = firestore.collection(FirestorePaths.MATCHES)

    private fun messagesRef(matchId: String) =
        matches.document(matchId).collection(FirestorePaths.MESSAGES)

    suspend fun getMatch(matchId: String): MatchInfo? =
        runCatching { matches.document(matchId).get().data<MatchInfo>() }.getOrNull()

    fun observeMatch(matchId: String): Flow<MatchInfo?> =
        matches.document(matchId).snapshots.map { runCatching { it.data<MatchInfo>() }.getOrNull() }

    fun observeMatches(uid: String): Flow<List<MatchInfo>> =
        matches
            .where { "uids" contains uid }
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { runCatching { it.data<MatchInfo>() }.getOrNull() }
                    .sortedByDescending { it.createdAtMillis }
            }

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> =
        messagesRef(matchId)
            .orderBy("sentAtMillis", Direction.ASCENDING)
            .snapshots
            .map { snapshot ->
                snapshot.documents.mapNotNull { runCatching { it.data<ChatMessage>() }.getOrNull() }
            }

    suspend fun sendMessage(
        matchId: String,
        senderUid: String,
        text: String,
        replyTo: ChatMessage? = null,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String = "",
        mediaDurationSeconds: Int = 0
    ) {
        val sentAt = nowMillis()
        // Firestore generates the id; mirror it into the document so ChatMessage.id is populated
        // for LazyColumn keys without a second read.
        val doc = messagesRef(matchId).document
        doc.set(
            ChatMessage(
                id = doc.id,
                senderUid = senderUid,
                text = text,
                sentAtMillis = sentAt,
                replyToId = replyTo?.id.orEmpty(),
                replyToText = replyTo?.previewText().orEmpty(),
                replyToSenderUid = replyTo?.senderUid.orEmpty(),
                type = type.name,
                mediaUrl = mediaUrl,
                mediaDurationSeconds = mediaDurationSeconds
            )
        )

        // Mirror onto the parent match so the chat list and unread badge stay in sync without
        // opening a messages listener per conversation. This is only a denormalised cache — the
        // message itself is already committed above, so a failure here must never lose it or take
        // the app down with it (a stale preview is far better than a crash).
        runCatching {
            matches.document(matchId).update(
                "lastMessageText" to previewFor(type, text),
                "lastMessageSenderUid" to senderUid,
                "lastMessageAtMillis" to sentAt,
                "lastMessageType" to type.name
            )
        }
    }

    /**
     * Stamps `readAtMillis` on the other person's unread messages. Best-effort per message: one
     * rejected write (a rule tweak, a message deleted mid-flight) shouldn't abort the rest.
     */
    suspend fun markMessagesRead(matchId: String, selfUid: String, messages: List<ChatMessage>) {
        val now = nowMillis()
        messages
            .filter { it.senderUid != selfUid && it.readAtMillis == 0L && !it.isDeleted }
            .forEach { message ->
                runCatching {
                    messagesRef(matchId).document(message.id).update("readAtMillis" to now)
                }
            }
    }

    /**
     * Pings "I'm typing" onto the match. Callers debounce this (see ChatViewModel) — it is
     * deliberately a plain overwrite so the last writer wins and no cleanup pass is needed.
     */
    suspend fun setTyping(matchId: String, selfUid: String, typing: Boolean) {
        runCatching {
            matches.document(matchId).update(
                "typingUid" to if (typing) selfUid else "",
                "typingAtMillis" to if (typing) nowMillis() else 0L
            )
        }
    }

    suspend fun editMessage(matchId: String, messageId: String, newText: String) {
        messagesRef(matchId).document(messageId).update(
            "text" to newText,
            "editedAtMillis" to nowMillis()
        )
    }

    /** Unsend: keeps the doc as a tombstone so the thread doesn't reshuffle for the other person. */
    suspend fun deleteForEveryone(matchId: String, messageId: String) {
        messagesRef(matchId).document(messageId).update(
            "deletedForEveryone" to true,
            "text" to "",
            "mediaUrl" to "",
            "reactions" to emptyMap<String, String>()
        )
    }

    suspend fun deleteForMe(matchId: String, messageId: String, selfUid: String) {
        messagesRef(matchId).document(messageId)
            .update("deletedFor" to FieldValue.arrayUnion(selfUid))
    }

    /** Toggles [emoji] for [selfUid]; re-sending the same emoji clears it, as in WhatsApp. */
    suspend fun setReaction(matchId: String, messageId: String, selfUid: String, emoji: String?) {
        val key = "reactions.$selfUid"
        messagesRef(matchId).document(messageId).update(
            key to (emoji ?: FieldValue.delete)
        )
    }

    private fun previewFor(type: MessageType, text: String): String = when (type) {
        MessageType.TEXT -> text
        MessageType.IMAGE -> "📷 Photo"
        MessageType.VOICE -> "🎤 Voice message"
    }

    private fun ChatMessage.previewText(): String =
        if (messageType == MessageType.TEXT) text else previewFor(messageType, text)
}
