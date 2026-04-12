package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.ui.household.HouseholdViewModel

@Composable
fun UserProfileScreen(modifier: Modifier = Modifier) {
    // the scope is the viewModel
    val scope = rememberCoroutineScope()

    // having both the profileView model and the householdView model is fine
    // but userprofile should be a viewmodel
    val householdViewModel: HouseholdViewModel = viewModel()
    val householdListState by householdViewModel.householdListState.collectAsState()
    val householdDetailState by householdViewModel.detailState.collectAsState()
    val navState by householdViewModel.navState.collectAsState()
    val currentMemberState by householdViewModel.currentUserMember.collectAsState()


    val profileViewModel : UserProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()

    // State for navigating to Account Settings
    var isEditingAccount by remember { mutableStateOf(false) }

    // Observe the state of remove, leave, and delete household actions
    val actionState by householdViewModel.actionState.collectAsState()

    // we tell the viewmodel to get the userinfo
    // in LaunchedEffect so that it only runs once
    LaunchedEffect(Unit) {
        profileViewModel.getUserInfo()
    }

    if (isEditingAccount) {
        AccountSettingsScreen(onBack = { isEditingAccount = false })
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val navStateValue = navState) {
                // profile view - no household selected
                is HouseholdViewModel.NavState.Idle -> {
                    Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    // handle the profile's state
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
                            Text(text = pState.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(text = pState.email, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(24.dp))
                            // button are inside success scope, won't appear otherwise
                            Button(onClick = { householdViewModel.navToListHouseholds() }) {
                                Text("Manage Households")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { isEditingAccount = true }) {
                                Text("Account Settings")
                            }
                        }
                        else -> {}
                    }
                    // Logout button moved to Account Settings
                }

                // List households view
                is HouseholdViewModel.NavState.List -> {
                    Text(text = "Your Households", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    // handle household UI states
                    when (val hState = householdListState) {
                        is HouseholdViewModel.HouseholdListState.Loading -> {
                            CircularProgressIndicator()
                        }

                        is HouseholdViewModel.HouseholdListState.Error -> {
                            Text(text = hState.message, color = Color.Red)
                        }

                        is HouseholdViewModel.HouseholdListState.Success -> {
                            if (hState.isEmpty) {
                                Text("You are not in any households.")
                            } else {
                                LazyColumn {
                                    items(
                                        items = hState.households, // each item is a Household object
                                        key = { it.household_id } // unique identifier for each item
                                    ) { household ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            // have the viewModel retrieve details of this household
                                            onClick = { householdViewModel.getHouseholdDetails(household.household_id) }
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
                        else -> {}
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Navigate back to just userProfile - which is not list nor detail, so idle
                    TextButton(onClick = {householdViewModel.navToIdle() }) { Text("Back to Profile") }
                }
                is HouseholdViewModel.NavState.Detail -> {
                    // dState holds the detail view of the selected household
                    when (val dState = householdDetailState) {
                        is HouseholdViewModel.HouseholdDetailState.Loading -> {
                            CircularProgressIndicator()
                        }

                        is HouseholdViewModel.HouseholdDetailState.Idle -> {}

                        is HouseholdViewModel.HouseholdDetailState.Success -> {
                            // variables
                            var emailToAdd by remember { mutableStateOf("")}
                            var isAdding by remember { mutableStateOf(false) }

                            // Check whether the current user is an admin of this household
                            val isAdmin = currentMemberState?.role?.equals("admin", ignoreCase = true) == true

                            // Store current user's id to prevent showing remove button on themselves
                            val currentUserId = currentMemberState?.user_id

                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(text = dState.household.household_name, style = MaterialTheme.typography.headlineMedium)
                                // list out user's member role
                                Text(text = "Your Role: ${currentMemberState?.role}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Show success or error messages for household actions
                                when (val aState = actionState) {
                                    is HouseholdViewModel.HouseholdActionState.Error -> {
                                        Text(
                                            text = aState.message,
                                            color = Color.Red,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    is HouseholdViewModel.HouseholdActionState.Success -> {
                                        Text(
                                            text = aState.message,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    else -> {}
                                }

                                // admin panel for adding new users
                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedTextField(
                                        value = emailToAdd,
                                        onValueChange = { emailToAdd = it },
                                        label = { Text("Add member by email") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            if (emailToAdd.isNotBlank()) {
                                                householdViewModel.addMemberByEmail(dState.household, emailToAdd.trim())
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        // protect button from being spammed
                                        enabled = !isAdding && emailToAdd.isNotBlank()
                                    ) {
                                        Text("Add Member")
                                    }
                                }

                                // List current members of this household
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(
                                        items = dState.members,
                                        key = { it.member_id }
                                    ) { member ->
                                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                            val profile = member.profile
                                            val nameToShow = profile?.name ?: profile?.email ?: "User (${member.user_id.take(8)}...)"

                                            Text(text = nameToShow)
                                            Text(
                                                text = "Role: ${member.role}",
                                                style = MaterialTheme.typography.bodySmall
                                            )

                                            // Only admins can remove other users from the household
                                            if (isAdmin && member.user_id != currentUserId) {
                                                Button(
                                                    onClick = {
                                                        householdViewModel.removeMember(
                                                            dState.household,
                                                            member.user_id
                                                        )
                                                    },
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Text("Remove User")
                                                }
                                            }

                                            HorizontalDivider()
                                        }
                                    }
                                }
                                // Admins can delete the household, while regular users can leave it
                                if (isAdmin) {
                                    Button(
                                        onClick = {
                                            householdViewModel.deleteHousehold(dState.household)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Delete Household")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            householdViewModel.leaveHousehold(dState.household, currentMemberState?.member_id ?: "")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Leave Household")
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Navigate back to the household list view
                                TextButton(
                                    onClick = { householdViewModel.navToListHouseholds() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Households")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
