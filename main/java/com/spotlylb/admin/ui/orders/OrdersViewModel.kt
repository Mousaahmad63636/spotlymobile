package com.spotlylb.admin.ui.orders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotlylb.admin.api.ApiClient
import com.spotlylb.admin.models.Order
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrdersViewModel : ViewModel() {

    private val _orders = MutableLiveData<OrdersResult>()
    val orders: LiveData<OrdersResult> = _orders

    private val _filteredOrders = MutableLiveData<List<Order>>()
    val filteredOrders: LiveData<List<Order>> = _filteredOrders

    private var allOrders: List<Order> = listOf()
    private var currentFilters = OrderFilters()

    fun fetchOrders(token: String) {
        _orders.value = OrdersResult.Loading

        viewModelScope.launch {
            try {
                val apiService = ApiClient.getAuthenticatedApiService(token)
                val response = apiService.getOrders()

                if (response.isSuccessful && response.body() != null) {
                    allOrders = response.body()!!
                    _orders.value = OrdersResult.Success(allOrders)

                    // Apply current filters to the new data
                    applyFilters(currentFilters)
                } else {
                    _orders.value = OrdersResult.Error("Failed to load orders")
                }
            } catch (e: HttpException) {
                _orders.value = OrdersResult.Error("Server error: ${e.message()}")
            } catch (e: IOException) {
                _orders.value = OrdersResult.Error("Network error. Please check your connection")
            } catch (e: Exception) {
                _orders.value = OrdersResult.Error("An unexpected error occurred")
            }
        }
    }

    fun applyFilters(filters: OrderFilters) {
        currentFilters = filters
        var filtered = allOrders

        // Filter by orderId
        currentFilters.orderId?.let { orderId ->
            if (orderId.isNotEmpty()) {
                filtered = filtered.filter { it.orderId.contains(orderId, ignoreCase = true) }
            }
        }

        // Filter by status
        currentFilters.status?.let { status ->
            filtered = filtered.filter { it.status.equals(status, ignoreCase = true) }
        }

        // Filter by date range
        currentFilters.dateFrom?.let { from ->
            filtered = filtered.filter {
                try {
                    val orderDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)
                    orderDate != null && orderDate >= from
                } catch (e: Exception) {
                    false
                }
            }
        }

        currentFilters.dateTo?.let { to ->
            filtered = filtered.filter {
                try {
                    val orderDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)
                    orderDate != null && orderDate <= to
                } catch (e: Exception) {
                    false
                }
            }
        }

        // Apply sorting
        filtered = when (currentFilters.sortBy) {
            SortOption.NEWEST -> filtered.sortedByDescending {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)?.time ?: 0
                } catch (e: Exception) {
                    0
                }
            }
            SortOption.OLDEST -> filtered.sortedBy {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }
        }

        _filteredOrders.value = filtered
    }

    // For backward compatibility with existing code
    fun filterOrders(status: String?) {
        applyFilters(currentFilters.copy(status = status))
    }

    sealed class OrdersResult {
        data class Success(val orders: List<Order>) : OrdersResult()
        data class Error(val message: String) : OrdersResult()
        object Loading : OrdersResult()
    }
}