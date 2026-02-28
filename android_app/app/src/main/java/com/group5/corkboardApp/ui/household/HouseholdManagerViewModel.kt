package com.group5.corkboardApp.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateHouseholdParams(
    val p_household_id: String,
    val p_household_name: String,
    val p_member_id: String
)

sealed class CreateHouseholdState {
    data object Idle : CreateHouseholdState()
    data object Loading : CreateHouseholdState()
    data object Success : CreateHouseholdState()
    data class Error(val message: String) : CreateHouseholdState()
}

class HouseholdManagerViewModel : ViewModel() {

    private val client = SupabaseClient.client

    private val _createState = MutableStateFlow<CreateHouseholdState>(CreateHouseholdState.Idle)
    val createState: StateFlow<CreateHouseholdState> = _createState

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _createState.value = CreateHouseholdState.Loading
            try {
                val householdId = UUID.randomUUID().toString()
                val memberId = client.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not authenticated")

                client.postgrest.rpc(
                    "create_household",
                    CreateHouseholdParams(
                        p_household_id = householdId,
                        p_household_name = name,
                        p_member_id = memberId
                    )
                )

                _createState.value = CreateHouseholdState.Success
            } catch (e: Exception) {
                _createState.value = CreateHouseholdState.Error(e.localizedMessage ?: "Failed to create household")
            }
        }
    }

    fun resetState() {
        _createState.value = CreateHouseholdState.Idle
    }
}