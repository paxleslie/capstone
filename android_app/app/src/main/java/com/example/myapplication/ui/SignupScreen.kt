package com.example.myapplication.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun SignupScreen(onSignupSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Create Account", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )

        errorText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = androidx.compose.ui.graphics.Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                val trimmedEmail = email.trim()
                val trimmedPassword = password.trim()
                val trimmedFullName = fullName.trim()
                val trimmedDisplayName = displayName.trim()
                val trimmedPhone = phoneNumber.trim()

                if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty() || trimmedFullName.isEmpty()) {
                    errorText = "Name, Email and Password are required"
                    return@launch
                }

                try {
                    val metadata = buildJsonObject {
                        put("full_name", trimmedFullName)
                        put("name", trimmedFullName)
                        put("display_name", trimmedDisplayName)
                        put("displayName", trimmedDisplayName)
                        put("phone", trimmedPhone)
                        put("phone_number", trimmedPhone)
                    }
                    
                    Log.d("SupabaseSignup", "Sending metadata: $metadata")

                    SupabaseClient.client.auth.signUpWith(Email) {
                        this.email = trimmedEmail
                        this.password = trimmedPassword
                        this.data = metadata
                    }
                    errorText = "Sign up successful! Please check your email."
                } catch (e: Exception) {
                    Log.e("SupabaseSignup", "Signup error", e)
                    errorText = e.localizedMessage ?: "Sign up failed"
                }
            }
        }) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBackToLogin) {
            Text("Already have an account? Log In")
        }
    }
}
