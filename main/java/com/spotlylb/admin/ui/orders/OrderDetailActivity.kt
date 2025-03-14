package com.spotlylb.admin.ui.orders

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
        private const val TAG = "OrderDetailActivity"
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

        Log.d(TAG, "Displaying order details for order #${order.orderId}")

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

        // Promo discount
        val promoDiscount = order.promoDiscount
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

        // Enhanced Products Section
        binding.layoutProducts.removeAllViews()
        val productsList = order.products

        // Log product information for debugging
        Log.d(TAG, "Order ID: ${order.orderId}, Products list: ${productsList?.size ?: 0} items")

        if (productsList != null && productsList.isNotEmpty()) {
            Log.d(TAG, "First product structure: ${productsList[0]}")
        }

        if (productsList == null || productsList.isEmpty()) {
            // Handle null or empty products list
            val textView = android.widget.TextView(this).apply {
                text = "No products in this order"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            binding.layoutProducts.addView(textView)
            Log.d(TAG, "No products found for order ${order.orderId}")
        } else {
            var displayedProductCount = 0

            // Loop through all products
            productsList.forEachIndexed { index, orderProduct ->
                Log.d(TAG, "Processing product index $index, null product: ${orderProduct.product == null}")

                try {
                    if (orderProduct.product == null) {
                        // Instead of skipping, show a placeholder for null products
                        val productView = layoutInflater.inflate(
                            R.layout.item_order_product,
                            binding.layoutProducts,
                            false
                        )

                        val imageView = productView.findViewById<com.google.android.material.imageview.ShapeableImageView>(
                            R.id.ivProduct
                        )

                        // Use placeholder for image
                        Glide.with(this)
                            .load(R.drawable.placeholder_image)
                            .into(imageView)

                        // Set placeholder text for missing product
                        productView.findViewById<android.widget.TextView>(R.id.tvProductName).text =
                            "Product (details unavailable)"
                        productView.findViewById<android.widget.TextView>(R.id.tvProductPrice).text =
                            "Quantity: ${orderProduct.quantity}"
                        productView.findViewById<android.widget.TextView>(R.id.tvProductTotal).text =
                            "N/A"

                        // Hide variant containers for missing products
                        productView.findViewById<LinearLayout>(R.id.colorContainer).visibility = View.GONE
                        productView.findViewById<TextView>(R.id.tvSize).visibility = View.GONE

                        binding.layoutProducts.addView(productView)
                        displayedProductCount++

                        Log.d(TAG, "Added placeholder for null product at index $index")
                    } else {
                        // Handle valid product
                        val product = orderProduct.product
                        val productView = layoutInflater.inflate(
                            R.layout.item_order_product,
                            binding.layoutProducts,
                            false
                        )

                        // Set product image with error handling
                        val imageView = productView.findViewById<com.google.android.material.imageview.ShapeableImageView>(
                            R.id.ivProduct
                        )

                        try {
                            Glide.with(this)
                                .load(product.getFullImageUrl())
                                .placeholder(R.drawable.placeholder_image)
                                .error(R.drawable.placeholder_image)
                                .into(imageView)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading product image", e)
                            imageView.setImageResource(R.drawable.placeholder_image)
                        }

                        // Set product details
                        productView.findViewById<android.widget.TextView>(R.id.tvProductName).text =
                            product.name

                        // Handle color variant if available
                        val colorContainer = productView.findViewById<LinearLayout>(R.id.colorContainer)
                        val colorIndicator = productView.findViewById<View>(R.id.colorIndicator)
                        val tvColorName = productView.findViewById<TextView>(R.id.tvColorName)

                        // Check color - using when for cleaner code
                        val selectedColor = when {
                            !orderProduct.selectedColor.isNullOrEmpty() -> orderProduct.selectedColor
                            product.colors != null && product.colors.isNotEmpty() -> product.colors[0]
                            else -> null
                        }

                        if (selectedColor != null) {
                            colorContainer.visibility = View.VISIBLE
                            tvColorName.text = selectedColor

                            // Try to set the color indicator
                            try {
                                val colorValue = if (selectedColor.startsWith("#")) {
                                    Color.parseColor(selectedColor)
                                } else {
                                    // For named colors like "RED", "BLUE", etc.
                                    getColorValue(selectedColor)
                                }
                                val drawable = colorIndicator.background.mutate()
                                drawable.setColorFilter(colorValue, PorterDuff.Mode.SRC_ATOP)
                                colorIndicator.background = drawable
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing color: $selectedColor", e)
                                // Use a default color if parsing fails
                                colorIndicator.setBackgroundResource(R.drawable.circular_color_indicator)
                            }
                        } else {
                            colorContainer.visibility = View.GONE
                        }

                        // Handle size variant if available
                        val tvSize = productView.findViewById<TextView>(R.id.tvSize)
                        val selectedSize = when {
                            !orderProduct.selectedSize.isNullOrEmpty() -> orderProduct.selectedSize
                            product.sizes != null && product.sizes.isNotEmpty() -> product.sizes[0]
                            else -> null
                        }

                        if (selectedSize != null) {
                            tvSize.visibility = View.VISIBLE
                            tvSize.text = "Size: $selectedSize"
                        } else {
                            tvSize.visibility = View.GONE
                        }

                        // Calculate price - use variant price if available, otherwise use product price
                        val unitPrice = orderProduct.price ?: product.price
                        val quantity = orderProduct.quantity
                        val totalPrice = unitPrice * quantity

                        productView.findViewById<android.widget.TextView>(R.id.tvProductPrice).text =
                            "${decimalFormat.format(unitPrice)} × $quantity"
                        productView.findViewById<android.widget.TextView>(R.id.tvProductTotal).text =
                            decimalFormat.format(totalPrice)

                        binding.layoutProducts.addView(productView)
                        displayedProductCount++

                        Log.d(TAG, "Added product '${product.name}' at index $index")
                    }
                } catch (e: Exception) {
                    // Catch and handle any exceptions to prevent the app from crashing
                    Log.e(TAG, "Error processing product at index $index", e)

                    // Add an error indicator item
                    val errorView = android.widget.TextView(this).apply {
                        text = "Error displaying item ${index + 1}"
                        setTextColor(Color.RED)
                        textSize = 14f
                        setPadding(16, 8, 16, 8)
                    }
                    binding.layoutProducts.addView(errorView)
                    displayedProductCount++
                }
            }

            // If we didn't display any products despite having a non-empty list
            if (displayedProductCount == 0) {
                val textView = android.widget.TextView(this).apply {
                    text = "No products could be displayed"
                    textSize = 16f
                    setPadding(16, 16, 16, 16)
                }
                binding.layoutProducts.addView(textView)
                Log.d(TAG, "No products could be displayed despite having ${productsList.size} items")
            } else {
                Log.d(TAG, "Successfully displayed $displayedProductCount products out of ${productsList.size}")
            }
        }

        // Update the action buttons based on status
        updateActionsBasedOnStatus()
    }

    // Helper function to convert color names to color values
    private fun getColorValue(colorName: String): Int {
        return when (colorName.uppercase()) {
            "RED" -> Color.RED
            "GREEN" -> Color.GREEN
            "BLUE" -> Color.BLUE
            "BLACK" -> Color.BLACK
            "WHITE" -> Color.WHITE
            "YELLOW" -> Color.YELLOW
            "GRAY", "GREY" -> Color.GRAY
            "CYAN" -> Color.CYAN
            "MAGENTA" -> Color.MAGENTA
            "PINK" -> Color.parseColor("#FFC0CB")
            "PURPLE" -> Color.parseColor("#800080")
            "ORANGE" -> Color.parseColor("#FFA500")
            "BROWN" -> Color.parseColor("#A52A2A")
            "NAVY" -> Color.parseColor("#000080")
            "TEAL" -> Color.parseColor("#008080")
            else -> Color.DKGRAY // Default color if name not recognized
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
        Log.d(TAG, "Updating order status from ${order.status} to $newStatus")

        lifecycleScope.launch {
            try {
                val token = sessionManager.getAuthToken() ?: ""
                if (token.isEmpty()) {
                    ToastUtil.showShort(this@OrderDetailActivity, "Authentication token missing")
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val apiService = ApiClient.getAuthenticatedApiService(token)

                // First update the status
                val updateResponse = apiService.updateOrderStatus(
                    order._id,
                    StatusUpdateRequest(newStatus)
                )

                if (updateResponse.isSuccessful) {
                    // If status update was successful, fetch the complete order data
                    val getOrderResponse = apiService.getOrderById(order._id)

                    if (getOrderResponse.isSuccessful && getOrderResponse.body() != null) {
                        // Use the complete order data from the GET request
                        order = getOrderResponse.body()!!
                        Log.d(TAG, "Retrieved complete order data after status update")
                    } else {
                        // If GET fails, try to use the update response data
                        if (updateResponse.body() != null) {
                            val updatedOrder = updateResponse.body()!!
                            // If the update response has a new status but is missing other fields
                            if (updatedOrder.status != null) {
                                // Just update the status field while keeping other fields
                                order = order.copy(status = updatedOrder.status)
                                Log.d(TAG, "Using partial update data - status only")
                            }
                        }
                    }

                    // Create an Intent to pass back the updated order to OrdersActivity
                    val resultIntent = Intent().apply {
                        putExtra("UPDATED_ORDER", order)
                        putExtra("ORDER_POSITION", intent.getIntExtra("ORDER_POSITION", -1))
                    }
                    setResult(RESULT_OK, resultIntent)

                    // Update the UI
                    runOnUiThread {
                        ToastUtil.showShort(this@OrderDetailActivity, "Status updated to $newStatus")
                        displayOrderDetails() // Refresh the UI with updated order data
                    }
                } else {
                    val errorBody = updateResponse.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Failed response: $errorBody")
                    ToastUtil.showShort(this@OrderDetailActivity, "Failed to update status: ${updateResponse.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating status", e)
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
            Log.e(TAG, "Error opening WhatsApp", e)
            ToastUtil.showShort(this, "Error opening WhatsApp: ${e.message}")
        }
    }

    private fun buildWhatsAppMessage(): String {
        val productsList = order.products // Create a local immutable copy
        val orderItems = if (productsList != null) {
            productsList
                .filter { it.product != null } // Filter out null products
                .joinToString("\n") { orderProduct ->
                    val product = orderProduct.product!!
                    val productName = product.name
                    val quantity = orderProduct.quantity
                    val unitPrice = orderProduct.price ?: product.price
                    val totalPrice = unitPrice * quantity

                    // Include variant details in the message if available
                    val variantDetails = mutableListOf<String>()

                    // Check color - use when for cleaner code
                    val color = when {
                        !orderProduct.selectedColor.isNullOrEmpty() -> orderProduct.selectedColor
                        product.colors != null && product.colors.isNotEmpty() -> product.colors[0]
                        else -> null
                    }

                    if (color != null) variantDetails.add("Color: $color")

                    // Check size - use when for cleaner code
                    val size = when {
                        !orderProduct.selectedSize.isNullOrEmpty() -> orderProduct.selectedSize
                        product.sizes != null && product.sizes.isNotEmpty() -> product.sizes[0]
                        else -> null
                    }

                    if (size != null) variantDetails.add("Size: $size")

                    val variantInfo = if (variantDetails.isNotEmpty()) {
                        " (${variantDetails.joinToString(", ")})"
                    } else {
                        ""
                    }

                    "📦 ${productName}${variantInfo} - ${decimalFormat.format(unitPrice)} × ${quantity} = ${decimalFormat.format(totalPrice)}"
                }
        } else {
            "تفاصيل الطلب غير متوفرة"
        }

        return """
        ✨ الرجاء تأكيد طلبك ✨  
        ━━━━━━━━━━━━━━  
        *مرحباً ${order.customerName} ! 👋  
        يسعدنا إبلاغك بأننا تلقينا طلبك بنجاح ✅  
        📌 رقم الطلب: ${order.orderId} 
        ━━━━━━━━━━━━━━  
        🛍 تفاصيل الطلب:  
        ${orderItems}
        
        🚚 رسوم التوصيل: ${decimalFormat.format(order.shippingFee)}
        💰 المجموع النهائي مع التوصيل: ${decimalFormat.format(order.totalAmount)}
        ━━━━━━━━━━━━━━  
        📩 *يرجى الرد بـ "تم" لتأكيد طلبك.
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