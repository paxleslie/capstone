package com.group5.corkboardApp.ui.household

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class Household(
    val household_id: String,
    val household_name: String,
    val owner_member_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class UserProfile(
    val display_name: String? = null,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class HouseholdMember(
    val member_id: String,
    val user_id: String,
    val household_id: String,
    val role: String,
    val users: UserProfile? = null
)

sealed class HouseholdUiState {
    object Loading : HouseholdUiState()
    data class Success(val households: List<Household>) : HouseholdUiState()
    data class Error(val message: String) : HouseholdUiState()
}

class HouseholdViewModel : ViewModel() {
    private val client = SupabaseClient.client
    
    private val _uiState = MutableStateFlow<HouseholdUiState>(HouseholdUiState.Loading)
    val uiState: StateFlow<HouseholdUiState> = _uiState

    init {
        fetchHouseholds()
    }

    fun fetchHouseholds() {
        viewModelScope.launch {
            _uiState.value = HouseholdUiState.Loading
            try {
                val user = client.auth.currentUserOrNull()
                val userId = user?.id ?: run {
                    _uiState.value = HouseholdUiState.Error("User not authenticated")
                    return@launch
                }
                
                Log.d("HouseholdVM", "Fetching households for user_id: $userId")
                
                val memberEntries = client.postgrest["household_members"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<HouseholdMember>()
                
                Log.d("HouseholdVM", "Found ${memberEntries.size} membership entries")
                val householdIds = memberEntries.map { it.household_id }
                
                if (householdIds.isEmpty()) {
                    _uiState.value = HouseholdUiState.Success(emptyList())
                    return@launch
                }

                val households = client.postgrest["households"]
                    .select {
                        filter {
                            isIn("household_id", householdIds)
                        }
                    }.decodeList<Household>()
                
                Log.d("HouseholdVM", "Fetched ${households.size} households")
                _uiState.value = HouseholdUiState.Success(households)
            } catch (e: Exception) {
                Log.e("HouseholdVM", "Error fetching households", e)
                _uiState.value = HouseholdUiState.Error(e.localizedMessage ?: "Failed to fetch households")
            }
        }
    }

    suspend fun getMembers(householdId: String): List<HouseholdMember> {
        return try {
            Log.d("HouseholdVM", "Fetching members for household: $householdId")
            val result = client.postgrest["household_members"]
                .select(Columns.raw("*, users(display_name, name, email)")) {
                    filter {
                        eq("household_id", householdId)
                    }
                }.decodeList<HouseholdMember>()
            Log.d("HouseholdVM", "Found ${result.size} members")
            result
        } catch (e: Exception) {
            Log.e("HouseholdVM", "Join query failed, trying simple query", e)
            try {
                client.postgrest["household_members"]
                    .select {
                        filter {
                            eq("household_id", householdId)
                        }
                    }.decodeList<HouseholdMember>()
            } catch (e2: Exception) {
                Log.e("HouseholdVM", "Simple query also failed", e2)
                emptyList()
            }
        }
    }

    fun addMemberByEmail(householdId: String, email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_household_id", householdId)
                    put("p_email", email)
                }
                
                client.postgrest.rpc(
                    "add_user_to_household_by_email",
                    params
                )
                onResult(true, "Member added successfully")
            } catch (e: Exception) {
                Log.e("HouseholdVM", "RPC failed", e)
                onResult(false, e.localizedMessage ?: "Failed to add member")
            }
        }
    }
}
