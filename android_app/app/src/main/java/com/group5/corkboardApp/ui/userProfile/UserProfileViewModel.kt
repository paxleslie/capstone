package com.group5.corkboardApp.ui.userProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.UserRepository




import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    private val _uiState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val uiState: StateFlow<ProfileState> = _uiState

    fun getUserInfo() {
        _uiState.value = ProfileState.Loading
        viewModelScope.launch {
            try {
                val user = AuthRepository.currentUser()

                if (user != null) {
                    val userProfile = UserRepository.getUserProfile(user.id)
                    _uiState.value = ProfileState.Success(
                        userId = user.id,
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
            AuthRepository.signOut()
        }
    }
}
