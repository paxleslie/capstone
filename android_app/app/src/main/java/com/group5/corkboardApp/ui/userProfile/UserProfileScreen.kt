package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.data.model.HouseholdMember
import com.group5.corkboardApp.ui.household.HouseholdViewModel
import com.group5.corkboardApp.ui.theme.HandDrawnBlack

// Reusable modifier for the hand-drawn look
fun Modifier.handDrawnBorder() = this
    .border(width = 2.dp, color = HandDrawnBlack, shape = RoundedCornerShape(0.dp))

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
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamedHouseholdName by remember { mutableStateOf("") }

    // MVVM Point editing state
    val showEditPointsDialog by householdViewModel.showEditPointsDialog.collectAsState()
    val editPointsMember by householdViewModel.editPointsMember.collectAsState()
    val editPointsValue by householdViewModel.editPointsValue.collectAsState()
    
    val focusManager = LocalFocusManager.current

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
    
    LaunchedEffect(actionState) {
        if (actionState is HouseholdViewModel.HouseholdActionState.Success) {
            if (showRenameDialog) {
                showRenameDialog = false
                renamedHouseholdName = ""
            }
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
                            Button (
                                onClick = { profileViewModel.getUserInfo() },
                                modifier = Modifier.handDrawnBorder(),
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                        is UserProfileViewModel.ProfileState.Success -> {
                            Text(text = pState.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(text = pState.email, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { householdViewModel.navToListHouseholds() },
                                modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Text("Manage Households")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { isEditingAccount = true },
                                modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                shape = RoundedCornerShape(0.dp)
                            ) {
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
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
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
                                                .padding(vertical = 4.dp)
                                                .handDrawnBorder(),
                                            shape = RoundedCornerShape(0.dp),
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
                    Button(
                        onClick = { householdViewModel.navToIdle() },
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { 
                        Text("Back to Profile") 
                    }
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
                                Text(
                                    text = dState.household.household_name, 
                                    style = MaterialTheme.typography.headlineMedium,
                                    textDecoration = if (isAdmin) TextDecoration.Underline else TextDecoration.None,
                                    fontWeight = FontWeight.Bold,
                                    modifier = if (isAdmin) Modifier.clickable {
                                        renamedHouseholdName = dState.household.household_name
                                        showRenameDialog = true
                                    } else Modifier
                                )
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
                                        onValueChange = { if (it.length <= 50) emailToAdd = it },
                                        label = { Text("Add member by email") },
                                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                        enabled = addMemberState !is HouseholdViewModel.MemberAddState.Loading,
                                        singleLine = true,
                                        shape = RoundedCornerShape(0.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (emailToAdd.isNotBlank()) {
                                                    focusManager.clearFocus()
                                                    householdViewModel.addMemberByEmail(dState.household, emailToAdd.trim())
                                                }
                                            }
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (emailToAdd.isNotBlank()) {
                                                focusManager.clearFocus()
                                                householdViewModel.addMemberByEmail(dState.household, emailToAdd.trim())
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).handDrawnBorder(),
                                        shape = RoundedCornerShape(0.dp),
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
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).handDrawnBorder(),
                                            shape = RoundedCornerShape(0.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                val profile = member.profile
                                                val nameToShow = member.nickname?.takeIf { it.isNotBlank() }
                                    ?: profile?.display_name
                                    ?: profile?.name
                                    ?: "User (${member.user_id.take(8)}...)"

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = nameToShow,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "(${member.total_points ?: 0} pts)",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(text = "Role: ${member.role}", style = MaterialTheme.typography.bodySmall)

                                                if (isAdmin) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Button(
                                                            onClick = { householdViewModel.startEditingPoints(member) },
                                                            modifier = Modifier.weight(1f).handDrawnBorder(),
                                                            shape = RoundedCornerShape(0.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                                        ) {
                                                            Text("Edit Points", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                        
                                                        if (member.user_id != currentUserId) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Button(
                                                                onClick = { householdViewModel.removeMember(dState.household, member.member_id) },
                                                                modifier = Modifier.weight(1f).handDrawnBorder(),
                                                                shape = RoundedCornerShape(0.dp),
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                                                            ) {
                                                                Text("Remove User", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { householdViewModel.navToListHouseholds() },
                                    modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                    shape = RoundedCornerShape(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                                ) {
                                    Text("Back to Households")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isAdmin) {
                                    Button(
                                        onClick = { householdViewModel.deleteHousehold(dState.household) },
                                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                        shape = RoundedCornerShape(0.dp),
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
                                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                                        shape = RoundedCornerShape(0.dp),
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
                        onValueChange = { if (it.length <= 50) newHouseholdName = it },
                        label = { Text("Household Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newHouseholdName.isNotBlank()) {
                                    focusManager.clearFocus()
                                    householdViewModel.createHousehold(newHouseholdName.trim())
                                }
                            }
                        )
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
                            focusManager.clearFocus()
                            householdViewModel.createHousehold(newHouseholdName.trim())
                        }
                    },
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (createState is HouseholdViewModel.HouseholdCreateState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showCreateDialog = false
                        newHouseholdName = ""
                        createErrorText = null
                        householdViewModel.resetCreateState()
                    },
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showRenameDialog) {
        val dState = householdDetailState as? HouseholdViewModel.HouseholdDetailState.Success
        AlertDialog(
            onDismissRequest = {
                if (actionState !is HouseholdViewModel.HouseholdActionState.Loading) {
                    showRenameDialog = false
                }
            },
            title = { Text("Rename Household") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renamedHouseholdName,
                        onValueChange = { if (it.length <= 50) renamedHouseholdName = it },
                        label = { Text("New Household Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (renamedHouseholdName.isNotBlank() && dState != null) {
                                    focusManager.clearFocus()
                                    householdViewModel.updateHouseholdName(dState.household, renamedHouseholdName.trim())
                                }
                            }
                        )
                    )
                    if (actionState is HouseholdViewModel.HouseholdActionState.Error) {
                        Text(text = (actionState as HouseholdViewModel.HouseholdActionState.Error).message, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renamedHouseholdName.isNotBlank() && dState != null) {
                            focusManager.clearFocus()
                            householdViewModel.updateHouseholdName(dState.household, renamedHouseholdName.trim())
                        }
                    },
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading && renamedHouseholdName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (actionState is HouseholdViewModel.HouseholdActionState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = { showRenameDialog = false },
                    enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditPointsDialog && editPointsMember != null) {
        val dState = householdDetailState as? HouseholdViewModel.HouseholdDetailState.Success
        AlertDialog(
            onDismissRequest = {
                if (actionState !is HouseholdViewModel.HouseholdActionState.Loading) {
                    householdViewModel.cancelEditingPoints()
                }
            },
            title = { Text("Edit Points for ${editPointsMember?.nickname ?: editPointsMember?.profile?.display_name ?: "Member"}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editPointsValue,
                        onValueChange = { householdViewModel.onEditPointsValueChange(it) },
                        label = { Text("Points") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading,
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (dState != null) {
                                    focusManager.clearFocus()
                                    householdViewModel.saveMemberPoints(dState.household.household_id)
                                }
                            }
                        )
                    )
                    if (actionState is HouseholdViewModel.HouseholdActionState.Error) {
                        Text(text = (actionState as HouseholdViewModel.HouseholdActionState.Error).message, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dState != null) {
                            focusManager.clearFocus()
                            householdViewModel.saveMemberPoints(dState.household.household_id)
                        }
                    },
                    enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (actionState is HouseholdViewModel.HouseholdActionState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = { householdViewModel.cancelEditingPoints() },
                    enabled = actionState !is HouseholdViewModel.HouseholdActionState.Loading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
