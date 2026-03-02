package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.ui.household.Household
import com.group5.corkboardApp.ui.household.HouseholdMember
import com.group5.corkboardApp.ui.household.HouseholdUiState
import com.group5.corkboardApp.ui.household.HouseholdViewModel
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun UserProfileScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val householdViewModel: HouseholdViewModel = viewModel()
    val uiState by householdViewModel.uiState.collectAsState()
    
    val user = SupabaseClient.client.auth.currentUserOrNull()
    val fullName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content ?: "No full name"
    val email = user?.email ?: "No email"

    var showHouseholdList by remember { mutableStateOf(false) }
    var selectedHousehold by remember { mutableStateOf<Household?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!showHouseholdList && selectedHousehold == null) {
            // Profile Info View
            Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Name: $fullName")
            Text(text = "Email: $email")
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = { showHouseholdList = true }) {
                Text("Manage Households")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { scope.launch { SupabaseClient.client.auth.signOut() } }) {
                Text("Log Out")
            }
        } else if (showHouseholdList && selectedHousehold == null) {
            // Household List View
            Text(text = "Your Households", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            when (val state = uiState) {
                is HouseholdUiState.Loading -> CircularProgressIndicator()
                is HouseholdUiState.Error -> Text(text = state.message, color = Color.Red)
                is HouseholdUiState.Success -> {
                    if (state.households.isEmpty()) {
                        Text("You are not in any households.")
                    } else {
                        LazyColumn {
                            items(state.households) { household ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    onClick = { selectedHousehold = household }
                                ) {
                                    Text(text = household.household_name, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showHouseholdList = false }) { Text("Back to Profile") }
        } else if (selectedHousehold != null) {
            // Detailed Household View
            HouseholdDetailsView(
                household = selectedHousehold!!,
                viewModel = householdViewModel,
                onBack = { selectedHousehold = null }
            )
        }
    }
}

@Composable
fun HouseholdDetailsView(household: Household, viewModel: HouseholdViewModel, onBack: () -> Unit) {
    var members by remember { mutableStateOf<List<HouseholdMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var emailToAdd by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    
    val currentUser = SupabaseClient.client.auth.currentUserOrNull()
    
    // Find the current user's membership in this list
    val myMemberRecord = members.find { it.user_id == currentUser?.id }
    val myRole = myMemberRecord?.role ?: "Loading..."
    
    // Admin check: either explicitly 'admin' role OR the owner of the household
    val isAdmin = myRole.equals("admin", ignoreCase = true) || 
                  (myMemberRecord != null && myMemberRecord.member_id == household.owner_member_id)

    // Helper to refresh members
    val refreshMembers = {
        isLoading = true
        viewModel.viewModelScope.launch {
            members = viewModel.getMembers(household.household_id)
            isLoading = false
        }
    }

    LaunchedEffect(household.household_id) {
        members = viewModel.getMembers(household.household_id)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = household.household_name, style = MaterialTheme.typography.headlineMedium)
        Text(text = "Your Role: $myRole", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Members", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            if (!isLoading) {
               TextButton(onClick = { refreshMembers() }) { Text("Refresh") }
            }
        }
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(members) { member ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Accessing display_name through the profiles object
                        val profile = member.profiles
                        val nameToShow = profile?.display_name ?: profile?.full_name ?: profile?.email ?: "User (${member.user_id.take(8)}...)"
                        Text(text = nameToShow)
                        Text(text = "Role: ${member.role}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }

        // Show add member section if admin OR if members haven't loaded yet (to avoid flickering)
        if (isAdmin || isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = emailToAdd,
                onValueChange = { emailToAdd = it },
                label = { Text("Add member by email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            Button(
                onClick = {
                    if (emailToAdd.isNotBlank()) {
                        viewModel.addMemberByEmail(household.household_id, emailToAdd.trim()) { success, msg ->
                            statusMessage = success to msg
                            if (success) {
                                emailToAdd = ""
                                refreshMembers() 
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !isLoading
            ) {
                Text("Add Member")
            }
        }

        statusMessage?.let { (success, msg) ->
            Text(
                text = msg, 
                color = if (success) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to List")
        }
    }
}
