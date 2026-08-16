package com.openly.shared.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Chat timestamp formatting, done with kotlinx-datetime so it behaves identically on both
 * platforms. Deliberately hand-rolled rather than using platform date formatters: those differ in
 * output between Android and iOS, which would make the same thread look different on each.
 */

/** "14:05" — shown under every bubble. */
fun formatClockTime(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    return "${dt.hour.padded()}:${dt.minute.padded()}"
}

/** "Today" / "Yesterday" / "5 Aug 2026" — the sticky separator between days. */
fun formatDaySeparator(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    val today = Clock.System.todayIn(timeZone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "${date.dayOfMonth} ${date.monthShortName()} ${date.year}"
    }
}

/**
 * "online" / "last seen today at 14:05" / "last seen 5 Aug 2026" for the chat header.
 * [onlineWindowMillis] mirrors the radar's staleness rule so "online" means the same thing here.
 */
fun formatLastSeen(
    lastActiveMillis: Long,
    nowMillis: Long,
    onlineWindowMillis: Long = 5 * 60 * 1000L,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    if (lastActiveMillis <= 0L) return ""
    if (nowMillis - lastActiveMillis < onlineWindowMillis) return "online"

    val date = Instant.fromEpochMilliseconds(lastActiveMillis).toLocalDateTime(timeZone).date
    val today = Clock.System.todayIn(timeZone)
    val clock = formatClockTime(lastActiveMillis, timeZone)
    return when (date) {
        today -> "last seen today at $clock"
        today.minusDays(1) -> "last seen yesterday at $clock"
        else -> "last seen ${date.dayOfMonth} ${date.monthShortName()} ${date.year}"
    }
}

/** True when the two instants fall on different calendar days — drives separator insertion. */
fun isDifferentDay(
    firstMillis: Long,
    secondMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): Boolean {
    val a = Instant.fromEpochMilliseconds(firstMillis).toLocalDateTime(timeZone).date
    val b = Instant.fromEpochMilliseconds(secondMillis).toLocalDateTime(timeZone).date
    return a != b
}

/** mm:ss, for voice-note duration. */
fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.padded()}"
}

private fun Int.padded(): String = if (this < 10) "0$this" else toString()

private fun LocalDate.monthShortName(): String = when (monthNumber) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
    7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
}

private fun LocalDate.minusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() - days)
