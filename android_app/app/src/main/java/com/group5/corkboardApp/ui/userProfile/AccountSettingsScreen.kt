package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group5.corkboardApp.ui.theme.HandDrawnBlack
import com.group5.corkboardApp.ui.theme.handDrawnBorder

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val out = StringBuilder()
        
        for (i in digits.indices) {
            out.append(digits[i])
            if (i == 2 || i == 5) out.append("-")
        }
        
        val formattedText = out.toString().take(12)
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 6) return offset + 1
                if (offset <= 10) return offset + 2
                return formattedText.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 7) return offset - 1
                if (offset <= 12) return offset - 2
                return 10
            }
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}

@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val viewModel: AccountSettingsViewModel = viewModel()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val currentUserData by viewModel.currentUserData.collectAsState()
    val passwordVerificationStatus by viewModel.passwordVerificationStatus.collectAsState()
    
    val snackbarHostState = SnackbarHostState()
    val focusManager = LocalFocusManager.current
    
    var name by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserData) {
        name = currentUserData.name
        displayName = currentUserData.display_name
        phone = currentUserData.phone.filter { it.isDigit() }.take(10)
        email = currentUserData.email
    }

    LaunchedEffect(updateStatus) {
        when (val state = updateStatus) {
            is AccountSettingsViewModel.UpdateState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetStatus()
                showPasswordDialog = false
            }
            is AccountSettingsViewModel.UpdateState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetStatus()
            }
            else -> {}
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Account Settings",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Profile Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Update Profile Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name, 
                        onValueChange = { name = it }, 
                        label = { Text("Full Name") }, 
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = displayName, 
                        onValueChange = { displayName = it }, 
                        label = { Text("Display Name") }, 
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, 
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            if (digitsOnly.length <= 10) {
                                phone = digitsOnly
                            }
                        }, 
                        label = { Text("Phone Number") }, 
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        visualTransformation = PhoneVisualTransformation(),
                        shape = RoundedCornerShape(0.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (name.isNotBlank() || displayName.isNotBlank() || phone.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.updateProfile(name, displayName, phone)
                                }
                            }
                        ),
                        singleLine = true
                    )
                    Button(
                        onClick = { 
                            focusManager.clearFocus()
                            viewModel.updateProfile(name, displayName, phone) 
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = name.isNotBlank() || displayName.isNotBlank() || phone.isNotBlank()
                    ) {
                        Text("Update Profile")
                    }
                }
            }

            // Email Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Update Email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email, 
                        onValueChange = { email = it }, 
                        label = { Text("New Email") }, 
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        singleLine = true,
                        shape = RoundedCornerShape(0.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (email.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.updateEmail(email)
                                }
                            }
                        )
                    )
                    Button(
                        onClick = { 
                            focusManager.clearFocus()
                            viewModel.updateEmail(email) 
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp),
                        enabled = email.isNotBlank()
                    ) {
                        Text("Update Email")
                    }
                }
            }

            // Password Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { 
                            viewModel.resetPasswordVerification()
                            showPasswordDialog = true 
                        },
                        modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text("Change Password")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Back to Profile
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text("Back to Profile")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Section
            Button(
                onClick = { viewModel.signOut() },
                modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
            ) {
                Text("Log Out")
            }

            // Loading Indicator
            if (updateStatus is AccountSettingsViewModel.UpdateState.Loading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            verificationStatus = passwordVerificationStatus,
            onVerify = { viewModel.verifyCurrentPassword(it) },
            onUpdate = { viewModel.updatePassword(it) },
            onDismiss = { 
                showPasswordDialog = false
                viewModel.resetPasswordVerification()
            }
        )
    }
}

@Composable
fun ChangePasswordDialog(
    verificationStatus: AccountSettingsViewModel.PasswordVerificationState,
    onVerify: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    val focusManager = LocalFocusManager.current

    val isMinLength = newPassword.length >= 6
    val hasLetter = newPassword.any { it.isLetter() }
    val hasDigit = newPassword.any { it.isDigit() }
    val isPasswordValid = isMinLength && hasLetter && hasDigit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                when (verificationStatus) {
                    is AccountSettingsViewModel.PasswordVerificationStatus.Success -> {
                        Text(text = "New Password Rules:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(text = "• At least 6 characters\n• At least one letter\n• At least one number", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
                        
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(0.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm New Password") },
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(0.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { 
                                    if (isPasswordValid && newPassword == confirmPassword) {
                                        onUpdate(newPassword)
                                    }
                                }
                            ),
                            singleLine = true
                        )
                        if (newPassword.isNotBlank() && confirmPassword.isNotBlank() && newPassword != confirmPassword) {
                            Text(text = "The passwords do not match", color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    else -> {
                        Text("Please enter your current password to continue.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current Password") },
                            modifier = Modifier.fillMaxWidth().handDrawnBorder(),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(0.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { 
                                    if (currentPassword.isNotBlank()) {
                                        onVerify(currentPassword)
                                    }
                                }
                            ),
                            singleLine = true
                        )
                        if (verificationStatus is AccountSettingsViewModel.PasswordVerificationStatus.Error) {
                            Text(text = (verificationStatus as AccountSettingsViewModel.PasswordVerificationStatus.Error).message, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isSuccess = verificationStatus is AccountSettingsViewModel.PasswordVerificationStatus.Success
            val text = if (isSuccess) "Update" else "Next"
            val action = if (isSuccess) { { onUpdate(newPassword) } } else { { onVerify(currentPassword) } }
            val enabled = if (isSuccess) (isPasswordValid && newPassword == confirmPassword) else currentPassword.isNotBlank()

            Button(
                onClick = action,
                enabled = enabled,
                modifier = Modifier.handDrawnBorder(),
                shape = RoundedCornerShape(0.dp)
            ) {
                if (verificationStatus is AccountSettingsViewModel.PasswordVerificationStatus.Loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Text(text)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
