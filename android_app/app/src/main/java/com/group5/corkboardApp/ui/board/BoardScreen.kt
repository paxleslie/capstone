package com.group5.corkboardApp.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.data.model.Post

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(modifier: Modifier = Modifier) {
    val viewModel: BoardViewModel = viewModel()
    val householdLoadState by viewModel.householdLoadState.collectAsState()
    val createPostState by viewModel.createPostState.collectAsState()
    val postsLoadState by viewModel.postsLoadState.collectAsState()
    val postActionState by viewModel.postActionState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHouseholds() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var postToEdit by remember { mutableStateOf<Post?>(null) }
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("note") }
    var pointValue by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }
    var selectedHouseholdId by remember { mutableStateOf<String?>(null) }
    var householdDropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val households = (householdLoadState as? BoardViewModel.HouseholdLoadState.Success)?.households ?: emptyList()
    val effectiveHouseholdId = selectedHouseholdId ?: households.firstOrNull()?.household_id
    val selectedHousehold = households.find { it.household_id == effectiveHouseholdId }

    // Persist the default selection once loaded so chip shows as selected
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

    LaunchedEffect(postActionState) {
        when (postActionState) {
            is BoardViewModel.PostActionState.Success -> {
                postToEdit = null
                postToDelete = null
                viewModel.resetActionState()
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

    Column(modifier = modifier.fillMaxSize()) {
        if (households.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                )
            ) {
                items(households, key = { it.household_id }) { household ->
                    FilterChip(
                        selected = household.household_id == effectiveHouseholdId,
                        onClick = { selectedHouseholdId = household.household_id },
                        label = { Text(household.household_name) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val state = postsLoadState) {
                is BoardViewModel.PostsLoadState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is BoardViewModel.PostsLoadState.Error -> {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                is BoardViewModel.PostsLoadState.Success -> {
                    val visiblePosts = if (effectiveHouseholdId != null)
                        state.posts.filter { it.household_id == effectiveHouseholdId }
                    else
                        state.posts

                    if (visiblePosts.isEmpty()) {
                        Text(
                            text = "No posts yet",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visiblePosts, key = { it.post_id ?: it.hashCode() }) { post ->
                                PostCard(
                                    post = post,
                                    onEdit = { postToEdit = post },
                                    onDelete = { postToDelete = post },
                                    onComplete = { viewModel.completeChore(post) }
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create post")
            }
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

    // Edit dialog
    postToEdit?.let { post ->
        val actionLoading = postActionState is BoardViewModel.PostActionState.Loading
        var editTitle by remember(post.post_id) { mutableStateOf(post.title ?: "") }
        var editBody by remember(post.post_id) { mutableStateOf(post.body ?: "") }
        var editPointValue by remember(post.post_id) { mutableStateOf(post.point_value?.toString() ?: "") }
        var editDueAt by remember(post.post_id) { mutableStateOf(post.due_at ?: "") }
        var editError by remember(post.post_id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!actionLoading) { postToEdit = null; viewModel.resetActionState() } },
            title = { Text("Edit Post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !actionLoading
                    )
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        label = { Text("Body") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !actionLoading
                    )
                    if (post.type == "chore") {
                        OutlinedTextField(
                            value = editPointValue,
                            onValueChange = { editPointValue = it },
                            label = { Text("Point Value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !actionLoading
                        )
                        OutlinedTextField(
                            value = editDueAt,
                            onValueChange = { editDueAt = it },
                            label = { Text("Due Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !actionLoading
                        )
                    }
                    editError?.let { Text(text = it, color = Color.Red) }
                    (postActionState as? BoardViewModel.PostActionState.Error)?.let {
                        Text(text = it.message, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTitle.isBlank()) {
                            editError = "Title cannot be empty"
                        } else {
                            viewModel.updatePost(
                                post = post,
                                title = editTitle.trim(),
                                body = editBody.trim(),
                                pointValue = editPointValue.toIntOrNull(),
                                dueAt = editDueAt.ifBlank { null }
                            )
                        }
                    },
                    enabled = !actionLoading
                ) {
                    if (actionLoading) CircularProgressIndicator() else Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { postToEdit = null; viewModel.resetActionState() },
                    enabled = !actionLoading
                ) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation
    postToDelete?.let { post ->
        val actionLoading = postActionState is BoardViewModel.PostActionState.Loading
        AlertDialog(
            onDismissRequest = { if (!actionLoading) { postToDelete = null; viewModel.resetActionState() } },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete \"${post.title ?: "this post"}\"?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deletePost(post.post_id!!) },
                    enabled = !actionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    if (actionLoading) CircularProgressIndicator() else Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { postToDelete = null; viewModel.resetActionState() },
                    enabled = !actionLoading
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PostCard(
    post: Post,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                post.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = post.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (post.type == "chore") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.padding(4.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.padding(4.dp))
                }
            }
            post.body?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            if (post.type == "chore") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        post.status?.let {
                            Text(
                                text = it.replaceFirstChar { c -> c.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        post.point_value?.let {
                            Text(text = "$it pts", style = MaterialTheme.typography.labelSmall)
                        }
                        post.due_at?.let {
                            Text(text = "Due: $it", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (post.status == "pending") {
                        TextButton(onClick = onComplete) {
                            Text("Mark Complete")
                        }
                    }
                }
            }
        }
    }
}
