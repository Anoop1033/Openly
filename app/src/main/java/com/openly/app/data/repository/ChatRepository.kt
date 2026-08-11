package com.openly.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.openly.app.data.model.ChatMessage
import com.openly.app.data.model.MatchInfo
import com.openly.app.data.remote.FirestorePaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "ChatRepository"

class ChatRepository(private val firestore: FirebaseFirestore) {

    private val matches get() = firestore.collection(FirestorePaths.MATCHES)

    suspend fun getMatch(matchId: String): MatchInfo? =
        matches.document(matchId).get().await().toObject(MatchInfo::class.java)

    fun observeMatches(uid: String): Flow<List<MatchInfo>> = callbackFlow {
        val registration = matches
            .whereArrayContains("uids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(MatchInfo::class.java) }
                    .sortedByDescending { it.createdAtMillis }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = matches.document(matchId)
            .collection(FirestorePaths.MESSAGES)
            .orderBy("sentAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toObject(ChatMessage::class.java) })
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(matchId: String, senderUid: String, text: String) {
        val messagesRef = matches.document(matchId).collection(FirestorePaths.MESSAGES)
        val id = messagesRef.document().id
        val sentAt = System.currentTimeMillis()
        messagesRef.document(id).set(
            ChatMessage(
                id = id,
                senderUid = senderUid,
                text = text,
                sentAtMillis = sentAt
            )
        ).await()

        // Mirror onto the parent match so the chat list and unread badge stay in sync without
        // opening a messages listener per conversation. This is only a denormalised cache — the
        // message itself is already committed above, so a failure here must never lose it or take
        // the app down with it (a stale preview is far better than a crash).
        runCatching {
            matches.document(matchId).set(
                mapOf(
                    "lastMessageText" to text,
                    "lastMessageSenderUid" to senderUid,
                    "lastMessageAtMillis" to sentAt
                ),
                SetOptions.merge()
            ).await()
        }.onFailure { Log.w(TAG, "Could not update last-message preview for $matchId", it) }
    }
}
