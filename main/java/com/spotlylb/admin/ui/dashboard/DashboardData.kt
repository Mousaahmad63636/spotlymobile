package com.spotlylb.admin.ui.dashboard

import com.spotlylb.admin.models.Order

data class DashboardData(
    val totalOrders: Int,
    val pendingOrders: Int,
    val confirmedOrders: Int,
    val shippedOrders: Int,
    val deliveredOrders: Int,
    val cancelledOrders: Int,
    val totalRevenue: Double,
    val recentOrders: List<Order>
)