package com.group7.agriserve.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(onBack: () -> Unit) {
    val marketPrices = listOf(
        MarketPrice("Rice (Well-milled)", "₱50.00/kg", true),
        MarketPrice("Corn (Yellow)", "₱22.50/kg", false),
        MarketPrice("Onion (Red)", "₱120.00/kg", true),
        MarketPrice("Tomato", "₱45.00/kg", false)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Market Prices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Daily Price Monitoring",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Last updated: today, 8:00 AM", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(marketPrices) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(item.price) },
                        trailingContent = {
                            Icon(
                                imageVector = if (item.isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (item.isUp) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    )
                }
            }
        }
    }
}

data class MarketPrice(val name: String, val price: String, val isUp: Boolean)
