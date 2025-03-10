package com.spotlylb.admin.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.spotlylb.admin.R
import com.spotlylb.admin.databinding.ActivityLoginBinding
import com.spotlylb.admin.ui.orders.OrdersActivity
import com.spotlylb.admin.utils.ToastUtil

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun navigateToOrdersScreen() {
        val intent = Intent(this, OrdersActivity::class.java)
        startActivity(intent)
        finish()
    }
}