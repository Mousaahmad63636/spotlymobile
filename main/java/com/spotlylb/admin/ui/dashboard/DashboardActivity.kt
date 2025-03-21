// main/java/com/spotlylb/admin/ui/dashboard/DashboardActivity.kt
package com.spotlylb.admin.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.spotlylb.admin.R
import com.spotlylb.admin.databinding.ActivityDashboardBinding
import com.spotlylb.admin.ui.auth.LoginActivity
import com.spotlylb.admin.ui.orders.OrdersActivity
import com.spotlylb.admin.utils.SessionManager
import com.spotlylb.admin.utils.ToastUtil
import java.text.DecimalFormat

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var sessionManager: SessionManager
    private val currencyFormatter = DecimalFormat("$#,##0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Dashboard"

        // Setup welcome message
        binding.tvAdminName.text = getString(R.string.welcome_admin, sessionManager.getUserName())

        // Setup refresh action
        binding.swipeRefresh.setOnRefreshListener {
            loadDashboardData()
        }

        // Setup navigation buttons
        binding.btnViewAllOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }

        setupObservers()
        loadDashboardData()
    }

    private fun setupObservers() {
        viewModel.dashboardData.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false

            when (result) {
                is DashboardViewModel.DashboardResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE

                    val data = result.data

                    // Update statistics cards
                    binding.tvTotalOrders.text = data.totalOrders.toString()
                    binding.tvPendingOrders.text = data.pendingOrders.toString()
                    binding.tvCompletedOrders.text = data.deliveredOrders.toString()
                    binding.tvCancelledOrders.text = data.cancelledOrders.toString()
                    binding.tvTotalRevenue.text = currencyFormatter.format(data.totalRevenue)

                    // Setup order status chart
                    setupOrderStatusChart(data)

                    // Setup recent orders list
                    setupRecentOrdersList(data)
                }

                is DashboardViewModel.DashboardResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.GONE
                    ToastUtil.showLong(this, result.message)
                }

                is DashboardViewModel.DashboardResult.Loading -> {
                    if (!binding.swipeRefresh.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.contentLayout.visibility = View.GONE
                }
            }
        }
    }

    private fun setupOrderStatusChart(data: DashboardData) {
        val entries = ArrayList<PieEntry>()

        // Only add entries with non-zero values
        if (data.pendingOrders > 0) entries.add(PieEntry(data.pendingOrders.toFloat(), "Pending"))
        if (data.confirmedOrders > 0) entries.add(PieEntry(data.confirmedOrders.toFloat(), "Confirmed"))
        if (data.shippedOrders > 0) entries.add(PieEntry(data.shippedOrders.toFloat(), "Shipped"))
        if (data.deliveredOrders > 0) entries.add(PieEntry(data.deliveredOrders.toFloat(), "Delivered"))
        if (data.cancelledOrders > 0) entries.add(PieEntry(data.cancelledOrders.toFloat(), "Cancelled"))

        // Configure chart
        val dataSet = PieDataSet(entries, "Order Status")
        dataSet.colors = listOf(
            Color.parseColor("#E9B93A"), // Amber for Pending
            Color.parseColor("#2196F3"), // Blue for Confirmed
            Color.parseColor("#3F51B5"), // Indigo for Shipped
            Color.parseColor("#4CAF50"), // Green for Delivered
            Color.parseColor("#F44336")  // Red for Cancelled
        )

        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(binding.chartOrderStatus))
        pieData.setValueTextSize(12f)
        pieData.setValueTextColor(Color.WHITE)

        binding.chartOrderStatus.apply {
            this.data = pieData
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            transparentCircleRadius = 61f
            holeRadius = 58f
            setDrawCenterText(true)
            centerText = "Orders by Status"
            setCenterTextSize(16f)
            setUsePercentValues(true)
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.textSize = 12f
            legend.setDrawInside(false)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            animateY(1400)
            invalidate() // refresh
        }
    }

    private fun setupRecentOrdersList(data: DashboardData) {
        // Clear existing views
        binding.recentOrdersContainer.removeAllViews()

        if (data.recentOrders.isEmpty()) {
            val textView = android.widget.TextView(this).apply {
                text = "No recent orders"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            binding.recentOrdersContainer.addView(textView)
        } else {
            // Add recent orders (limited to 5)
            data.recentOrders.take(5).forEach { order ->
                val orderView = layoutInflater.inflate(
                    R.layout.item_recent_order,
                    binding.recentOrdersContainer,
                    false
                )

                // Populate order view
                orderView.findViewById<android.widget.TextView>(R.id.tvOrderId).text = "#${order.orderId}"
                orderView.findViewById<android.widget.TextView>(R.id.tvCustomerName).text = order.customerName
                orderView.findViewById<android.widget.TextView>(R.id.tvOrderDate).text = order.getFormattedDate()
                orderView.findViewById<android.widget.TextView>(R.id.tvOrderTotal).text =
                    currencyFormatter.format(order.totalAmount)

                // Set status chip
                val chipStatus = orderView.findViewById<com.google.android.material.chip.Chip>(R.id.chipStatus)
                chipStatus.text = order.status
                chipStatus.setTextColor(Color.WHITE)
                chipStatus.chipBackgroundColor = ContextCompat.getColorStateList(
                    this,
                    when (order.status?.lowercase()) {
                        "pending" -> R.color.amber_500
                        "confirmed" -> R.color.blue_500
                        "shipped" -> R.color.indigo_500
                        "delivered" -> R.color.green_500
                        "cancelled" -> R.color.red_500
                        else -> R.color.gray_500
                    }
                )

                // Set click listener to open order detail
                orderView.setOnClickListener {
                    val intent = Intent(this, OrdersActivity::class.java)
                    intent.putExtra("OPEN_ORDER_ID", order._id)
                    startActivity(intent)
                }

                binding.recentOrdersContainer.addView(orderView)
            }
        }
    }

    private fun loadDashboardData() {
        val token = sessionManager.getAuthToken()
        if (token.isNullOrEmpty()) {
            ToastUtil.showShort(this, "Please log in again")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        viewModel.loadDashboardData(token)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadDashboardData()
                true
            }
            R.id.action_orders -> {
                startActivity(Intent(this, OrdersActivity::class.java))
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