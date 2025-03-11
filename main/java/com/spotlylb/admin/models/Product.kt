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
        // Same base URL as in your .env file
        val baseUrl = "https://backend-ecommerce-z7ih.onrender.com/uploads"
        var imageUrl = getFirstImageUrl()

        // Return placeholder or early return for empty images
        if (imageUrl.isEmpty()) {
            return "https://spotlylb.com/placeholder.jpg"
        }

        // If already an absolute URL, return as is
        if (imageUrl.startsWith("http")) {
            return imageUrl
        }

        // Clean the path similar to web app's imageUtils.js
        imageUrl = imageUrl
            .replace(Regex("^/"), "")           // Remove leading slash
            .replace(Regex("^uploads/"), "")    // Remove 'uploads/' prefix if present

        // Log the constructed URL for debugging
        val fullUrl = "$baseUrl/$imageUrl"
        android.util.Log.d("Product", "Image URL: $fullUrl")

        return fullUrl
    }
}