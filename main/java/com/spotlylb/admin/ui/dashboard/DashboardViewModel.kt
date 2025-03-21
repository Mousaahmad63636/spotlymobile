// main/java/com/spotlylb/admin/ui/dashboard/DashboardViewModel.kt
package com.spotlylb.admin.ui.dashboard

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

class DashboardViewModel : ViewModel() {
    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val _dashboardData = MutableLiveData<DashboardResult>()
    val dashboardData: LiveData<DashboardResult> = _dashboardData

    fun loadDashboardData(token: String) {
        _dashboardData.value = DashboardResult.Loading

        viewModelScope.launch {
            try {
                val apiService = ApiClient.getAuthenticatedApiService(token)
                val response = apiService.getOrders()

                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!
                    val dashboardData = processDashboardData(orders)
                    _dashboardData.value = DashboardResult.Success(dashboardData)
                } else {
                    _dashboardData.value = DashboardResult.Error(
                        "Failed to load dashboard data (${response.code()})"
                    )
                }
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP Exception: ${e.code()} - ${e.message()}", e)
                _dashboardData.value = DashboardResult.Error("Server error: ${e.message()}")
            } catch (e: IOException) {
                Log.e(TAG, "IO Exception: ${e.message}", e)
                _dashboardData.value = DashboardResult.Error("Network error. Please check your connection")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                _dashboardData.value = DashboardResult.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    private fun processDashboardData(orders: List<Order>): DashboardData {
        // Count orders by status
        val pendingOrders = orders.count { it.status?.lowercase() == "pending" }
        val confirmedOrders = orders.count { it.status?.lowercase() == "confirmed" }
        val shippedOrders = orders.count { it.status?.lowercase() == "shipped" }
        val deliveredOrders = orders.count { it.status?.lowercase() == "delivered" }
        val cancelledOrders = orders.count { it.status?.lowercase() == "cancelled" }

        // Calculate total revenue
        val totalRevenue = orders
            .filter { it.status?.lowercase() == "delivered" }
            .sumOf { it.totalAmount }

        // Get recent orders sorted by date
        val recentOrders = orders
            .sortedByDescending { it.createdAt }
            .take(5)

        return DashboardData(
            totalOrders = orders.size,
            pendingOrders = pendingOrders,
            confirmedOrders = confirmedOrders,
            shippedOrders = shippedOrders,
            deliveredOrders = deliveredOrders,
            cancelledOrders = cancelledOrders,
            totalRevenue = totalRevenue,
            recentOrders = recentOrders
        )
    }

    sealed class DashboardResult {
        data class Success(val data: DashboardData) : DashboardResult()
        data class Error(val message: String) : DashboardResult()
        object Loading : DashboardResult()
    }
}