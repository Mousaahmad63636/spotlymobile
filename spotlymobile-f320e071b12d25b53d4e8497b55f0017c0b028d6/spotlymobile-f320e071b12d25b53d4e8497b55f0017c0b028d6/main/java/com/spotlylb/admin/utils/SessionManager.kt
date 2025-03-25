package com.spotlylb.admin.utils

import android.content.Context
import android.content.SharedPreferences
import com.spotlylb.admin.models.User

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        Constants.PREF_NAME, Context.MODE_PRIVATE
    )

    fun saveAuthToken(token: String) {
        sharedPreferences.edit()
            .putString(Constants.KEY_TOKEN, token)
            .apply()
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(Constants.KEY_TOKEN, null)
    }

    fun saveUser(user: User) {
        sharedPreferences.edit()
            .putString(Constants.KEY_USER_ID, user._id)
            .putString(Constants.KEY_USER_NAME, user.name)
            .putString(Constants.KEY_USER_EMAIL, user.email)
            .putString(Constants.KEY_USER_ROLE, user.role)
            .apply()
    }

    fun getUserName(): String {
        return sharedPreferences.getString(Constants.KEY_USER_NAME, "") ?: ""
    }

    fun isUserAdmin(): Boolean {
        val role = sharedPreferences.getString(Constants.KEY_USER_ROLE, "")
        return role == "admin"
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return !getAuthToken().isNullOrEmpty() && isUserAdmin()
    }
}