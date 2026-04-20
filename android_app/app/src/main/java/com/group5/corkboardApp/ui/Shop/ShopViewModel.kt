package com.group5.corkboardApp.ui.Shop

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
            id = "green_note_placeholder", // Will find real ID during purchase
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

    fun loadShopData() {
        val hid = selectedHouseholdId.value ?: return
        viewModelScope.launch {
            if (_shopState.value !is ShopState.Success) {
                _shopState.value = ShopState.Loading
            }
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val member = memberships.find { it.household_id == hid } ?: throw Exception("Not a member")

                // Get items from DB
                val dbItems = try { ShopRepository.getShopItems(hid) } catch (e: Exception) { emptyList() }
                
                // Prioritize DB items to get real IDs, fallback to universal defaults
                val finalItems = (dbItems + universalItems).distinctBy { it.name }

                val owned = try { ShopRepository.getOwnedItems(member.member_id!!, hid) } catch (e: Exception) { emptyList() }
                
                _shopState.value = ShopState.Success(
                    items = finalItems,
                    ownedItemIds = owned,
                    currentPoints = member.total_points ?: 0
                )
            } catch (e: Exception) {
                _shopState.value = ShopState.Error(e.localizedMessage ?: "Failed to load shop")
            }
        }
    }

    fun buyItem(item: ShopItem) {
        val hid = selectedHouseholdId.value ?: return
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Loading
            try {
                val userId = AuthRepository.currentUser()?.id ?: throw Exception("Not logged in")
                val memberships = HouseholdRepository.getUserMemberships(userId)
                val member = memberships.find { it.household_id == hid } ?: throw Exception("Not a member")
                
                if ((member.total_points ?: 0) < item.price) {
                    throw Exception("Not enough points!")
                }

                var itemIdToBuy = item.id
                
                // If the ID is a placeholder, find the real one in your DB
                if (item.id.contains("placeholder")) {
                    val dbItems = ShopRepository.getShopItems(hid)
                    val realItem = dbItems.find { it.name == item.name }
                    if (realItem != null) {
                        itemIdToBuy = realItem.id
                    } else {
                        throw Exception("Item '${item.name}' not found in database. Please add it to Supabase first.")
                    }
                }

                ShopRepository.buyItem(member.member_id!!, hid, itemIdToBuy, item.price)
                
                _purchaseState.value = PurchaseState.Success("Purchased ${item.name}!")
                loadShopData() 
            } catch (e: Exception) {
                _purchaseState.value = PurchaseState.Error(e.localizedMessage ?: "Purchase failed")
            }
        }
    }

    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
}
