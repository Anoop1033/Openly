package com.openly.shared.notification

/**
 * Posts a local notification when a message lands in a conversation the user isn't looking at.
 *
 * Fires while the app process is alive (foreground on another screen, or recently backgrounded)
 * because it is driven by the app's own Firestore listeners. Waking a fully-killed app would need
 * FCM/APNs plus a server trigger — see "What's intentionally not built yet" in the README.
 */
expect class MessageNotifier {
    fun notifyNewMessage(
        matchId: String,
        senderName: String,
        senderEmoji: String,
        text: String,
        sentAtMillis: Long
    )

    /** Called when a thread is opened so re-entering it doesn't re-notify for already-seen messages. */
    fun markSeen(matchId: String, upToMillis: Long)
}
