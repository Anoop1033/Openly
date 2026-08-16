package com.openly.shared.notification

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * UserNotifications counterpart of the Android implementation.
 *
 * NOTE: never compiled or run — iOS builds require macOS + Xcode. Verify on a Mac.
 * Delivering these while the app is foregrounded also needs a UNUserNotificationCenterDelegate
 * on the Swift side (see iosApp/README notes), otherwise iOS suppresses them by design.
 */
actual class MessageNotifier {

    private val notifiedUpTo = mutableMapOf<String, Long>()
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    fun requestPermission() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    actual fun notifyNewMessage(
        matchId: String,
        senderName: String,
        senderEmoji: String,
        text: String,
        sentAtMillis: Long
    ) {
        // Same de-dupe rule as Android: Firestore re-emits the whole match list on any change.
        if ((notifiedUpTo[matchId] ?: 0L) >= sentAtMillis) return
        notifiedUpTo[matchId] = sentAtMillis

        val content = UNMutableNotificationContent().apply {
            setTitle("$senderEmoji $senderName")
            setBody(text)
            setSound(UNNotificationSound.defaultSound())
        }

        // No trigger => deliver immediately.
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = matchId,
            content = content,
            trigger = null
        )
        center.addNotificationRequest(request) { _ -> }
    }

    actual fun markSeen(matchId: String, upToMillis: Long) {
        val current = notifiedUpTo[matchId] ?: 0L
        if (upToMillis > current) notifiedUpTo[matchId] = upToMillis
        center.removeDeliveredNotificationsWithIdentifiers(listOf(matchId))
        center.removePendingNotificationRequestsWithIdentifiers(listOf(matchId))
    }
}
