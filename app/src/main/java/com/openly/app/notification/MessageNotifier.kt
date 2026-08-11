package com.openly.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openly.app.MainActivity
import com.openly.app.R

/**
 * Posts a local notification when a message lands in a conversation the user isn't looking at.
 *
 * This fires while the app process is alive (foreground on another screen, or recently backgrounded)
 * because it is driven by the app's own Firestore listeners. Waking a fully-killed app would need
 * FCM plus a Cloud Function trigger — see "What's intentionally not built yet" in the README.
 */
class MessageNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "openly_messages"
        private const val CHANNEL_NAME = "Messages"
    }

    /** matchId -> timestamp of the newest message we've already notified about. */
    private val notifiedUpTo = mutableMapOf<String, Long>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New messages from people you've matched with" }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows a notification for [matchId] unless we've already notified about this exact message.
     * De-duping on timestamp matters because Firestore re-emits the whole match list on any change.
     */
    fun notifyNewMessage(matchId: String, senderName: String, senderEmoji: String, text: String, sentAtMillis: Long) {
        if (!canPost()) return
        if ((notifiedUpTo[matchId] ?: 0L) >= sentAtMillis) return
        notifiedUpTo[matchId] = sentAtMillis

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$senderEmoji $senderName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(matchId.hashCode(), notification)
    }

    /** Called when a thread is opened so re-entering it doesn't re-notify for already-seen messages. */
    fun markSeen(matchId: String, upToMillis: Long) {
        val current = notifiedUpTo[matchId] ?: 0L
        if (upToMillis > current) notifiedUpTo[matchId] = upToMillis
        NotificationManagerCompat.from(context).cancel(matchId.hashCode())
    }
}
