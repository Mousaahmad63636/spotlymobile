package com.spotlylb.admin.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.spotlylb.admin.R
import com.spotlylb.admin.api.ApiClient
import com.spotlylb.admin.databinding.ActivityLoginBinding
import com.spotlylb.admin.ui.orders.OrdersActivity
import com.spotlylb.admin.utils.SessionManager
import com.spotlylb.admin.utils.ToastUtil
import kotlinx.coroutines.launch
import java.util.logging.Handler

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    // Added this missing declaration
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the sessionManager
        sessionManager = SessionManager(this)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                ToastUtil.showShort(this, getString(R.string.error_fields_empty))
                return@setOnClickListener
            }

            viewModel.login(email, password)
        }
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this, Observer { result ->
            when (result) {
                is LoginViewModel.LoginResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    // Register for notifications
                    registerForNotifications()
                    // Wait a moment and verify the token was saved
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        verifyFcmToken()
                    }, 3000) // 3 second delay to ensure token is processed
                    navigateToOrdersScreen()
                }
                is LoginViewModel.LoginResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    ToastUtil.showLong(this, result.message)
                }
                is LoginViewModel.LoginResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        })

        viewModel.isAdminCheck.observe(this, Observer { isAdmin ->
            if (!isAdmin) {
                ToastUtil.showLong(this, getString(R.string.error_not_admin))
                viewModel.logout()
            }
        })
    }

    private fun registerForNotifications() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "FCM Token: $token")

            // Send token to your backend
            updateFcmTokenOnServer(token)
        }
    }
    // main/java/com/spotlylb/admin/ui/auth/LoginActivity.kt
    private fun verifyFcmToken() {
        lifecycleScope.launch {
            try {
                val authToken = sessionManager.getAuthToken() ?: return@launch
                Log.d("FCM", "Verifying FCM token with auth token: ${authToken.take(10)}...")

                val apiService = ApiClient.getAuthenticatedApiService(authToken)

                // Attempt to get the user profile to check if FCM token exists
                val response = apiService.getProfile()

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    val fcmToken = user.fcmToken

                    Log.d("FCM", "Current FCM token on server: ${fcmToken?.take(10) ?: "null"}")

                    if (fcmToken.isNullOrEmpty()) {
                        Log.w("FCM", "FCM token is empty on server, registering again")
                        registerForNotifications()
                    } else {
                        Log.d("FCM", "FCM token already exists on server")
                    }
                } else {
                    Log.e("FCM", "Failed to get profile: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error verifying FCM token", e)
            }
        }
    }
    private fun updateFcmTokenOnServer(token: String) {
        lifecycleScope.launch {
            try {
                val authToken = sessionManager.getAuthToken() ?: return@launch
                Log.d("FCM", "Using auth token: ${authToken.take(10)}...")

                val apiService = ApiClient.getAuthenticatedApiService(authToken)

                Log.d("FCM", "Sending FCM token to server: ${token.take(10)}...")
                val response = apiService.updateFcmToken(mapOf("fcmToken" to token))

                if (response.isSuccessful) {
                    Log.d("FCM", "Token updated on server successfully")
                } else {
                    Log.e("FCM", "Failed to update token: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Failed to update token on server", e)
            }
        }
    }
    private fun navigateToOrdersScreen() {
        val intent = Intent(this, OrdersActivity::class.java)
        startActivity(intent)
        finish()
    }
}