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

    // UNIVERSAL SHOP ITEMS
    private val universalItems = listOf(
        ShopItem(
            id = "d82b1b39-3946-43e1-8fa0-2d5e938dc009", // Real ID from your screenshot
            name = "Red Note",
            price = 50,
            type = "color",
            value = "#FF5252"
        ),
        ShopItem(
            id = "green_note_placeholder", 
            name = "Green Note",
            price = 50,
            type = "color",
            value = "#AED581"
        )
    )

    init {
        loadHouseholds()
        observeSelectedHousehold()
    }

    private fun observeSelectedHousehold() {
        viewModelScope.launch {
            selectedHouseholdId.collectLatest { id ->
                if (id != null) {
                    loadShopData()
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
                val userHouseholds = HouseholdRepository.getUserHouseholds(userId)
                _households.value = userHouseholds
                
                if (SessionManager.selectedHouseholdId.value == null && userHouseholds.isNotEmpty()) {
                    selectHousehold(userHouseholds.first().household_id)
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
            val dbItems = try { ShopRepository.getShopItems(hid) } catch (e: Exception) { emptyList() }
            
            // 2. Merge with defaults, prioritizing DB items to get real IDs
            val finalItems = (dbItems + universalItems).distinctBy { it.name }

            // 3. Get currently owned items for this member
            val ownedRewardIds = try { ShopRepository.getOwnedItems(member.member_id!!, hid) } catch (e: Exception) { emptyList() }
            
            Log.d("ShopViewModel", "Found ${ownedRewardIds.size} owned reward IDs in DB for member ${member.member_id}")

            // 4. Map ownership by Name to handle potential ID inconsistencies
            val allPossibleItems = dbItems + universalItems
            val ownedNames = allPossibleItems.filter { it.id in ownedRewardIds }.map { it.name }.toSet()
            
            Log.d("ShopViewModel", "Owned item names: $ownedNames")

            // 5. Mark items as owned if ID matches OR Name matches an owned item
            val displayOwnedIds = finalItems.filter { item ->
                item.id in ownedRewardIds || item.name in ownedNames
            }.map { it.id }

            _shopState.value = ShopState.Success(
                items = finalItems,
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
                // Fetch fresh data to ensure we aren't buying twice
                val freshOwnedIds = ShopRepository.getOwnedItems(member.member_id!!, hid)
                val dbItems = ShopRepository.getShopItems(hid)
                val ownedNames = (dbItems + universalItems).filter { it.id in freshOwnedIds }.map { it.name }
                
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
                
                if (realItem == null && item.id.contains("placeholder")) {
                    throw Exception("Item '${item.name}' not found in database.")
                }

                // 3. Perform purchase
                ShopRepository.buyItem(member.member_id!!, hid, itemIdToBuy, item.price)
                
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
