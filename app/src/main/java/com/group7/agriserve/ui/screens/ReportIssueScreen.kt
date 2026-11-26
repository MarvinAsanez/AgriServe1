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
import androidx.compose.ui.res.stringResource
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
import com.group7.agriserve.R
import com.group7.agriserve.util.InsurancePdfHelper
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

private data class TaggedFarmChoice(
    val id: String,
    val farmName: String,
    val cropType: String,
    val farmSize: String,
    val farmAddress: String
)

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
    val hasVerifiedAccess = userViewModel.isVerified.value ||
        userViewModel.farmerStatus.value == "verified" ||
        userViewModel.role.value.equals("Admin", ignoreCase = true)

    if (!hasVerifiedAccess) {
        UnverifiedFarmerGate(
            onVerifyClick = onBack,
            onBack = onBack,
            title = stringResource(R.string.report_incident),
            featureRestrictionMessage = stringResource(R.string.incident_verified_only_desc)
        )
        return
    }

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
    var taggedFarms by remember { mutableStateOf<List<TaggedFarmChoice>>(emptyList()) }
    var isFarmsLoading by remember { mutableStateOf(true) }
    var selectedFarm by remember { mutableStateOf<TaggedFarmChoice?>(null) }
    var reportFormType by remember { mutableStateOf("") } // Determined by selected tagged farm ("CLAIM_INDEMNITY" or "HVCI")
    var cropType by remember { mutableStateOf("") }
    var varietyPlanted by remember { mutableStateOf("") }
    var dateOfLoss by remember { mutableStateOf(DateFormat.format("MM/dd/yyyy", java.util.Date()).toString()) }
    var expectedHarvestDate by remember { mutableStateOf("") }
    var extentOfDamage by remember { mutableStateOf("") }
    var policyNo by remember { mutableStateOf("") }
    var areaInsured by remember { mutableStateOf("") }
    var areaDamaged by remember { mutableStateOf("") }
    var plantingDate by remember { mutableStateOf("") }
    var cropStage by remember { mutableStateOf("") }
    var programType by remember { mutableStateOf("RSBSA") }
    var policyType by remember { mutableStateOf("High-Value Crop") }
    var adjacentNorth by remember { mutableStateOf("") }
    var adjacentSouth by remember { mutableStateOf("") }
    var adjacentEast by remember { mutableStateOf("") }
    var adjacentWest by remember { mutableStateOf("") }
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
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedInsuranceGuideReport by remember { mutableStateOf<Map<String, Any>?>(null) }
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

            // Load farmer's tagged farms (only their tagged farms appear as damage choices)
            isFarmsLoading = true
            firestore.collection("farms")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener { snap ->
                    val list = snap.documents.map { d ->
                        TaggedFarmChoice(
                            id = d.id,
                            farmName = d.getString("farmName") ?: "",
                            cropType = d.getString("cropType") ?: "",
                            farmSize = d.getString("farmSize") ?: "",
                            farmAddress = d.getString("farmAddress") ?: ""
                        )
                    }.toMutableList()
                    if (list.none { it.id == uid }) {
                        firestore.collection("farms").document(uid).get()
                            .addOnSuccessListener { legacy ->
                                if (legacy.exists()) {
                                    list.add(
                                        0,
                                        TaggedFarmChoice(
                                            id = legacy.id,
                                            farmName = legacy.getString("farmName") ?: "",
                                            cropType = legacy.getString("cropType") ?: "",
                                            farmSize = legacy.getString("farmSize") ?: "",
                                            farmAddress = legacy.getString("farmAddress") ?: ""
                                        )
                                    )
                                }
                                taggedFarms = list
                                isFarmsLoading = false
                            }
                            .addOnFailureListener {
                                taggedFarms = list
                                isFarmsLoading = false
                            }
                    } else {
                        taggedFarms = list
                        isFarmsLoading = false
                    }
                }
                .addOnFailureListener {
                    isFarmsLoading = false
                }
        } else {
            isHistoryLoading = false
            isFarmsLoading = false
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
        if (selectedFarm == null) {
            Toast.makeText(
                context,
                if (taggedFarms.isEmpty()) "Please tag your farm in Farm Tagging first before reporting." else "Please select which of your tagged farms was damaged.",
                Toast.LENGTH_LONG
            ).show()
            isUploading = false
            return
        }
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
            val autoDescription = reportDescription.ifBlank {
                if (reportFormType == "CLAIM_INDEMNITY") {
                    "$selectedCategory on $cropType${if (varietyPlanted.isNotBlank()) " ($varietyPlanted)" else ""} — ${extentOfDamage.ifBlank { "damaged" }} (${areaDamaged.ifBlank { "unspecified" }} ha)"
                } else {
                    "$selectedCategory on $cropType${if (varietyPlanted.isNotBlank()) " ($varietyPlanted)" else ""} — ${extentOfDamage.ifBlank { "damaged" }}"
                }
            }
            val reportData = hashMapOf(
                "reportId" to reportId,
                "farmerId" to uid,
                "userId" to uid,
                "farmerName" to farmerName,
                "contact" to contactNumber,
                "farmId" to (selectedFarm?.id ?: ""),
                "farmName" to (selectedFarm?.farmName ?: ""),
                "address" to (selectedFarm?.farmAddress ?: ""),
                "category" to selectedCategory,
                "severity" to selectedSeverity,
                "description" to autoDescription,
                "insuranceFormType" to reportFormType,
                "cropType" to cropType,
                "varietyPlanted" to varietyPlanted,
                "dateOfLoss" to dateOfLoss,
                "expectedHarvestDate" to expectedHarvestDate,
                "extentOfDamage" to extentOfDamage,
                "policyNo" to policyNo,
                "insurancePolicyNo" to policyNo,
                "areaInsured" to areaInsured,
                "areaDamaged" to areaDamaged,
                "plantingDate" to plantingDate,
                "cropStage" to cropStage,
                "programType" to programType,
                "policyType" to policyType,
                "adjacentNorth" to adjacentNorth,
                "adjacentSouth" to adjacentSouth,
                "adjacentEast" to adjacentEast,
                "adjacentWest" to adjacentWest,
                "imageUrls" to urls,
                "status" to "Pending",
                "isOffline" to offlineSaved,
                "syncStatus" to if (offlineSaved) "Queued Offline" else "Synced",
                "timestamp" to Timestamp.now(),
                "latitude" to (location?.latitude ?: liveLocation?.latitude ?: 0.0),
                "longitude" to (location?.longitude ?: liveLocation?.longitude ?: 0.0),
                "hasGeotag" to (location != null || liveLocation != null)
            )

            val writeTask = firestore.collection("reports").document(reportId).set(reportData)
            if (offlineSaved || !NetworkUtils.isOnline(context)) {
                isUploading = false
                Toast.makeText(
                    context,
                    "Report saved offline! Waiting for internet; will sync to DA automatically when back online.",
                    Toast.LENGTH_LONG
                ).show()
                reportDescription = ""
                selectedImageUris = emptyList()
                selectedTab = 1
            } else {
                writeTask
                    .addOnSuccessListener {
                        isUploading = false
                        Toast.makeText(context, "Incident Report Submitted Successfully!", Toast.LENGTH_LONG).show()
                        reportDescription = ""
                        selectedImageUris = emptyList()
                        selectedTab = 1
                    }
                    .addOnFailureListener {
                        isUploading = false
                        Toast.makeText(context, "Report stored offline. Will sync to DA automatically.", Toast.LENGTH_LONG).show()
                        reportDescription = ""
                        selectedImageUris = emptyList()
                        selectedTab = 1
                    }
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
        if (!NetworkUtils.isOnline(context)) {
            if (liveLocation != null) {
                performSubmission(liveLocation)
                return
            }
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { lastLoc ->
                        liveLocation = lastLoc
                        performSubmission(lastLoc)
                    }
                    .addOnFailureListener {
                        performSubmission(null)
                    }
            } catch (_: SecurityException) {
                performSubmission(null)
            }
            return
        }
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

    // Modal: PCIC Crop Insurance Filing Guide & Checklist
    selectedInsuranceGuideReport?.let { rep ->
        val category = rep["category"]?.toString() ?: ""
        val description = rep["description"]?.toString() ?: ""
        val rawFormType = rep["insuranceFormType"]?.toString()
        val formType = InsurancePdfHelper.detectFormType(rawFormType, category, description)
        val isIndemnity = formType == "CLAIM_INDEMNITY"
        val formTitle = if (isIndemnity) {
            "RC-CAS-01 — Claim for Indemnity (Rice & Corn)"
        } else {
            "HVCI CAS Form No. 01 — Notice of Loss (High-Value Crops)"
        }
        val ts = rep["timestamp"] as? Timestamp
        val dateStr = if (ts != null) DateFormat.format("MM/dd/yyyy", ts.toDate()).toString() else ""
        val lat = rep["latitude"]
        val lng = rep["longitude"]
        val locStr = if (lat != null && lng != null) "$lat, $lng" else (rep["address"]?.toString() ?: "")

        AlertDialog(
            onDismissRequest = { selectedInsuranceGuideReport = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crop Insurance Filing Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Assigned Form: $formTitle",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text("1. How to Fill Up the Form", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (isIndemnity) {
                        Text(
                            text = "• Header: Write the current Date and PCIC Regional Office.\n" +
                                "• Part I (Basic Info): Fill in your Full Name, Address, Phone, Farm Location, check Palay or Corn, Area Insured (hectares), Variety Planted, Actual Planting Date, CIC Number, Cooperative/Underwriter, and check RSBSA (or your program).\n" +
                                "• Part II (Damage Indicators): Enter Cause of Loss (e.g., Typhoon, Pest), Date of Loss, Age/Stage of Crop, Area Damaged (ha), Extent of Damage (%), and Expected Harvest Date.\n" +
                                "• Part III (Location Sketch Plan): Write the names of adjacent farm owners on the North, South, East, and West sides of each lot.\n" +
                                "• Sign over your printed name at the bottom.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "• Header: Enter your PCIC Regional Office No. and current Date.\n" +
                                "• Policy Paragraph: Fill in Type of Policy (High-Value Crop), Insurance Policy No., and Crop Variety.\n" +
                                "• Particulars (1–6): Verify/complete Farm Location, Nature/Cause of Loss, Date & Time of Loss, Extent of Loss/Damage, Expected Date of Harvest (if applicable), and Contact Number.\n" +
                                "• Sign over your printed name as Insured/Claimant.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    HorizontalDivider()
                    Text("2. Where to Submit the Form", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Step 1 — Municipal Agriculture Office (MAO): Submit the signed form to your assigned DA Agricultural Technician / Municipal Agriculturist for official verification, damage validation, and endorsement.\n\n" +
                            "Step 2 — Philippine Crop Insurance Corporation (PCIC) Regional Office: Once endorsed by MAO, submit the packet to the PCIC Regional Office / Claims & Adjustment Division (or via your MAO coordinator) for adjuster dispatch.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider()
                    Text("3. What to Bring Before Submitting", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "✓ Printed & signed PCIC Form ($formTitle)\n" +
                            "✓ Valid Government-Issued ID & RSBSA Enrollment ID/Stub\n" +
                            "✓ PCIC Certificate of Insurance Coverage (CIC) / Policy Number\n" +
                            "✓ Geotagged Field Damage Photos (captured in AgriServe)\n" +
                            "✓ Resolved Incident Report Reference / Barangay or MAO Endorsement\n" +
                            "✓ Ballpen (blue/black ink) for on-site verification signatures",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        InsurancePdfHelper.generateAndOpenForm(
                            context,
                            InsurancePdfHelper.InsuranceFormData(
                                formType = formType,
                                farmerName = rep["farmerName"]?.toString() ?: userViewModel.farmerName.value,
                                contact = rep["contact"]?.toString() ?: userViewModel.farmerPhone.value,
                                farmLocation = locStr,
                                cropCategory = category,
                                causeOfLoss = description,
                                dateOfLoss = dateStr,
                                regionalOffice = rep["insuranceRegionalOffice"]?.toString() ?: "",
                                policyNo = rep["insurancePolicyNo"]?.toString() ?: rep["policyNo"]?.toString() ?: "",
                                daNotes = rep["insuranceNotes"]?.toString() ?: "",
                                varietyPlanted = rep["varietyPlanted"]?.toString() ?: "",
                                areaInsured = rep["areaInsured"]?.toString() ?: "",
                                areaDamaged = rep["areaDamaged"]?.toString() ?: "",
                                plantingDate = rep["plantingDate"]?.toString() ?: "",
                                cropStage = rep["cropStage"]?.toString() ?: "",
                                programType = rep["programType"]?.toString() ?: "RSBSA",
                                expectedHarvestDate = rep["expectedHarvestDate"]?.toString() ?: "",
                                extentOfDamage = rep["extentOfDamage"]?.toString() ?: "",
                                policyType = rep["policyType"]?.toString() ?: "High-Value Crop",
                                adjacentNorth = rep["adjacentNorth"]?.toString() ?: "",
                                adjacentSouth = rep["adjacentSouth"]?.toString() ?: "",
                                adjacentEast = rep["adjacentEast"]?.toString() ?: "",
                                adjacentWest = rep["adjacentWest"]?.toString() ?: ""
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInsuranceGuideReport = null }) {
                    Text("Close")
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

                                // 1. Select Damaged Tagged Farm (only the farmer's tagged farms are shown; none pre-selected)
                                Text(
                                    text = "Select Damaged Farm *",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isFarmsLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Loading your tagged farms...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                } else if (taggedFarms.isEmpty()) {
                                    Surface(
                                        color = Color(0xFFFFF3E0),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "No tagged farms found. Please tag your farm(s) in Profile → Tag My Farm first so you can select which farm was damaged.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFE65100),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        taggedFarms.forEachIndexed { idx, farm ->
                                            val isPicked = selectedFarm?.id == farm.id
                                            val detectedType = InsurancePdfHelper.detectFormType(null, farm.cropType, farm.farmName)
                                            val displayName = farm.farmName.ifBlank { "Tagged Farm #${idx + 1}" }
                                            val subtitle = listOfNotNull(
                                                farm.cropType.takeIf { it.isNotBlank() },
                                                farm.farmSize.takeIf { it.isNotBlank() }?.let { "$it ha" }
                                            ).joinToString(" • ")

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedFarm = farm
                                                        cropType = farm.cropType.ifBlank { displayName }
                                                        reportFormType = detectedType
                                                        areaInsured = farm.farmSize
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isPicked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = BorderStroke(
                                                    width = if (isPicked) 1.5.dp else 1.dp,
                                                    color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (subtitle.isNotBlank()) {
                                                            Text(
                                                                text = subtitle,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Surface(
                                                        color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = if (detectedType == "CLAIM_INDEMNITY") "Claim for Indemnity" else "HVCI Form",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isPicked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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

                                // Extent of Damage Quick Chips
                                Text(
                                    text = "Extent / Degree of Damage",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("25%", "50%", "75%", "100%").forEach { pct ->
                                        FilterChip(
                                            selected = extentOfDamage == pct,
                                            onClick = { extentOfDamage = pct },
                                            label = { Text(pct, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = varietyPlanted,
                                        onValueChange = { varietyPlanted = it },
                                        label = { Text("Variety Planted") },
                                        placeholder = { Text(if (reportFormType == "CLAIM_INDEMNITY") "e.g. NSIC Rc222" else "e.g. Lakatan") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = dateOfLoss,
                                        onValueChange = { dateOfLoss = it },
                                        label = { Text("Date of Loss") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = policyNo,
                                        onValueChange = { policyNo = it },
                                        label = { Text(if (reportFormType == "CLAIM_INDEMNITY") "CIC No. (Opt.)" else "Policy No. (Opt.)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = expectedHarvestDate,
                                        onValueChange = { expectedHarvestDate = it },
                                        label = { Text("Harvest Date (Opt.)") },
                                        placeholder = { Text("MM/DD/YYYY") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                if (reportFormType == "CLAIM_INDEMNITY") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = areaInsured,
                                            onValueChange = { areaInsured = it },
                                            label = { Text("Area Insured (ha)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = areaDamaged,
                                            onValueChange = { areaDamaged = it },
                                            label = { Text("Area Damaged (ha)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }

                                    Text(
                                        text = "Crop Stage at Time of Loss",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(listOf("Seedling", "Vegetative", "Flowering", "Maturity")) { stg ->
                                            FilterChip(
                                                selected = cropStage == stg,
                                                onClick = { cropStage = stg },
                                                label = { Text(stg, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Program",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(listOf("RSBSA", "Regular", "Sikat Saka", "Punla", "Others")) { prog ->
                                            FilterChip(
                                                selected = programType == prog,
                                                onClick = { programType = prog },
                                                label = { Text(prog, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = adjacentNorth,
                                            onValueChange = { adjacentNorth = it },
                                            label = { Text("North Neighbor (Opt.)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = adjacentSouth,
                                            onValueChange = { adjacentSouth = it },
                                            label = { Text("South Neighbor (Opt.)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = adjacentEast,
                                            onValueChange = { adjacentEast = it },
                                            label = { Text("East Neighbor (Opt.)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = adjacentWest,
                                            onValueChange = { adjacentWest = it },
                                            label = { Text("West Neighbor (Opt.)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                // Description Field (Optional extra notes — auto-summarized from form above)
                                OutlinedTextField(
                                    value = reportDescription,
                                    onValueChange = { reportDescription = it },
                                    label = { Text("Additional Notes (Optional — auto-filled from form)") },
                                    placeholder = { Text("Optional extra field observations...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
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
                                    text = "Live camera capture only — take clear close-up photos of infested crops, pest specimens, or damaged farmland to expedite DA evaluation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Take Photo Button Card (Camera-only for real-time evidence)
                                    if (selectedImageUris.size < 5) {
                                        item {
                                            Surface(
                                                modifier = Modifier
                                                    .size(110.dp)
                                                    .clickable {
                                                        val uri = createImageUri()
                                                        if (uri != null) {
                                                            tempImageUri = uri
                                                            cameraLauncher.launch(uri)
                                                        }
                                                    },
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
                                                        imageVector = Icons.Default.PhotoCamera,
                                                        contentDescription = "Take Photo",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Take Photo",
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
                            enabled = !isUploading && selectedImageUris.isNotEmpty() && selectedFarm != null,
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

                                            // Crop Insurance Eligibility & Downloadable Form (when Resolved)
                                            if (status.equals("Resolved", ignoreCase = true)) {
                                                val rawFormType = rep["insuranceFormType"]?.toString()
                                                val formType = InsurancePdfHelper.detectFormType(rawFormType, category, description)
                                                val isIndemnity = formType == "CLAIM_INDEMNITY"
                                                val formLabel = if (isIndemnity) {
                                                    "RC-CAS-01 Claim for Indemnity (Rice & Corn)"
                                                } else {
                                                    "HVCI CAS Form No. 01 (High-Value Crops)"
                                                }
                                                val insStatus = rep["insuranceStatus"]?.toString() ?: "Eligible"
                                                val insNotes = rep["insuranceNotes"]?.toString() ?: ""
                                                val insDateStr = if (timestamp != null) DateFormat.format("MM/dd/yyyy", timestamp.toDate()).toString() else ""
                                                val insLat = rep["latitude"]
                                                val insLng = rep["longitude"]
                                                val insLoc = if (insLat != null && insLng != null) "$insLat, $insLng" else (rep["address"]?.toString() ?: "")

                                                Surface(
                                                    color = Color(0xFFE8F5E9).copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.35f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                Icons.Default.VerifiedUser,
                                                                contentDescription = null,
                                                                tint = Color(0xFF1B5E20),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "PCIC Crop Insurance ($insStatus)",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF1B5E20)
                                                            )
                                                        }

                                                        Text(
                                                            text = "Form: $formLabel",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF1B5E20)
                                                        )

                                                        if (insNotes.isNotBlank()) {
                                                            Text(
                                                                text = insNotes,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color(0xFF2E7D32)
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Button(
                                                                onClick = {
                                                                    InsurancePdfHelper.generateAndOpenForm(
                                                                        context,
                                                                        InsurancePdfHelper.InsuranceFormData(
                                                                            formType = formType,
                                                                            farmerName = rep["farmerName"]?.toString() ?: userViewModel.farmerName.value,
                                                                            contact = rep["contact"]?.toString() ?: userViewModel.farmerPhone.value,
                                                                            farmLocation = insLoc,
                                                                            cropCategory = category,
                                                                            causeOfLoss = description,
                                                                            dateOfLoss = insDateStr,
                                                                            regionalOffice = rep["insuranceRegionalOffice"]?.toString() ?: "",
                                                                            policyNo = rep["insurancePolicyNo"]?.toString() ?: rep["policyNo"]?.toString() ?: "",
                                                                            daNotes = insNotes,
                                                                            varietyPlanted = rep["varietyPlanted"]?.toString() ?: "",
                                                                            areaInsured = rep["areaInsured"]?.toString() ?: "",
                                                                            areaDamaged = rep["areaDamaged"]?.toString() ?: "",
                                                                            plantingDate = rep["plantingDate"]?.toString() ?: "",
                                                                            cropStage = rep["cropStage"]?.toString() ?: "",
                                                                            programType = rep["programType"]?.toString() ?: "RSBSA",
                                                                            expectedHarvestDate = rep["expectedHarvestDate"]?.toString() ?: "",
                                                                            extentOfDamage = rep["extentOfDamage"]?.toString() ?: "",
                                                                            policyType = rep["policyType"]?.toString() ?: "High-Value Crop",
                                                                            adjacentNorth = rep["adjacentNorth"]?.toString() ?: "",
                                                                            adjacentSouth = rep["adjacentSouth"]?.toString() ?: "",
                                                                            adjacentEast = rep["adjacentEast"]?.toString() ?: "",
                                                                            adjacentWest = rep["adjacentWest"]?.toString() ?: ""
                                                                        )
                                                                    )
                                                                },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Download Form", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                            }

                                                            OutlinedButton(
                                                                onClick = { selectedInsuranceGuideReport = rep },
                                                                modifier = Modifier.weight(1f),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                                border = BorderStroke(1.dp, Color(0xFF2E7D32))
                                                            ) {
                                                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF1B5E20))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Filing Guide", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1B5E20), maxLines = 1)
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
        }
    }
}
