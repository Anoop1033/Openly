package com.openly.shared.data.repository

import com.openly.shared.data.local.OpenlyPrefs
import com.openly.shared.data.model.DeletionRequest
import com.openly.shared.data.remote.FirestorePaths
import com.openly.shared.platform.nowMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * Account deletion, which Google Play requires any app with accounts to offer in-app.
 *
 * The work is split between the client and [functions/index.js] on purpose, and the split is a
 * security boundary rather than a convenience:
 *
 * - **Here**: the documents that belong to this user alone — their profile, the interest records
 *   they are a party to, and the anonymous auth account itself. Doing these client-side means the
 *   user stops appearing on anyone's radar the moment they tap confirm, even if Cloud Functions
 *   were never deployed.
 * - **In the function**: matches, their messages, and attachment files. Those are *shared*
 *   documents. Allowing a client to delete them would mean the security rules had to permit either
 *   participant to delete a thread at will, which hands anyone the ability to wipe someone else's
 *   history on a whim. The admin SDK bypasses rules, so nothing has to be loosened.
 */
class AccountRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val prefs: OpenlyPrefs
) {

    /**
     * Ordering here is not stylistic, for two separate reasons.
     *
     * Every Firestore write below is authorised by the credential that
     * [AuthRepository.deleteCurrentUser] destroys, so the auth account has to go after them —
     * deleting it first would strand the profile document permanently, since no one can ever sign
     * in as that uid again to clean it up.
     *
     * And [prefs] is cleared *before* that same call rather than after, because deleting the user
     * bumps [AuthRepository.sessionGeneration], which tears the signed-in UI down. The caller's
     * scope dies with it, so anything suspending placed after that line may simply never run.
     */
    suspend fun deleteAccount(uid: String) {
        firestore.collection(FirestorePaths.DELETION_REQUESTS)
            .document(uid)
            .set(DeletionRequest(uid = uid, requestedAtMillis = nowMillis()))

        deleteOwnInterests(uid)
        firestore.collection(FirestorePaths.USERS).document(uid).delete()
        prefs.clear()

        authRepository.deleteCurrentUser()
    }

    /**
     * Two queries rather than one: Firestore cannot OR across two different fields, and an interest
     * names the user in either [Interest.fromUid] or [Interest.toUid] depending on who reached out.
     */
    private suspend fun deleteOwnInterests(uid: String) {
        val interests = firestore.collection(FirestorePaths.INTERESTS)
        listOf("fromUid", "toUid").forEach { field ->
            interests.where { field equalTo uid }.get().documents.forEach { document ->
                interests.document(document.id).delete()
            }
        }
    }
}
