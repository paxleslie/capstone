package com.group5.corkboardApp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToSignup: () -> Unit) {
    val viewModel: AuthViewModel = viewModel()
    val loginState by viewModel.loginState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val isLoading = loginState is AuthViewModel.LoginState.Loading

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is AuthViewModel.LoginState.Success -> {
                viewModel.resetLoginState()
                onLoginSuccess()
            }
            is AuthViewModel.LoginState.Error -> errorText = state.message
            else -> {}
        }
    }

    val onLoginClick = {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            errorText = "Email and password cannot be empty"
        } else {
            errorText = null
            viewModel.signIn(trimmedEmail, trimmedPassword)
        }
    }

    val blackTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedLabelColor = Color.Black,
        unfocusedLabelColor = Color.Black,
        cursorColor = Color.Black,
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.Gray,
        focusedBorderColor = Color.Black,
        unfocusedBorderColor = Color.Black
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Login", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { if (it.length <= 50) email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true,
            enabled = !isLoading,
            colors = blackTextFieldColors
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= 50) password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onLoginClick()
                }
            ),
            singleLine = true,
            enabled = !isLoading,
            colors = blackTextFieldColors
        )

        errorText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onLoginClick() }, 
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Login")
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onNavigateToSignup, 
            enabled = !isLoading,
            colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}
