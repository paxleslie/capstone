package com.group5.corkboardApp.ui.Shop

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.model.ShopItem
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.data.repository.ShopRepository
import com.group5.corkboardApp.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ShopViewModel : ViewModel() {

    sealed class ShopState {
        data object Loading : ShopState()
        data object NoHousehold : ShopState()
        data class Success(
            val items: List<ShopItem>,
            val ownedItemIds: List<String>,
            val currentPoints: Int
        ) : ShopState()
        data class Error(val message: String) : ShopState()
    }

    sealed class PurchaseState {
        data object Idle : PurchaseState()
        data object Loading : PurchaseState()
        data class Success(val message: String) : PurchaseState()
        data class Error(val message: String) : PurchaseState()
    }

    private val _households = MutableStateFlow<List<Household>>(emptyList())
    val households: StateFlow<List<Household>> = _households

    val selectedHouseholdId: StateFlow<String?> = SessionManager.selectedHouseholdId

    private val _shopState = MutableStateFlow<ShopState>(ShopState.Loading)
    val shopState: StateFlow<ShopState> = _shopState

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState

    init {
        loadHouseholds()
        observeSelectedHousehold()
    }

    private fun observeSelectedHousehold() {
        viewModelScope.launch {
            selectedHouseholdId.collectLatest { id ->
                if (id != null) {
                    loadShopData()
                } else {
                    _shopState.value = ShopState.NoHousehold
                    _households.value = emptyList()
                }
            }
        }
    }

    fun selectHousehold(householdId: String) {
        SessionManager.selectHousehold(householdId)
    }

    fun loadHouseholds() {
        viewModelScope.launch {
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")
                
                // Fetch households
                val unsortedHouseholds = HouseholdRepository.getUserHouseholds(userId)
                
                // Lock the order by created_at (oldest first / leftmost)
                val sortedHouseholds = unsortedHouseholds.sortedBy { it.created_at }

                _households.value = sortedHouseholds
                
                // Handle selection sync
                val currentId = SessionManager.selectedHouseholdId.value
                if (currentId != null && sortedHouseholds.none { it.household_id == currentId }) {
                    if (sortedHouseholds.isNotEmpty()) {
                        selectHousehold(sortedHouseholds.first().household_id)
                    } else {
                        SessionManager.selectHousehold(null)
                    }
                } else if (currentId == null && sortedHouseholds.isNotEmpty()) {
                    selectHousehold(sortedHouseholds.first().household_id)
                }
            } catch (e: Exception) {
                _shopState.value = ShopState.Error(e.localizedMessage ?: "Failed to load households")
            }
        }
    }

    private suspend fun refreshShopDataInternal() {
        val hid = selectedHouseholdId.value ?: return
        try {
            val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")
            val memberships = HouseholdRepository.getUserMemberships(userId)
            val member = memberships.find { it.household_id == hid } ?: throw Exception("Not a member")

            // 1. Get available items from rewards table
            val dbItems = try { ShopRepository.getShopItems(hid) } catch (_: Exception) { emptyList() }

            // 2. Get currently owned items for this member
            val ownedRewardIds = try { ShopRepository.getOwnedItems(member.member_id, hid) } catch (_: Exception) { emptyList() }

            Log.d("ShopViewModel", "Found ${ownedRewardIds.size} owned reward IDs in DB for member ${member.member_id}")

            // 3. Map ownership by Name to handle potential ID inconsistencies
            val ownedNames = dbItems.filter { it.id in ownedRewardIds }.map { it.name }.toSet()

            Log.d("ShopViewModel", "Owned item names: $ownedNames")

            // 4. Mark items as owned if ID matches OR Name matches an owned item
            val displayOwnedIds = dbItems.filter { item ->
                item.id in ownedRewardIds || item.name in ownedNames
            }.map { it.id }

            _shopState.value = ShopState.Success(
                items = dbItems,
                ownedItemIds = displayOwnedIds,
                currentPoints = member.total_points ?: 0
            )
        } catch (e: Exception) {
            Log.e("ShopViewModel", "Error refreshing shop data", e)
            _shopState.value = ShopState.Error(e.localizedMessage ?: "Failed to load shop data")
        }
    }

    fun loadShopData() {
        viewModelScope.launch {
            if (_shopState.value !is ShopState.Success) {
                _shopState.value = ShopState.Loading
            }
            refreshShopDataInternal()
        }
    }

    fun buyItem(item: ShopItem) {
        val hid = selectedHouseholdId.value ?: return
        if (_purchaseState.value is PurchaseState.Loading) return 

        viewModelScope.launch {
            // 1. Check current local state first (UI level)
            val currentState = _shopState.value
            if (currentState is ShopState.Success) {
                val isAlreadyOwned = currentState.ownedItemIds.contains(item.id) || 
                                     currentState.items.any { it.name == item.name && it.id in currentState.ownedItemIds }
                
                if (isAlreadyOwned) {
                    _purchaseState.value = PurchaseState.Error("Already owned")
                    return@launch
                }
            }

            _purchaseState.value = PurchaseState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val member = memberships.find { it.household_id == hid } ?: throw Exception("Not a member")
                
                // 2. Database-level safety check (Name-based)
                // Freshly fetch data to ensure we aren't buying twice
                val freshOwnedIds = ShopRepository.getOwnedItems(member.member_id, hid)
                val dbItems = ShopRepository.getShopItems(hid)
                val ownedNames = dbItems.filter { it.id in freshOwnedIds }.map { it.name }
                
                if (ownedNames.contains(item.name)) {
                    _purchaseState.value = PurchaseState.Error("Already owned")
                    refreshShopDataInternal() // Update UI to show it as owned
                    return@launch
                }

                if ((member.total_points ?: 0) < item.price) {
                    throw Exception("Not enough points!")
                }

                // Resolve real UUID from DB
                val realItem = dbItems.find { it.name == item.name }
                val itemIdToBuy = realItem?.id ?: item.id

                // 3. Perform purchase
                ShopRepository.buyItem(member.member_id, hid, itemIdToBuy, item.price)
                
                // 4. Force a refresh and wait for it to complete
                refreshShopDataInternal()
                
                _purchaseState.value = PurchaseState.Success("Purchased ${item.name}!")
            } catch (e: Exception) {
                Log.e("ShopViewModel", "Purchase failed", e)
                
                // Catch common database errors for duplicate rows and show a friendly message
                val msg = e.message ?: ""
                val friendlyError = if (msg.contains("unique", ignoreCase = true) || 
                                       msg.contains("already exists", ignoreCase = true) ||
                                       msg.contains("duplicate", ignoreCase = true)) {
                    "Already owned"
                } else {
                    e.localizedMessage ?: "Purchase failed"
                }
                
                _purchaseState.value = PurchaseState.Error(friendlyError)
                
                // If it was a duplicate error, refresh data to sync UI
                if (friendlyError == "Already owned") {
                    refreshShopDataInternal()
                }
            }
        }
    }

    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
}
