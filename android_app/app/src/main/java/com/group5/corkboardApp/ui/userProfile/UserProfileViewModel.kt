package com.group5.corkboardApp.ui.userProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive


class UserProfileViewModel : ViewModel() {

    // will hold the actual data for the profile
    sealed class ProfileState {
        data object Idle : ProfileState()
        data object Loading : ProfileState()

        data class Success(
            val userId : String,
            val fullName: String,
            val email: String,
            // can add more values here if we need to - like their role
            val isRefreshing : Boolean = false
        ) : ProfileState() {
            // have isEmpty automatically set based user string
            val userExists get() = fullName.isEmpty()
        }
        data class Error(
            val message: String
        ) : ProfileState()
    }

    // for navigation, no logic just states
    sealed class NavState {
        // for just the user profile
        data object Profile : NavState()
        // for when we need to list the households
        data object List : NavState()
        // for a specific household, with details
        data object Detail : NavState()
    }

    // use client for anything to do with supabase
    private val client = SupabaseClient.client
    private val _uiState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val uiState: StateFlow<ProfileState> = _uiState
    // stateflow for the navigation state
    private val _navState = MutableStateFlow<NavState>(NavState.Profile)
    val navState: StateFlow<NavState> = _navState

    // get the user info from supabase
    fun getUserInfo() {
        //set it to loading
        _uiState.value = ProfileState.Loading
        // this makes it launch in the background and won't freeze the app if it
        // takes a while to respond.
        viewModelScope.launch {
            // have supabase be in scope of the viewModel launch
            try {
                // the auth part goes into authRepo, we shouldn't be handling database
                // calls here
                val user = client.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id
                    val email = user.email

                    val fullName = user.userMetadata?.get("full_name")?.jsonPrimitive?.content
                    // make sure these have values
                    if (fullName == null) {
                        _uiState.value = ProfileState.Error("User info not available")
                        return@launch
                    }
                    //else we set to success with the info we have
                    else {
                        _uiState.value = ProfileState.Success(
                            userId = userId,
                            fullName = fullName,
                            email = email ?: "" // just in case it returns null
                        )
                    }
                }
                // catch if we get here without a user logged in
                else {
                    _uiState.value = ProfileState.Error("No currently active user.")
                }
            } catch (e: Exception) {
                _uiState.value =
                    ProfileState.Error(e.localizedMessage ?: "An Unknown Error Occurred")
            }
        }
    }

    fun signOut () {
        viewModelScope.launch {
            // this goes into authRepo
            client.auth.signOut()
        }
    }

    fun navToListHouseholds() {
        _navState.value = NavState.List
    }

    fun navToProfile() {
        _navState.value = NavState.Profile
    }

    fun navToDetail() {
        //will need to figure out how to get detailed household view - probably not here though
        _navState.value = NavState.Detail
    }
}