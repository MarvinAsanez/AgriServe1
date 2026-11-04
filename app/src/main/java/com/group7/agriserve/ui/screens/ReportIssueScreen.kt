package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.text.format.DateFormat
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onBack: () -> Unit,
    userViewModel: UserViewModel
) {
    val context = LocalContext.current
    val storage = remember { FirebaseStorage.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val savedUserId = SessionManager.getSavedUserId(context) ?: ""
    val uid = auth.currentUser?.uid?.ifBlank { savedUserId } ?: savedUserId

    // Tabs: 0 = Submit Report, 1 = My Reports
    var selectedTab by remember { mutableIntStateOf(0) }

    // Form States
    var contactNumber by remember {
        mutableStateOf(
            userViewModel.farmerPhone.value.ifBlank {
                userViewModel.phoneNumber.value
            }
        )
    }
    var selectedCategory by remember { mutableStateOf("Pest Infestation") }
    var selectedSeverity by remember { mutableStateOf("Moderate / Needs Aid") }
    var reportDescription by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    // GPS Location States
    var liveLocation by remember { mutableStateOf<Location?>(null) }
    var isDetectingLocation by remember { mutableStateOf(false) }

    // Reports History
    var myReports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var historyFilter by remember { mutableStateOf("All") }
    var isHistoryLoading by remember { mutableStateOf(true) }

    // Dialog & Preview States
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val categories = listOf(
        "Pest Infestation",
        "Crop Disease",
        "Typhoon / Flood",
        "Drought Damage",
        "Livestock Issue",
        "Land Boundary / Dispute",
        "Other Farm Damage"
    )

    val severityOptions = listOf(
        "Low / Monitoring",
        "Moderate / Needs Aid",
        "Severe / Emergency"
    )

    // Real-time synchronization of previous reports
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            isHistoryLoading = true
            firestore.collection("reports")
                .whereEqualTo("farmerId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data
                            if (data != null) {
                                data + ("id" to doc.id)
                            } else null
                        }.sortedByDescending {
                            it["timestamp"] as? Timestamp ?: Timestamp.now()
                        }
                        myReports = list
                    }
                    isHistoryLoading = false
                }
        } else {
            isHistoryLoading = false
        }
    }

    // Attempt to acquire initial location
    @SuppressLint("MissingPermission")
    fun updateLiveCoordinates() {
        isDetectingLocation = true
        try {
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    isDetectingLocation = false
                    if (loc != null) {
                        liveLocation = loc
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                            liveLocation = last
                        }
                    }
                }
                .addOnFailureListener {
                    isDetectingLocation = false
                    fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                        liveLocation = last
                    }
                }
        } catch (_: SecurityException) {
            isDetectingLocation = false
        }
    }

    LaunchedEffect(Unit) {
        updateLiveCoordinates()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            updateLiveCoordinates()
            Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val combined = (selectedImageUris + uris).distinct().take(5)
            selectedImageUris = combined
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                if (selectedImageUris.size < 5) {
                    selectedImageUris = selectedImageUris + uri
                }
            }
        }
    }

    fun createImageUri(): Uri? {
        val directory = File(context.cacheDir, "camera_reports")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "report_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun performSubmission(location: Location?) {
        if (selectedImageUris.isEmpty()) {
            Toast.makeText(context, "Please attach at least one photo of the incident", Toast.LENGTH_SHORT).show()
            isUploading = false
            return
        }

        val reportId = UUID.randomUUID().toString()
        val isOnline = NetworkUtils.isOnline(context)
        val farmerName = userViewModel.farmerName.value.ifBlank { "Registered Farmer" }

        // Generate compressed Base64 images for offline resilience
        val base64Images = mutableListOf<String>()
        selectedImageUris.forEach { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    val scaled = if (bmp.width > 600) {
                        val ratio = bmp.height.toFloat() / bmp.width.toFloat()
                        Bitmap.createScaledBitmap(bmp, 600, (600 * ratio).toInt(), true)
                    } else bmp
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                    base64Images.add("data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        fun saveReportToFirestore(urls: List<String>, offlineSaved: Boolean) {
            val reportData = hashMapOf(
                "reportId" to reportId,
                "farmerId" to uid,
                "userId" to uid,
                "farmerName" to farmerName,
                "contact" to contactNumber,
                "category" to selectedCategory,
                "severity" to selectedSeverity,
                "description" to reportDescription,
                "imageUrls" to urls,
                "status" to "Pending",
                "isOffline" to offlineSaved,
                "syncStatus" to if (offlineSaved) "Queued Offline" else "Synced",
                "timestamp" to Timestamp.now(),
                "latitude" to (location?.latitude ?: liveLocation?.latitude ?: 0.0),
                "longitude" to (location?.longitude ?: liveLocation?.longitude ?: 0.0),
                "hasGeotag" to (location != null || liveLocation != null)
            )

            firestore.collection("reports").document(reportId).set(reportData)
                .addOnSuccessListener {
                    isUploading = false
                    val msg = if (offlineSaved)
                        "Report Saved Locally! Will sync to DA when online."
                    else "Incident Report Submitted Successfully!"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                    // Reset Form
                    reportDescription = ""
                    selectedImageUris = emptyList()
                    selectedTab = 1 // Switch to history tab to view newly queued report
                }
                .addOnFailureListener {
                    isUploading = false
                    Toast.makeText(context, "Report stored offline. Will sync to DA automatically.", Toast.LENGTH_LONG).show()
                    reportDescription = ""
                    selectedImageUris = emptyList()
                    selectedTab = 1
                }
        }

        if (!isOnline) {
            // Save directly with Base64 photos offline
            saveReportToFirestore(base64Images, offlineSaved = true)
        } else {
            // Upload to Storage with fallback
            val uploadedUrls = mutableListOf<String>()
            var count = 0
            selectedImageUris.forEachIndexed { index, uri ->
                val ref = storage.reference.child("reports/$reportId/img_$index.jpg")
                ref.putFile(uri).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                        uploadedUrls.add(downloadUrl.toString())
                        count++
                        if (count == selectedImageUris.size) {
                            saveReportToFirestore(uploadedUrls, offlineSaved = false)
                        }
                    }.addOnFailureListener {
                        count++
                        if (count == selectedImageUris.size) {
                            saveReportToFirestore(base64Images, offlineSaved = true)
                        }
                    }
                }.addOnFailureListener {
                    count++
                    if (count == selectedImageUris.size) {
                        saveReportToFirestore(base64Images, offlineSaved = true)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureLocationAndSubmit() {
        isUploading = true
        try {
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location: Location? ->
                    val finalLoc = location ?: liveLocation
                    if (finalLoc != null) {
                        liveLocation = finalLoc
                        performSubmission(finalLoc)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            liveLocation = lastLoc
                            performSubmission(lastLoc)
                        }.addOnFailureListener {
                            performSubmission(null)
                        }
                    }
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        liveLocation = lastLoc
                        performSubmission(lastLoc)
                    }.addOnFailureListener {
                        performSubmission(null)
                    }
                }
        } catch (_: SecurityException) {
            performSubmission(liveLocation)
        }
    }

    // Modal: Image Source Selection
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Evidence Source", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("Choose whether to snap a new photo with your camera or select from your gallery.") },
            confirmButton = {
                Button(
                    onClick = {
                        showImageSourceDialog = false
                        val uri = createImageUri()
                        if (uri != null) {
                            tempImageUri = uri
                            cameraLauncher.launch(uri)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showImageSourceDialog = false
                        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse Gallery")
                }
            }
        )
    }

    // Modal: Full-screen Image Zoom Preview
    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Incident Photo Preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { previewImageUrl = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = previewImageUrl,
                        contentDescription = "Photo Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { previewImageUrl = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Report Incident",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Department of Agriculture • Farm Damage & Pest Desk",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Screen Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Submit Incident", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            if (myReports.isNotEmpty()) "My Reports (${myReports.size})" else "My Reports",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }

            AnimatedContent(
                targetState = selectedTab,
                label = "TabContent"
            ) { targetTab ->
                if (targetTab == 0) {
                    // SUBMIT INCIDENT TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. DA OFFICIAL BANNER
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Municipal Agriculture Incident Desk",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Report pest attacks, crop diseases, or typhoon/flood damage. Geotagged reports enable rapid DA verification and relief intervention.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 2. CARD 1: FARMER & CONTACT DETAILS
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Farmer & Reporter Details",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = userViewModel.farmerName.value.ifBlank { "Registered Farmer" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val rsbsa = userViewModel.rsbsaNumber.value
                                            Text(
                                                text = if (rsbsa.isNotBlank()) "RSBSA: $rsbsa" else "Department of Agriculture Registered",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Verified Profile",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = contactNumber,
                                    onValueChange = { contactNumber = it },
                                    label = { Text("Contact Mobile Number") },
                                    placeholder = { Text("09XXXXXXXXX") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // 3. CARD 2: INCIDENT CLASSIFICATION & DETAILS
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Incident Classification & Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Category Dropdown
                                ExposedDropdownMenuBox(
                                    expanded = categoryExpanded,
                                    onExpandedChange = { categoryExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategory,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Incident Category") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                        leadingIcon = { Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = categoryExpanded,
                                        onDismissRequest = { categoryExpanded = false }
                                    ) {
                                        categories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat) },
                                                onClick = {
                                                    selectedCategory = cat
                                                    categoryExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Severity Selection
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Severity / Impact Level",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        severityOptions.forEach { sev ->
                                            val isSelected = selectedSeverity == sev
                                            val chipColor = when {
                                                sev.contains("Severe") -> if (isSelected) Color(0xFFD32F2F) else Color(0xFFFFEBEE)
                                                sev.contains("Moderate") -> if (isSelected) Color(0xFFEF6C00) else Color(0xFFFFF3E0)
                                                else -> if (isSelected) Color(0xFF2E7D32) else Color(0xFFE8F5E9)
                                            }
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedSeverity = sev },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                border = BorderStroke(1.dp, if (isSelected) chipColor else Color.Transparent)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = sev.substringBefore(" /"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Description Field
                                OutlinedTextField(
                                    value = reportDescription,
                                    onValueChange = { reportDescription = it },
                                    label = { Text("Describe Symptoms, Affected Crops or Field Area") },
                                    placeholder = { Text("e.g., Brown planthopper infestation observed in south sector palay field. Approximately 1.5 hectares affected.") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // 4. CARD 3: PHOTO EVIDENCE UPLOAD
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Field Photo Evidence",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${selectedImageUris.size}/5 Attached",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedImageUris.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "Clear close-up photos of infested crops, pest specimens, or damaged farmland expedite DA evaluation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Add Photo Button Card
                                    if (selectedImageUris.size < 5) {
                                        item {
                                            Surface(
                                                modifier = Modifier
                                                    .size(110.dp)
                                                    .clickable { showImageSourceDialog = true },
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AddAPhoto,
                                                        contentDescription = "Add Photo",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Add Photo",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Uploaded Photos Thumbnails
                                    items(selectedImageUris) { uri ->
                                        Box(modifier = Modifier.size(110.dp)) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Evidence photo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { previewImageUrl = uri.toString() },
                                                contentScale = ContentScale.Crop
                                            )
                                            // Zoom icon badge
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp)
                                                    .size(22.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ZoomIn,
                                                    contentDescription = "Zoom",
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(3.dp)
                                                )
                                            }
                                            // Delete icon badge
                                            IconButton(
                                                onClick = { selectedImageUris = selectedImageUris - uri },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. CARD 4: GEOTAGGING & LOCATION
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Farm GPS Geotag",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                            updateLiveCoordinates()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh GPS",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (liveLocation != null) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (liveLocation != null) Icons.Default.CheckCircle else Icons.Default.GpsFixed,
                                            contentDescription = null,
                                            tint = if (liveLocation != null) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (liveLocation != null) {
                                                val lat = String.format("%.5f", liveLocation!!.latitude)
                                                val lng = String.format("%.5f", liveLocation!!.longitude)
                                                Text(
                                                    text = "GPS Coordinates Attached ($lat, $lng)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                Text(
                                                    text = "Precision geotag verified for ground intervention.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF388E3C)
                                                )
                                            } else {
                                                Text(
                                                    text = if (isDetectingLocation) "Acquiring GPS fix via satellite..." else "Coordinates will be captured on submission.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEF6C00)
                                                )
                                                Text(
                                                    text = "Tap refresh if you want to pre-lock satellite coordinates.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. OFFLINE NOTICE (IF OFFLINE)
                        val isOnline = NetworkUtils.isOnline(context)
                        if (!isOnline) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Offline Mode: Your report will be safely saved locally and automatically synchronized to the DA system once connection is restored.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // 7. SUBMIT BUTTON
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                captureLocationAndSubmit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isUploading && selectedImageUris.isNotEmpty() && reportDescription.isNotBlank(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Submitting Geotagged Report...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    if (isOnline) "SUBMIT GEOTAGGED INCIDENT REPORT" else "SAVE REPORT OFFLINE",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    // MY REPORTS TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Filter Chips
                        val filterOptions = listOf("All", "Pending", "Investigating", "Resolved")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filterOptions) { filter ->
                                FilterChip(
                                    selected = historyFilter == filter,
                                    onClick = { historyFilter = filter },
                                    label = { Text(filter) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }

                        val filteredReports = myReports.filter { rep ->
                            if (historyFilter == "All") true
                            else {
                                val status = (rep["status"] ?: "Pending").toString()
                                status.equals(historyFilter, ignoreCase = true)
                            }
                        }

                        if (isHistoryLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (filteredReports.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (historyFilter == "All") "No incidents reported yet." else "No reports with status: $historyFilter",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { selectedTab = 0 },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.AddAlert, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Report New Incident")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredReports) { rep ->
                                    val status = (rep["status"] ?: "Pending").toString()
                                    val category = (rep["category"] ?: "Farm Incident").toString()
                                    val description = (rep["description"] ?: "No description provided").toString()
                                    val isOff = rep["isOffline"] == true || rep["syncStatus"] == "Queued Offline"
                                    val hasGeotag = (rep["hasGeotag"] == true) || (rep["latitude"] != null && rep["latitude"] != 0.0)
                                    val imageUrls = (rep["imageUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                    val timestamp = rep["timestamp"] as? Timestamp

                                    val statusColor = when (status.lowercase()) {
                                        "resolved" -> Color(0xFF2E7D32)
                                        "investigating" -> Color(0xFF1976D2)
                                        else -> Color(0xFFEF6C00)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.BugReport,
                                                        contentDescription = null,
                                                        tint = statusColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = category,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    color = statusColor.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = status,
                                                        color = statusColor,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            // Photos row
                                            if (imageUrls.isNotEmpty()) {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(imageUrls) { imgUrl ->
                                                        AsyncImage(
                                                            model = imgUrl,
                                                            contentDescription = "Report Photo",
                                                            modifier = Modifier
                                                                .size(60.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable { previewImageUrl = imgUrl },
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }

                                            // Geotag & Offline Status Info
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (hasGeotag) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "GPS Geotagged",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.width(1.dp))
                                                }

                                                if (isOff) {
                                                    Surface(
                                                        color = Color(0xFFFFF3E0),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            "Saved Locally • Will sync",
                                                            color = Color(0xFFE65100),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                                            // Timestamp & Reviewer
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val dateText = if (timestamp != null) {
                                                    DateFormat.format("MMM dd, yyyy • hh:mm a", timestamp.toDate()).toString()
                                                } else "Recent"
                                                Text(
                                                    text = dateText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )

                                                val daResponse = rep["daResponse"] ?: rep["resolutionNotes"] ?: rep["clearedBy"]
                                                if (daResponse != null && daResponse.toString().isNotBlank()) {
                                                    Text(
                                                        text = "DA Action: ${daResponse.toString().take(25)}...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusColor,
                                                        fontWeight = FontWeight.Bold
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
        }
    }
}
