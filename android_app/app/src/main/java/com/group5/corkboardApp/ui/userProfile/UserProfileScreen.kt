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
                        Text(text = "Name: ${pState.fullName}")
                        Text(text = "Email: ${pState.email}")

                        Spacer(modifier = Modifier.height(24.dp))
                        // button are inside success scope, won't appear otherwise
                        Button(onClick = { householdViewModel.navToListHouseholds() }) {
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

                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(text = dState.household.household_name, style = MaterialTheme.typography.headlineMedium)
                            // list out user's member role
                            Text(text = "Your Role: ${currentMemberState?.role}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))

                            // admin panel for adding new users
                            if (currentMemberState?.role?.equals("admin", ignoreCase = true) == true) {
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

                            // list current members of this houshold
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(
                                    items = dState.members,
                                    key = { it.member_id }
                                ) { member ->
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        val profile = member.profiles
                                        val nameToShow = profile?.display_name ?: profile?.full_name ?: profile?.email ?: "User (${member.user_id.take(8)}...)"
                                        Text(text = nameToShow)
                                        Text(text = "Role: ${member.role}", style = MaterialTheme.typography.bodySmall)
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}