package com.group5.corkboardApp.ui.userProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class UserSettings(
    val name: String = "",
    val display_name: String = "",
    val phone: String = "",
    val email: String = ""
)

class AccountSettingsViewModel : ViewModel() {
    private val client = SupabaseClient.client

    private val _updateStatus = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateStatus: StateFlow<UpdateState> = _updateStatus

    private val _currentUserData = MutableStateFlow<UserSettings>(UserSettings())
    val currentUserData: StateFlow<UserSettings> = _currentUserData

    sealed class UpdateState {
        object Idle : UpdateState()
        object Loading : UpdateState()
        data class Success(val message: String) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    init {
        loadCurrentUserData()
    }

    private fun loadCurrentUserData() {
        viewModelScope.launch {
            try {
                val user = client.auth.currentUserOrNull()
                val userId = user?.id ?: return@launch
                val email = user.email ?: ""
                
                val userData = client.postgrest["users"].select {
                    filter { eq("user_id", userId) }
                }.decodeSingle<UserSettings>()
                
                _currentUserData.value = userData.copy(email = email)
            } catch (e: Exception) {
                // If fetching from DB fails, at least try to get email from Auth
                client.auth.currentUserOrNull()?.email?.let {
                    _currentUserData.value = _currentUserData.value.copy(email = it)
                }
            }
        }
    }

    fun updateProfile(name: String, displayName: String, phone: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                val userId = client.auth.currentUserOrNull()?.id ?: throw Exception("Not logged in")
                
                client.postgrest["users"].update(
                    {
                        set("name", name)
                        set("display_name", displayName)
                        set("phone", phone)
                    }
                ) {
                    filter { eq("user_id", userId) }
                }

                client.auth.updateUser {
                    data = buildJsonObject {
                        put("full_name", name)
                        put("display_name", displayName)
                    }
                }

                _updateStatus.value = UpdateState.Success("Profile updated successfully")
                loadCurrentUserData() // Refresh data
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Update failed")
            }
        }
    }

    fun updateEmail(newEmail: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                client.auth.updateUser {
                    email = newEmail
                }
                _updateStatus.value = UpdateState.Success("Email update started. Check your new email for confirmation.")
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Email update failed")
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateState.Loading
            try {
                client.auth.updateUser {
                    password = newPassword
                }
                _updateStatus.value = UpdateState.Success("Password updated successfully")
            } catch (e: Exception) {
                _updateStatus.value = UpdateState.Error(e.localizedMessage ?: "Password update failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            client.auth.signOut()
        }
    }

    fun resetStatus() {
        _updateStatus.value = UpdateState.Idle
    }
}
