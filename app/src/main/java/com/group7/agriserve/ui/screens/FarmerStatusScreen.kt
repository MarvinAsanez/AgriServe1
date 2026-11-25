package com.group7.agriserve.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.google.firebase.firestore.SetOptions
import com.group7.agriserve.R
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerStatusScreen(
    userViewModel: UserViewModel? = null,
    initialFilter: String = "All",
    initialTab: Int = 0,
    onBack: () -> Unit,
    onRequestClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val isOnline = NetworkUtils.isOnline(context)

    // Gather authenticated user ID strictly to prevent phantom requests/reports
    val authUid = auth.currentUser?.uid ?: ""

    var selectedTab by remember { mutableIntStateOf(if (initialTab in 0..1) initialTab else 0) } // 0 = Requests, 1 = Incidents

    // Request state
    var myRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var requestFilter by remember { mutableStateOf(if (initialFilter != "Disputes") initialFilter else "All") }
    var isRequestsLoading by remember { mutableStateOf(true) }

    // Incident Report state
    var myReports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var reportFilter by remember { mutableStateOf("All") }
    var isReportsLoading by remember { mutableStateOf(true) }

    // Real-time query listeners strictly scoped to authenticated farmer ID
    DisposableEffect(authUid) {
        val listeners = mutableListOf<ListenerRegistration>()
        val requestsMap = mutableMapOf<String, Map<String, Any>>()
        val reportsMap = mutableMapOf<String, Map<String, Any>>()

        isRequestsLoading = true
        isReportsLoading = true

        if (authUid.isBlank()) {
            myRequests = emptyList()
            myReports = emptyList()
            isRequestsLoading = false
            isReportsLoading = false
            return@DisposableEffect onDispose {}
        }

        fun updateRequestsList() {
            myRequests = requestsMap.values.toList().sortedByDescending {
                it["timestamp"] as? Timestamp ?: (it["createdAt"] as? Timestamp) ?: Timestamp.now()
            }
            isRequestsLoading = false
        }

        fun updateReportsList() {
            myReports = reportsMap.values.toList().sortedByDescending {
                it["timestamp"] as? Timestamp ?: (it["createdAt"] as? Timestamp) ?: Timestamp.now()
            }
            isReportsLoading = false
        }

        fun addRequestListener(field: String, value: String) {
            if (value.isBlank()) return
            try {
                listeners.add(
                    db.collection("requests")
                        .whereEqualTo(field, value)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                isRequestsLoading = false
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                for (doc in snap.documents) {
                                    val d = doc.data ?: emptyMap<String, Any>()
                                    requestsMap[doc.id] = d + ("id" to doc.id)
                                }
                                updateRequestsList()
                            }
                        }
                )
            } catch (e: Exception) {
                Log.w("FarmerStatus", "Listener setup failed for requests: ${e.message}")
            }
        }

        fun addReportListener(field: String, value: String) {
            if (value.isBlank()) return
            try {
                listeners.add(
                    db.collection("reports")
                        .whereEqualTo(field, value)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                isReportsLoading = false
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                for (doc in snap.documents) {
                                    val d = doc.data ?: emptyMap<String, Any>()
                                    reportsMap[doc.id] = d + ("id" to doc.id)
                                }
                                updateReportsList()
                            }
                        }
                )
            } catch (e: Exception) {
                Log.w("FarmerStatus", "Report listener setup failed: ${e.message}")
            }
        }

        // Strictly query requests submitted by this authenticated farmer
        addRequestListener("farmerId", authUid)
        addRequestListener("userId", authUid)

        // Strictly query incident reports submitted by this authenticated farmer
        addReportListener("farmerId", authUid)
        addReportListener("userId", authUid)

        onDispose {
            listeners.forEach { it.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.status_aid_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isOnline) "Connected • Real-time DA sync" else "Offline • Showing cached records",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.requests_tab), fontSize = 13.sp)
                            if (myRequests.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${myRequests.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.incidents_tab), fontSize = 13.sp)
                            if (myReports.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${myReports.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    ResourceRequestsTab(
                        requests = myRequests,
                        currentFilter = requestFilter,
                        onFilterChange = { requestFilter = it },
                        isLoading = isRequestsLoading,
                        onRequestClick = onRequestClick
                    )
                }
                1 -> {
                    FarmIncidentsTab(
                        reports = myReports,
                        currentFilter = reportFilter,
                        onFilterChange = { reportFilter = it },
                        isLoading = isReportsLoading,
                        onReportClick = onReportClick
                    )
                }
            }
        }
    }
}

