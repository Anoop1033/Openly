package com.openly.shared.data.repository

import com.openly.shared.data.model.Interest
import com.openly.shared.data.model.InterestStatus
import com.openly.shared.data.model.MatchInfo
import com.openly.shared.data.remote.FirestorePaths
import com.openly.shared.platform.nowMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
                createdAtMillis = nowMillis()
            )
        )
    }

    fun observeIncoming(uid: String): Flow<List<Interest>> =
        observeByStatus(field = "toUid", uid = uid, status = InterestStatus.PENDING)

    fun observeOutgoingPending(uid: String): Flow<List<Interest>> =
        observeByStatus(field = "fromUid", uid = uid, status = InterestStatus.PENDING)

    private fun observeByStatus(field: String, uid: String, status: InterestStatus): Flow<List<Interest>> =
        interests
            .where { all(field equalTo uid, "status" equalTo status.name) }
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { runCatching { it.data<Interest>() }.getOrNull() }
                    .sortedByDescending { it.createdAtMillis }
            }

    /** Accepting creates the shared match doc that unlocks chat for both sides. */
    suspend fun accept(interest: Interest): MatchInfo {
        interests.document(interest.id).set(interest.copy(status = InterestStatus.ACCEPTED.name))

        val matchId = FirestorePaths.matchId(interest.fromUid, interest.toUid)
        val match = MatchInfo(
            id = matchId,
            uids = listOf(interest.fromUid, interest.toUid),
            createdAtMillis = nowMillis()
        )
        matches.document(matchId).set(match)
        return match
    }

    suspend fun decline(interest: Interest) {
        interests.document(interest.id).set(interest.copy(status = InterestStatus.DECLINED.name))
    }
}
