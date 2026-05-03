package com.group5.corkboardApp.ui.userProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.model.UserProfile
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.UserRepository
import com.group5.corkboardApp.util.SessionManager
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AccountSettingsViewModel : ViewModel() {

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Success(val message: String) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    interface PasswordVerificationState
    object PasswordVerificationStatus {
        object Idle : PasswordVerificationState
        object Loading : PasswordVerificationState
        object Success : PasswordVerificationState
        data class Error(val message: String) : PasswordVerificationState
    }

    private val _updateStatus = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateStatus: StateFlow<UpdateState> = _updateStatus

    private val _currentUserData = MutableStateFlow<UserProfile>(UserProfile())
    val currentUserData: StateFlow<UserProfile> = _currentUserData

    private val _passwordVerificationStatus =
        MutableStateFlow<PasswordVerificationState>(PasswordVerificationStatus.Idle)
    val passwordVerificationStatus: StateFlow<PasswordVerificationState> = _passwordVerificationStatus

    init {
        observeSessionStatus()
    }

    private fun observeSessionStatus() {
        viewModelScope.launch {
            SupabaseClient.client.auth.sessionStatus.collectLatest { status ->
                if (status is SessionStatus.Authenticated) {
                    loadCurrentUserData()
                } else if (status is SessionStatus.NotAuthenticated) {
                    _currentUserData.value = UserProfile()
                }
            }
        }
    }

    fun loadCurrentUserData() {
        viewModelScope.launch {
            try {
                val user = AuthRepository.currentUser() ?: return@launch
                val userProfile = UserRepository.getUserProfile(user.id)
                _currentUserData.value = userProfile.copy(email = user.email ?: userProfile.email)
            } catch (e: Exception) {
                // If DB fetch fails, at least surface the auth email
                AuthRepository.currentUser()?.email?.let {
                    _currentUserData.value = _currentUserData.value.copy(email = it)
                }
            }
        }
    }

    fun updateProfile(name: String, displayName: String, phone: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")

                UserRepository.updateProfile(userId, name, displayName, phone)

                AuthRepository.updateUser(buildJsonObject {
                    put("full_name", name)
                    put("display_name", displayName)
                })

                _updateStatus.value = UpdateState.Success("Profile updated successfully")
                loadCurrentUserData()
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Update failed")
            }
        }
    }

    fun updateEmail(newEmail: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")

                AuthRepository.updateEmail(newEmail)
                UserRepository.updateEmail(userId, newEmail)

                _updateStatus.value =
                    UpdateState.Success("Email update started. Check your new email for confirmation.")
                loadCurrentUserData()
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Email update failed")
            }
        }
    }

    fun verifyCurrentPassword(password: String) {
        viewModelScope.launch {
            _passwordVerificationStatus.value = PasswordVerificationStatus.Loading
            try {
                val email = AuthRepository.currentUser()?.email ?: throw Exception("Not logged in")
                AuthRepository.verifyPassword(email, password)
                _passwordVerificationStatus.value = PasswordVerificationStatus.Success
            } catch (e: Exception) {
                _passwordVerificationStatus.value =
                    PasswordVerificationStatus.Error("Incorrect current password")
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                AuthRepository.updatePassword(newPassword)
                _updateStatus.value = UpdateState.Success("Password updated successfully")
                resetPasswordVerification()
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Password update failed")
            }
        }
    }

    fun resetPasswordVerification() {
        _passwordVerificationStatus.value = PasswordVerificationStatus.Idle
    }

    fun signOut() {
        viewModelScope.launch {
            SessionManager.clear()
            AuthRepository.signOut()
        }
    }

    fun resetStatus() {
        _updateStatus.value = UpdateState.Idle
    }
}
