package com.group5.corkboardApp.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

object Constants {
    enum class AppDestinations(
        val label: String,
        val icon: ImageVector,
    ) {
        BOARD("Board", Icons.Default.Home),
        MESSAGE("Chat", Icons.Default.MailOutline),
        SHOP("Shop", Icons.Default.ShoppingCart),
        PROFILE(label = "Profile", Icons.Default.AccountBox)
    }
}
