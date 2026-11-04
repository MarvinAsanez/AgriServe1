package com.group7.agriserve.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.util.ContentSafetyModerator
import com.group7.agriserve.viewmodel.Comment
import com.group7.agriserve.viewmodel.CommunityViewModel
import com.group7.agriserve.viewmodel.Post
import java.text.SimpleDateFormat
import java.util.*

private val COMMUNITY_CATEGORIES = listOf(
    "All" to "🌾",
    "Crops & Harvest" to "🌾",
    "Livestock & Poultry" to "🐄",
    "Market & Pricing" to "📈",
    "Pest & Disease Control" to "🐛",
    "Farming Tips & Weather" to "🌦️",
    "Tools & Equipment" to "🚜"
)

private val REPORT_REASONS = listOf(
    "Violent / Threatening Language",
    "Physical Harm or Assault Threat",
    "Harassment or Hate Speech",
    "Dangerous Weapons or Terrorism",
    "Spam or Misinformation"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onBack: () -> Unit,
    userName: String,
    isUserVerified: Boolean = false,
    viewModel: CommunityViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    val posts by viewModel.posts
    val selectedCategory by viewModel.selectedCategory
    val searchQuery by viewModel.searchQuery
    val hiddenPostIds by viewModel.hiddenPostIds
    val isUploading by viewModel.isUploadingPost

    var showPostDialog by remember { mutableStateOf(false) }
    var showSafetyRulesModal by remember { mutableStateOf(false) }
    var violentWarningMessage by remember { mutableStateOf<String?>(null) }
    var expandedPostImage by remember { mutableStateOf<String?>(null) }

    // Filter posts according to category, search, and reporter blocklist
    val displayedPosts = remember(posts, selectedCategory, searchQuery, hiddenPostIds) {
        posts.filter { post ->
            !hiddenPostIds.contains(post.id) &&
            (selectedCategory == "All" || post.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
             post.content.contains(searchQuery, ignoreCase = true) ||
             post.authorName.contains(searchQuery, ignoreCase = true) ||
             post.category.contains(searchQuery, ignoreCase = true))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Farmer Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Safe Agricultural Discussion",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSafetyRulesModal = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Community Guidelines",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPostDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create Post") },
                text = { Text("New Post") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar & Community Safety Banner
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search discussions, crops, pests, tips...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
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

                    // Interactive Quick "What's on your farm?" prompt card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPostDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "What's happening on your farm, $userName? Tap to share...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Attach Photo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Zero-Tolerance Violent Content Safety Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Safe Agricultural Space: Real-time anti-violence & threat filter enabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. Horizontal Scrollable Category Chips
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(COMMUNITY_CATEGORIES) { (categoryName, icon) ->
                        val isSelected = selectedCategory.equals(categoryName, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectedCategory.value = categoryName },
                            label = { Text("$icon $categoryName") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // 3. Post Feed
            if (displayedPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🌱",
                                fontSize = 48.sp
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No discussions match '$searchQuery'" else "No posts in this category yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Be the first farmer to start an agricultural conversation!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showPostDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Create First Post")
                            }
                        }
                    }
                }
            } else {
                items(displayedPosts, key = { it.id }) { post ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PostCard(
                            post = post,
                            currentUserId = currentUserId,
                            currentUserName = userName,
                            isUserVerified = isUserVerified,
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onCommentSubmit = { text ->
                                viewModel.addComment(post.id, text, userName, isUserVerified) { success, errorMsg ->
                                    if (!success) {
                                        violentWarningMessage = errorMsg ?: "Violent language is strictly prohibited."
                                    }
                                }
                            },
                            onReportPost = { reason ->
                                viewModel.reportPost(post.id, reason) {
                                    Toast.makeText(context, "Post reported and hidden. Thank you.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeletePost = {
                                viewModel.deletePost(post.id) {
                                    Toast.makeText(context, "Post deleted successfully.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onImageClick = { imgUrl -> expandedPostImage = imgUrl }
                        )
                    }
                }
            }
        }

        // Create Post Modal Dialog with Real-Time Anti-Violence Check
        if (showPostDialog) {
            CreatePostDialog(
                userName = userName,
                isUserVerified = isUserVerified,
                isUploading = isUploading,
                onDismiss = { showPostDialog = false },
                onViolentContentDetected = { warning ->
                    violentWarningMessage = warning
                },
                onSubmit = { content, category, imageUri ->
                    viewModel.createPost(
                        content = content,
                        authorName = userName,
                        category = category,
                        isAuthorVerified = isUserVerified,
                        imageUri = imageUri
                    ) { success, errorMsg ->
                        if (success) {
                            showPostDialog = false
                            Toast.makeText(context, "Post shared with farmer community!", Toast.LENGTH_SHORT).show()
                        } else {
                            violentWarningMessage = errorMsg ?: "Violent or harmful content is not permitted."
                        }
                    }
                }
            )
        }

        // Full Screen Image Preview Modal
        if (expandedPostImage != null) {
            Dialog(onDismissRequest = { expandedPostImage = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { expandedPostImage = null }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { expandedPostImage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            AsyncImage(
                                model = expandedPostImage,
                                contentDescription = "Enlarged photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        // Strict Anti-Violence Warning Modal Dialog
        if (violentWarningMessage != null) {
            AlertDialog(
                onDismissRequest = { violentWarningMessage = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Violent Content Detected",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Violent Content Blocked",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = violentWarningMessage ?: "Your message contains prohibited violent or threatening content.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "AgriServe maintains a strict zero-tolerance policy against violent threats, bodily harm, physical assaults, or harassment. Legitimate agricultural terminology (e.g. weed killer, pest control) is allowed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { violentWarningMessage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Understood, Revise Content")
                    }
                }
            )
        }

        // Community Safety Guidelines Modal
        if (showSafetyRulesModal) {
            AlertDialog(
                onDismissRequest = { showSafetyRulesModal = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Safety Policy",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Community Safety Guidelines",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SafetyRuleItem(
                            icon = "🚫",
                            title = "No Violent Content or Threats",
                            description = "Threats of physical harm, bodily injury, violence, or dangerous weapons are strictly prohibited and immediately blocked."
                        )
                        SafetyRuleItem(
                            icon = "🌾",
                            title = "Focus on Agriculture",
                            description = "Exchange farming techniques, market updates, crop protection, and livestock care with your fellow farmers."
                        )
                        SafetyRuleItem(
                            icon = "🤝",
                            title = "Respect & Professionalism",
                            description = "Treat all farmers, agricultural extension workers, and technicians with dignity."
                        )
                        SafetyRuleItem(
                            icon = "🚩",
                            title = "Community Reporting",
                            description = "If you encounter any inappropriate or suspicious content, use the 3-dot menu to report it immediately."
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSafetyRulesModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Got it")
                    }
                }
            )
        }
    }
}

@Composable
fun SafetyRuleItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

/**
 * Interactive Create Post Dialog with Category Selection, Photo Attachment,
 * and Instant Client-Side Content Moderation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostDialog(
    userName: String,
    isUserVerified: Boolean,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onViolentContentDetected: (String) -> Unit,
    onSubmit: (content: String, category: String, imageUri: Uri?) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Crops & Harvest") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Community Post", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Author identity info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Posting as: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isUserVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "✓ Verified Farmer",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Topic / Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        COMMUNITY_CATEGORIES.filter { it.first != "All" }.forEach { (catName, icon) ->
                            DropdownMenuItem(
                                text = { Text("$icon $catName") },
                                onClick = {
                                    selectedCategory = catName
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                // Post Content Input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    placeholder = { Text("Ask a question, share crop tips, or discuss prices...") },
                    shape = RoundedCornerShape(10.dp)
                )

                // Character counter & Anti-violence reminder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛡️ Real-time safety filter enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "${content.length}/500",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (content.length > 500) Color.Red else Color.Gray
                    )
                }

                // Photo Attachment Area
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Attached photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attach Photo (Crop, Pest, Livestock)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = content.trim()
                    if (trimmed.isBlank()) return@Button

                    // Pre-flight anti-violence inspection
                    val safety = ContentSafetyModerator.checkContent(trimmed)
                    if (!safety.isSafe) {
                        onViolentContentDetected(safety.reason ?: "Violent or harmful language is not allowed.")
                        return@Button
                    }

                    onSubmit(trimmed, selectedCategory, selectedImageUri)
                },
                enabled = content.isNotBlank() && !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publishing...")
                } else {
                    Text("Publish Post")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Interactive Post Card displaying author badges, category tags, images,
 * reaction counters, interactive comments, and moderation reporting.
 */
@Composable
fun PostCard(
    post: Post,
    currentUserId: String?,
    currentUserName: String,
    isUserVerified: Boolean,
    onLikeClick: () -> Unit,
    onCommentSubmit: (String) -> Unit,
    onReportPost: (String) -> Unit,
    onDeletePost: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val isLiked = currentUserId != null && post.likes.contains(currentUserId)
    val isMyPost = currentUserId != null && post.authorId == currentUserId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Author Avatar, Name, Verified Badge, Category, and Options Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author Avatar Initial
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (post.isAuthorVerified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (post.isAuthorVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Farmer",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = post.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )

                        Text(
                            text = formatTimestamp(post.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                // 3-Dots Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isMyPost) {
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showMenu = false
                                    onDeletePost()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report Post") },
                            leadingIcon = {
                                Icon(Icons.Default.Report, contentDescription = null, tint = Color.Red)
                            },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Post Text
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )

            // Attached Post Image (if present)
            if (!post.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(post.imageUrl) }
                ) {
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Post attachment",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "🔍 Tap to view",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Interaction Bar: Likes, Comments Toggle, Share Advice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                TextButton(onClick = onLikeClick) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFE91E63) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.likes.size} ${if (post.likes.size == 1) "Like" else "Likes"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLiked) Color(0xFFE91E63) else Color.Gray,
                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Comments Toggle Button
                TextButton(onClick = { showComments = !showComments }) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${post.comments.size} ${if (post.comments.size == 1) "Comment" else "Comments"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Share Advice Button
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("AgriServe Community Advice", "${post.authorName}: ${post.content}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Post advice copied to clipboard!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            }

            // Interactive Comments Section
            if (showComments) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (post.comments.isEmpty()) {
                        Text(
                            text = "No comments yet. Share your agricultural insight below!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        post.comments.forEach { comment ->
                            CommentItem(comment)
                        }
                    }

                    // Comment Input Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Write a safe, helpful comment...", style = MaterialTheme.typography.bodySmall) },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val trimmed = commentText.trim()
                                if (trimmed.isNotBlank()) {
                                    onCommentSubmit(trimmed)
                                    commentText = ""
                                }
                            },
                            enabled = commentText.isNotBlank(),
                            modifier = Modifier
                                .background(
                                    if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    CircleShape
                                )
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Comment",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Report Post Dialog
    if (showReportDialog) {
        var selectedReason by remember { mutableStateOf(REPORT_REASONS[0]) }

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = "Report",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Report Prohibited Content", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Why are you reporting this post?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    REPORT_REASONS.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportPost(selectedReason)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Single Comment Item with author badge and relative timestamp.
 */
@Composable
fun CommentItem(comment: Comment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (comment.isAuthorVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = formatTimestamp(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Formats a UNIX timestamp into a readable date and time.
 */
fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "Recent"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            format.format(date)
        }
    }
}
