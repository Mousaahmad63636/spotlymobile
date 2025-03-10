package com.spotlylb.admin.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Product(
    val _id: String,
    val name: String,
    val price: Double,
    val images: List<String>? = null
) : Parcelable {

    fun getFirstImageUrl(): String {
        return if (images.isNullOrEmpty()) {
            ""
        } else {
            images[0]
        }
    }

    fun getFullImageUrl(): String {
        val baseUrl = "https://backend-ecommerce-z7ih.onrender.com"
        val imageUrl = getFirstImageUrl()

        return if (imageUrl.startsWith("http")) {
            imageUrl
        } else {
            "$baseUrl/uploads/$imageUrl"
        }
    }
}