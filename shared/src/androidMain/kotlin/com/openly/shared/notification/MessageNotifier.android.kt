package com.openly.shared.notification

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
import com.openly.shared.R

/** Shared with the FCM service in the app module so both post to the same channel. */
const val MESSAGES_CHANNEL_ID = "openly_messages"

private const val CHANNEL_ID = MESSAGES_CHANNEL_ID
private const val CHANNEL_NAME = "Messages"

actual class MessageNotifier(private val context: Context) {

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
    actual fun notifyNewMessage(
        matchId: String,
        senderName: String,
        senderEmoji: String,
        text: String,
        sentAtMillis: Long
    ) {
        if (!canPost()) return
        if ((notifiedUpTo[matchId] ?: 0L) >= sentAtMillis) return
        notifiedUpTo[matchId] = sentAtMillis

        // Resolved at runtime rather than referencing the Activity class directly: the launcher
        // lives in the app module, which depends on this one, so naming it here would invert the
        // module dependency.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return

        val pendingIntent = PendingIntent.getActivity(
            context,
            matchId.hashCode(),
            launchIntent,
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

    actual fun markSeen(matchId: String, upToMillis: Long) {
        val current = notifiedUpTo[matchId] ?: 0L
        if (upToMillis > current) notifiedUpTo[matchId] = upToMillis
        NotificationManagerCompat.from(context).cancel(matchId.hashCode())
    }
}
