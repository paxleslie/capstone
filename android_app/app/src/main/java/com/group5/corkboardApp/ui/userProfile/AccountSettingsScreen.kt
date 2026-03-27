package com.group5.corkboardApp.ui.userProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val viewModel: AccountSettingsViewModel = viewModel()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val currentUserData by viewModel.currentUserData.collectAsState()
    
    var name by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // This effect pre-fills the text boxes when data is loaded from the database
    LaunchedEffect(currentUserData) {
        name = currentUserData.name
        displayName = currentUserData.display_name
        phone = currentUserData.phone
        email = currentUserData.email
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Account Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Update Profile Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.updateProfile(name, displayName, phone) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    enabled = name.isNotBlank() || displayName.isNotBlank() || phone.isNotBlank()
                ) {
                    Text("Update Profile")
                }
            }
        }

        // Email Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Update Email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("New Email") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { viewModel.updateEmail(email) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    enabled = email.isNotBlank()
                ) {
                    Text("Update Email")
                }
            }
        }

        // Password Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Update Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, 
                    onValueChange = { password = it }, 
                    label = { Text("New Password") }, 
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(
                    onClick = { viewModel.updatePassword(password) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    enabled = password.length >= 6
                ) {
                    Text("Update Password")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logout Section
        Button(
            onClick = { viewModel.signOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("Log Out", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Messages
        when (val state = updateStatus) {
            is AccountSettingsViewModel.UpdateState.Loading -> CircularProgressIndicator()
            is AccountSettingsViewModel.UpdateState.Success -> Text(text = state.message, color = Color(0xFF4CAF50), modifier = Modifier.padding(8.dp))
            is AccountSettingsViewModel.UpdateState.Error -> Text(text = state.message, color = Color.Red, modifier = Modifier.padding(8.dp))
            else -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("Back to Profile")
        }
    }
}
