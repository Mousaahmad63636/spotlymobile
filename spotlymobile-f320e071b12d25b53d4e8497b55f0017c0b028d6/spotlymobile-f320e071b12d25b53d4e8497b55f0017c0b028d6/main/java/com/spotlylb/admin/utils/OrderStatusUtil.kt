package com.spotlylb.admin.utils

object OrderStatusUtil {

    val statusOptions = listOf(
        "Pending",
        "Confirmed",
        "Shipped",
        "Delivered",
        "Cancelled"
    )

    fun getNextStatusOptions(currentStatus: String?): List<String> {
        return when (currentStatus?.lowercase()) {
            "pending" -> listOf("Confirmed", "Cancelled")
            "confirmed" -> listOf("Shipped", "Cancelled")
            "shipped" -> listOf("Delivered", "Cancelled")
            "delivered", "cancelled" -> emptyList() // Terminal states
            null -> listOf("Pending", "Confirmed", "Cancelled") // If status is null
            else -> listOf("Pending", "Confirmed", "Cancelled") // For unknown status
        }
    }
}