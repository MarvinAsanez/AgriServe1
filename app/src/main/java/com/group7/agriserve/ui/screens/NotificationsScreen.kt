package com.group7.agriserve.ui.screens

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.group7.agriserve.R
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import java.util.Date

private enum class NotifCategory(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Notifications),
    REQUESTS("Aid Requests", Icons.Default.Inventory2),
    INCIDENTS("Incidents & Insurance", Icons.Default.Security),
    ADVISORIES("Advisories", Icons.Default.Campaign),
    ACCOUNT("Account & Support", Icons.Default.VerifiedUser)
}

private fun resolveCategory(rawType: String?, title: String, message: String): NotifCategory {
    val hay = "${rawType.orEmpty()} $title $message".lowercase()
    return when {
        hay.contains("request") || hay.contains("slip") || hay.contains("distribut") || hay.contains("seed") || hay.contains("fertilizer") ->
            NotifCategory.REQUESTS
        hay.contains("incident") || hay.contains("insurance") || hay.contains("report") || hay.contains("pcic") || hay.contains("claim") || hay.contains("damage") ->
            NotifCategory.INCIDENTS
        hay.contains("account") || hay.contains("verif") || hay.contains("profile") || hay.contains("ticket") || hay.contains("support") || hay.contains("feedback") ->
            NotifCategory.ACCOUNT
        else -> NotifCategory.ADVISORIES
    }
}

private fun extractTimestampMillis(raw: Map<String, Any>): Long {
    val ts = raw["timestamp"] ?: raw["createdAt"]
    return when (ts) {
        is Timestamp -> ts.toDate().time
        is Date -> ts.time
        is Number -> ts.toLong()
        else -> 0L
    }
}

