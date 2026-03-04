package com.group5.corkboardApp.ui.household

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.ktor.util.collections.StringMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
data class Household(
    val household_id: String,
    val household_name: String,
    val owner_member_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class Profile(
    val display_name: String? = null,
    val full_name: String? = null,
    val email: String? = null
)

@Serializable
data class HouseholdMember(
    val member_id: String,
    val user_id: String,
    val household_id: String,
    val role: String,
    val profiles: Profile? = null
)

@Serializable
data class CreateHouseholdParams(
    @SerialName ("p_household_name")
    val householdName: String,
    @SerialName ("p_member_id")
    val userID: String
)

class HouseholdViewModel : ViewModel() {
    sealed class CreateState {
        data object Idle : CreateState()
        data object Loading : CreateState()
        data object Success : CreateState()
        data class Error(val message: String) : CreateState()
    }

    // this seems like it should be in its own class, specifically for seeing
    // all of the households you're a part of, not part of a specific household screen
    sealed class ListHouseholdState {
        data object Idle : ListHouseholdState()
        data object Loading : ListHouseholdState()
        data class Success(
            val households: List<Household>,
            val isRefreshing : Boolean = false
        ) : ListHouseholdState() {
            // have isEmpty automatically set based on the houseHolds list
            val isEmpty get() = households.isEmpty()
        }
        data class Error(
            val message: String,
            // determine if they can retry (network error vs db error)
            val canRetry: Boolean = true
        ) : ListHouseholdState()
    }

    sealed class ListMemberState {
        data object Idle : ListMemberState()
        data object Loading : ListMemberState()

        data class Success (
            val members : List<HouseholdMember>
        ) : ListMemberState () {
            val isEmpty get() = members.isEmpty()
        }
        data class Error(
            val message: String,
        ) : ListMemberState()
    }
    private val client = SupabaseClient.client
    // stateflow for the household list state
    private val _uiState = MutableStateFlow<ListHouseholdState>(ListHouseholdState.Loading)
    val uiState: StateFlow<ListHouseholdState> = _uiState

    //stateflow for the create state
    private val _createState = MutableStateFlow<CreateState>(CreateState.Idle)
    val createState: StateFlow<CreateState> = _createState

    private val _listMemberState = MutableStateFlow<ListMemberState>(ListMemberState.Idle)
    val listMemberState: StateFlow<ListMemberState> = _listMemberState



    init {
        fetchHouseholds()
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _createState.value = CreateState.Loading
            // THIS SHOULD GO INTO DATAREPO
            try {
                val memberId = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                client.postgrest.rpc(
                    "create_household",
                    CreateHouseholdParams(
                        householdName = name,
                        userID = memberId
                    )
                )

                _createState.value = CreateState.Success
            } catch (e: Exception) {
                _createState.value = CreateState.Error(e.localizedMessage ?: "Failed to create household")
            }
        }
    }

    fun fetchHouseholds() {
        viewModelScope.launch {
            _uiState.value = ListHouseholdState.Loading
            // THIS GOES INTO DATAREPO
            try {
                val user = client.auth.currentUserOrNull()
                val userId = user?.id ?: run {
                    _uiState.value = ListHouseholdState.Error("User not authenticated")
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
                    _uiState.value = ListHouseholdState.Success(emptyList())
                    return@launch
                }

                val households = client.postgrest["households"]
                    .select {
                        filter {
                            isIn("household_id", householdIds)
                        }
                    }.decodeList<Household>()
                
                Log.d("HouseholdVM", "Fetched ${households.size} households")
                _uiState.value = ListHouseholdState.Success(households)
            } catch (e: Exception) {
                Log.e("HouseholdVM", "Error fetching households", e)
                _uiState.value = ListHouseholdState.Error(e.localizedMessage ?: "Failed to fetch households")
            }
        }
    }


    fun getHouseholdMembers (householdId: String) {
        viewModelScope.launch {
            _listMemberState.value = ListMemberState.Loading
            try {
                val members = data_getMembers(householdId)
                _listMemberState.value = ListMemberState.Success(members)
            } catch (e: Exception) {
                _listMemberState.value = ListMemberState.Error(
                    e.localizedMessage ?: "Failed to fetch members of household $householdId"
                )
            }
        }
    }

    fun resetCreateState () {
        _createState.value = CreateState.Idle
    }

    //THIS SHOULD BE IN DATAREPO
    suspend fun data_getMembers(householdId: String): List<HouseholdMember> {
        return try {
            Log.d("HouseholdVM", "Fetching members for household: $householdId")
            val result = client.postgrest["household_members"]
                .select(Columns.raw("*, profiles(display_name, full_name, email)")) {
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
                // No need to fetch all households, just refresh members in the UI
            } catch (e: Exception) {
                Log.e("HouseholdVM", "RPC failed", e)
                onResult(false, e.localizedMessage ?: "Failed to add member")
            }
        }
    }
}
