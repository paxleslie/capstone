@file:Suppress("PropertyName")

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

    // Points editing state (MVVM pattern)
    private val _showEditPointsDialog = MutableStateFlow(false)
    val showEditPointsDialog: StateFlow<Boolean> = _showEditPointsDialog

    private val _editPointsMember = MutableStateFlow<HouseholdMember?>(null)
    val editPointsMember: StateFlow<HouseholdMember?> = _editPointsMember

    private val _editPointsValue = MutableStateFlow("")
    val editPointsValue: StateFlow<String> = _editPointsValue

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
                HouseholdRepository.updateHouseholdName(household.household_id, newName)

                _actionState.value = HouseholdActionState.Success("Household name updated")

                // Refresh detail state
                val members = HouseholdRepository.getMembers(household.household_id)
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
                val userId = AuthRepository.currentUser()?.id ?: run {
                    _householdListState.value = HouseholdListState.Error("User not authenticated")
                    return@launch
                }
                val households = HouseholdRepository.getUserHouseholds(userId)
                _householdListState.value = HouseholdListState.Success(households)
            } catch (e: Exception) {
                _householdListState.value = HouseholdListState.Error(e.localizedMessage ?: "Failed to fetch households")
            }
        }
    }

    fun getHouseholdDetails(householdId: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _detailState.value = HouseholdDetailState.Loading
            }
            try {
                val household = HouseholdRepository.getHouseholdDetails(householdId)

                if (household != null) {
                    val members = HouseholdRepository.getMembers(householdId)
                    _detailState.value = HouseholdDetailState.Success(household, members)
                    if (showLoading) {
                        _navState.value = NavState.Detail
                    }
                } else if (showLoading) {
                    _detailState.value = HouseholdDetailState.Error("Household not found")
                }
            } catch (e: Exception) {
                if (showLoading) {
                    _detailState.value = HouseholdDetailState.Error(e.localizedMessage ?: "Failed to fetch details")
                }
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

    fun resetAddMemberState() {
        _addMemberState.value = MemberAddState.Idle
    }

    fun addMemberByEmail(household: Household, email: String) {
        viewModelScope.launch {
            _addMemberState.value = MemberAddState.Loading
            try {
                HouseholdRepository.addMemberByEmail(household.household_id, email)
                _addMemberState.value = MemberAddState.Success
                
                val members = HouseholdRepository.getMembers(household.household_id)
                _detailState.value = HouseholdDetailState.Success(household, members)

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

    fun startEditingPoints(member: HouseholdMember) {
        _editPointsMember.value = member
        _editPointsValue.value = (member.total_points ?: 0).toString()
        _showEditPointsDialog.value = true
    }

    fun onEditPointsValueChange(newValue: String) {
        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
            _editPointsValue.value = newValue
        }
    }

    fun cancelEditingPoints() {
        _showEditPointsDialog.value = false
        _editPointsMember.value = null
        _editPointsValue.value = ""
        resetActionState()
    }

    fun saveMemberPoints(householdId: String) {
        val member = _editPointsMember.value ?: return
        val points = _editPointsValue.value.toIntOrNull() ?: 0
        
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                Log.d(TAG, "Attempting to update points for member ${member.member_id} to $points")
                HouseholdRepository.updateMemberPoints(member.member_id, points)
                
                // 1. Manually update local state for immediate feedback
                val currentState = _detailState.value
                if (currentState is HouseholdDetailState.Success) {
                    val updatedMembers = currentState.members.map {
                        if (it.member_id == member.member_id) it.copy(total_points = points) else it
                    }
                    _detailState.value = currentState.copy(members = updatedMembers)
                }

                // 2. Clear dialog state
                _showEditPointsDialog.value = false
                _editPointsMember.value = null
                _editPointsValue.value = ""
                _actionState.value = HouseholdActionState.Success("Points updated successfully")
                
                // 3. Re-fetch from DB in background (no loading spinner) to sync final state
                getHouseholdDetails(householdId, showLoading = false)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update member points", e)
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to update points. Please try again."
                )
            }
        }
    }

    fun updateMemberPoints(householdId: String, memberId: String, totalPoints: Int) {
        viewModelScope.launch {
            _actionState.value = HouseholdActionState.Loading
            try {
                HouseholdRepository.updateMemberPoints(memberId, totalPoints)
                _actionState.value = HouseholdActionState.Success("Points updated successfully")
                getHouseholdDetails(householdId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update member points", e)
                _actionState.value = HouseholdActionState.Error(
                    e.localizedMessage ?: "Failed to update member points"
                )
            }
        }
    }
}
