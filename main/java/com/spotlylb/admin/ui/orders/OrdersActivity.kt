package com.spotlylb.admin.ui.orders

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private val viewModel: OrdersViewModel by viewModels()
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var sessionManager: SessionManager

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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val orderId = intent.getStringExtra("OPEN_ORDER_ID")
        if (orderId != null) {
            // Load the specific order and open its details
            Log.d("OrdersActivity", "Opening order from notification: $orderId")
            // Implement logic to open the specific order
            // This could be navigating to OrderDetailActivity with the order ID
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Orders Management"

        // Welcome message with admin name
        binding.tvAdminName.text = getString(R.string.welcome_admin, sessionManager.getUserName())

        binding.swipeRefresh.setOnRefreshListener {
            loadOrders()
        }

        // Filter buttons
        binding.chipAll.setOnClickListener { viewModel.filterOrders(null) }
        binding.chipPending.setOnClickListener { viewModel.filterOrders("pending") }
        binding.chipConfirmed.setOnClickListener { viewModel.filterOrders("confirmed") }
        binding.chipShipped.setOnClickListener { viewModel.filterOrders("shipped") }
        binding.chipDelivered.setOnClickListener { viewModel.filterOrders("delivered") }
        binding.chipCancelled.setOnClickListener { viewModel.filterOrders("cancelled") }
    }

    private fun setupRecyclerView() {
        orderAdapter = OrderAdapter { order ->
            navigateToOrderDetail(order)
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

                    if (result.orders.isEmpty()) {
                        binding.textNoOrders.visibility = View.VISIBLE
                    } else {
                        binding.textNoOrders.visibility = View.GONE
                        orderAdapter.submitList(result.orders)
                    }
                }
                is OrdersViewModel.OrdersResult.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressBar.visibility = View.GONE
                    binding.textNoOrders.visibility = View.VISIBLE
                    binding.textNoOrders.text = result.message
                    ToastUtil.showLong(this, result.message)
                }
                is OrdersViewModel.OrdersResult.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.textNoOrders.visibility = View.GONE
                }
            }
        }

        viewModel.filteredOrders.observe(this) { filteredOrders ->
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
        viewModel.fetchOrders(sessionManager.getAuthToken() ?: "")
    }

    private fun navigateToOrderDetail(order: Order) {
        val intent = Intent(this, OrderDetailActivity::class.java).apply {
            putExtra(OrderDetailActivity.EXTRA_ORDER, order)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_orders, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
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

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}