private fun formatNotifDate(millis: Long): String {
    if (millis <= 0L) return "Just now"
    return DateFormat.format("MMM dd, yyyy • h:mm a", Date(millis)).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    userViewModel: UserViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val authUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val sessionUid = remember { SessionManager.getSavedUserId(context) ?: "" }
    val vmDocId = userViewModel?.farmerDocId?.value ?: ""

    val candidateIds = remember(authUid, sessionUid, vmDocId) {
        listOf(authUid, sessionUid, vmDocId).filter { it.isNotBlank() }.distinct()
    }

    var notifications by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(NotifCategory.ALL) }
    var unreadOnly by remember { mutableStateOf(false) }
    var selectedNotif by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showClearReadDialog by remember { mutableStateOf(false) }

    DisposableEffect(candidateIds) {
        val listeners = mutableListOf<ListenerRegistration>()
        val merged = mutableMapOf<String, Map<String, Any>>()

        if (candidateIds.isEmpty()) {
            isLoading = false
            return@DisposableEffect onDispose {}
        }

        fun publishList() {
            notifications = merged.values
                .sortedByDescending { extractTimestampMillis(it) }
            isLoading = false
        }

        val queryFields = listOf("targetUserId", "recipientId", "userId")
        for (cid in candidateIds) {
            for (field in queryFields) {
                listeners.add(
                    db.collection("notifications")
                        .whereEqualTo(field, cid)
                        .addSnapshotListener { snap, _ ->
                            if (snap != null) {
                                for (doc in snap.documents) {
                                    merged[doc.id] = (doc.data ?: emptyMap()) + ("id" to doc.id)
                                }
                                // Remove deleted docs that previously matched (cid, field)
                                val activeIds = snap.documents.map { it.id }.toSet()
                                val toPrune = merged.entries
                                    .filter { (_, data) -> (data[field]?.toString() == cid) && !activeIds.contains(data["id"]?.toString()) }
                                    .map { it.key }
                                toPrune.forEach { key -> merged.remove(key) }
                                publishList()
                            } else {
                                isLoading = false
                            }
                        }
                )
            }
        }

        onDispose {
            listeners.forEach { it.remove() }
        }
    }

    val unreadCount = remember(notifications) {
        notifications.count { (it["read"] as? Boolean) != true }
    }
    val readCount = remember(notifications, unreadCount) {
        notifications.size - unreadCount
    }

    val categoryCounts = remember(notifications) {
        val counts = mutableMapOf<NotifCategory, Int>()
        NotifCategory.entries.forEach { counts[it] = 0 }
        counts[NotifCategory.ALL] = notifications.size
        notifications.forEach { n ->
            val cat = resolveCategory(
                n["type"]?.toString(),
                n["title"]?.toString().orEmpty(),
                n["message"]?.toString().orEmpty()
            )
            counts[cat] = (counts[cat] ?: 0) + 1
        }
        counts
    }

    val filteredNotifications = remember(notifications, selectedCategory, unreadOnly) {
        notifications.filter { n ->
            val catMatch = selectedCategory == NotifCategory.ALL || resolveCategory(
                n["type"]?.toString(),
                n["title"]?.toString().orEmpty(),
                n["message"]?.toString().orEmpty()
            ) == selectedCategory
            val isUnread = (n["read"] as? Boolean) != true
            val readMatch = !unreadOnly || isUnread
            catMatch && readMatch
        }
    }

    fun setNotificationReadState(notifId: String, markRead: Boolean) {
        if (notifId.isBlank()) return
        db.collection("notifications").document(notifId)
            .update("read", markRead)
            .addOnFailureListener { err ->
                Toast.makeText(context, "Unable to update notification: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun deleteNotification(notifId: String) {
        if (notifId.isBlank()) return
        db.collection("notifications").document(notifId)
            .delete()
            .addOnSuccessListener {
                if ((selectedNotif?.get("id")?.toString()) == notifId) {
                    selectedNotif = null
                }
                Toast.makeText(context, context.getString(R.string.notification_deleted_toast), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { err ->
                Toast.makeText(context, "Delete failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun markAllAsRead() {
        val unreadIds = notifications
            .filter { (it["read"] as? Boolean) != true }
            .mapNotNull { it["id"]?.toString() }
        if (unreadIds.isEmpty()) return

        val batch = db.batch()
        unreadIds.forEach { id ->
            batch.update(db.collection("notifications").document(id), "read", true)
        }
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(context, context.getString(R.string.all_notifications_read_toast), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { err ->
                Toast.makeText(context, "Action failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    fun deleteAllRead() {
        val readIds = notifications
            .filter { (it["read"] as? Boolean) == true }
            .mapNotNull { it["id"]?.toString() }
        if (readIds.isEmpty()) return

        val batch = db.batch()
        readIds.forEach { id ->
            batch.delete(db.collection("notifications").document(id))
        }
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(context, context.getString(R.string.cleared_read_notifications_toast), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { err ->
                Toast.makeText(context, "Clear failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.notifications_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (unreadCount > 0) {
                                "$unreadCount unread • App, Email & SMS sync"
                            } else {
                                "All caught up • App, Email & SMS sync"
                            },
                            style = MaterialTheme.typography.labelSmall,
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
                    if (unreadCount > 0) {
                        TextButton(onClick = { markAllAsRead() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.mark_all_read), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (readCount > 0) {
                        IconButton(onClick = { showClearReadDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_read_notifications),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Filter Chips + Unread Toggle
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = unreadOnly,
                        onClick = { unreadOnly = !unreadOnly },
                        label = { Text("${stringResource(R.string.unread_only)} ($unreadCount)") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (unreadOnly) Icons.Default.MarkEmailUnread else Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                items(NotifCategory.entries) { cat ->
                    val count = categoryCounts[cat] ?: 0
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.label} ($count)") },
                        leadingIcon = {
                            Icon(cat.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (unreadOnly) {
                                stringResource(R.string.no_unread_notifications)
                            } else {
                                stringResource(R.string.no_notifications_in_category)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.notifications_email_sms_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotifications, key = { it["id"]?.toString() ?: it.hashCode().toString() }) { notif ->
                        val notifId = notif["id"]?.toString().orEmpty()
                        val title = notif["title"]?.toString().orEmpty().ifBlank { "DA Update" }
                        val message = notif["message"]?.toString().orEmpty()
                        val rawType = notif["type"]?.toString()
                        val isRead = (notif["read"] as? Boolean) == true
                        val cat = resolveCategory(rawType, title, message)
                        val timeText = formatNotifDate(extractTimestampMillis(notif))
                        val delivery = notif["deliveryStatus"] as? Map<*, *>

                        val accentColor = when (cat) {
                            NotifCategory.REQUESTS -> MaterialTheme.colorScheme.primary
                            NotifCategory.INCIDENTS -> MaterialTheme.colorScheme.error
                            NotifCategory.ACCOUNT -> Color(0xFF1976D2)
                            NotifCategory.ADVISORIES, NotifCategory.ALL -> Color(0xFFEF6C00)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .clickable {
                                    if (!isRead) setNotificationReadState(notifId, true)
                                    selectedNotif = notif
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRead) {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                }
                            ),
                            border = BorderStroke(
                                width = if (isRead) 1.dp else 1.5.dp,
                                color = if (isRead) {
                                    MaterialTheme.colorScheme.outlineVariant
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isRead) 1.dp else 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header row: icon, category chip, unread dot, timestamp
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = CircleShape,
                                            color = accentColor.copy(alpha = 0.14f),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = cat.icon,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = cat.label.uppercase(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor
                                            )
                                        }
                                        if (!isRead) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }

                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 20.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Footer row: delivery channels + quick Read/Unread & Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val emailTag = when (delivery?.get("email")?.toString()) {
                                        "sent" -> "Email ✓"
                                        "failed" -> "Email"
                                        else -> "Email"
                                    }
                                    val smsTag = when (delivery?.get("sms")?.toString()) {
                                        "sent" -> "SMS ✓"
                                        "failed" -> "SMS"
                                        else -> "SMS"
                                    }
                                    Text(
                                        text = "In-App • $emailTag • $smsTag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = { setNotificationReadState(notifId, !isRead) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isRead) Icons.Default.MarkEmailUnread else Icons.Default.Done,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(if (isRead) R.string.mark_unread else R.string.mark_read),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        IconButton(
                                            onClick = { deleteNotification(notifId) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.delete_notification),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Expanded Full Notification Detail Dialog
    selectedNotif?.let { notif ->
        val notifId = notif["id"]?.toString().orEmpty()
        val title = notif["title"]?.toString().orEmpty().ifBlank { "DA Update" }
        val message = notif["message"]?.toString().orEmpty()
        val cat = resolveCategory(notif["type"]?.toString(), title, message)
        val timeText = formatNotifDate(extractTimestampMillis(notif))
        val isRead = (notif["read"] as? Boolean) == true

        AlertDialog(
            onDismissRequest = { selectedNotif = null },
            title = {
                Column {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = cat.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = message.ifBlank { "No additional message details." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Dispatched to: AgriServe App, Registered Email & Registered Cellphone SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { selectedNotif = null }) {
                    Text(stringResource(R.string.close_btn))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        setNotificationReadState(notifId, !isRead)
                        selectedNotif = null
                    }) {
                        Text(stringResource(if (isRead) R.string.mark_unread else R.string.mark_read))
                    }
                    TextButton(
                        onClick = { deleteNotification(notifId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete_notification))
                    }
                }
            }
        )
    }

    if (showClearReadDialog) {
        AlertDialog(
            onDismissRequest = { showClearReadDialog = false },
            title = { Text(stringResource(R.string.clear_read_notifications)) },
            text = { Text("Delete all $readCount already-read notification(s)? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearReadDialog = false
                        deleteAllRead()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_notification))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearReadDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
