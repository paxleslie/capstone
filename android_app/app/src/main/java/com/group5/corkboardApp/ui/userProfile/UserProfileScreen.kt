package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.ui.household.HouseholdViewModel

@Composable
fun UserProfileScreen(modifier: Modifier = Modifier) {
    val householdViewModel: HouseholdViewModel = viewModel()
    val householdListState by householdViewModel.householdListState.collectAsState()
    val householdDetailState by householdViewModel.detailState.collectAsState()
    val navState by householdViewModel.navState.collectAsState()
    val currentMemberState by householdViewModel.currentUserMember.collectAsState()
    val createState by householdViewModel.createState.collectAsState()

    val profileViewModel : UserProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()

    var isEditingAccount by remember { mutableStateOf(false) }
    val actionState by householdViewModel.actionState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newHouseholdName by remember { mutableStateOf("") }
    var createErrorText by remember { mutableStateOf<String?>(null) }

    val addMemberState by householdViewModel.addMemberState.collectAsState()
    var emailToAdd by remember { mutableStateOf("")}

    LaunchedEffect(Unit) {
        profileViewModel.getUserInfo()
    }

    LaunchedEffect(createState) {
        when (createState) {
            is HouseholdViewModel.HouseholdCreateState.Success -> {
                showCreateDialog = false
                newHouseholdName = ""
                createErrorText = null
                householdViewModel.resetCreateState()
            }
            is HouseholdViewModel.HouseholdCreateState.Error -> {
                createErrorText = (createState as HouseholdViewModel.HouseholdCreateState.Error).message
            }
            else -> {}
        }
    }

    LaunchedEffect(addMemberState) {
        if (addMemberState is HouseholdViewModel.MemberAddState.Success) {
            emailToAdd = ""
            householdViewModel.resetAddMemberState()
        }
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
            when (navState) {
                is HouseholdViewModel.NavState.Idle -> {
                    Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (val pState = profileUiState) {
                        is UserProfileViewModel.ProfileState.Loading -> {
                            CircularProgressIndicator()
                        }
                        is UserProfileViewModel.ProfileState.Error -> {
                            Text(text = pState.message, color = Color.Red)
                            Button (onClick = { profileViewModel.getUserInfo() }) {
                                Text("Retry")
                            }
                        }
                        is UserProfileViewModel.ProfileState.Success -> {
                            Text(text = pState.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(text = pState.email, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(24.dp))
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
                }

                is HouseholdViewModel.NavState.List -> {
                    Text(text = "Your Households", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create New Household")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                                        items = hState.households,
                                        key = { it.household_id }
                                    ) { household ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
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
                    TextButton(onClick = { householdViewModel.navToIdle() }) { Text("Back to Profile") }
                }
                is HouseholdViewModel.NavState.Detail -> {
                    when (val dState = householdDetailState) {
                        is HouseholdViewModel.HouseholdDetailState.Loading -> {
                            CircularProgressIndicator()
                        }
                        is HouseholdViewModel.HouseholdDetailState.Success -> {
                            val isAdmin = currentMemberState?.role?.equals("admin", ignoreCase = true) == true
                            val currentUserId = currentMemberState?.user_id

                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(text = dState.household.household_name, style = MaterialTheme.typography.headlineMedium)
                                Text(text = "Your Role: ${currentMemberState?.role}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))

                                when (val aState = actionState) {
                                    is HouseholdViewModel.HouseholdActionState.Error -> {
                                        Text(text = aState.message, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                    is HouseholdViewModel.HouseholdActionState.Success -> {
                                        Text(text = aState.message, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                    else -> {}
                                }

                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedTextField(
                                        value = emailToAdd,
                                        onValueChange = { emailToAdd = it },
                                        label = { Text("Add member by email") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = addMemberState !is HouseholdViewModel.MemberAddState.Loading
                                    )
                                    Button(
                                        onClick = {
                                            if (emailToAdd.isNotBlank()) {
                                                householdViewModel.addMemberByEmail(dState.household, emailToAdd.trim())
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        enabled = addMemberState !is HouseholdViewModel.MemberAddState.Loading && emailToAdd.isNotBlank()
                                    ) {
                                        if (addMemberState is HouseholdViewModel.MemberAddState.Loading) {
                                            CircularProgressIndicator(color = Color.White)
                                        } else {
                                            Text("Add Member")
                                        }
                                    }
                                }

                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(
                                        items = dState.members,
                                        key = { it.member_id }
                                    ) { member ->
                                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                            val profile = member.profile
                                            val nameToShow = member.nickname ?: profile?.display_name ?: profile?.name ?: "User (${member.user_id.take(8)}...)"

                                            Text(text = nameToShow, style = MaterialTheme.typography.bodyLarge)
                                            Text(text = "Role: ${member.role}", style = MaterialTheme.typography.bodySmall)

                                            // FIX: Pass member.member_id instead of user_id to match DB logic
                                            if (isAdmin && member.user_id != currentUserId) {
                                                Button(
                                                    onClick = { householdViewModel.removeMember(dState.household, member.member_id) },
                                                    modifier = Modifier.padding(top = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                                ) {
                                                    Text("Remove User")
                                                }
                                            }
                                            HorizontalDivider()
                                        }
                                    }
                                }

                                // Layout Fix: Reordered buttons and changed styles
                                Button(
                                    onClick = { householdViewModel.navToListHouseholds() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Households")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isAdmin) {
                                    Button(
                                        onClick = { householdViewModel.deleteHousehold(dState.household) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Red,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Delete Household")
                                    }
                                } else {
                                    Button(
                                        onClick = { householdViewModel.leaveHousehold(dState.household, currentMemberState?.member_id ?: "") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Red,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Leave Household")
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

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (createState !is HouseholdViewModel.HouseholdCreateState.Loading) {
                    showCreateDialog = false
                    newHouseholdName = ""
                    createErrorText = null
                    householdViewModel.resetCreateState()
                }
            },
            title = { Text("Create Household") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newHouseholdName,
                        onValueChange = { newHouseholdName = it },
                        label = { Text("Household Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                    )
                    createErrorText?.let {
                        Text(text = it, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newHouseholdName.isBlank()) {
                            createErrorText = "Please enter a household name"
                        } else {
                            householdViewModel.createHousehold(newHouseholdName.trim())
                        }
                    },
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                ) {
                    if (createState is HouseholdViewModel.HouseholdCreateState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newHouseholdName = ""
                        createErrorText = null
                        householdViewModel.resetCreateState()
                    },
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
