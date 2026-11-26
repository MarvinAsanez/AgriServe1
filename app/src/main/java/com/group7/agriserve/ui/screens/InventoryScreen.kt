package com.group7.agriserve.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.R
import com.group7.agriserve.data.model.InventoryItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit = {},
    onRequestResource: (String) -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    var rawInventoryList by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var inStockOnly by remember { mutableStateOf(false) }

    // Real-time listener on Firestore inventory collection
    LaunchedEffect(Unit) {
        db.collection("inventory").addSnapshotListener { snapshot, error ->
            if (snapshot != null) {
                rawInventoryList = snapshot.documents.mapNotNull { doc ->
                    val itemName = doc.getString("item_name")
                        ?: doc.getString("name")
                        ?: doc.getString("itemName")
                        ?: ""
                    val category = doc.getString("category") ?: "General"
                    val qty = doc.getLong("quantity")?.toInt()
                        ?: (doc.get("quantity") as? Number)?.toInt()
                        ?: 0
                    val unit = doc.getString("unit") ?: "units"

                    if (itemName.isNotBlank()) {
                        InventoryItem(
                            id = doc.id,
                            item_name = itemName,
                            category = category,
                            quantity = qty,
                            unit = unit
                        )
                    } else null
                }
            }
            isLoading = false
        }
    }

    // Filtered items based on search query, category, and in-stock toggle
    val filteredList = remember(rawInventoryList, searchQuery, selectedCategory, inStockOnly) {
        rawInventoryList.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.item_name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            val matchesStock = !inStockOnly || item.quantity > 0

            matchesCategory && matchesSearch && matchesStock
        }
    }

    // Dynamic categories from data + standard DA categories
    val categories = remember(rawInventoryList) {
        val standard = listOf("All", "Seeds", "Fertilizer", "Machinery", "Crops", "Pesticide")
        val fromData = rawInventoryList.map { it.category.trim() }.filter { it.isNotBlank() }.distinct()
        (standard + fromData).distinct()
    }

    val totalItemsCount = rawInventoryList.size
    val inStockItemsCount = rawInventoryList.count { it.quantity > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DA Resources & Supplies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Department of Agriculture • Municipal Agriculture Office",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. BANNER: OFFICIAL DA AID & INSTRUCTIONS
            item {
                DaAidOverviewBanner(
                    totalItems = totalItemsCount,
                    inStockItems = inStockItemsCount
                )
            }

            // 2. SEARCH & STOCK FILTER BAR
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search seeds, fertilizer, equipment...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // CATEGORY FILTER CHIPS
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategory.equals(category, ignoreCase = true)
                            val categoryIcon = getCategoryIcon(category)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        item {
                            FilterChip(
                                selected = inStockOnly,
                                onClick = { inStockOnly = !inStockOnly },
                                label = { Text("In Stock Only", fontSize = 13.sp) },
                                leadingIcon = if (inStockOnly) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. RESULTS STATUS HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "All") "All Available Aid (${filteredList.size})" else "$selectedCategory (${filteredList.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (searchQuery.isNotEmpty() || selectedCategory != "All" || inStockOnly) {
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = "All"
                                inStockOnly = false
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Reset Filters", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. INVENTORY ITEMS LIST
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (filteredList.isEmpty()) {
                item {
                    EmptyInventoryCard(
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        onReset = {
                            searchQuery = ""
                            selectedCategory = "All"
                            inStockOnly = false
                        }
                    )
                }
            } else {
                items(filteredList) { item ->
                    UserFriendlyResourceCard(
                        item = item,
                        onRequestClick = {
                            if (item.quantity > 0) {
                                onRequestResource(item.item_name)
                            }
                        }
                    )
                }
            }

            // 5. HELPFUL INSTRUCTION FOOTER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "How to Claim DA Assistance",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "1. Select any item labeled 'In Stock' and tap 'Request Aid'.\n" +
                                        "2. Fill in your farm size and required quantity.\n" +
                                        "3. Track your request approval under 'Requests Status' on the home dashboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. Overview Banner with DA Official Colors & Quick Count
 */
@Composable
fun DaAidOverviewBanner(
    totalItems: Int,
    inStockItems: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "DA Resources & Allocation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Subsidized & Free Inputs for Registered Farmers",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalItems",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Catalog Varieties",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$inStockItems",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "Ready in Warehouse",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Resource Item Card with Category Badges & Request Action
 */
@Composable
fun UserFriendlyResourceCard(
    item: InventoryItem,
    onRequestClick: () -> Unit
) {
    val categoryColor = getCategoryThemeColor(item.category)
    val categoryIcon = getCategoryIcon(item.category)
    val isInStock = item.quantity > 0
    val isLowStock = item.quantity in 1..20

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isInStock) { onRequestClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category Icon Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = categoryColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = item.category,
                            tint = categoryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Middle Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.item_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.category.ifBlank { "General" },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "• Package: ${item.unit.ifBlank { "bag" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Stock Status + Request Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stock Badge
                val (stockBg, stockFg, stockText) = when {
                    !isInStock -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Out of Stock")
                    isLowStock -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Low Stock: ${item.quantity} ${item.unit}")
                    else -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "${item.quantity} ${item.unit} available")
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = stockBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(stockFg)
                        )
                        Text(
                            text = stockText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = stockFg
                        )
                    }
                }

                // Action Button
                if (isInStock) {
                    Button(
                        onClick = onRequestClick,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Request Aid",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Request Aid", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Restocking Soon", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Empty State Card
 */
@Composable
fun EmptyInventoryCard(
    searchQuery: String,
    selectedCategory: String,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No Resources Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            val desc = if (searchQuery.isNotBlank()) {
                "No DA supplies match \"$searchQuery\" in $selectedCategory."
            } else {
                "There are currently no resources listed under $selectedCategory."
            }

            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onReset,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("View All Resources")
            }
        }
    }
}

/**
 * Visual Helpers for DA Categories
 */
fun getCategoryThemeColor(category: String): Color {
    val clean = category.lowercase(Locale.US).trim()
    return when {
        clean.contains("seed") -> Color(0xFF2E7D32) // Forest Green
        clean.contains("fertilizer") || clean.contains("abono") -> Color(0xFFE65100) // Deep Orange
        clean.contains("machinery") || clean.contains("equipment") || clean.contains("tool") -> Color(0xFF1565C0) // Royal Blue
        clean.contains("crop") || clean.contains("seedling") -> Color(0xFF00796B) // Teal
        clean.contains("pesticide") || clean.contains("chemical") -> Color(0xFFC2185B) // Pink/Crimson
        else -> Color(0xFF5D4037) // Earthy Brown
    }
}

fun getCategoryIcon(category: String): ImageVector {
    val clean = category.lowercase(Locale.US).trim()
    return when {
        clean.contains("seed") -> Icons.Default.Grass
        clean.contains("fertilizer") || clean.contains("abono") -> Icons.Default.Grain
        clean.contains("machinery") || clean.contains("equipment") || clean.contains("tool") -> Icons.Default.Agriculture
        clean.contains("crop") || clean.contains("seedling") -> Icons.Default.Yard
        clean.contains("pesticide") || clean.contains("chemical") -> Icons.Default.Shield
        else -> Icons.Default.Inventory2
    }
}
