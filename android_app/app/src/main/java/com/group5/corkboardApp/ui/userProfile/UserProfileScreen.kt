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
import com.group5.corkboardApp.ui.household.HouseholdViewModel
import com.group5.corkboardApp.util.SupabaseClient
import kotlinx.coroutines.launch

@Composable
fun UserProfileScreen(modifier: Modifier = Modifier) {
    // the scope is the viewModel
    val scope = rememberCoroutineScope()

    // having both the profileView model and the householdView model is fine
    // but userprofile should be a viewmodel
    val householdViewModel: HouseholdViewModel = viewModel()
    val householdUiState by householdViewModel.uiState.collectAsState()

    val profileViewModel : UserProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val navState by profileViewModel.navState.collectAsState()



    // should be in the view model, not the UI
//    val user = SupabaseClient.client.auth.currentUserOrNull()
//    val fullName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content ?: "No full name"
//    val email = user?.email ?: "No email"

    // needs to be in the view model, will reset if the screen rotates
//    var showHouseholdList by remember { mutableStateOf(false) }
//    var selectedHousehold by remember { mutableStateOf<Household?>(null) }

    // we tell the viewmodel to get the userinfo
    // in LaunchedEffect so that it only runs once
    LaunchedEffect(Unit) {
        profileViewModel.getUserInfo()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val navState = navState) {
            // explorer view - no household selected
            is UserProfileViewModel.NavState.Profile -> {
                Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // handle the different state of profileUI
                // But this will show the user profile
                when (val pState = profileUiState) {
                    // loading
                    is UserProfileViewModel.ProfileState.Loading -> {
                        CircularProgressIndicator()
                    }
                    //error
                    is UserProfileViewModel.ProfileState.Error -> {
                        Text(text = pState.message, color = Color.Red)
                        Button (onClick = { profileViewModel.getUserInfo() }) {
                            Text("Retry")
                        }
                    }
                    // success, we got the user info
                    is UserProfileViewModel.ProfileState.Success -> {
                        Text(text = "Name: ${pState.fullName}")
                        Text(text = "Email: ${pState.email}")

                        Spacer(modifier = Modifier.height(24.dp))
                        // button are inside success scope, won't appear otherwise
                        Button(onClick = { profileViewModel.navToListHouseholds() }) {
                            Text("Manage Households")
                        }

                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {profileViewModel.signOut()}) {
                    Text("Log Out")
                }

            }

            // List households view
            is UserProfileViewModel.NavState.List -> {
                Text(text = "Your Households", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // handle household UI states
                when (val hState = householdUiState) {
                    is HouseholdViewModel.ListHouseholdState.Loading -> CircularProgressIndicator()
                    is HouseholdViewModel.ListHouseholdState.Error -> Text(text = hState.message, color = Color.Red)
                    is HouseholdViewModel.ListHouseholdState.Success -> {
                        if (hState.isEmpty) {
                            Text("You are not in any households.")
                        } else {
                            LazyColumn {
                                items(hState.households.size) { household ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        onClick = { selectedHousehold = household }
                                    ) {
                                        Text(
                                            text = household.household_name,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // should be using the viewmodel
                TextButton(onClick = { showHouseholdList = false }) { Text("Back to Profile") }
            } else if (selectedHousehold != null) {
                // Detailed Household View
                HouseholdDetailsView(
                    household = selectedHousehold!!,
                    viewModel = householdViewModel,
                    onBack = { selectedHousehold = null }
                )
            }
            else -> {}
        }
    }
}

@Composable
fun HouseholdDetailsView(household: Household, viewModel: HouseholdViewModel, onBack: () -> Unit) {
    // this all should be in a viewmodel - householdViewmodel probably has this now
    var members by remember { mutableStateOf<List<HouseholdMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var emailToAdd by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // this wont survive a rotation
    val currentUser = SupabaseClient.client.auth.currentUserOrNull()
    
    // Find the current user's membership in this list
    // viewmodel logic
    val myMemberRecord = members.find { it.user_id == currentUser?.id }
    val myRole = myMemberRecord?.role ?: "Loading..."
    
    // Admin check: either explicitly 'admin' role OR the owner of the household
    // WHATS THE POINT OF HAVING A ROLE IF WE ALSO HAVE AN OWNER COL IN HOUSEHOLDS
    val isAdmin = myRole.equals("admin", ignoreCase = true) || 
                  (myMemberRecord != null && myMemberRecord.member_id == household.owner_member_id)

    // Helper to refresh members
    // we don't need to refresh - we're suppose to rely on state flows
    val refreshMembers = {
        isLoading = true
        viewModel.viewModelScope.launch {
            members = viewModel.getMembers(household.household_id)
            isLoading = false
        }
    }

    // should be relying on state flows
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
                // we shouldn't need a refresh button - symptom of not relying on state flows
               TextButton(onClick = { refreshMembers() }) { Text("Refresh") }
            }
        }
        // good, but should be using the state of the viewmodel, not a remember
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(members) { member ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Accessing display_name through the profiles object
                        // member should be accessed THROUGH the viewmodel state
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
        // this state should be in the viewModel - we can easily set isAdmin default to false,
        // so that we never get the flickering
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
