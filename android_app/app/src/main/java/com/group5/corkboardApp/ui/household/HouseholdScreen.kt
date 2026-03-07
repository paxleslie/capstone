package com.group5.corkboardApp.ui.household

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HouseholdScreen(modifier: Modifier = Modifier) {
    val viewModel: HouseholdViewModel = viewModel()
    val createState by viewModel.createState.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var householdName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(createState) {
        when (val state = createState) {
            is HouseholdViewModel.HouseholdCreateState.Success -> {
                showDialog = false
                householdName = ""
                errorText = null
                viewModel.resetCreateState()
            }
            is HouseholdViewModel.HouseholdCreateState.Error -> {
                errorText = state.message
            }
            else -> {}
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showDialog = true }) {
            Text(text = "Create Household")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (createState !is HouseholdViewModel.HouseholdCreateState.Loading) {
                    showDialog = false
                    householdName = ""
                    errorText = null
                    viewModel.resetCreateState()
                }
            },
            title = { Text("Create Household") },
            text = {
                Column {
                    OutlinedTextField(
                        value = householdName,
                        onValueChange = { householdName = it },
                        label = { Text("Household Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                    )
                    errorText?.let {
                        Text(text = it, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (householdName.isBlank()) {
                            errorText = "Please enter a household name"
                        } else {
                            viewModel.createHousehold(householdName.trim())
                        }
                    },
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                ) {
                    if (createState is HouseholdViewModel.HouseholdCreateState.Loading) {
                        CircularProgressIndicator()
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        householdName = ""
                        errorText = null
                        viewModel.resetCreateState()
                    },
                    enabled = createState !is HouseholdViewModel.HouseholdCreateState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}