package com.group5.corkboardApp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import com.group5.corkboardApp.ui.auth.LoginScreen
import com.group5.corkboardApp.ui.auth.SignupScreen
import com.group5.corkboardApp.ui.board.BoardScreen
import com.group5.corkboardApp.ui.userProfile.UserProfileScreen
import com.group5.corkboardApp.ui.household.HouseholdScreen
import com.group5.corkboardApp.ui.message.MessageScreen
import com.group5.corkboardApp.ui.theme.MyApplicationTheme
import com.group5.corkboardApp.util.Constants
import com.group5.corkboardApp.util.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _root_ide_package_.com.group5.corkboardApp.ui.theme.MyApplicationTheme {
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
    var currentDestination by remember { mutableStateOf(Constants.AppDestinations.BOARD) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Constants.AppDestinations.entries.forEach {
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
            when (currentDestination) {
                Constants.AppDestinations.BOARD -> BoardScreen(modifier = Modifier.padding(innerPadding))
                Constants.AppDestinations.MESSAGE -> MessageScreen(modifier = Modifier.padding(innerPadding))
                Constants.AppDestinations.HOUSEHOLD -> HouseholdScreen(modifier = Modifier.padding(innerPadding))
                Constants.AppDestinations.PROFILE -> UserProfileScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

// this should go into UserProfileScreen
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
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


