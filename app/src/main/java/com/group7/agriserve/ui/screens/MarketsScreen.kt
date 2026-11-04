package com.group7.agriserve.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group7.agriserve.data.model.MarketCommodity
import com.group7.agriserve.data.model.WeeklyPriceComparison
import com.group7.agriserve.data.repository.DaPriceMonitoringRepository
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedCommodityForDetail by remember { mutableStateOf<MarketCommodity?>(null) }
    var selectedWeeklyForDetail by remember { mutableStateOf<WeeklyPriceComparison?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter daily commodities
    val filteredCommodities = remember(searchQuery, selectedCategory) {
        DaPriceMonitoringRepository.commodities.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.localName?.contains(searchQuery, ignoreCase = true) == true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DA Price Monitoring",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bantay Presyo • ${DaPriceMonitoringRepository.BULLETIN_DATE_SHORT}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DaPriceMonitoringRepository.PORTAL_URL))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = "View Official DA Portal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Official DA Bantay Presyo Header Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "OFFICIAL DA BULLETIN",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = DaPriceMonitoringRepository.BULLETIN_DATE,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Department of Agriculture - AMAS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Prevailing retail prices and weekly average price movement of land-farm agricultural commodities (crops, vegetables, fruits, and spices) across ${DaPriceMonitoringRepository.TOTAL_MARKETS_MONITORED} major public wet markets in Metro Manila (NCR).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // Tab Switcher: "Daily Farm Prices" vs "Weekly Price Comparison"
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            "Daily Farm Prices (${DaPriceMonitoringRepository.commodities.size})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            "Weekly Price Comparison",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            if (selectedTabIndex == 0) {
                // Commodity Daily Prices Tab Content
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search farm produce (e.g. Rice, Kamatis, Onion, Banana)...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Category Filter Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DaPriceMonitoringRepository.categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Commodity List
                    if (filteredCommodities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 42.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No commodities found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Try clearing your search query or choosing another category.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${filteredCommodities.size} Commodities Listed",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Tap any item for price range breakdown",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(filteredCommodities, key = { it.id }) { commodity ->
                                CommodityCard(
                                    commodity = commodity,
                                    onClick = { selectedCommodityForDetail = commodity }
                                )
                            }
                        }
                    }
                }
            } else {
                // Weekly Price Comparison Tab Content
                WeeklyPriceComparisonContent(
                    onItemClick = { item -> selectedWeeklyForDetail = item }
                )
            }
        }
    }

    // Modal Bottom Sheet for detailed daily commodity view
    selectedCommodityForDetail?.let { commodity ->
        ModalBottomSheet(
            onDismissRequest = { selectedCommodityForDetail = null },
            sheetState = sheetState
        ) {
            CommodityDetailSheetContent(
                commodity = commodity,
                onClose = { selectedCommodityForDetail = null },
                onOpenPortal = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DaPriceMonitoringRepository.PORTAL_URL))
                    context.startActivity(intent)
                }
            )
        }
    }

    // Modal Bottom Sheet for detailed weekly comparison item
    selectedWeeklyForDetail?.let { weeklyItem ->
        ModalBottomSheet(
            onDismissRequest = { selectedWeeklyForDetail = null },
            sheetState = sheetState
        ) {
            WeeklyDetailSheetContent(
                item = weeklyItem,
                onClose = { selectedWeeklyForDetail = null },
                onOpenPortal = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DaPriceMonitoringRepository.PORTAL_URL))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun CommodityCard(
    commodity: MarketCommodity,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(commodity.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commodity.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!commodity.localName.isNullOrBlank()) {
                    Text(
                        text = commodity.localName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Price range indicator
                Text(
                    text = "Range: ₱${String.format("%.2f", commodity.lowPrice)} - ₱${String.format("%.2f", commodity.highPrice)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Prevailing Price Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₱${String.format("%.2f", commodity.prevailingPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = commodity.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Prevailing",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyPriceComparisonContent(
    onItemClick: (WeeklyPriceComparison) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMovement by remember { mutableStateOf("All") }
    var selectedCategory by remember { mutableStateOf("All") }

    val allComparisons = DaPriceMonitoringRepository.weeklyComparisons

    // Calculate overall statistics
    val upCount = remember { allComparisons.count { it.isUp } }
    val downCount = remember { allComparisons.count { it.isDown } }
    val stableCount = remember { allComparisons.count { it.isStable } }

    val filteredList = remember(searchQuery, selectedMovement, selectedCategory) {
        allComparisons.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    (item.specification?.contains(searchQuery, ignoreCase = true) == true) ||
                    item.category.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)

            val matchesMovement = when (selectedMovement) {
                "Increased" -> item.isUp
                "Decreased" -> item.isDown
                "Stable" -> item.isStable
                else -> true
            }

            matchesSearch && matchesCategory && matchesMovement
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Header Description
            Text(
                text = "DA Weekly Price Movement Comparison",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Comparing NCR weekly retail averages: ${DaPriceMonitoringRepository.CURRENT_WEEK_PERIOD} vs ${DaPriceMonitoringRepository.PREVIOUS_WEEK_PERIOD}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Summary KPI Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Increased KPI
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Increased",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                        Text(
                            text = "$upCount Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }

                // Decreased KPI
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Decreased",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Text(
                            text = "$downCount Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                // Stable KPI
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Stable",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "$stableCount Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search weekly item (e.g. Siling Labuyo, Kamatis, Rice, Onion)...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Movement Filter Chips
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val movements = listOf("All", "Increased", "Decreased", "Stable")
                items(movements) { mvt ->
                    FilterChip(
                        selected = selectedMovement == mvt,
                        onClick = { selectedMovement = mvt },
                        label = {
                            Text(
                                when (mvt) {
                                    "Increased" -> "📈 Price Increased ($upCount)"
                                    "Decreased" -> "📉 Price Decreased ($downCount)"
                                    "Stable" -> "➖ Stable ($stableCount)"
                                    else -> "All Movements (${allComparisons.size})"
                                }
                            )
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // Category Filter Chips
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DaPriceMonitoringRepository.categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // Comparison Items Count Label
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} Commodities Compared",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap to view weekly details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Comparison List
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No matching commodities found",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Try selecting 'All Movements' or clearing your search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { item ->
                WeeklyComparisonCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun WeeklyComparisonCard(
    item: WeeklyPriceComparison,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(item.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name & Specification
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.specification.isNullOrBlank()) {
                    Text(
                        text = item.specification,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Previous vs Current Week Price Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Prev: ₱${String.format("%.2f", item.previousWeekPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "Now: ₱${String.format("%.2f", item.currentWeekPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Movement Badge
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        item.isUp -> Color(0xFFFFEBEE)
                        item.isDown -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                item.isUp -> Icons.AutoMirrored.Filled.TrendingUp
                                item.isDown -> Icons.AutoMirrored.Filled.TrendingDown
                                else -> Icons.Default.Remove
                            },
                            contentDescription = null,
                            tint = when {
                                item.isUp -> Color(0xFFC62828)
                                item.isDown -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = when {
                                item.isUp -> "+₱${String.format("%.2f", item.priceDiff)}"
                                item.isDown -> "-₱${String.format("%.2f", abs(item.priceDiff))}"
                                else -> "₱0.00"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                item.isUp -> Color(0xFFC62828)
                                item.isDown -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Text(
                    text = when {
                        item.isUp -> "+${String.format("%.1f", item.percentChange)}%"
                        item.isDown -> "${String.format("%.1f", item.percentChange)}%"
                        else -> "0.0%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        item.isUp -> Color(0xFFC62828)
                        item.isDown -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun WeeklyDetailSheetContent(
    item: WeeklyPriceComparison,
    onClose: () -> Unit,
    onOpenPortal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(item.emoji, fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!item.specification.isNullOrBlank()) {
                    Text(
                        text = item.specification,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(14.dp))

        // Week-on-Week Comparison Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Previous Week Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PREVIOUS WEEK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DaPriceMonitoringRepository.PREVIOUS_WEEK_PERIOD,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₱${String.format("%.2f", item.previousWeekPrice)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Current Week Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CURRENT WEEK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = DaPriceMonitoringRepository.CURRENT_WEEK_PERIOD,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₱${String.format("%.2f", item.currentWeekPrice)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Net Movement Highlight Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                item.isUp -> Color(0xFFFFEBEE)
                item.isDown -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = when {
                            item.isUp -> "PRICE INCREASED"
                            item.isDown -> "PRICE DECREASED"
                            else -> "PRICE STABLE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isUp -> Color(0xFFC62828)
                            item.isDown -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = when {
                            item.isUp -> "Retail rate rose by ₱${String.format("%.2f", item.priceDiff)} per ${item.unit}"
                            item.isDown -> "Retail rate dropped by ₱${String.format("%.2f", abs(item.priceDiff))} per ${item.unit}"
                            else -> "No change in retail rate between monitoring periods"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when {
                        item.isUp -> "+${String.format("%.1f", item.percentChange)}%"
                        item.isDown -> "${String.format("%.1f", item.percentChange)}%"
                        else -> "0.0%"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        item.isUp -> Color(0xFFC62828)
                        item.isDown -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Official Agency Footer
        Text(
            text = "Official Data Source: ${DaPriceMonitoringRepository.SOURCE_AGENCY}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Coverage: 33 Monitored NCR Public Wet Markets",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close")
            }
            Button(
                onClick = onOpenPortal,
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("DA Portal")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CommodityDetailSheetContent(
    commodity: MarketCommodity,
    onClose: () -> Unit,
    onOpenPortal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(commodity.emoji, fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commodity.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!commodity.localName.isNullOrBlank()) {
                    Text(
                        text = commodity.localName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = commodity.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(14.dp))

        // Prevailing Price Highlight Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PREVAILING RETAIL PRICE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "₱${String.format("%.2f", commodity.prevailingPrice)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " ${commodity.unit}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Price Statistics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Low Price Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Lowest", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("₱${String.format("%.2f", commodity.lowPrice)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            // High Price Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Highest", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("₱${String.format("%.2f", commodity.highPrice)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            // Metro Manila Average Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NCR Average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("₱${String.format("%.2f", commodity.avgPrice)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!commodity.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = commodity.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Official Agency Footer
        Text(
            text = "Official Data Source: ${DaPriceMonitoringRepository.SOURCE_AGENCY}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Bulletin Date: ${DaPriceMonitoringRepository.BULLETIN_DATE}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close")
            }
            Button(
                onClick = onOpenPortal,
                modifier = Modifier.weight(1.3f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("DA Portal")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
