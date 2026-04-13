@file:Suppress("PropertyName")

package com.group5.corkboardApp.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
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
data class Profile(
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
    val nickname: String? = null,
    @SerialName("users")
    val profile: Profile? = null
)

@Serializable
data class CreateHouseholdParams(
    @SerialName("p_household_name")
    val householdName: String,
    @SerialName("p_user_id")
    val userID: String
)

@Serializable
data class DeleteHouseholdParams(
    @SerialName("householdid")
    val householdId: String
)

class HouseholdViewModel : ViewModel() {

    sealed class HouseholdCreateState {
        data object Idle : HouseholdCreateState()
        data object Loading : HouseholdCreateState()
        data object Success : HouseholdCreateState()
        data class Error(val message: String) : HouseholdCreateState()
    }

    sealed class HouseholdListState {
        data object Idle : HouseholdListState()
        data object Loading : HouseholdListState()
        data class Success(
            val households: List<Household>,
            val isRefreshing: Boolean = false
        ) : HouseholdListState() {
            val isEmpty get() = households.isEmpty()
        }
        data class Error(val message: String, val canRetry: Boolean = true) : HouseholdListState()
    }

    sealed class HouseholdDetailState {
        data object Idle : HouseholdDetailState()
        data object Loading : HouseholdDetailState()
        data class Success(
            val household: Household,
            val members: List<HouseholdMember> = emptyList()
        ) : HouseholdDetailState()
        data class Error(val message: String) : HouseholdDetailState()
    }

    sealed class MemberAddState {
        data object Idle : MemberAddState()
        data object Loading : MemberAddState()
        data object Success : MemberAddState()
        data class Error(val message: String) : MemberAddState()
    }

    sealed class NavState {
        data object Idle : NavState()
        data object List : NavState()
        data object Detail : NavState()
    }

    sealed class HouseholdActionState {
        data object Idle : HouseholdActionState()
        data object Loading : HouseholdActionState()
        data class Success(val message: String) : HouseholdActionState()
        data class Error(val message: String) : HouseholdActionState()
    }

    private val client = SupabaseClient.client

    private val _householdListState = MutableStateFlow<HouseholdListState>(HouseholdListState.Loading)
    val householdListState: StateFlow<HouseholdListState> = _householdListState

    private val _createState = MutableStateFlow<HouseholdCreateState>(HouseholdCreateState.Idle)
    val createState: StateFlow<HouseholdCreateState> = _createState

    private val _detailState = MutableStateFlow<HouseholdDetailState>(HouseholdDetailState.Idle)
    val detailState: StateFlow<HouseholdDetailState> = _detailState

    private val _actionState = MutableStateFlow<HouseholdActionState>(HouseholdActionState.Idle)
    val actionState: StateFlow<HouseholdActionState> = _actionState

    private val _addMemberState = MutableStateFlow<MemberAddState>(MemberAddState.Idle)
    val addMemberState: StateFlow<MemberAddState> = _addMemberState

    private val _navState = MutableStateFlow<NavState>(NavState.Idle)
    val navState: StateFlow<NavState> = _navState

    val currentUserMember: StateFlow<HouseholdMember?> = _detailState.map { state ->
        if (state is HouseholdDetailState.Success) {
            val currentUserId = client.auth.currentUserOrNull()?.id
            state.members.find { it.user_id == currentUserId }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        fetchHouseholds()
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _createState.value = HouseholdCreateState.Loading
            try {
                val userId = client.auth.currentUserOrNull()?.id ?: throw Exception("User not authenticated")
                client.postgrest.rpc("create_household", CreateHouseholdParams(householdName = name, userID = userId))
                _createState.value = HouseholdCreateState.Success
                fetchHouseholds()
            } catch (e: Exception) {
                _createState.value = HouseholdCreateState.Error(e.message ?: "Failed to create household")
            }
        }
    }

    fun updateHouseholdName(household: Household, newName: String) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                client.postgrest["households"].update(
                    {
                        set("household_name", newName)
                    }
                ) {
                    filter {
                        eq("household_id", household.household_id)
                    }
                }
                
                _actionState.value = HouseholdActionState.Success("Household name updated")
                
