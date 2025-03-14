package com.spotlylb.admin.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

            // Check for order-related notifications
            val title = it.title ?: "New Order"
            val body = it.body ?: "You have received a new order!"

            // Try to extract order ID from data payload if present
            val orderId = remoteMessage.data["orderId"]

            sendNotification(title, body, orderId)
        }

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Handle data payload
            val title = remoteMessage.data["title"] ?: "New Order"
            val message = remoteMessage.data["message"] ?: "You have received a new order!"
            val orderId = remoteMessage.data["orderId"]
            val notificationType = remoteMessage.data["type"] ?: "order"

            // Process different notification types
            when (notificationType) {
                "new_order" -> {
                    // Handle new order notification
                    sendNotification(title, message, orderId)

                    // Broadcast to refresh orders if the app is in the foreground
                    val refreshIntent = Intent("com.spotlylb.admin.NEW_ORDER")
                    orderId?.let { refreshIntent.putExtra("orderId", it) }
                    sendBroadcast(refreshIntent)
                }
                "order_status_update" -> {
                    // Handle status update notification
                    sendNotification(title, message, orderId)

                    // Broadcast status update
                    val updateIntent = Intent("com.spotlylb.admin.ORDER_UPDATED")
                    orderId?.let { updateIntent.putExtra("orderId", it) }
                    sendBroadcast(updateIntent)
                }
                else -> {
                    // Default notification handling
                    sendNotification(title, message, orderId)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.order_notification_channel_id),
                "Order Notifications",
                NotificationManager.IMPORTANCE_HIGH // This is important for notifications to alert
            ).apply {
                description = "Notifications for new and updated orders"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC // Corrected reference
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created with ID: ${channel.id}")
        }
    }
    private fun sendRegistrationToServer(token: String) {
        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val authToken = sessionManager.getAuthToken() ?: return@launch
                    val apiService = ApiClient.getAuthenticatedApiService(authToken)

                    // Uncomment this line
                    val response = apiService.updateFcmToken(mapOf("fcmToken" to token))

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

            // If we have an order ID, set it to open that specific order
            if (orderId != null) {
                putExtra("OPEN_ORDER_ID", orderId)
            }
        }

        // Make the PendingIntent unique for different notifications
        val requestCode = if (orderId != null) orderId.hashCode() else System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = getString(R.string.order_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Create a more informative notification with appropriate styling
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_orders)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))

        // Set notification color if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationBuilder.color = getColor(R.color.purple_500)
        }

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
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Use a unique ID for each notification
        val notificationId = if (orderId != null) orderId.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}