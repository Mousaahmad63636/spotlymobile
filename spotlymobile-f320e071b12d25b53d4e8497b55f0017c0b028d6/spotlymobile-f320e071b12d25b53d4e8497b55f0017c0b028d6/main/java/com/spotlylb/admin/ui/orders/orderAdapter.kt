package com.spotlylb.admin.ui.orders

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spotlylb.admin.databinding.ItemOrderBinding
import com.spotlylb.admin.models.Order
import java.text.DecimalFormat

class OrderAdapter(private val onOrderClick: (Order, Int) -> Unit) :
    ListAdapter<Order, OrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val decimalFormat = DecimalFormat("$#,##0.00")

        fun bind(order: Order) {
            binding.apply {
                tvOrderId.text = "Order #${order.orderId}"
                tvCustomerName.text = order.customerName
                tvOrderDate.text = order.getFormattedDate()
                tvOrderTotal.text = decimalFormat.format(order.totalAmount)

                // Fix for nullable products list - use safe call (?.) with elvis operator
                tvItemCount.text = "${order.products?.sumOf { it.quantity } ?: 0} items"

                // Set status
                chipStatus.text = order.status
                chipStatus.setTextColor(Color.WHITE)
                chipStatus.setChipBackgroundColorResource(
                    when (order.status?.lowercase()) {
                        "pending" -> android.R.color.holo_orange_dark
                        "confirmed" -> android.R.color.holo_blue_dark
                        "shipped" -> android.R.color.holo_blue_light
                        "delivered" -> android.R.color.holo_green_dark
                        "cancelled" -> android.R.color.holo_red_dark
                        else -> android.R.color.darker_gray
                    }
                )

                // Set click listener with position
                root.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onOrderClick(order, position)
                    }
                }
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem._id == newItem._id
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}