                // Refresh detail state
                val members = fetchMembersData(household.household_id)
                _detailState.value = HouseholdDetailState.Success(household.copy(household_name = newName), members)
                fetchHouseholds()
            } catch (e: Exception) {
                _actionState.value = HouseholdActionState.Error(e.localizedMessage ?: "Failed to update household name")
            }
        }
    }

    fun fetchHouseholds() {
        viewModelScope.launch {
            _householdListState.value = HouseholdListState.Loading
            try {
                val userId = client.auth.currentUserOrNull()?.id ?: run {
                    _householdListState.value = HouseholdListState.Error("User not authenticated")
                    return@launch
                }
                val memberEntries = client.postgrest["household_members"].select { filter { eq("user_id", userId) } }.decodeList<HouseholdMember>()
                val householdIds = memberEntries.map { it.household_id }
                if (householdIds.isEmpty()) {
                    _householdListState.value = HouseholdListState.Success(emptyList())
                    return@launch
                }
                val households = client.postgrest["households"].select { filter { isIn("household_id", householdIds) } }.decodeList<Household>()
                _householdListState.value = HouseholdListState.Success(households)
            } catch (e: Exception) {
                _householdListState.value = HouseholdListState.Error(e.localizedMessage ?: "Failed to fetch households")
            }
        }
    }

    fun getHouseholdDetails(householdId: String) {
        viewModelScope.launch {
            _detailState.value = HouseholdDetailState.Loading
            try {
                val household = client.postgrest["households"].select { filter { eq("household_id", householdId) } }.decodeList<Household>().firstOrNull()
                if (household != null) {
                    val members = fetchMembersData(householdId)
                    _detailState.value = HouseholdDetailState.Success(household, members)
                    _navState.value = NavState.Detail
                } else {
                    _detailState.value = HouseholdDetailState.Error("Household not found")
                }
            } catch (e: Exception) {
                _detailState.value = HouseholdDetailState.Error(e.localizedMessage ?: "Failed to fetch details")
            }
        }
    }

    private suspend fun fetchMembersData(householdId: String): List<HouseholdMember> {
        return try {
            client.postgrest["household_members"]
                .select(Columns.raw("*, users(display_name, name, email)")) { filter { eq("household_id", householdId) } }
                .decodeList<HouseholdMember>()
        } catch (_: Exception) {
            client.postgrest["household_members"].select { filter { eq("household_id", householdId) } }.decodeList<HouseholdMember>()
        }
    }

    fun addMemberByEmail(household: Household, email: String) {
        viewModelScope.launch {
            _addMemberState.value = MemberAddState.Loading
            try {
                val params = buildJsonObject {
                    put("p_household_id", household.household_id)
                    put("p_new_member_email", email)
                }
                client.postgrest.rpc("add_user_to_household_by_email", params)
                _addMemberState.value = MemberAddState.Success
                
                val members = fetchMembersData(household.household_id)
                _detailState.value = HouseholdDetailState.Success(household, members)
                
            } catch (e: Exception) {
                _addMemberState.value = MemberAddState.Error(e.localizedMessage ?: "Failed to add member")
            }
        }
    }

    fun removeMember(household: Household, memberId: String) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                val params = buildJsonObject {
                    put("householdid", household.household_id)
                    put("userid", memberId)
                }
                
                client.postgrest.rpc("remove_household_member", params)
                _actionState.value = HouseholdActionState.Success("Member removed")
                
                val members = fetchMembersData(household.household_id)
                _detailState.value = HouseholdDetailState.Success(household, members)
                fetchHouseholds()
            } catch (e: Exception) {
                _actionState.value = HouseholdActionState.Error(e.localizedMessage ?: "Failed to remove member")
            }
        }
    }

    fun leaveHousehold(household: Household, memberId: String) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                val params = buildJsonObject {
                    put("householdid", household.household_id)
                    put("userid", memberId)
                }

                client.postgrest.rpc("remove_household_member", params)
                _actionState.value = HouseholdActionState.Success("Left household")
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                _actionState.value = HouseholdActionState.Error(e.localizedMessage ?: "Failed to leave household")
            }
        }
    }

    fun deleteHousehold(household: Household) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                client.postgrest.rpc("delete_household", DeleteHouseholdParams(householdId = household.household_id))
                _actionState.value = HouseholdActionState.Success("Household deleted")
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                _actionState.value = HouseholdActionState.Error(e.localizedMessage ?: "Failed to delete household")
            }
        }
    }

    fun navToListHouseholds() {
        fetchHouseholds()
        _navState.value = NavState.List
    }

    fun navToIdle() { _navState.value = NavState.Idle }
    fun resetCreateState() { _createState.value = HouseholdCreateState.Idle }
    fun resetActionState() { _actionState.value = HouseholdActionState.Idle }
    fun resetAddMemberState() { _addMemberState.value = MemberAddState.Idle }
}
