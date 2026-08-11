package com.openly.app.data.model

enum class InterestStatus { PENDING, ACCEPTED, DECLINED }

/**
 * Mirrors an `interests/{id}` document, id = "${fromUid}_${toUid}" so a sender can't spam
 * the same person with duplicate pending requests.
 */
data class Interest(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val fromName: String = "",
    val fromEmoji: String = "🙂",
    val status: String = InterestStatus.PENDING.name,
    val createdAtMillis: Long = 0L
)
