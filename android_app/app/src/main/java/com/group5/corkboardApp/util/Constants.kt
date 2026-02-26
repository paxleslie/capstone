package com.group5.corkboardApp.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.group5.corkboardApp.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

object Constants {
    enum class AppDestinations(
        val label: String,
        val icon: ImageVector,
    ) {
        BOARD("Board", Icons.Default.Home),
        MESSAGE("Chat", Icons.Default.MailOutline),
        HOUSEHOLD("Household", Icons.Default.Home),
        PROFILE(label = "Profile", Icons.Default.AccountBox)
    }
}