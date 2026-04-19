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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.data.model.Post
import com.group5.corkboardApp.ui.theme.PostItCyan
import com.group5.corkboardApp.ui.theme.handDrawnBorder

class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val out = StringBuilder()

        for (i in digits.indices) {
            out.append(digits[i])
            if (i == 1 || i == 3) out.append("/")
        }

        val formattedText = out.toString().take(10)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 4) return offset + 1
                if (offset <= 8) return offset + 2
                return formattedText.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                if (offset <= 10) return offset - 2
                return digits.length
            }
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(modifier: Modifier = Modifier) {
    val viewModel: BoardViewModel = viewModel()
    val householdLoadState by viewModel.householdLoadState.collectAsState()
    val createPostState by viewModel.createPostState.collectAsState()
    val postsLoadState by viewModel.postsLoadState.collectAsState()
    val postActionState by viewModel.postActionState.collectAsState()
    val selectedHouseholdId by viewModel.selectedHouseholdId.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHouseholds() }

    val focusManager = LocalFocusManager.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var postToEdit by remember { mutableStateOf<Post?>(null) }
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("note") }
    var pointValue by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }
    var householdDropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val households = (householdLoadState as? BoardViewModel.HouseholdLoadState.Success)?.households ?: emptyList()
    val selectedHousehold = households.find { it.household_id == selectedHouseholdId }

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

    fun handleCreatePost() {
        val hid = selectedHouseholdId
        if (title.isBlank()) {
            errorText = "Please enter a title"
        } else if (hid == null) {
            errorText = "Please select a household"
        } else {
            val formattedDate = if (dueAt.length == 8) {
                "${dueAt.substring(4, 8)}-${dueAt.substring(0, 2)}-${dueAt.substring(2, 4)}"
            } else null

            viewModel.createPost(
                title = title.trim(),
                body = body.trim(),
                type = selectedType,
                householdId = hid,
                pointValue = pointValue.toIntOrNull(),
                dueAt = formattedDate
            )
        }
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
                        selected = household.household_id == selectedHouseholdId,
                        onClick = { viewModel.selectHousehold(household.household_id) },
                        label = { Text(household.household_name) },
                        modifier = Modifier.handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    val visiblePosts = state.posts.filter { post ->
                        val hidMatch = selectedHouseholdId == null || post.household_id == selectedHouseholdId
                        val isActive = post.type == "note" || post.status == "pending"
                        hidMatch && isActive
                    }

                    if (visiblePosts.isNotEmpty()) {
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
                                    onEdit = { 
                                        postToEdit = post 
                                        dueAt = post.due_at?.filter { it.isDigit() } ?: ""
                                    },
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
                    .handDrawnBorder(),
                shape = RoundedCornerShape(0.dp),
                containerColor = Color.White,
                contentColor = Color.Black
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "note",
                            onClick = { selectedType = "note" },
                            label = { Text("Note") },
                            enabled = !isLoading,
                            modifier = Modifier.handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp)
                        )
                        FilterChip(
                            selected = selectedType == "chore",
                            onClick = { selectedType = "chore" },
                            label = { Text("Chore") },
                            enabled = !isLoading,
                            modifier = Modifier.handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp)
                        )
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Body") },
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = !isLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = if (selectedType == "chore") ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { 
                                if (selectedType == "note") {
                                    focusManager.clearFocus()
                                    handleCreatePost()
                                }
                            }
                        )
                    )

                    if (selectedType == "chore") {
                        OutlinedTextField(
                            value = pointValue,
                            onValueChange = { if (it.all { char -> char.isDigit() }) pointValue = it },
                            label = { Text("Point Value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = dueAt,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }
                                if (digitsOnly.length <= 8) dueAt = digitsOnly
                            },
                            label = { Text("Due Date (MM/DD/YYYY)") },
                            visualTransformation = DateVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                handleCreatePost()
                            }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
                            enabled = !isLoading
                        )
                    }

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
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
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
                                        viewModel.selectHousehold(household.household_id)
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
                        focusManager.clearFocus()
                        handleCreatePost()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    if (isLoading) CircularProgressIndicator() else Text("Create")
                }
            },
            dismissButton = {
                Button(
                    onClick = { resetDialogState() },
                    enabled = !isLoading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    postToEdit?.let { post ->
        val actionLoading = postActionState is BoardViewModel.PostActionState.Loading
        var editTitle by remember(post.post_id) { mutableStateOf(post.title ?: "") }
        var editBody by remember(post.post_id) { mutableStateOf(post.body ?: "") }
        var editPointValue by remember(post.post_id) { mutableStateOf(post.point_value?.toString() ?: "") }
        var editDueAt by remember(post.post_id) { mutableStateOf(post.due_at?.filter { it.isDigit() } ?: "") }
        var editError by remember(post.post_id) { mutableStateOf<String?>(null) }

        fun handleUpdatePost() {
            if (editTitle.isBlank()) {
                editError = "Title cannot be empty"
            } else {
                val formattedDate = if (editDueAt.length == 8) {
                    "${editDueAt.substring(4, 8)}-${editDueAt.substring(0, 2)}-${editDueAt.substring(2, 4)}"
                } else null

                viewModel.updatePost(
                    post = post,
                    title = editTitle.trim(),
                    body = editBody.trim(),
                    pointValue = editPointValue.toIntOrNull(),
                    dueAt = formattedDate
                )
            }
        }

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
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = !actionLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        label = { Text("Body") },
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = !actionLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = if (post.type == "chore") ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = {
                                if (post.type != "chore") {
                                    focusManager.clearFocus()
                                    handleUpdatePost()
                                }
                            }
                        )
                    )
                    if (post.type == "chore") {
                        OutlinedTextField(
                            value = editPointValue,
                            onValueChange = { if (it.all { char -> char.isDigit() }) editPointValue = it },
                            label = { Text("Point Value") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
                            enabled = !actionLoading
                        )
                        OutlinedTextField(
                            value = editDueAt,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }
                                if (digitsOnly.length <= 8) editDueAt = digitsOnly
                            },
                            label = { Text("Due Date (MM/DD/YYYY)") },
                            visualTransformation = DateVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                handleUpdatePost()
                            }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
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
                        focusManager.clearFocus()
                        handleUpdatePost()
                    },
                    enabled = !actionLoading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    if (actionLoading) CircularProgressIndicator() else Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = { postToEdit = null; viewModel.resetActionState() },
                    enabled = !actionLoading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Cancel") }
            }
        )
    }

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
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    if (actionLoading) CircularProgressIndicator() else Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    onClick = { postToDelete = null; viewModel.resetActionState() },
                    enabled = !actionLoading,
                    modifier = Modifier.handDrawnBorder(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
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
        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = PostItCyan)
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
                    color = MaterialTheme.colorScheme.primary
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
                    Column(modifier = Modifier.weight(1f)) {
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
                        }
                        post.due_at?.let {
                            val displayDate = if (it.length == 10 && it[4] == '-' && it[7] == '-') {
                                "${it.substring(5, 7)}/${it.substring(8, 10)}/${it.substring(0, 4)}"
                            } else it
                            Text(text = "Due: $displayDate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (post.status == "pending") {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Text("Complete", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
