package com.group5.corkboardApp.ui.Shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ShopScreen(modifier: Modifier = Modifier) {
    val viewModel: ShopViewModel = viewModel()
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Shopping Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
