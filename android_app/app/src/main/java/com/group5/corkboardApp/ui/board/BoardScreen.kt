package com.group5.corkboardApp.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(modifier: Modifier = Modifier) {
    val viewModel: BoardViewModel = viewModel()
    val householdLoadState by viewModel.householdLoadState.collectAsState()
    val createPostState by viewModel.createPostState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("note") }
    var pointValue by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }
    var selectedHouseholdId by remember { mutableStateOf<String?>(null) }
    var householdDropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val households = (householdLoadState as? BoardViewModel.HouseholdLoadState.Success)?.households ?: emptyList()
    val selectedHousehold = households.find { it.household_id == selectedHouseholdId }

    // Pre-select first household once loaded
    LaunchedEffect(householdLoadState) {
        if (householdLoadState is BoardViewModel.HouseholdLoadState.Success && selectedHouseholdId == null) {
            selectedHouseholdId = households.firstOrNull()?.household_id
        }
    }

    LaunchedEffect(createPostState) {
        when (val state = createPostState) {
            is BoardViewModel.CreatePostState.Success -> {
                showCreateDialog = false
                title = ""
                body = ""
                selectedType = "note"
                pointValue = ""
                dueAt = ""
                errorText = null
                viewModel.resetCreateState()
            }
            is BoardViewModel.CreatePostState.Error -> {
                errorText = state.message
            }
            else -> {}
        }
    }

    fun resetDialogState() {
        showCreateDialog = false
        title = ""
        body = ""
        selectedType = "note"
        pointValue = ""
        dueAt = ""
        errorText = null
        viewModel.resetCreateState()
    }

    val isLoading = createPostState is BoardViewModel.CreatePostState.Loading

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Board",
            modifier = Modifier.align(Alignment.Center)
        )

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create post")
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) resetDialogState() },
            title = { Text("Create Post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "note",
                            onClick = { selectedType = "note" },
                            label = { Text("Note") },
                            enabled = !isLoading
                        )
                        FilterChip(
                            selected = selectedType == "chore",
                            onClick = { selectedType = "chore" },
                            label = { Text("Chore") },
                            enabled = !isLoading
                        )
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Body") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    if (selectedType == "chore") {
                        OutlinedTextField(
                            value = pointValue,
                            onValueChange = { pointValue = it },
                            label = { Text("Point Value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = dueAt,
                            onValueChange = { dueAt = it },
                            label = { Text("Due Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )
                    }

                    // Household dropdown
                    ExposedDropdownMenuBox(
                        expanded = householdDropdownExpanded,
                        onExpandedChange = { if (!isLoading) householdDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedHousehold?.household_name ?: "Select household",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Household") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(householdDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            enabled = !isLoading
                        )
                        ExposedDropdownMenu(
                            expanded = householdDropdownExpanded,
                            onDismissRequest = { householdDropdownExpanded = false }
                        ) {
                            households.forEach { household ->
                                DropdownMenuItem(
                                    text = { Text(household.household_name) },
                                    onClick = {
                                        selectedHouseholdId = household.household_id
                                        householdDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    errorText?.let {
                        Text(text = it, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hid = selectedHouseholdId
                        if (title.isBlank()) {
                            errorText = "Please enter a title"
                        } else if (hid == null) {
                            errorText = "Please select a household"
                        } else {
                            viewModel.createPost(
                                title = title.trim(),
                                body = body.trim(),
                                type = selectedType,
                                householdId = hid,
                                pointValue = pointValue.toIntOrNull(),
                                dueAt = dueAt.ifBlank { null }
                            )
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator() else Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { resetDialogState() },
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
