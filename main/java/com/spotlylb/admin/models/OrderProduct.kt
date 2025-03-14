package com.spotlylb.admin.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class OrderProduct(
    // Change the product field to be nullable
    val product: Product?,
    val quantity: Int,
    // Add fields for variants
    val selectedColor: String? = null,
    val selectedSize: String? = null,
    val price: Double? = null
) : Parcelable