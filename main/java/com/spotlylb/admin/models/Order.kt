package com.spotlylb.admin.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Parcelize
data class Order(
    val _id: String,
    val orderId: String,
    val customerName: String,
    val customerEmail: String?,
    val phoneNumber: String,
    val address: String,
    val products: List<OrderProduct>?,
    val subtotal: Double,
    val shippingFee: Double,
    val totalAmount: Double,
    val status: String?,
    val specialInstructions: String?,
    val createdAt: String,
    val updatedAt: String,
    val promoCode: String?,
    val promoDiscount: Int?
) : Parcelable {

    fun getFormattedDate(): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

        // Set the input format timezone to UTC
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")

        return try {
            val date = inputFormat.parse(createdAt)

            // Subtract 2 hours from the parsed date
            val calendar = Calendar.getInstance()
            calendar.time = date ?: Date()
            calendar.add(Calendar.HOUR_OF_DAY, -0)

            outputFormat.format(calendar.time)
        } catch (e: Exception) {
            createdAt
        }
    }

    fun getStatusColor(): Int {
        // Safely handle null status
        val statusLower = status?.lowercase() ?: return 0xFF9E9E9E.toInt() // Default gray color if status is null

        return when (statusLower) {
            "pending" -> 0xFFE9B93A.toInt() // Amber
            "confirmed" -> 0xFF2196F3.toInt() // Blue
            "shipped" -> 0xFF3F51B5.toInt() // Indigo
            "delivered" -> 0xFF4CAF50.toInt() // Green
            "cancelled" -> 0xFFF44336.toInt() // Red
            else -> 0xFF9E9E9E.toInt() // Grey
        }
    }
}