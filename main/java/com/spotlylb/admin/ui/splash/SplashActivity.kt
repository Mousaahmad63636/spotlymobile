package com.spotlylb.admin.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.spotlylb.admin.R
import com.spotlylb.admin.ui.auth.LoginActivity
import com.spotlylb.admin.ui.orders.OrdersActivity
import com.spotlylb.admin.utils.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sessionManager = SessionManager(this)

        // Simulate a delay for splash screen (1.5 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            // Check if user is logged in and is admin
            if (sessionManager.isLoggedIn()) {
                startActivity(Intent(this, OrdersActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1500)
    }
}