@Composable
fun ResourceRequestsTab(
    requests: List<Map<String, Any>>,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    isLoading: Boolean,
    onRequestClick: () -> Unit
) {
    val filterOptions = listOf("All", "Pending", "Approved", "Distributed", "Rejected")

    val filtered = requests.filter { req ->
        if (currentFilter == "All") return@filter true
        val statusField = (req["status"] ?: req["Status"] ?: "Pending").toString().trim()
        val isDistributed = (req["distributed"] == true) || (req["isDistributed"] == true) || statusField.equals("Distributed", ignoreCase = true) || req["distributed_at"] != null
        val isRejected = (req["rejected"] == true) || (req["isRejected"] == true) || statusField.equals("Rejected", ignoreCase = true) || statusField.equals("Denied", ignoreCase = true)
        val isApproved = !isRejected && !isDistributed && ((req["approved"] == true) || (req["isApproved"] == true) || statusField.equals("Approved", ignoreCase = true) || statusField.equals("Accepted", ignoreCase = true) || (req["processed_at"] != null && !statusField.equals("Rejected", ignoreCase = true)))

        when (currentFilter) {
            "Distributed" -> isDistributed
            "Approved" -> isApproved
            "Rejected" -> isRejected
            "Pending" -> (!isApproved && !isDistributed && !isRejected)
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { filter ->
                FilterChip(
                    selected = currentFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (currentFilter == "All") "No resource requests found." else "No requests with status: $currentFilter",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit New Request")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filtered) { req ->
                    RequestCard(req)
                }
            }
        }
    }
}

@Composable
fun RequestCard(req: Map<String, Any>) {
    val statusField = (req["status"] ?: req["Status"] ?: req["state"] ?: "Pending").toString().trim()
    val isDistributed = (req["distributed"] == true) || (req["isDistributed"] == true) || statusField.equals("Distributed", ignoreCase = true) || req["distributed_at"] != null
    val isApproved = (req["approved"] == true) || (req["isApproved"] == true) || statusField.equals("Approved", ignoreCase = true) || (req["processed_at"] != null && !isDistributed)
    val isRejected = (req["rejected"] == true) || (req["isRejected"] == true) || statusField.equals("Rejected", ignoreCase = true) || statusField.equals("Denied", ignoreCase = true)

    val status = when {
        isDistributed -> "Distributed"
        isRejected -> "Rejected"
        isApproved -> "Approved"
        else -> "Pending"
    }

    val itemName = (req["itemName"] ?: req["item_name"] ?: req["requestType"] ?: "Unknown Item").toString()
    val category = (req["requestType"] ?: req["category"] ?: "Resource").toString()
    val quantity = (req["quantity"] ?: "0").toString()

    val statusColor = when (status) {
        "Approved" -> Color(0xFF4CAF50)
        "Distributed" -> Color(0xFF2196F3)
        "Rejected" -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }

    val statusIcon = when (status) {
        "Approved" -> Icons.Default.CheckCircle
        "Distributed" -> Icons.Default.LocalShipping
        "Rejected" -> Icons.Default.Cancel
        else -> Icons.Default.HourglassEmpty
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Category: $category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = status.uppercase(),
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Requested Qty: $quantity", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val timestamp = req["timestamp"] as? Timestamp ?: (req["createdAt"] as? Timestamp)
                if (timestamp != null) {
                    val dateString = android.text.format.DateFormat.format("MMM dd, yyyy", timestamp.toDate())
                    Text(text = "Date: $dateString", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FarmIncidentsTab(
    reports: List<Map<String, Any>>,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    isLoading: Boolean,
    onReportClick: () -> Unit
) {
    val filterOptions = listOf("All", "Pending", "Investigating", "Resolved")

    val filtered = reports.filter { rep ->
        if (currentFilter == "All") return@filter true
        val statusField = (rep["status"] ?: "Pending").toString().trim()
        statusField.equals(currentFilter, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { filter ->
                FilterChip(
                    selected = currentFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (currentFilter == "All") "No farm issues or incidents reported yet." else "No reports with status: $currentFilter",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onReportClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report Farm Issue")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filtered) { rep ->
                    val status = (rep["status"] ?: "Pending").toString()
                    val description = (rep["description"] ?: "No description provided").toString()
                    val issueType = (rep["issueType"] ?: rep["category"] ?: "Farm Issue").toString()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(issueType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Surface(
                                    color = Color(0xFFE0F2FE),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = status.uppercase(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = description, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(8.dp))
                            val timestamp = rep["timestamp"] as? Timestamp ?: (rep["createdAt"] as? Timestamp)
                            if (timestamp != null) {
                                val dateStr = android.text.format.DateFormat.format("MMM dd, yyyy", timestamp.toDate())
                                Text(text = "Reported: $dateStr", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
