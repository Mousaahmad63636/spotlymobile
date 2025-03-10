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

class OrdersViewModel : ViewModel() {

    private val _orders = MutableLiveData<OrdersResult>()
    val orders: LiveData<OrdersResult> = _orders

    private val _filteredOrders = MutableLiveData<List<Order>>()
    val filteredOrders: LiveData<List<Order>> = _filteredOrders

    private var allOrders: List<Order> = listOf()

    fun fetchOrders(token: String) {
        _orders.value = OrdersResult.Loading

        viewModelScope.launch {
            try {
                val apiService = ApiClient.getAuthenticatedApiService(token)
                val response = apiService.getOrders()

                if (response.isSuccessful && response.body() != null) {
                    allOrders = response.body()!!
                    _orders.value = OrdersResult.Success(allOrders)
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

    fun filterOrders(status: String?) {
        if (status == null) {
            _filteredOrders.value = allOrders
            return
        }

        _filteredOrders.value = allOrders.filter {
            it.status.equals(status, ignoreCase = true)
        }
    }

    sealed class OrdersResult {
        data class Success(val orders: List<Order>) : OrdersResult()
        data class Error(val message: String) : OrdersResult()
        object Loading : OrdersResult()
    }
}