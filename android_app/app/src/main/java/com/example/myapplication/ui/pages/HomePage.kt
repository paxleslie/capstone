package com.example.myapplication.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun HomePage(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // Directly get the user from the current session
    val user = SupabaseClient.client.auth.currentUserOrNull()

    val fullName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content ?: "No full name"
    val displayName = user?.userMetadata?.get("display_name")?.jsonPrimitive?.content ?: "No display name"
    val phoneNumber = user?.userMetadata?.get("phone")?.jsonPrimitive?.content ?: "No phone number"
    val email = user?.email ?: "No email"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "User Information",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Full Name: $fullName")
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Display Name: $displayName")
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Email: $email")
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Phone: $phoneNumber")
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            scope.launch {
                SupabaseClient.client.auth.signOut()
            }
        }) {
            Text("Log Out")
        }
    }
}