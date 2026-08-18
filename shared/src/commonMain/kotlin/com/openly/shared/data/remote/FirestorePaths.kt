package com.openly.shared.data.remote

object FirestorePaths {
    const val USERS = "users"
    const val INTERESTS = "interests"
    const val MATCHES = "matches"
    const val MESSAGES = "messages"

    /** Write-only signal picked up by the purge function; see AccountRepository. */
    const val DELETION_REQUESTS = "deletionRequests"

    /** Deterministic id so a sender can't create duplicate pending requests to the same person. */
    fun interestId(fromUid: String, toUid: String) = "${fromUid}_$toUid"

    /** Deterministic, order-independent id shared by both sides of a match. */
    fun matchId(uidA: String, uidB: String) = listOf(uidA, uidB).sorted().joinToString("_")
}
