package com.group5.corkboardApp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {

    sealed class LoginState {
        data object Idle : LoginState()
        data object Loading : LoginState()
        data object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }

    sealed class SignupState {
        data object Idle : SignupState()
        data object Loading : SignupState()
        data object Success : SignupState()
        data class Error(val message: String) : SignupState()
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val _signupState = MutableStateFlow<SignupState>(SignupState.Idle)
    val signupState: StateFlow<SignupState> = _signupState

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                AuthRepository.signIn(email, password)
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.localizedMessage ?: "Login failed")
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        fullName: String,
        displayName: String,
        phone: String
    ) {
        viewModelScope.launch {
            _signupState.value = SignupState.Loading
            try {
                val metadata = buildJsonObject {
                    put("full_name", fullName)
                    put("name", fullName)
                    put("display_name", displayName)
                    put("displayName", displayName)
                    put("phone", phone)
                    put("phone_number", phone)
                }
                AuthRepository.signUp(email, password, metadata)
                _signupState.value = SignupState.Success
            } catch (e: Exception) {
                _signupState.value = SignupState.Error(e.localizedMessage ?: "Sign up failed")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    fun resetSignupState() {
        _signupState.value = SignupState.Idle
    }
}
