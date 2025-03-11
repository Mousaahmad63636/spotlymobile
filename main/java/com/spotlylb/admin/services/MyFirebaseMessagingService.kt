package com.spotlylb.admin.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spotlylb.admin.R
import com.spotlylb.admin.api.ApiClient
import com.spotlylb.admin.ui.orders.OrdersActivity
import com.spotlylb.admin.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCMService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "New Order", it.body ?: "You have received a new order!")
        }

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Handle data payload
            val title = remoteMessage.data["title"] ?: "New Order"
            val message = remoteMessage.data["message"] ?: "You have received a new order!"
            val orderId = remoteMessage.data["orderId"]

            // Send notification with order details
            sendNotification(title, message, orderId)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val authToken = sessionManager.getAuthToken() ?: return@launch
                    val apiService = ApiClient.getAuthenticatedApiService(authToken)
                    // Call your API endpoint to update the FCM token
                    // This endpoint needs to be implemented
                    // apiService.updateFcmToken(mapOf("fcmToken" to token))
                    Log.d(TAG, "FCM token updated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update FCM token", e)
                }
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, orderId: String? = null) {
        val intent = Intent(this, OrdersActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass order ID if available to open specific order
            orderId?.let { putExtra("OPEN_ORDER_ID", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = getString(R.string.order_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_orders)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Order Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new and updated orders"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Use a unique ID for each notification
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}