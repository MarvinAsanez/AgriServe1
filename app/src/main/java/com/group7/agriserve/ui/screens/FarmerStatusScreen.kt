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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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

    // Gather candidate identifiers from multiple sources for bulletproof matching
    val authUid = auth.currentUser?.uid ?: ""
    val sessionUid = SessionManager.getSavedUserId(context) ?: ""
    val vmPhone = userViewModel?.phoneNumber?.value ?: ""
    val vmFarmerPhone = userViewModel?.farmerPhone?.value ?: ""
    val vmRsbsa = userViewModel?.rsbsaNumber?.value ?: ""
    val vmEmail = userViewModel?.farmerEmail?.value ?: ""
    val vmGmail = userViewModel?.gmail?.value ?: ""
    val farmerName = userViewModel?.farmerName?.value ?: ""

    // Candidate user IDs
    val vmDocId = userViewModel?.farmerDocId?.value ?: ""
    val candidateIds = remember(authUid, sessionUid, vmDocId) {
        listOf(authUid, sessionUid, vmDocId).filter { it.isNotBlank() }.distinct()
    }
    // Candidate phone numbers
    val candidatePhones = remember(vmPhone, vmFarmerPhone) {
        listOf(vmPhone, vmFarmerPhone).filter { it.isNotBlank() }.distinct()
    }
    // Candidate RSBSA
    val candidateRsbsa = remember(vmRsbsa) {
        listOf(vmRsbsa).filter { it.isNotBlank() && it != "RSBSA Enrolled" }.distinct()
    }
    // Candidate emails
    val candidateEmails = remember(auth.currentUser?.email, vmEmail, vmGmail) {
        listOfNotNull(auth.currentUser?.email, vmEmail, vmGmail).filter { it.isNotBlank() }.distinct()
    }

    var selectedTab by remember { mutableIntStateOf(if (initialFilter == "Disputes") 2 else initialTab) } // 0 = Requests, 1 = Incidents, 2 = Farm Disputes & Notices

    // Request state
    var myRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var requestFilter by remember { mutableStateOf(if (initialFilter != "Disputes") initialFilter else "All") }
    var isRequestsLoading by remember { mutableStateOf(true) }

    // Incident Report state
    var myReports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var reportFilter by remember { mutableStateOf("All") }
    var isReportsLoading by remember { mutableStateOf(true) }

    // Farm Disputes state (both reports against user's farm and reports filed by user)
    var myDisputes by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var disputeFilter by remember { mutableStateOf("All") }
    var isDisputesLoading by remember { mutableStateOf(true) }

    // Counter-Dispute Dialog state
    var disputeToContest by remember { mutableStateOf<Map<String, Any>?>(null) }
    var contestExplanation by remember { mutableStateOf("") }
    var contestEvidence by remember { mutableStateOf("") }
    var hasSwornDeclaration by remember { mutableStateOf(false) }
    var isSubmittingContest by remember { mutableStateOf(false) }

    // Multi-criteria real-time query listeners
    DisposableEffect(candidateIds, candidatePhones, candidateRsbsa, candidateEmails, farmerName) {
        val listeners = mutableListOf<ListenerRegistration>()
        val requestsMap = mutableMapOf<String, Map<String, Any>>()
        val reportsMap = mutableMapOf<String, Map<String, Any>>()
        val disputesMap = mutableMapOf<String, Map<String, Any>>()

        isRequestsLoading = true
        isReportsLoading = true
        isDisputesLoading = true

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

        fun updateDisputesList() {
            myDisputes = disputesMap.values.toList().sortedByDescending {
                it["timestamp"] as? Timestamp ?: (it["createdAt"] as? Timestamp) ?: Timestamp.now()
            }
            isDisputesLoading = false
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

        fun addDisputeListener(field: String, value: String, isAgainstMyFarm: Boolean) {
            if (value.isBlank()) return
            try {
                listeners.add(
                    db.collection("farm_disputes")
                        .whereEqualTo(field, value)
                        .addSnapshotListener { snap, err ->
                            if (err != null) {
                                isDisputesLoading = false
                                return@addSnapshotListener
                            }
                            if (snap != null) {
                                for (doc in snap.documents) {
                                    val d = doc.data ?: emptyMap<String, Any>()
                                    val extra = mutableMapOf<String, Any>(
                                        "id" to doc.id,
                                        "isDispute" to true
                                    )
                                    if (isAgainstMyFarm || d["farmUserId"] == value || candidateIds.contains(d["farmUserId"])) {
                                        extra["isReportAgainstMyFarm"] = true
                                    }
                                    disputesMap[doc.id] = d + extra
                                }
                                updateDisputesList()
                            }
                        }
                )
            } catch (e: Exception) {
                Log.w("FarmerStatus", "Dispute listener setup failed: ${e.message}")
            }
        }

        // Attach listeners across candidate user IDs
        for (id in candidateIds) {
            addRequestListener("farmerId", id)
            addRequestListener("userId", id)
            addReportListener("farmerId", id)
            addReportListener("userId", id)
            // Disputes filed by this farmer
            addDisputeListener("reporterId", id, isAgainstMyFarm = false)
            // Disputes reported AGAINST this farmer's farm
            addDisputeListener("farmUserId", id, isAgainstMyFarm = true)
            addDisputeListener("farmerId", id, isAgainstMyFarm = true)
        }

        // Attach listeners across candidate phone numbers
        for (phone in candidatePhones) {
            addRequestListener("farmerPhone", phone)
            addRequestListener("phoneNumber", phone)
            addReportListener("contactNumber", phone)
            addReportListener("phone", phone)
            addDisputeListener("reporterPhone", phone, isAgainstMyFarm = false)
            addDisputeListener("farmOwnerPhone", phone, isAgainstMyFarm = true)
        }

        // Attach listeners across candidate RSBSA
        for (rsbsa in candidateRsbsa) {
            addRequestListener("rsbsaNumber", rsbsa)
        }

        // Attach listeners across candidate Emails
        for (email in candidateEmails) {
            addRequestListener("farmerEmail", email)
            addRequestListener("email", email)
        }

        // Attach listener for farmer name matching farmOwner
        if (farmerName.isNotBlank()) {
            addDisputeListener("farmOwner", farmerName, isAgainstMyFarm = true)
        }

        if (candidateIds.isEmpty() && candidatePhones.isEmpty() && candidateRsbsa.isEmpty() && candidateEmails.isEmpty()) {
            isRequestsLoading = false
            isReportsLoading = false
            isDisputesLoading = false
        }

        onDispose {
            listeners.forEach { it.remove() }
        }
    }

    // Number of active reports against this farmer's farm requiring attention
    val activeReportsAgainstMeCount = remember(myDisputes) {
        myDisputes.count {
            (it["isReportAgainstMyFarm"] == true) && (it["farmerDisputed"] != true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Status & Agricultural Aid",
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
                            Text("Requests", fontSize = 13.sp)
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
                            Text("Incidents", fontSize = 13.sp)
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
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (activeReportsAgainstMeCount > 0) Icons.Default.Warning else Icons.Default.Gavel,
                                contentDescription = null,
                                tint = if (activeReportsAgainstMeCount > 0) Color(0xFFE65100) else LocalContentColor.current,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Disputes",
                                fontSize = 13.sp,
                                color = if (activeReportsAgainstMeCount > 0) Color(0xFFE65100) else Color.Unspecified
                            )
                            if (activeReportsAgainstMeCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$activeReportsAgainstMeCount",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else if (myDisputes.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${myDisputes.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
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
                2 -> {
                    FarmDisputesTab(
                        disputes = myDisputes,
                        currentFilter = disputeFilter,
                        onFilterChange = { disputeFilter = it },
                        isLoading = isDisputesLoading,
                        onContestClick = { dispute ->
                            disputeToContest = dispute
                            contestExplanation = ""
                            contestEvidence = ""
                            hasSwornDeclaration = false
                        }
                    )
                }
            }
        }
    }

    // Modal Counter-Dispute Dialog for Registered Farmer
    if (disputeToContest != null) {
        val targetDispute = disputeToContest!!
        val farmName = (targetDispute["farmName"] ?: "Disputed Farm Parcel").toString()
        val category = (targetDispute["category"] ?: targetDispute["reportCategory"] ?: "Land Dispute").toString()
        val complainantClaim = (targetDispute["description"] ?: "No explanation provided").toString()

        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingContest) disputeToContest = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Contest False / Misleading Report",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Disputed Farm: $farmName", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text("Reported Violation: $category", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Complainant Claim: '$complainantClaim'",
                                fontSize = 11.5.sp,
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "As the registered farmer/claimant, submit your official defense to the DA Agrarian Unit explaining why this report is fraudulent, inaccurate, or malicious:",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = contestExplanation,
                        onValueChange = { contestExplanation = it },
                        label = { Text("Why is this report false or misleading? *") },
                        placeholder = {
                            Text(
                                "e.g. I am the legitimate registered owner/tenant of this farm under Title/Tax Dec #... The complainant has no legitimate boundary claim.",
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = contestEvidence,
                        onValueChange = { contestEvidence = it },
                        label = { Text("Supporting Proof / Land Title Ref") },
                        placeholder = { Text("e.g. OCT/TCT # 48291, Tax Dec # 2024-001, BARC Certification", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (hasSwornDeclaration) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (hasSwornDeclaration) Color(0xFF4CAF50) else Color.LightGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { hasSwornDeclaration = !hasSwornDeclaration }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasSwornDeclaration,
                                onCheckedChange = { hasSwornDeclaration = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I solemnly declare under penalty of perjury (RA 3815) that this defense statement and information are true, lawful, and accurate.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (hasSwornDeclaration) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contestExplanation.isBlank()) {
                            Toast.makeText(context, "Please enter your explanation statement", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!hasSwornDeclaration) {
                            Toast.makeText(context, "You must check the sworn declaration before submitting", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmittingContest = true
                        val docId = (targetDispute["id"] ?: "").toString()
                        val updates = hashMapOf<String, Any>(
                            "farmerDisputed" to true,
                            "farmerDisputeExplanation" to contestExplanation.trim(),
                            "farmerDisputeEvidence" to contestEvidence.trim(),
                            "farmerDisputeTimestamp" to Timestamp.now(),
                            "status" to "Contested by Farm Owner",
                            "lastUpdated" to Timestamp.now()
                        )
                        db.collection("farm_disputes").document(docId).set(updates, SetOptions.merge())
                            .addOnSuccessListener {
                                isSubmittingContest = false
                                disputeToContest = null
                                contestExplanation = ""
                                contestEvidence = ""
                                hasSwornDeclaration = false
                                Toast.makeText(context, "Official counter-dispute submitted! The DA Agrarian Committee will review your defense.", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                isSubmittingContest = false
                                Toast.makeText(context, "Failed to submit defense: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    enabled = !isSubmittingContest && contestExplanation.isNotBlank() && hasSwornDeclaration,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSubmittingContest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("SUBMIT COUNTER-DISPUTE")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disputeToContest = null
                        contestExplanation = ""
                        contestEvidence = ""
                        hasSwornDeclaration = false
                    },
                    enabled = !isSubmittingContest
                ) {
                    Text("CANCEL")
                }
            }
        )
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

@Composable
fun FarmDisputesTab(
    disputes: List<Map<String, Any>>,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    isLoading: Boolean,
    onContestClick: (Map<String, Any>) -> Unit
) {
    val filterOptions = listOf("All", "Reported Against My Farm", "Reports Filed by Me")

    val filtered = disputes.filter { d ->
        when (currentFilter) {
            "Reported Against My Farm" -> d["isReportAgainstMyFarm"] == true
            "Reports Filed by Me" -> d["isReportAgainstMyFarm"] != true
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
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No active farm disputes or fraudulent reports on your account.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filtered) { dispute ->
                    val isAgainstMyFarm = dispute["isReportAgainstMyFarm"] == true
                    val farmName = (dispute["farmName"] ?: "Disputed Farm").toString()
                    val category = (dispute["category"] ?: dispute["reportCategory"] ?: "Land Dispute").toString()
                    val description = (dispute["description"] ?: "No description").toString()
                    val status = (dispute["status"] ?: "Pending Investigation").toString()
                    val isContested = dispute["farmerDisputed"] == true
                    val explanation = (dispute["farmerDisputeExplanation"] ?: "").toString()
                    val evidence = (dispute["farmerDisputeEvidence"] ?: "").toString()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (isAgainstMyFarm && !isContested) Color(0xFFFFB74D) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Notice
                            if (isAgainstMyFarm) {
                                Surface(
                                    color = if (isContested) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isContested) Color(0xFFA5D6A7) else Color(0xFFFFB74D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isContested) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isContested) Color(0xFF2E7D32) else Color(0xFFE65100),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (isContested) "REPORT CONTESTED BY YOU (ON FILE)" else "⚠️ REPORT FILED AGAINST YOUR FARM",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isContested) Color(0xFF2E7D32) else Color(0xFFE65100)
                                            )
                                            Text(
                                                text = if (isContested)
                                                    "Your official defense statement has been submitted to the DA Agrarian committee."
                                                else
                                                    "A complaint was filed regarding this farm. If this report is false, inaccurate, or fraudulent, you have the right to contest it.",
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = if (isContested) Color(0xFF1B5E20) else Color(0xFF4E342E)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = farmName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Violation Allegation: $category",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(
                                    color = if (isContested) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = status.uppercase(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isContested) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Complainant Claim Statement:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "\"$description\"",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // If farmer has already submitted counter-defense
                            if (isContested) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Your Official Defense Statement", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = explanation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (evidence.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "Title / Document Ref: $evidence", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                        }
                                    }
                                }
                            } else if (isAgainstMyFarm) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onContestClick(dispute) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CONTEST THIS REPORT / SUBMIT EVIDENCE", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            val timestamp = dispute["timestamp"] as? Timestamp ?: (dispute["createdAt"] as? Timestamp)
                            if (timestamp != null) {
                                val dateStr = android.text.format.DateFormat.format("MMM dd, yyyy - hh:mm a", timestamp.toDate())
                                Text(text = "Filed: $dateStr", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
