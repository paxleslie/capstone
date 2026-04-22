package com.group5.corkboardApp.ui.Shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.group5.corkboardApp.data.model.ShopItem
import com.group5.corkboardApp.ui.theme.CorkboardBackground
import com.group5.corkboardApp.ui.theme.PostItMagenta
import com.group5.corkboardApp.ui.theme.handDrawnBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(modifier: Modifier = Modifier) {
    val viewModel: ShopViewModel = viewModel()
    val households by viewModel.households.collectAsState()
    val selectedHouseholdId by viewModel.selectedHouseholdId.collectAsState()
    val shopState by viewModel.shopState.collectAsState()
    val purchaseState by viewModel.purchaseState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadHouseholds()
    }

    LaunchedEffect(purchaseState) {
        when (val state = purchaseState) {
            is ShopViewModel.PurchaseState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetPurchaseState()
            }
            is ShopViewModel.PurchaseState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetPurchaseState()
            }
            else -> {}
        }
    }

    // Using Box instead of Scaffold to avoid double padding and ensure same height as Board
    Box(modifier = modifier.fillMaxSize().background(CorkboardBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (households.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp
                    )
                ) {
                    items(households, key = { it.household_id }) { household ->
                        FilterChip(
                            selected = household.household_id == selectedHouseholdId,
                            onClick = { viewModel.selectHousehold(household.household_id) },
                            label = { Text(household.household_name, color = Color.Black) },
                            modifier = Modifier.handDrawnBorder(),
                            shape = RoundedCornerShape(0.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFD1C4E9),
                                containerColor = Color.White
                            )
                        )
                    }
                }
            }

            when (val state = shopState) {
                is ShopViewModel.ShopState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Black)
                    }
                }
                is ShopViewModel.ShopState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = Color.Red)
                    }
                }
                is ShopViewModel.ShopState.Success -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Shop", 
                                style = MaterialTheme.typography.headlineLarge, 
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "Pts: ${state.currentPoints}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.items) { item ->
                                ShopItemCard(
                                    item = item,
                                    isOwned = state.ownedItemIds.contains(item.id),
                                    onBuy = { viewModel.buyItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun ShopItemCard(item: ShopItem, isOwned: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .handDrawnBorder()
            .alpha(if (isOwned) 0.6f else 1f),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(1.5.dp, Color.Black)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                when (item.type) {
                    "color" -> {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(parseColorSafe(item.value))
                                .border(1.dp, Color.Black)
                        )
                    }
                    "sticker" -> {
                        AsyncImage(
                            model = item.value,
                            contentDescription = item.name,
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    "icon" -> {
                        val icon = when (item.value) {
                            "star" -> Icons.Default.Star
                            "smiley" -> Icons.Default.Face
                            else -> Icons.Default.Star
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.name, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (isOwned) TextDecoration.LineThrough else null,
                color = Color.Black
            )
            Text(
                text = "${item.price} pts", 
                style = MaterialTheme.typography.bodyMedium, 
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBuy,
                enabled = !isOwned,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .handDrawnBorder(),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PostItMagenta,
                    contentColor = Color.White,
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = Color.DarkGray
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isOwned) "Owned" else "Buy",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

fun parseColorSafe(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        Color.Gray
    }
}
