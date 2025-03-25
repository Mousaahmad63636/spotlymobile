package com.spotlylb.admin.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spotlylb.admin.api.ApiClient
import com.spotlylb.admin.models.AuthRequest
import com.spotlylb.admin.utils.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    private val _isAdminCheck = MutableLiveData<Boolean>()
    val isAdminCheck: LiveData<Boolean> = _isAdminCheck

    fun login(email: String, password: String) {
        _loginResult.value = LoginResult.Loading

        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.login(AuthRequest(email, password))

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    sessionManager.saveAuthToken(authResponse.token)
                    sessionManager.saveUser(authResponse.user)

                    // Check if user is admin
                    if (authResponse.user.role == "admin") {
                        _isAdminCheck.value = true
                        _loginResult.value = LoginResult.Success
                    } else {
                        _isAdminCheck.value = false
                    }
                } else {
                    _loginResult.value = LoginResult.Error("Authentication failed")
                }
            } catch (e: HttpException) {
                _loginResult.value = LoginResult.Error("Server error: ${e.message()}")
            } catch (e: IOException) {
                _loginResult.value = LoginResult.Error("Network error. Please check your connection")
            } catch (e: Exception) {
                _loginResult.value = LoginResult.Error("An unexpected error occurred")
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    sealed class LoginResult {
        object Success : LoginResult()
        data class Error(val message: String) : LoginResult()
        object Loading : LoginResult()
    }
}