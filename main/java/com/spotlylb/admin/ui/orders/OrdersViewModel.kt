package com.spotlylb.admin.ui.orders

import android.util.Log
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
    companion object {
        private const val TAG = "OrdersViewModel"
    }

    private val _orders = MutableLiveData<OrdersResult>()
    val orders: LiveData<OrdersResult> = _orders

    private val _filteredOrders = MutableLiveData<List<Order>>()
    val filteredOrders: LiveData<List<Order>> = _filteredOrders

    private var allOrders: List<Order> = listOf()
    private var currentFilters = OrderFilters()

    fun fetchOrders(token: String) {
        _orders.value = OrdersResult.Loading
        Log.d(TAG, "Fetching orders...")

        viewModelScope.launch {
            try {
                val apiService = ApiClient.getAuthenticatedApiService(token)
                val response = apiService.getOrders()

                if (response.isSuccessful && response.body() != null) {
                    allOrders = response.body()!!
                    Log.d(TAG, "Successfully fetched ${allOrders.size} orders")
                    _orders.value = OrdersResult.Success(allOrders)

                    // Apply current filters to the new data
                    applyFilters(currentFilters)
                } else {
                    Log.e(TAG, "Error response: ${response.code()} - ${response.message()}")
                    _orders.value = OrdersResult.Error("Failed to load orders (${response.code()})")
                }
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP Exception: ${e.code()} - ${e.message()}", e)
                _orders.value = OrdersResult.Error("Server error: ${e.message()}")
            } catch (e: IOException) {
                Log.e(TAG, "IO Exception: ${e.message}", e)
                _orders.value = OrdersResult.Error("Network error. Please check your connection")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                _orders.value = OrdersResult.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    fun updateOrderInList(updatedOrder: Order) {
        Log.d(TAG, "Updating order in list: ${updatedOrder._id}, status: ${updatedOrder.status}")

        val currentList = allOrders.toMutableList()
        val index = currentList.indexOfFirst { it._id == updatedOrder._id }

        if (index != -1) {
            currentList[index] = updatedOrder
            allOrders = currentList
            _orders.value = OrdersResult.Success(allOrders)
            Log.d(TAG, "Order updated successfully at index $index")

            // Also update filtered orders with current filters
            applyFilters(currentFilters)
        } else {
            Log.w(TAG, "Order not found in list: ${updatedOrder._id}")
        }
    }

    fun applyFilters(filters: OrderFilters) {
        currentFilters = filters
        Log.d(TAG, "Applying filters: status=${filters.status}, orderId=${filters.orderId}")

        var filtered = allOrders

        // Filter by orderId
        filters.orderId?.let { orderId ->
            if (orderId.isNotEmpty()) {
                filtered = filtered.filter { it.orderId.contains(orderId, ignoreCase = true) }
                Log.d(TAG, "Filtered by orderId: ${filtered.size} orders remain")
            }
        }

        // Filter by status
        filters.status?.let { status ->
            filtered = filtered.filter { it.status.equals(status, ignoreCase = true) }
            Log.d(TAG, "Filtered by status '$status': ${filtered.size} orders remain")
        }

        // Filter by date range
        filters.dateFrom?.let { from ->
            filtered = filtered.filter {
                try {
                    val orderDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)
                    orderDate != null && orderDate >= from
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing date from: ${e.message}")
                    false
                }
            }
            Log.d(TAG, "Filtered by dateFrom: ${filtered.size} orders remain")
        }

        filters.dateTo?.let { to ->
            filtered = filtered.filter {
                try {
                    val orderDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)
                    orderDate != null && orderDate <= to
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing date to: ${e.message}")
                    false
                }
            }
            Log.d(TAG, "Filtered by dateTo: ${filtered.size} orders remain")
        }

        // Apply sorting
        filtered = when (filters.sortBy) {
            SortOption.NEWEST -> filtered.sortedByDescending {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)?.time ?: 0
                } catch (e: Exception) {
                    Log.e(TAG, "Error sorting by date: ${e.message}")
                    0
                }
            }
            SortOption.OLDEST -> filtered.sortedBy {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        .parse(it.createdAt)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Log.e(TAG, "Error sorting by date: ${e.message}")
                    Long.MAX_VALUE
                }
            }
        }

        Log.d(TAG, "Final filtered result: ${filtered.size} orders")
        _filteredOrders.value = filtered
    }

    // For backward compatibility with existing code
    fun filterOrders(status: String?) {
        Log.d(TAG, "Legacy filterOrders called with status: $status")
        applyFilters(currentFilters.copy(status = status))
    }

    sealed class OrdersResult {
        data class Success(val orders: List<Order>) : OrdersResult()
        data class Error(val message: String) : OrdersResult()
        object Loading : OrdersResult()
    }
}