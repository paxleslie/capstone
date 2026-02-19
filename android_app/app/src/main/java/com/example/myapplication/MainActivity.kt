package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.SignupScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AuthGate()
            }
        }
    }
}

@Composable
fun AuthGate() {
    val sessionStatus by SupabaseClient.client.auth.sessionStatus.collectAsState()
    var showSignup by remember { mutableStateOf(false) }

    when (sessionStatus) {
        is SessionStatus.Authenticated -> {
            MyApplicationApp()
        }
        is SessionStatus.NotAuthenticated -> {
            if (showSignup) {
                SignupScreen(
                    onSignupSuccess = { showSignup = false },
                    onBackToLogin = { showSignup = false }
                )
            } else {
                LoginScreen(
                    onLoginSuccess = { /* Status will update to Authenticated automatically */ },
                    onNavigateToSignup = { showSignup = true }
                )
            }
        }
        else -> {
            // This covers Initializing, RefreshFailure, and Loading states
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MyApplicationApp() {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "User",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    MESSAGE("Message", Icons.Default.Email),
    SHOP("Shop", Icons.Default.ShoppingCart),
    ACCOUNT("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hey look! this this shit is working",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}
