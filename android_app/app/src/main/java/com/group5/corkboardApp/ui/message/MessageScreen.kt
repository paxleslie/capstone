package com.group5.corkboardApp.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.data.model.Household
import com.group5.corkboardApp.data.repository.AuthRepository
import com.group5.corkboardApp.data.repository.HouseholdRepository
import com.group5.corkboardApp.ui.theme.handDrawnBorder
import com.group5.corkboardApp.util.SessionManager

@Composable
fun MessageScreen(modifier: Modifier = Modifier) {
    val viewModel: MessageViewModel = viewModel()

    val messages by viewModel.messages.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedHouseholdId by SessionManager.selectedHouseholdId.collectAsState()

    var households by remember { mutableStateOf<List<Household>>(emptyList()) }
    var currentMemberId by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Load households for the top tab row
    LaunchedEffect(Unit) {
        val userId = AuthRepository.currentUser()?.id

        if (userId != null) {
            households = HouseholdRepository.getUserHouseholds(userId)

            if (SessionManager.selectedHouseholdId.value == null && households.isNotEmpty()) {
                SessionManager.selectHousehold(households.first().household_id)
            }
        }
    }

    // Update current member and fetch messages when household changes
    LaunchedEffect(selectedHouseholdId) {
        val householdId = selectedHouseholdId
        val userId = AuthRepository.currentUser()?.id

        if (householdId != null && userId != null) {
            val memberships = HouseholdRepository.getUserMemberships(userId)

            currentMemberId = memberships
                .find { it.household_id == householdId }
                ?.member_id

            viewModel.fetchMessages(householdId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Household tabs
        if (households.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(households, key = { it.household_id }) { household ->
                    FilterChip(
                        selected = household.household_id == selectedHouseholdId,
                        onClick = {
                            SessionManager.selectHousehold(household.household_id)
                        },
                        label = {
                            Text(
                                text = household.household_name,
                                color = Color.Black
                            )
                        },
                        modifier = Modifier.handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD1C4E9),
                            containerColor = Color.White
                        )
                    )
                }
            }
        }

        Text(
            text = "Household Chat",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(10.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedHouseholdId == null) {
            Text("Select a household first")
            return@Column
        }

        if (currentMemberId == null) {
            Text("Could not find your membership for this household")
            return@Column
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.message_id }) { message ->
                MessageItem(
                    message = message,
                    currentMemberId = currentMemberId!!,
                    onDelete = {
                        viewModel.deleteMessage(
                            messageId = message.message_id,
                            householdId = selectedHouseholdId!!
                        )
                    },
                    onEdit = { newContent ->
                        viewModel.editMessage(
                            messageId = message.message_id,
                            householdId = selectedHouseholdId!!,
                            newContent = newContent
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Message input row
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(
                                householdId = selectedHouseholdId!!,
                                memberId = currentMemberId!!,
                                content = messageText.trim()
                            )

                            messageText = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(
                            householdId = selectedHouseholdId!!,
                            memberId = currentMemberId!!,
                            content = messageText.trim()
                        )

                        messageText = ""
                        focusManager.clearFocus()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    currentMemberId: String,
    onDelete: () -> Unit,
    onEdit: (String) -> Unit
) {
    val isOwnMessage = message.from_member_id == currentMemberId

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(message.content) }

    val senderName = if (isOwnMessage) {
        "You"
    } else {
        message.senderMember?.nickname?.takeIf { it.isNotBlank() }
            ?: message.senderMember?.user?.display_name?.takeIf { it.isNotBlank() }
            ?: message.senderMember?.user?.name?.takeIf { it.isNotBlank() }
            ?: "User"
    }

    val bubbleColor = if (isOwnMessage) Color(0xFFD1C4E9) else Color.White
    val rowArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = rowArrangement
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .handDrawnBorder(),
            shape = RoundedCornerShape(0.dp),
            color = bubbleColor
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = if (message.edited_at != null) {
                        "Edited ${formatMessageTime(message.edited_at)}"
                    } else {
                        formatMessageTime(message.sent_at)
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                if (isOwnMessage) {
                    Row {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit message"
                            )
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete message",
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = {
                editedText = message.content
                showEditDialog = false
            },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedText.isNotBlank()) {
                            showEditDialog = false
                            onEdit(editedText.trim())
                        }
                    }
                ) {
                    Text("Save", color = Color(0xFF1976D2))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editedText = message.content
                        showEditDialog = false
                    }
                ) {
                    Text("Cancel", color = Color(0xFF1976D2))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete message?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF1976D2))
                }
            }
        )
    }
}

fun formatMessageTime(rawTime: String): String {
    return try {
        rawTime.substring(0, 16).replace("T", " ")
    } catch (e: Exception) {
        rawTime
    }
}