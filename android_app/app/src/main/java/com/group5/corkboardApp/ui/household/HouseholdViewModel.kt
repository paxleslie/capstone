package com.group5.corkboardApp.ui.household

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.HouseholdMember
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

        data class Error(
            val message: String,
            val canRetry: Boolean = true
        ) : HouseholdListState()
    }

    sealed class HouseholdDetailState {
        data object Idle : HouseholdDetailState()
        data object Loading : HouseholdDetailState()

        data class Success(
            val household: Household,
            val members: List<HouseholdMember> = emptyList()
        ) : HouseholdDetailState() {
            val isEmpty get() = members.isEmpty()
        }

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

    private val TAG = "HouseholdViewModel"

    private val _householdListState =
        MutableStateFlow<HouseholdListState>(HouseholdListState.Loading)
    val householdListState: StateFlow<HouseholdListState> = _householdListState

    private val _createState =
        MutableStateFlow<HouseholdCreateState>(HouseholdCreateState.Idle)
    val createState: StateFlow<HouseholdCreateState> = _createState

    private val _detailState =
        MutableStateFlow<HouseholdDetailState>(HouseholdDetailState.Idle)
    val detailState: StateFlow<HouseholdDetailState> = _detailState

    private val _actionState =
        MutableStateFlow<HouseholdActionState>(HouseholdActionState.Idle)
    val actionState: StateFlow<HouseholdActionState> = _actionState

    private val _addMemberState =
        MutableStateFlow<MemberAddState>(MemberAddState.Idle)
    val addMemberState: StateFlow<MemberAddState> = _addMemberState

    private val _navState = MutableStateFlow<NavState>(NavState.Idle)
    val navState: StateFlow<NavState> = _navState

    val currentUserMember: StateFlow<HouseholdMember?> = _detailState.map { state ->
        if (state is HouseholdDetailState.Success) {
            val currentUserId = AuthRepository.currentUser()?.id
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
                val userId = AuthRepository.currentUser()?.id
                    ?: throw Exception("User not authenticated")

                HouseholdRepository.createHousehold(name, userId)

                Log.d(TAG, "Created household: $name")
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
            try {
                val userId = AuthRepository.currentUser()?.id ?: run {
                    _householdListState.value = HouseholdListState.Error("User not authenticated")
                    return@launch
                }

                val households = HouseholdRepository.getUserHouseholds(userId)
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
                val household = HouseholdRepository.getHouseholdDetails(householdId)

                if (household != null) {
                    val members = HouseholdRepository.getMembers(householdId)
                    _detailState.value = HouseholdDetailState.Success(household, members)
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

    fun resetActionState() {
        _actionState.value = HouseholdActionState.Idle
    }

    fun addMemberByEmail(household: Household, email: String) {
        viewModelScope.launch {
            try {
                HouseholdRepository.addMemberByEmail(household.household_id, email)
                _addMemberState.value = MemberAddState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add member", e)
                _addMemberState.value =
                    MemberAddState.Error(e.localizedMessage ?: "Failed to add member")
            }
        }
    }

    fun removeMember(household: Household, targetUserId: String) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                HouseholdRepository.removeMember(household.household_id, targetUserId)
                _actionState.value = HouseholdActionState.Success("Member removed successfully")
                getHouseholdDetails(household.household_id)
                fetchHouseholds()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove member", e)
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to remove member"
                )
            }
        }
    }

    fun leaveHousehold(household: Household, memberId: String) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                HouseholdRepository.removeMember(household.household_id, memberId)
                _actionState.value = HouseholdActionState.Success("Left household successfully")
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave household", e)
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to leave household"
                )
            }
        }
    }

    fun deleteHousehold(household: Household) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                HouseholdRepository.deleteHousehold(household.household_id)
                _actionState.value = HouseholdActionState.Success("Household deleted successfully")
                fetchHouseholds()
                _navState.value = NavState.List
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete household", e)
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to delete household"
                )
            }
        }
    }
}
