package com.openly.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.openly.app.data.model.Interest
import com.openly.app.data.model.InterestStatus
import com.openly.app.data.model.MatchInfo
import com.openly.app.data.remote.FirestorePaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InterestRepository(private val firestore: FirebaseFirestore) {

    private val interests get() = firestore.collection(FirestorePaths.INTERESTS)
    private val matches get() = firestore.collection(FirestorePaths.MATCHES)

    suspend fun sendInterest(fromUid: String, fromName: String, fromEmoji: String, toUid: String) {
        val id = FirestorePaths.interestId(fromUid, toUid)
        interests.document(id).set(
            Interest(
                id = id,
                fromUid = fromUid,
                toUid = toUid,
                fromName = fromName,
                fromEmoji = fromEmoji,
                status = InterestStatus.PENDING.name,
                createdAtMillis = System.currentTimeMillis()
            )
        ).await()
    }

    fun observeIncoming(uid: String): Flow<List<Interest>> =
        observeByStatus(field = "toUid", uid = uid, status = InterestStatus.PENDING)

    fun observeOutgoingPending(uid: String): Flow<List<Interest>> =
        observeByStatus(field = "fromUid", uid = uid, status = InterestStatus.PENDING)

    private fun observeByStatus(field: String, uid: String, status: InterestStatus): Flow<List<Interest>> =
        callbackFlow {
            val registration = interests
                .whereEqualTo(field, uid)
                .whereEqualTo("status", status.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents.orEmpty()
                        .mapNotNull { it.toObject(Interest::class.java) }
                        .sortedByDescending { it.createdAtMillis }
                    trySend(list)
                }
            awaitClose { registration.remove() }
        }

    /** Accepting creates the shared match doc that unlocks chat for both sides. */
    suspend fun accept(interest: Interest): MatchInfo {
        interests.document(interest.id)
            .set(interest.copy(status = InterestStatus.ACCEPTED.name))
            .await()

        val matchId = FirestorePaths.matchId(interest.fromUid, interest.toUid)
        val match = MatchInfo(
            id = matchId,
            uids = listOf(interest.fromUid, interest.toUid),
            createdAtMillis = System.currentTimeMillis()
        )
        matches.document(matchId).set(match).await()
        return match
    }

    suspend fun decline(interest: Interest) {
        interests.document(interest.id)
            .set(interest.copy(status = InterestStatus.DECLINED.name))
            .await()
    }
}
