package com.openly.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.openly.shared.notification.MESSAGES_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Delivers new-message pushes while the app is backgrounded or killed — the case the in-process
 * [com.openly.shared.notification.MessageNotifier] can't cover, since that only runs while the
 * Firestore listener is alive.
 *
 * The payload is sent as *data-only* by the Cloud Function (see functions/index.js) precisely so
 * this handler runs for every message rather than letting the system auto-post a notification
 * tray entry that the app can't customise or de-duplicate.
 */
class OpenlyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // The uid is only known once anonymous sign-in has completed, so the token is parked in
        // prefs and flushed by AppRoot on next launch if sign-in hasn't happened yet.
        val app = application as? OpenlyApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            app.registerPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val data = message.data
        val matchId = data["matchId"] ?: return
        val title = data["title"].orEmpty().ifBlank { "New message" }
        val body = data["body"].orEmpty()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            matchId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The channel normally gets created by MessageNotifier, but a push can arrive before the
        // app has ever been opened in this process — create it here too, it's idempotent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                android.app.NotificationChannel(
                    MESSAGES_CHANNEL_ID,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL_ID)
            .setSmallIcon(com.openly.shared.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Same id as the in-process notifier so a foreground and a pushed notification for the
        // same thread collapse into one entry instead of stacking.
        NotificationManagerCompat.from(this).notify(matchId.hashCode(), notification)
    }
}
