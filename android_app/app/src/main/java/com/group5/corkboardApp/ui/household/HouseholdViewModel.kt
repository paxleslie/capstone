package com.group5.corkboardApp.ui.household

import android.util.Log
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

// these should go into models object
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
    // the actual table is called users
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

// Parameters for removing a member from a household
@Serializable
data class RemoveMemberParams(
    // Household to remove the user from
    @SerialName("householdid")
    val householdId: String,

    // The user being removed
    @SerialName("userid")
    val targetUserId: String
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
            // have isEmpty automatically set based on the households list
            val isEmpty get() = households.isEmpty()
        }

        data class Error(
            val message: String,
            // determine if they can retry (network error vs db error)
            val canRetry: Boolean = true
        ) : HouseholdListState()
    }

    sealed class HouseholdDetailState {
        data object Idle : HouseholdDetailState()
        data object Loading : HouseholdDetailState()

        data class Success(
            val household: Household,
            // default to empty list, gets updated by derived flow
            val members: List<HouseholdMember> = emptyList()
        ) : HouseholdDetailState() {
            val isEmpty get() = members.isEmpty()
        }

        // main error will be household not existing for that ID
        data class Error(val message: String) : HouseholdDetailState()
    }

    sealed class MemberAddState {
        data object Idle : MemberAddState()
        data object Loading : MemberAddState()

        data object Success : MemberAddState() {
            val message get() = "Member added successfully"
        }

        data class Error(val message: String) : MemberAddState() {
            val errorMessage get() = "Failed to add member: $message"
        }
    }

    // for navigation, no logic just states
    sealed class NavState {
        // for just the user profile
        data object Idle : NavState()

        // for when we need to list the households
        data object List : NavState()

        // for a specific household, with details
        data object Detail : NavState()
    }

    // Represents the state of household actions like remove, leave, and delete
    sealed class HouseholdActionState {
        // No household action is currently happening
        data object Idle : HouseholdActionState()

        // A household action is currently in progress
        data object Loading : HouseholdActionState()

        // A household action completed successfully
        data class Success(val message: String) : HouseholdActionState()

        // A household action failed
        data class Error(val message: String) : HouseholdActionState()
    }

    private val TAG = "HouseholdViewModel"
    private val client = SupabaseClient.client

    // stateflow for the household list state
    private val _householdListState =
        MutableStateFlow<HouseholdListState>(HouseholdListState.Loading)
    val householdListState: StateFlow<HouseholdListState> = _householdListState

    // stateflow for the create state
    private val _createState =
        MutableStateFlow<HouseholdCreateState>(HouseholdCreateState.Idle)
    val createState: StateFlow<HouseholdCreateState> = _createState

    // stateflow for the detail state
    private val _detailState =
        MutableStateFlow<HouseholdDetailState>(HouseholdDetailState.Idle)
    val detailState: StateFlow<HouseholdDetailState> = _detailState

    // stateflow for household actions for the UI to observe
    private val _actionState =
        MutableStateFlow<HouseholdActionState>(HouseholdActionState.Idle)
    val actionState: StateFlow<HouseholdActionState> = _actionState

    // stateflow for adding members to a household
    private val _addMemberState =
        MutableStateFlow<MemberAddState>(MemberAddState.Idle)
    val addMemberState: StateFlow<MemberAddState> = _addMemberState

    // stateflow for navigation
    private val _navState = MutableStateFlow<NavState>(NavState.Idle)
    val navState: StateFlow<NavState> = _navState

    // derived flows
    // automatically get the current user's member record
    // the stateIn keeps this flow active for 5 seconds after its not needed
    // so that we don't fetch upstream on a hiccup (like screen rotation)
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

            // THIS SHOULD GO INTO DATAREPO
            try {
                // make sure we have an authenticated user logged in
                val memberId = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                val newHouseholdId = client.postgrest.rpc(
                    "create_household",
                    CreateHouseholdParams(
                        householdName = name,
                        userID = memberId
                    )
                )

                Log.d(TAG, "Created household with ID: ${newHouseholdId.data}")
                _createState.value = HouseholdCreateState.Success
            } catch (e: Exception) {
                _createState.value =
                    HouseholdCreateState.Error(e.message ?: "Failed to create household")
                Log.e(TAG, "Failed to create household: ${e.message}", e)
            }
        }
    }

    fun fetchHouseholds() {
        viewModelScope.launch {
            _householdListState.value = HouseholdListState.Loading

            // THIS GOES INTO DATAREPO
            try {
                val user = client.auth.currentUserOrNull()
                val userId = user?.id ?: run {
                    _householdListState.value =
                        HouseholdListState.Error("User not authenticated")
                    return@launch
                }

                Log.d(TAG, "Fetching households for user_id: $userId")

                val memberEntries = client.postgrest["household_members"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<HouseholdMember>()

                Log.d(TAG, "Found ${memberEntries.size} membership entries")
                val householdIds = memberEntries.map { it.household_id }

                if (householdIds.isEmpty()) {
                    _householdListState.value = HouseholdListState.Success(emptyList())
                    return@launch
                }

                val households = client.postgrest["households"]
                    .select {
                        filter {
                            isIn("household_id", householdIds)
                        }
                    }
                    .decodeList<Household>()

                Log.d(TAG, "Fetched ${households.size} households")
                _householdListState.value = HouseholdListState.Success(households)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching households", e)
                _householdListState.value =
                    HouseholdListState.Error(e.localizedMessage ?: "Failed to fetch households")
            }
        }
    }

    fun getHouseholdDetails(householdId: String) {
        viewModelScope.launch {
            _detailState.value = HouseholdDetailState.Loading

            try {
                // should be in data repo and we should be calling a function
                val household = client.postgrest["households"]
                    .select {
                        filter {
                            eq("household_id", householdId)
                        }
                    }
                    .decodeList<Household>()
                    .firstOrNull()

                // set the stateflow and nav
                if (household != null) {
                    _detailState.value = HouseholdDetailState.Success(household)

                    // automatically get the members of this household
                    getHouseholdMembers(household)

                    // member profile is automatically fetched by derived state flow
                    _navState.value = NavState.Detail
                } else {
                    _detailState.value = HouseholdDetailState.Error("Household not found")
                }
            } catch (e: Exception) {
                _detailState.value = HouseholdDetailState.Error(
                    e.localizedMessage ?: "Failed to fetch household details"
                )
            }
        }
    }

    fun getHouseholdMembers(household: Household) {
        viewModelScope.launch {
            _detailState.value = HouseholdDetailState.Loading

            try {
                val members = data_getMembers(household.household_id)

                if (household.household_id.isNotEmpty()) {
                    _detailState.value = HouseholdDetailState.Success(household, members = members)
                } else {
                    _detailState.value = HouseholdDetailState.Error("Household not found")
                }
            } catch (e: Exception) {
                _detailState.value = HouseholdDetailState.Error(
                    e.localizedMessage ?: "Failed to fetch members of household ${household.household_id}"
                )
            }
        }
    }

    fun navToListHouseholds() {
        fetchHouseholds()
        _navState.value = NavState.List
    }

    fun navToIdle() {
        _navState.value = NavState.Idle
    }

    fun resetCreateState() {
        _createState.value = HouseholdCreateState.Idle
    }

    // Resets the household action state after showing a success/error message
    fun resetActionState() {
        _actionState.value = HouseholdActionState.Idle
    }

    // THIS SHOULD BE IN DATAREPO
    suspend fun data_getMembers(householdId: String): List<HouseholdMember> {
        return try {
            Log.d(TAG, "Fetching members for household: $householdId")

            val result = client.postgrest["household_members"]
                .select(Columns.raw("*, users(display_name, name, email)")) {
                    filter {
                        eq("household_id", householdId)
                    }
                }
                .decodeList<HouseholdMember>()

            Log.d(TAG, "Found ${result.size} members")
            Log.d(TAG, "Found members: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Join query failed, trying simple query", e)

            try {
                client.postgrest["household_members"]
                    .select {
                        filter {
                            eq("household_id", householdId)
                        }
                    }
                    .decodeList<HouseholdMember>()
            } catch (e2: Exception) {
                Log.e(TAG, "Simple query also failed", e2)
                emptyList()
            }
        }
    }

    fun addMemberByEmail(household: Household, email: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_household_id", household.household_id)
                    put("p_new_member_email", email)
                }

                client.postgrest.rpc(
                    "add_user_to_household_by_email",
                    params
                )

                _addMemberState.value = MemberAddState.Success
            } catch (e: Exception) {
                Log.e(TAG, "RPC failed", e)
                _addMemberState.value =
                    MemberAddState.Error(e.localizedMessage ?: "Failed to add member")
            }
        }
    }

    // Removes a selected user from the household
    // Admins can remove other users, while regular users should only be able to remove themselves
    fun removeMember(household: Household, targetUserId: String) {
        viewModelScope.launch {
            // Set action state to loading while the request is being processed
            _actionState.value = HouseholdActionState.Loading

            try {
                // Call the database RPC to remove the selected member
                client.postgrest.rpc(
                    "remove_household_member",
                    RemoveMemberParams(
                        householdId = household.household_id,
                        targetUserId = targetUserId
                    )
                )

                // Update UI state on success
                _actionState.value =
                    HouseholdActionState.Success("Member removed successfully")

                // Refresh household details and household list after removal
                getHouseholdDetails(household.household_id)
                fetchHouseholds()
            } catch (e: Exception) {
                // Log the error for debugging
                Log.e(TAG, "Failed to remove member", e)

                // Update UI state with the error message
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to remove member"
                )
            }
        }
    }

    // Allows the current user to leave a household by removing their own membership
    fun leaveHousehold(household: Household) {
        viewModelScope.launch {
            // Set action state to loading while the request is being processed
            _actionState.value = HouseholdActionState.Loading

            try {
                // Get the currently authenticated user
                val currentUserId = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                // Call the same database RPC used for removing members
                // RLS rules should allow a regular user to remove only themselves
                client.postgrest.rpc(
                    "remove_household_member",
                    RemoveMemberParams(
                        householdId = household.household_id,
                        targetUserId = currentUserId
                    )
                )

                // Update UI state on success
                _actionState.value =
                    HouseholdActionState.Success("Left household successfully")

                // Refresh household list and navigate back to the household list screen
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                // Log the error for debugging
                Log.e(TAG, "Failed to leave household", e)

                // Update UI state with the error message
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to leave household"
                )
            }
        }
    }

    // Deletes the selected household
    // Backend rules should allow only admins to do this
    fun deleteHousehold(household: Household) {
        viewModelScope.launch {
            // Set action state to loading while the request is being processed
            _actionState.value = HouseholdActionState.Loading

            try {
                // Call the database RPC to delete the household
                client.postgrest.rpc(
                    "delete_household",
                    DeleteHouseholdParams(
                        householdId = household.household_id
                    )
                )

                // Update UI state on success
                _actionState.value =
                    HouseholdActionState.Success("Household deleted successfully")

                // Refresh household list and navigate back to the household list screen
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                // Log the error for debugging
                Log.e(TAG, "Failed to delete household", e)

                // Update UI state with the error message
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to delete household"
                )
            }
        }
    }
}