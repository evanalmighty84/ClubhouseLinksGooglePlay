package com.clubhouselinks.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Android push notification receiving groundwork.
 * Requires Firebase app registration, google-services.json, and a signed-in
 * server-side token registration endpoint before production use.
 */
class ClubhouseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Deliberately not POSTing an unverified token to the iOS APNs endpoint.
        getSharedPreferences("clubhouse_push", MODE_PRIVATE).edit()
            .putString("pending_fcm_token", token).apply()
    }
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "Clubhouse Links"
        val body = message.notification?.body ?: message.data["body"] ?: "New neighborhood update"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("clubhouse_updates", "Neighborhood Updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(this,"clubhouse_updates")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setAutoCancel(true).setContentIntent(pending).build())
    }
}
