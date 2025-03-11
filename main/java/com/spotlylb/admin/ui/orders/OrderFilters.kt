package com.spotlylb.admin.ui.orders

import java.util.Date

data class OrderFilters(
    val orderId: String? = null,
    val status: String? = null,
    val dateFrom: Date? = null,
    val dateTo: Date? = null,
    val sortBy: SortOption = SortOption.NEWEST
)

enum class SortOption {
    NEWEST, OLDEST
}