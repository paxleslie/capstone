package com.group5.corkboardApp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global session manager to keep track of the selected household across different screens.
 */
object SessionManager {
    private val _selectedHouseholdId = MutableStateFlow<String?>(null)
    val selectedHouseholdId: StateFlow<String?> = _selectedHouseholdId

    fun selectHousehold(id: String?) {
        _selectedHouseholdId.value = id
    }
}
