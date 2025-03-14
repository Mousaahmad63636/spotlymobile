// main/java/com/spotlylb/admin/models/AuthResponse.kt
package com.spotlylb.admin.models

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val _id: String,
    val name: String,
    val email: String,
    val role: String,
    val fcmToken: String? = null  // Add this line
)