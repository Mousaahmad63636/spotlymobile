package com.spotlylb.admin.ui.orders

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.spotlylb.admin.R
import com.spotlylb.admin.databinding.ActivityOrdersBinding
import com.spotlylb.admin.models.Order
import com.spotlylb.admin.ui.auth.LoginActivity
import com.spotlylb.admin.utils.SessionManager
import com.spotlylb.admin.utils.ToastUtil
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.lifecycleScope
import com.spotlylb.admin.api.ApiClient
import kotlinx.coroutines.launch

class OrdersActivity : AppCompatActivity(), OrderFilterDialogFragment.FilterAppliedListener {
    companion object {
        private const val TAG = "OrdersActivity"
    }

    private lateinit var binding: ActivityOrdersBinding
    private val viewModel: OrdersViewModel by viewModels()
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var sessionManager: SessionManager
    private var currentFilters = OrderFilters()

    // Define the activity result launcher at the class level
    private val orderDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedOrder = result.data?.getParcelableExtra<Order>("UPDATED_ORDER")
            val position = result.data?.getIntExtra("ORDER_POSITION", -1) ?: -1

            Log.d(TAG, "Received result from OrderDetailActivity. Updated order: ${updatedOrder?._id}, position: $position")

            if (updatedOrder != null) {
                // Refresh only the specific order that was updated
                viewModel.updateOrderInList(updatedOrder)
                Log.d(TAG, "Updated specific order in list: ${updatedOrder.orderId}, status: ${updatedOrder.status}")
            } else {
                // Fallback to refreshing the entire list
                Log.d(TAG, "No updated order received, refreshing entire list")
                loadOrders()
            }
        } else {
            Log.d(TAG, "OrderDetailActivity returned with result code: ${result.resultCode}")
        }
    }
    private val orderUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.spotlylb.admin.NEW_ORDER" -> {
                    Log.d(TAG, "Received broadcast for new order")

                    // Show a toast notification
                    ToastUtil.showShort(this@OrdersActivity, "New order received!")

                    // Refresh the orders list
                    loadOrders()

                    // Try to get the specific order ID
                    val orderId = intent.getStringExtra("orderId")
                    orderId?.let {
                        Log.d(TAG, "New order ID: $it")
                        // You could open the specific order here if desired
                    }
                }
                "com.spotlylb.admin.ORDER_UPDATED" -> {
                    Log.d(TAG, "Received broadcast for order update")

                    // Refresh the orders list
                    loadOrders()

                    // Try to get the specific order ID
                    val orderId = intent.getStringExtra("orderId")
                    orderId?.let {
                        Log.d(TAG, "Updated order ID: $it")
                    }
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupUI()
        setupRecyclerView()
        observeViewModel()
        loadOrders()
        handleNotificationIntent(intent)

        // Register the broadcast receiver using explicit broadcast actions
        // Define our custom intent filter with our app-specific actions
        val filter = IntentFilter().apply {
            addAction("com.spotlylb.admin.NEW_ORDER")
            addAction("com.spotlylb.admin.ORDER_UPDATED")
        }

        // Register differently based on Android version
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 Tiramisu
            // Use the named constant instead of raw value 4
            registerReceiver(orderUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // For Android 12 and below, no flag is needed
            registerReceiver(orderUpdateReceiver, filter)
        }
    }

    // Add the onDestroy method to unregister the receiver
    override fun onDestroy() {
        // Unregister the broadcast receiver
        unregisterReceiver(orderUpdateReceiver)
        super.onDestroy()
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        // This ensures the list is refreshed when returning from any screen
        Log.d(TAG, "onResume called, refreshing orders")
        loadOrders()
    }

    private fun handleNotificationIntent(intent: Intent) {
        val orderId = intent.getStringExtra("OPEN_ORDER_ID")
        if (orderId != null) {
            Log.d(TAG, "Opening order from notification: $orderId")

            // Show a toast to indicate we're loading the order
            ToastUtil.showShort(this, "Loading order details...")

            // Start a loading indicator
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    val token = sessionManager.getAuthToken() ?: ""
                    if (token.isEmpty()) {
                        binding.progressBar.visibility = View.GONE
                        return@launch
                    }

                    val apiService = ApiClient.getAuthenticatedApiService(token)
                    val response = apiService.getOrderById(orderId)

                    if (response.isSuccessful && response.body() != null) {
                        val order = response.body()!!
                        binding.progressBar.visibility = View.GONE
                        navigateToOrderDetail(order, -1)
                    } else {
                        binding.progressBar.visibility = View.GONE
                        ToastUtil.showShort(this@OrdersActivity, "Failed to load order details")
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Error loading order from notification", e)
                    ToastUtil.showShort(this@OrdersActivity, "Error loading order: ${e.message}")
                }
            }
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Orders Management"

        // Welcome message with admin name
        binding.tvAdminName.text = getString(R.string.welcome_admin, sessionManager.getUserName())

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "SwipeRefresh triggered, refreshing orders")
            loadOrders()
        }

        // Update filter chip click listeners
        binding.chipAll.setOnClickListener {
            Log.d(TAG, "All status filter selected")
            currentFilters = currentFilters.copy(status = null)
            viewModel.applyFilters(currentFilters)
        }
        binding.chipPending.setOnClickListener {
            Log.d(TAG, "Pending status filter selected")
            currentFilters = currentFilters.copy(status = "pending")
            viewModel.applyFilters(currentFilters)
        }
        binding.chipConfirmed.setOnClickListener {
            Log.d(TAG, "Confirmed status filter selected")
            currentFilters = currentFilters.copy(status = "confirmed")
            viewModel.applyFilters(currentFilters)
        }
        binding.chipShipped.setOnClickListener {
            Log.d(TAG, "Shipped status filter selected")
            currentFilters = currentFilters.copy(status = "shipped")
            viewModel.applyFilters(currentFilters)
        }
        binding.chipDelivered.setOnClickListener {
            Log.d(TAG, "Delivered status filter selected")
            currentFilters = currentFilters.copy(status = "delivered")
            viewModel.applyFilters(currentFilters)
        }
        binding.chipCancelled.setOnClickListener {
            Log.d(TAG, "Cancelled status filter selected")
            currentFilters = currentFilters.copy(status = "cancelled")
            viewModel.applyFilters(currentFilters)
        }
    }

    private fun setupRecyclerView() {
        orderAdapter = OrderAdapter { order, position ->
            navigateToOrderDetail(order, position)
        }

        binding.recyclerOrders.apply {
            layoutManager = LinearLayoutManager(this@OrdersActivity)
            adapter = orderAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.orders.observe(this) { result ->
            when (result) {
                is OrdersViewModel.OrdersResult.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE

                    Log.d(TAG, "Received ${result.orders.size} orders from API")

                    if (result.orders.isEmpty()) {
                        binding.textNoOrders.visibility = View.VISIBLE
                    } else {
                        binding.textNoOrders.visibility = View.GONE
                    }
                }
                is OrdersViewModel.OrdersResult.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.textNoOrders.visibility = View.VISIBLE
                    binding.textNoOrders.text = result.message

                    Log.e(TAG, "Error fetching orders: ${result.message}")
                    ToastUtil.showLong(this, result.message)
                }
                is OrdersViewModel.OrdersResult.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.textNoOrders.visibility = View.GONE
                    Log.d(TAG, "Loading orders...")
                }
            }
        }

        viewModel.filteredOrders.observe(this) { filteredOrders ->
            Log.d(TAG, "Filtered orders updated: ${filteredOrders.size} orders match current filters")
            orderAdapter.submitList(filteredOrders)

            if (filteredOrders.isEmpty()) {
                binding.textNoOrders.visibility = View.VISIBLE
                binding.textNoOrders.text = getString(R.string.no_orders_for_filter)
            } else {
                binding.textNoOrders.visibility = View.GONE
            }
        }
    }

    private fun loadOrders() {
        Log.d(TAG, "Loading orders from API")
        val token = sessionManager.getAuthToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Auth token is null or empty")
            ToastUtil.showShort(this, "Please log in again")
            navigateToLogin()
            return
        }
        viewModel.fetchOrders(token)
    }

    private fun navigateToOrderDetail(order: Order, position: Int) {
        Log.d(TAG, "Navigating to order details for order #${order.orderId} at position $position")
        val intent = Intent(this, OrderDetailActivity::class.java).apply {
            putExtra(OrderDetailActivity.EXTRA_ORDER, order)
            putExtra("ORDER_POSITION", position)
        }
        orderDetailLauncher.launch(intent)
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_orders, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_refresh -> {
                Log.d(TAG, "Refresh menu item selected")
                loadOrders()
                true
            }
            R.id.action_logout -> {
                showLogoutConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val filterDialog = OrderFilterDialogFragment.newInstance(currentFilters)
        filterDialog.setFilterAppliedListener(this)
        filterDialog.show(supportFragmentManager, OrderFilterDialogFragment.TAG)
    }

    override fun onFiltersApplied(filters: OrderFilters) {
        Log.d(TAG, "Filters applied: status=${filters.status}, orderId=${filters.orderId}")
        currentFilters = filters
        viewModel.applyFilters(filters)

        // Update chip selection based on status filter
        binding.chipGroup.clearCheck()
        when (filters.status?.lowercase()) {
            null -> binding.chipAll.isChecked = true
            "pending" -> binding.chipPending.isChecked = true
            "confirmed" -> binding.chipConfirmed.isChecked = true
            "shipped" -> binding.chipShipped.isChecked = true
            "delivered" -> binding.chipDelivered.isChecked = true
            "cancelled" -> binding.chipCancelled.isChecked = true
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                Log.d(TAG, "User confirmed logout")
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}