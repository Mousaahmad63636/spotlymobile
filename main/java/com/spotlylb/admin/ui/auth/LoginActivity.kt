package com.spotlylb.admin.ui.auth

import android.content.Intent
import android.os.Bundle
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
                    // Add this line
                    registerForNotifications()
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

    private fun updateFcmTokenOnServer(token: String) {
        lifecycleScope.launch {
            try {
                val authToken = sessionManager.getAuthToken() ?: return@launch
                val apiService = ApiClient.getAuthenticatedApiService(authToken)

                // Uncomment this when you've added the API endpoint
                // val response = apiService.updateFcmToken(mapOf("fcmToken" to token))

                Log.d("FCM", "Token updated on server")
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