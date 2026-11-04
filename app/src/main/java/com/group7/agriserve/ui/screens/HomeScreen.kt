package com.group7.agriserve.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.R
import com.group7.agriserve.data.model.Announcement
import com.group7.agriserve.viewmodel.AnnouncementViewModel
import com.group7.agriserve.viewmodel.NewsViewModel
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    userViewModel: UserViewModel? = null,
    onInventoryClick: () -> Unit = {},
    onWeatherClick: () -> Unit = {},
    onMarketsClick: () -> Unit = {},
    onCommunityClick: () -> Unit = {},
    onStatusClick: () -> Unit = {},
    onStatusFilterClick: (String) -> Unit = {},
    onFarmTaggingClick: () -> Unit = {},
    onAllFarmsMapClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onSupportClick: () -> Unit = {},
    onAiChatClick: () -> Unit = {},
    newsViewModel: NewsViewModel = viewModel(),
    announcementViewModel: AnnouncementViewModel = viewModel()
) {
    val context = LocalContext.current
    val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val sessionUid = remember { com.group7.agriserve.util.SessionManager.getSavedUserId(context) ?: "" }
    val vmDocId = userViewModel?.farmerDocId?.value ?: ""
    val candidateIds = remember(authUid, sessionUid, vmDocId) {
        listOf(authUid, sessionUid, vmDocId).filter { it.isNotBlank() }.distinct()
    }
    var activeDisputesCount by remember { mutableStateOf(0) }
    var latestDisputeFarmName by remember { mutableStateOf("") }

    val currentThemeMode = userViewModel?.themeMode?.value ?: "system"
    val isDarkThemeActive = when (currentThemeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    var showNotificationsDialog by remember { mutableStateOf(false) }
    var notificationsList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var unreadNotificationsCount by remember { mutableStateOf(0) }

    LaunchedEffect(candidateIds) {
        if (candidateIds.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            for (cid in candidateIds) {
                db.collection("farm_disputes")
                    .whereEqualTo("farmUserId", cid)
                    .whereEqualTo("farmerDisputed", false)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            activeDisputesCount = snapshot.size()
                            if (!snapshot.isEmpty) {
                                latestDisputeFarmName = snapshot.documents.firstOrNull()?.getString("farmName") ?: "Your Farm"
                            }
                        }
                    }

                db.collection("notifications")
                    .whereEqualTo("targetUserId", cid)
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null) {
                            val items = snapshot.documents.mapNotNull { doc ->
                                doc.data?.plus("id" to doc.id)
                            }
                            val previousIds = notificationsList.mapNotNull { it.get("id") as? String }.toSet()
                            if (previousIds.isNotEmpty()) {
                                items.filter { (it.get("read") as? Boolean) == false && !previousIds.contains(it.get("id") as? String) }.forEach { newNotif ->
                                    val title = newNotif.get("title") as? String ?: "DA Update"
                                    val msg = newNotif.get("message") as? String ?: "You have a new update from AgriServe."
                                    com.group7.agriserve.util.AgriServeNotificationHelper.showSystemNotification(context, title, msg)
                                }
                            }
                            notificationsList = items.sortedByDescending { (it.get("timestamp") as? com.google.firebase.Timestamp)?.seconds ?: 0L }
                            unreadNotificationsCount = items.count { it.get("read") == false }
                        }
                    }
            }
        }
    }

    val newsArticles by newsViewModel.newsArticles
    val isNewsLoading by newsViewModel.isLoading
    val newsError by newsViewModel.error

    val municipalAnnouncements by announcementViewModel.announcements
    val isAnnouncementsLoading by announcementViewModel.isLoading
    val selectedAnnouncement by announcementViewModel.selectedAnnouncement
    val emergencyAlert = announcementViewModel.emergencyAlerts.value.firstOrNull()

    val displayedArticles = if (newsArticles.isNotEmpty()) newsArticles else newsViewModel.fallbackArticles
    val hasMunicipalAdvisories = municipalAnnouncements.isNotEmpty()
    val totalPagerCount = if (hasMunicipalAdvisories) municipalAnnouncements.size else (if (displayedArticles.isEmpty()) 1 else displayedArticles.size)

    val pagerState = rememberPagerState(pageCount = { totalPagerCount })

    LaunchedEffect(hasMunicipalAdvisories, municipalAnnouncements.size, displayedArticles.size) {
        if (totalPagerCount > 1) {
            while (true) {
                delay(5000)
                if (pagerState.pageCount > 0) {
                    val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(20.dp)
        ) {
            // Subtle watermark tractor icon
            Icon(
                imageVector = Icons.Filled.Agriculture,
                contentDescription = null,
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp),
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
            )

            // Top action bar (Notification on Upper Left, Theme toggle on Upper Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upper Left: Notification Icon with unread badge
                Box {
                    IconButton(
                        onClick = { showNotificationsDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    val totalBadges = unreadNotificationsCount + activeDisputesCount
                    if (totalBadges > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .background(Color(0xFFFF3D00), CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (totalBadges > 9) "9+" else "$totalBadges",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Upper Right: Theme Toggle
                IconButton(
                    onClick = {
                        val nextMode = if (isDarkThemeActive) "light" else "dark"
                        userViewModel?.updateThemeMode(nextMode, context)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isDarkThemeActive) Icons.Filled.DarkMode else Icons.Filled.WbSunny,
                        contentDescription = "Toggle Theme",
                        tint = if (isDarkThemeActive) Color(0xFFFFD54F) else Color(0xFFFFE082),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Welcome Text Banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.welcome_to_agriserve),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(R.string.app_slogan),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // High Priority Municipal Emergency / Calamity Warning Banner
        if (emergencyAlert != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { announcementViewModel.openAnnouncementDetails(emergencyAlert) },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFEBEE),
                border = androidx.compose.foundation.BorderStroke(1.8.dp, Color(0xFFD32F2F))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Calamity Siren",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🚨 EMERGENCY CALAMITY ALERT",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                color = Color(0xFFB71C1C)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${emergencyAlert.targetBarangay}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                        Text(
                            text = emergencyAlert.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF212121),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.emergency_precaution_hint),
                            fontSize = 11.5.sp,
                            color = Color(0xFFC62828)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // News & Advisories Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasMunicipalAdvisories) stringResource(R.string.da_advisories_updates) else stringResource(R.string.da_updates),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
            if (isNewsLoading || isAnnouncementsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { 
                    announcementViewModel.listenToAnnouncements()
                    newsViewModel.fetchNews() 
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Advisories", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (newsError != null && newsArticles.isEmpty() && !hasMunicipalAdvisories) {
            Text(
                text = "Could not load latest news from DA.gov.ph",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // Sliding News & Municipal Advisories Carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp
        ) { page ->
            if (hasMunicipalAdvisories) {
                val item = municipalAnnouncements[page % municipalAnnouncements.size]
                val catIcon = when (item.category) {
                    "Calamity & Weather" -> "🌪️"
                    "Pest & Disease Outbreak" -> "🐛"
                    "Seed & Fertilizer Distribution" -> "🌾"
                    "Livestock & Animal Health" -> "💉"
                    else -> "📢"
                }
                NewsCard(
                    title = item.title,
                    description = "$catIcon ${item.category} • ${item.targetBarangay}",
                    imageUrl = item.imageUrl,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        announcementViewModel.openAnnouncementDetails(item)
                    }
                )
            } else if (displayedArticles.isNotEmpty()) {
                val article = displayedArticles[page % displayedArticles.size]
                NewsCard(
                    title = article.title,
                    description = article.source.name ?: "Official DA Source",
                    imageUrl = article.urlToImage,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val targetUrl = article.url.takeIf { it.isNotBlank() } ?: "https://www.da.gov.ph"
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("HomeScreen", "Error opening news link: " + targetUrl, e)
                        }
                    }
                )
            }
        }

        // Pager Indicator
        if (totalPagerCount > 0) {
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(totalPagerCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp, 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Actions & Services Section
        Text(
            text = stringResource(R.string.services_monitoring),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Row 1: DA Resources & Requests Status
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.inventory_card),
                icon = Icons.Default.Inventory,
                color = Color(0xFF81C784),
                onClick = onInventoryClick
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.my_status),
                icon = Icons.AutoMirrored.Filled.Assignment,
                color = Color(0xFF64B5F6),
                onClick = onStatusClick
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Row 2: Markets & Weather
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.markets),
                icon = Icons.Default.Storefront,
                color = Color(0xFFD4E157),
                onClick = onMarketsClick
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.weather),
                icon = Icons.Default.WbSunny,
                color = Color(0xFFFFB74D),
                onClick = onWeatherClick
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row 3: Farmer Community & Farm Tagging
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.farmer_community),
                icon = Icons.Default.Groups,
                color = Color(0xFF4DB6AC),
                onClick = onCommunityClick
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.farm_tagging),
                icon = Icons.Default.AddLocationAlt,
                color = Color(0xFFFF7043),
                onClick = onFarmTaggingClick
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row 4: AI Support Assistant & Unified Map
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.ai_support_agent),
                icon = Icons.Default.SmartToy,
                color = Color(0xFF7E57C2),
                onClick = onAiChatClick
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.unified_farms_map),
                icon = Icons.Default.Map,
                color = Color(0xFF26A69A),
                onClick = onAllFarmsMapClick
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Notifications Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.notifications_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showNotificationsDialog = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (notificationsList.isEmpty() && activeDisputesCount == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No notifications yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeDisputesCount > 0) {
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showNotificationsDialog = false
                                                onStatusFilterClick("Disputes")
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFFF3E0),
                                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFE65100))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFE65100),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "⚠️ Farm Report Alert (" + activeDisputesCount + ")",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFFBF360C)
                                                )
                                                Text(
                                                    text = "A report was filed against " + latestDisputeFarmName + ". Tap to respond or contest.",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFE65100)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(notificationsList) { notif ->
                                val title = notif.get("title") as? String ?: "DA Advisory"
                                val msg = notif.get("message") as? String ?: ""
                                val isRead = notif.get("read") as? Boolean ?: true

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val notifId = notif.get("id") as? String
                                            if (notifId != null && !isRead) {
                                                FirebaseFirestore.getInstance()
                                                    .collection("notifications")
                                                    .document(notifId)
                                                    .update("read", true)
                                            }
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isRead) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (msg.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = msg,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Official Municipal Advisory Details Dialog
    selectedAnnouncement?.let { adv ->
        AnnouncementDetailsDialog(
            announcement = adv,
            onDismiss = { announcementViewModel.closeAnnouncementDetails() }
        )
    }
}

@Composable
fun AnnouncementDetailsDialog(
    announcement: Announcement,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close Advisory", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (announcement.severity) {
                    "Emergency" -> "🚨"
                    "Warning" -> "⚠️"
                    else -> "📢"
                }
                Text(
                    text = "$icon ${announcement.category}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!announcement.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = announcement.imageUrl,
                        contentDescription = announcement.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (announcement.severity) {
                            "Emergency" -> Color(0xFFFFCDD2)
                            "Warning" -> Color(0xFFFFE0B2)
                            else -> Color(0xFFC8E6C9)
                        }
                    ) {
                        Text(
                            text = announcement.severity.uppercase(),
                            color = when (announcement.severity) {
                                "Emergency" -> Color(0xFFB71C1C)
                                "Warning" -> Color(0xFFE65100)
                                else -> Color(0xFF1B5E20)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "📍 ${announcement.targetBarangay}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Official Release • ${announcement.author}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = announcement.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
        }
    )
}

@Composable
fun NewsCard(
    title: String, 
    description: String, 
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    modifier: Modifier, 
    title: String, 
    icon: ImageVector, 
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
