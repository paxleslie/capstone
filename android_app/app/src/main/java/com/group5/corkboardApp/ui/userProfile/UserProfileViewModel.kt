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

@Serializable
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val display_name: String = "",
    val phone: String = ""
)

class UserProfileViewModel : ViewModel() {

    sealed class ProfileState {
        data object Idle : ProfileState()
        data object Loading : ProfileState()

        data class Success(
            val userId: String,
            val fullName: String,
            val email: String,
            val displayName: String = "",
            val phone: String = ""
        ) : ProfileState()

        data class Error(val message: String) : ProfileState()
    }

    private val client = SupabaseClient.client
    private val _uiState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val uiState: StateFlow<ProfileState> = _uiState

    fun getUserInfo() {
        _uiState.value = ProfileState.Loading
        viewModelScope.launch {
            try {
                val user = client.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id
                    
                    // Fetch user info from the 'users' table in the database
                    val userProfile = client.postgrest["users"].select {
                        filter { eq("user_id", userId) }
                    }.decodeSingle<UserProfile>()

                    _uiState.value = ProfileState.Success(
                        userId = userId,
                        fullName = userProfile.name,
                        email = userProfile.email,
                        displayName = userProfile.display_name,
                        phone = userProfile.phone
                    )
                } else {
                    _uiState.value = ProfileState.Error("No currently active user.")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileState.Error(e.localizedMessage ?: "An Unknown Error Occurred")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            client.auth.signOut()
        }
    }
}
