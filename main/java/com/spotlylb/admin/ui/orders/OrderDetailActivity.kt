package com.spotlylb.admin.ui.orders

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.spotlylb.admin.R
import com.spotlylb.admin.api.ApiClient
import com.spotlylb.admin.databinding.ActivityOrderDetailBinding
import com.spotlylb.admin.models.Order
import com.spotlylb.admin.models.StatusUpdateRequest
import com.spotlylb.admin.utils.OrderStatusUtil
import com.spotlylb.admin.utils.SessionManager
import com.spotlylb.admin.utils.ToastUtil
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class OrderDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER = "extra_order"
    }

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var order: Order
    private val decimalFormat = DecimalFormat("$#,##0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Get order from intent
        order = intent.getParcelableExtra(EXTRA_ORDER) ?: run {
            ToastUtil.showShort(this, "Order not found")
            finish()
            return
        }

        setupToolbar()
        displayOrderDetails()
        setupActionButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Order #${order.orderId}"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun displayOrderDetails() {
        // Order header info
        binding.tvOrderStatus.text = order.status
        binding.tvOrderStatus.setTextColor(Color.WHITE)
        binding.tvOrderStatus.setBackgroundColor(order.getStatusColor())

        binding.tvOrderDate.text = order.getFormattedDate()
        binding.tvOrderTotal.text = decimalFormat.format(order.totalAmount)

        // Customer info
        binding.tvCustomerName.text = order.customerName
        binding.tvCustomerPhone.text = order.phoneNumber
        binding.tvCustomerEmail.text = order.customerEmail ?: "N/A"
        binding.tvCustomerAddress.text = order.address

        // Special instructions
        if (order.specialInstructions.isNullOrEmpty()) {
            binding.cardSpecialInstructions.visibility = View.GONE
        } else {
            binding.cardSpecialInstructions.visibility = View.VISIBLE
            binding.tvSpecialInstructions.text = order.specialInstructions
        }

        // Order summary
        binding.tvSubtotal.text = decimalFormat.format(order.subtotal)
        binding.tvShippingFee.text = decimalFormat.format(order.shippingFee)

        // Replace this code in displayOrderDetails()
// Promo discount
        val promoDiscount = order.promoDiscount // Create a local copy
        if (promoDiscount != null && promoDiscount > 0) {
            binding.layoutDiscount.visibility = View.VISIBLE
            val discountAmount = (order.subtotal * promoDiscount) / 100
            binding.tvDiscount.text = "-${decimalFormat.format(discountAmount)}"
            binding.tvPromoCode.text = order.promoCode ?: "Discount"
            binding.tvPromoCode.visibility = View.VISIBLE
        } else {
            binding.layoutDiscount.visibility = View.GONE
            binding.tvPromoCode.visibility = View.GONE
        }

        binding.tvTotal.text = decimalFormat.format(order.totalAmount)

        // Products
        // Products
        binding.layoutProducts.removeAllViews()
        val productsList = order.products // Create a local immutable copy
        if (productsList != null) {  // Now we can safely check the local copy
            productsList.forEach { orderProduct ->
                // Skip if product is null
                if (orderProduct.product == null) return@forEach

                val product = orderProduct.product
                val productView = layoutInflater.inflate(
                    R.layout.item_order_product,
                    binding.layoutProducts,
                    false
                )

                // Set product image
                val imageView =
                    productView.findViewById<com.google.android.material.imageview.ShapeableImageView>(
                        R.id.ivProduct
                    )
                Glide.with(this)
                    .load(product.getFullImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(imageView)

                // Set product details
                productView.findViewById<android.widget.TextView>(R.id.tvProductName).text =
                    product.name
                productView.findViewById<android.widget.TextView>(R.id.tvProductPrice).text =
                    "${decimalFormat.format(product.price)} × ${orderProduct.quantity}"
                productView.findViewById<android.widget.TextView>(R.id.tvProductTotal).text =
                    decimalFormat.format(product.price * orderProduct.quantity)

                binding.layoutProducts.addView(productView)
            }
        } else {
            // Add a message or handle empty product list
            val textView = android.widget.TextView(this).apply {
                text = "No product details available"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            binding.layoutProducts.addView(textView)
        }
    }

    private fun updateActionsBasedOnStatus() {
        val nextStatusOptions = OrderStatusUtil.getNextStatusOptions(order.status)

        if (nextStatusOptions.isEmpty()) {
            binding.btnUpdateStatus.visibility = View.GONE
        } else {
            binding.btnUpdateStatus.visibility = View.VISIBLE
        }
    }

    private fun setupActionButtons() {
        // Call customer button
        binding.btnCall.setOnClickListener {
            val phoneNumber = order.phoneNumber
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        }

        // Update status button
        binding.btnUpdateStatus.setOnClickListener {
            showStatusUpdateDialog()
        }

        // WhatsApp button
        binding.btnWhatsapp.setOnClickListener {
            sendWhatsAppMessage()
        }
    }

    private fun showStatusUpdateDialog() {
        // Use the Elvis operator (?:) to provide a default value if status is null
        val safeStatus = order.status ?: "Unknown"
        val nextStatusOptions = OrderStatusUtil.getNextStatusOptions(safeStatus)

        if (nextStatusOptions.isEmpty()) {
            ToastUtil.showShort(this, "Cannot update status from ${order.status ?: "Unknown"}")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Update Order Status")
            .setItems(nextStatusOptions.toTypedArray()) { _, which ->
                val newStatus = nextStatusOptions[which]
                updateOrderStatus(newStatus)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateOrderStatus(newStatus: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val token = sessionManager.getAuthToken() ?: ""
                if (token.isEmpty()) {
                    ToastUtil.showShort(this@OrderDetailActivity, "Authentication token missing")
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val apiService = ApiClient.getAuthenticatedApiService(token)
                Log.d("OrderDetailActivity", "Updating order ${order._id} to status: $newStatus")

                val response = apiService.updateOrderStatus(
                    order._id,
                    StatusUpdateRequest(newStatus)
                )

                if (response.isSuccessful && response.body() != null) {
                    order = response.body()!!
                    // Remove runOnUiThread - already on main thread with lifecycleScope
                    ToastUtil.showShort(this@OrderDetailActivity, "Status updated to $newStatus")
                    displayOrderDetails() // Refresh the UI
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("OrderDetailActivity", "Failed response: $errorBody")
                    ToastUtil.showShort(this@OrderDetailActivity, "Failed to update status: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OrderDetailActivity", "Error updating status", e)
                ToastUtil.showShort(this@OrderDetailActivity, "Error: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun sendWhatsAppMessage() {
        try {
            val phoneNumber = order.phoneNumber.let {
                if (it.startsWith("+")) it else "+$it"
            }

            // Create a formatted message
            val message = buildWhatsAppMessage()

            // Open WhatsApp with pre-filled message
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}")
            }

            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.showShort(this, "Error opening WhatsApp: ${e.message}")
        }
    }

    private fun buildWhatsAppMessage(): String {
        val productsList = order.products // Create a local immutable copy
        val orderItems = if (productsList != null) {
            productsList
                .filter { it.product != null } // Filter out null products
                .joinToString("\n") { orderProduct ->
                    "${orderProduct.product?.name ?: "Unknown product"} × ${orderProduct.quantity}"
                }
        } else {
            "Order details not available"
        }

        return """
        *Order Status Update*
        
        Hello ${order.customerName},
        
        This is an update for your order #${order.orderId}.
        Current status: *${order.status}*
        
        *Order Details:*
        $orderItems
        
        Total: ${decimalFormat.format(order.totalAmount)}
        
        If you have any questions, please let us know.
        
        Thank you!
    """.trimIndent()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}