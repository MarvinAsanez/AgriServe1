package com.group7.agriserve.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.group7.agriserve.data.model.InventoryItem
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestResourceScreen(
    onBack: () -> Unit,
    userViewModel: UserViewModel,
    preSelectedType: String = "",
    preSelectedCategory: String = ""
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val savedUserId = SessionManager.getSavedUserId(context) ?: ""
    val currentUserId = auth.currentUser?.uid?.ifBlank { savedUserId } ?: savedUserId

    // Form inputs
    var itemName by remember { mutableStateOf(preSelectedType) }
    var selectedCategory by remember { mutableStateOf(preSelectedCategory) }
    var quantity by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("sacks") }
    var croppingSeason by remember { mutableStateOf("Wet Season Cropping 2026") }
    var intendedUsageDate by remember { mutableStateOf("Within Next 30 Days") }
    var selectedPurpose by remember { mutableStateOf("Regular Season Planting") }
    var description by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf(userViewModel.phoneNumber.value.ifBlank { userViewModel.farmerPhone.value }) }
    var termsAccepted by remember { mutableStateOf(true) }

    // Media states
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // Dropdown and UI states
    var productDropdownExpanded by remember { mutableStateOf(false) }
    var seasonDropdownExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Tagged farm data
    var farmName by remember { mutableStateOf<String?>(null) }

    // Default fallback catalog if offline
    val defaultCatalog = listOf(
        InventoryItem(item_name = "Certified Inbred Palay Seeds (RC 222)", category = "Seeds", quantity = 100, unit = "sacks"),
        InventoryItem(item_name = "Hybrid Palay Seeds (Mestiso 20)", category = "Seeds", quantity = 80, unit = "sacks"),
        InventoryItem(item_name = "White Corn Seeds (IPB Var 6)", category = "Seeds", quantity = 50, unit = "sacks"),
        InventoryItem(item_name = "Complete Fertilizer (14-14-14)", category = "Fertilizer", quantity = 150, unit = "bags"),
        InventoryItem(item_name = "Urea Fertilizer (46-0-0)", category = "Fertilizer", quantity = 120, unit = "bags"),
        InventoryItem(item_name = "Bio-Organic Microbial Fertilizer", category = "Fertilizer", quantity = 200, unit = "bags"),
        InventoryItem(item_name = "Irrigation Water Pump 3HP", category = "Machinery", quantity = 15, unit = "units"),
        InventoryItem(item_name = "Knapsack Power Sprayer 16L", category = "Machinery", quantity = 25, unit = "units"),
        InventoryItem(item_name = "Hand Tractor / Cultivator", category = "Machinery", quantity = 8, unit = "units")
    )

    var availableInventory by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    var isLoadingInventory by remember { mutableStateOf(true) }

    // Launchers for media
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedMediaUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri -> selectedMediaUri = uri }
        }
    }

    fun createImageUri(): Uri? {
        val directory = File(context.externalCacheDir, "camera_requests")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "request_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Load available inventory from Firestore
    LaunchedEffect(Unit) {
        firestore.collection("inventory")
            .whereGreaterThan("quantity", 0)
            .get()
            .addOnSuccessListener { result ->
                val fetched = result.documents.mapNotNull { doc ->
                    val name = doc.getString("item_name")
                        ?: doc.getString("name")
                        ?: doc.getString("itemName")
                        ?: ""
                    val cat = doc.getString("category") ?: "General"
                    val qty = doc.getLong("quantity")?.toInt()
                        ?: (doc.get("quantity") as? Number)?.toInt()
                        ?: 0
                    val unit = doc.getString("unit") ?: "units"

                    if (name.isNotBlank()) {
                        InventoryItem(id = doc.id, item_name = name, category = cat, quantity = qty, unit = unit)
                    } else null
                }
                availableInventory = if (fetched.isNotEmpty()) fetched else defaultCatalog
                isLoadingInventory = false

                // Pre-select matching item if requested from inventory page
                if (itemName.isNotEmpty()) {
                    val match = availableInventory.find { it.item_name.equals(itemName, ignoreCase = true) }
                    if (match != null) {
                        selectedCategory = match.category
                        selectedUnit = match.unit
                    }
                }
            }
            .addOnFailureListener {
                availableInventory = defaultCatalog
                isLoadingInventory = false
            }

        // Check if farm details exist in Firestore to auto-fill farm size
        if (currentUserId.isNotBlank()) {
            firestore.collection("farms").document(currentUserId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val fName = doc.getString("farmName")
                        val fSize = doc.getString("farmSize")
                        if (!fName.isNullOrBlank()) farmName = fName
                        if (!fSize.isNullOrBlank() && farmSize.isBlank()) {
                            farmSize = fSize
                        }
                    }
                }
        }
    }

    // Sync phone number if viewmodel loads later
    LaunchedEffect(userViewModel.phoneNumber.value, userViewModel.farmerPhone.value) {
        if (contactNumber.isBlank()) {
            contactNumber = userViewModel.phoneNumber.value.ifBlank { userViewModel.farmerPhone.value }
        }
    }

    // Modal image source selector
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Farmland Evidence Source", fontWeight = FontWeight.Bold) },
            text = { Text("Take an updated photo of your farm plot or select from your gallery.") },
            confirmButton = {
                Button(onClick = {
                    showImageSourceDialog = false
                    val uri = createImageUri()
                    if (uri != null) {
                        tempImageUri = uri
                        cameraLauncher.launch(uri)
                    }
                }) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showImageSourceDialog = false
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DA Resource Application",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Department of Agriculture • Farm Inputs & Subsidy",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. OFFICIAL DA SUBSIDY OVERVIEW BANNER
            DaSubsidyBanner()

            // 2. SECTION 1: FARMER & RSBSA IDENTIFICATION
            FarmerCredentialsCard(
                userViewModel = userViewModel,
                contactNumber = contactNumber,
                onContactChange = { contactNumber = it },
                farmName = farmName
            )

            // 3. SECTION 2: REQUESTED DA RESOURCE SELECTION
            RequestedResourceCard(
                itemName = itemName,
                selectedCategory = selectedCategory,
                selectedUnit = selectedUnit,
                availableInventory = availableInventory,
                isLoadingInventory = isLoadingInventory,
                expanded = productDropdownExpanded,
                onExpandedChange = { productDropdownExpanded = it },
                onSelectItem = { item ->
                    itemName = item.item_name
                    selectedCategory = item.category
                    selectedUnit = item.unit
                    productDropdownExpanded = false
                }
            )

            // 4. SECTION 3: QUANTITY, FARM SIZE & CROPPING SEASON
            AllocationAndSeasonCard(
                quantity = quantity,
                onQuantityChange = { quantity = it },
                farmSize = farmSize,
                onFarmSizeChange = { farmSize = it },
                selectedUnit = selectedUnit,
                croppingSeason = croppingSeason,
                onSeasonChange = { croppingSeason = it },
                seasonDropdownExpanded = seasonDropdownExpanded,
                onSeasonDropdownChange = { seasonDropdownExpanded = it },
                intendedUsageDate = intendedUsageDate,
                onIntendedDateChange = { intendedUsageDate = it }
            )

            // 5. SECTION 4: PURPOSE & NEED JUSTIFICATION
            PurposeAndRemarksCard(
                selectedPurpose = selectedPurpose,
                onPurposeSelected = { selectedPurpose = it },
                description = description,
                onDescriptionChange = { description = it }
            )

            // 6. SECTION 5: FARMLAND EVIDENCE PHOTO UPLOAD
            FarmEvidencePhotoCard(
                selectedMediaUri = selectedMediaUri,
                onUploadClick = { showImageSourceDialog = true },
                onRemovePhoto = { selectedMediaUri = null }
            )

            // 7. SECTION 6: RSBSA COMPLIANCE DECLARATION
            ComplianceDeclarationCard(
                accepted = termsAccepted,
                onCheckedChange = { termsAccepted = it }
            )

            // OFFLINE BANNER IF OFFLINE
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
                        Icon(Icons.Default.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Offline Mode: Your application will be stored locally and queued for synchronization as soon as internet connection is restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // SUBMIT BUTTON
            Button(
                onClick = {
                    if (itemName.isBlank()) {
                        Toast.makeText(context, "Please select an agricultural resource", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (quantity.isBlank() || (quantity.toIntOrNull() ?: 0) <= 0) {
                        Toast.makeText(context, "Please enter a valid requested quantity", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (farmSize.isBlank()) {
                        Toast.makeText(context, "Please specify your farm area in hectares", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedMediaUri == null) {
                        Toast.makeText(context, "Please upload photo evidence of your farmland", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!termsAccepted) {
                        Toast.makeText(context, "Please confirm the RSBSA compliance declaration", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Stock check
                    val chosenItem = availableInventory.find { it.item_name.equals(itemName, ignoreCase = true) }
                    val requestedQty = quantity.toIntOrNull() ?: 0
                    if (chosenItem != null && requestedQty > chosenItem.quantity) {
                        Toast.makeText(context, "Requested quantity exceeds available stock (${chosenItem.quantity} ${chosenItem.unit} remaining).", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    isSubmitting = true

                    try {
                        val inputStream = context.contentResolver.openInputStream(selectedMediaUri!!)
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        val scaledBitmap = if (originalBitmap.width > 800) {
                            val ratio = originalBitmap.height.toFloat() / originalBitmap.width.toFloat()
                            Bitmap.createScaledBitmap(originalBitmap, 800, (800 * ratio).toInt(), true)
                        } else originalBitmap

                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                        val imageByteArray = outputStream.toByteArray()
                        val base64DataUri = "data:image/jpeg;base64," + Base64.encodeToString(imageByteArray, Base64.NO_WRAP)

                        fun saveApplication(imgUrl: String, offlineFlag: Boolean) {
                            val addressFull = listOf(userViewModel.barangay.value, userViewModel.city.value, userViewModel.province.value).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Registered Farm Address" }
                            val effectiveUserId = currentUserId.ifBlank {
                                auth.currentUser?.uid ?: (SessionManager.getSavedUserId(context) ?: "")
                            }
                            val requestData = hashMapOf(
                                "farmerId" to effectiveUserId,
                                "userId" to effectiveUserId,
                                "farmerName" to userViewModel.farmerName.value.trim(),
                                "farmerPhone" to contactNumber.trim(),
                                "phoneNumber" to contactNumber.trim(),
                                "rsbsaNumber" to userViewModel.rsbsaNumber.value.trim(),
                                "farmerEmail" to userViewModel.gmail.value.trim(),
                                "email" to userViewModel.gmail.value.trim(),
                                "farmerAddress" to addressFull,
                                "farmName" to (farmName ?: "Farm Plot"),
                                "requestType" to selectedCategory.ifBlank { "General" },
                                "category" to selectedCategory.ifBlank { "General" },
                                "itemName" to itemName,
                                "quantity" to quantity.trim(),
                                "unit" to selectedUnit,
                                "farmSize" to farmSize.trim(),
                                "croppingSeason" to croppingSeason,
                                "intendedDate" to intendedUsageDate,
                                "purpose" to selectedPurpose,
                                "description" to if (description.isNotBlank()) description.trim() else selectedPurpose,
                                "evidenceImageUrl" to imgUrl,
                                "status" to "Pending",
                                "approved" to false,
                                "isApproved" to false,
                                "distributed" to false,
                                "isDistributed" to false,
                                "rejected" to false,
                                "isRejected" to false,
                                "isOffline" to offlineFlag,
                                "syncStatus" to if (offlineFlag) "Queued Offline" else "Synced",
                                "timestamp" to Timestamp.now()
                            )

                            firestore.collection("requests").add(requestData)
                                .addOnSuccessListener {
                                    isSubmitting = false
                                    showSuccessDialog = true
                                }
                                .addOnFailureListener {
                                    isSubmitting = false
                                    showSuccessDialog = true
                                }
                        }

                        if (!isOnline) {
                            saveApplication(base64DataUri, offlineFlag = true)
                        } else {
                            val filename = "requests/evidence_${UUID.randomUUID()}.jpg"
                            val storageRef = storage.reference.child(filename)
                            storageRef.putBytes(imageByteArray)
                                .continueWithTask { task ->
                                    if (!task.isSuccessful) task.exception?.let { throw it }
                                    storageRef.downloadUrl
                                }
                                .addOnSuccessListener { downloadUrl ->
                                    saveApplication(downloadUrl.toString(), offlineFlag = false)
                                }
                                .addOnFailureListener {
                                    saveApplication(base64DataUri, offlineFlag = true)
                                }
                        }
                    } catch (e: Exception) {
                        isSubmitting = false
                        Toast.makeText(context, "Error processing application: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SUBMIT AID APPLICATION TO DA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Success Confirmation Dialog
    if (showSuccessDialog) {
        val isOnline = NetworkUtils.isOnline(context)
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = if (isOnline) "Application Submitted!" else "Application Saved (Offline)",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your request for $quantity $selectedUnit of $itemName has been registered.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val targetMao = if (userViewModel.city.value.isNotBlank()) " in ${userViewModel.city.value}" else ""
                    Text(
                        text = "The Municipal Agriculture Office (MAO)$targetMao will evaluate your RSBSA credentials. You can track this application in 'Requests Status'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("View My Requests")
                }
            }
        )
    }
}

/**
 * 1. Overview Banner: DA Official Subsidy Program
 */
@Composable
fun DaSubsidyBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
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
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Department of Agriculture Aid",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "RSBSA Farm Subsidy & Distribution Program",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Fill in your cropping details below to apply for free/subsidized seeds, fertilizer, and farm equipment allocated by the Municipal Agriculture Office.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * 2. Section 1: Farmer & RSBSA Credentials
 */
@Composable
fun FarmerCredentialsCard(
    userViewModel: UserViewModel,
    contactNumber: String,
    onContactChange: (String) -> Unit,
    farmName: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.AccountCircle,
                title = "1. Farmer & RSBSA Credentials",
                subtitle = "Registered demographic and land profile"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Farmer Full Name
            OutlinedTextField(
                value = userViewModel.farmerName.value,
                onValueChange = {},
                readOnly = true,
                label = { Text("Farmer Full Name") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Verified",
                        tint = Color(0xFF2E7D32)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // RSBSA & Contact Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userViewModel.rsbsaNumber.value.ifBlank { "RSBSA Enrolled" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("RSBSA Reference") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = onContactChange,
                    label = { Text("Contact Phone") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Registered Barangay / Farm Location
            val addressText = listOf(userViewModel.barangay.value, userViewModel.city.value, userViewModel.province.value).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Registered Farm Address" }
            OutlinedTextField(
                value = if (!farmName.isNullOrBlank()) "$farmName • $addressText" else addressText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Registered Farm Location") },
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * 3. Section 2: Requested DA Resource Selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestedResourceCard(
    itemName: String,
    selectedCategory: String,
    selectedUnit: String,
    availableInventory: List<InventoryItem>,
    isLoadingInventory: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectItem: (InventoryItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Inventory2,
                title = "2. Requested DA Resource",
                subtitle = "Choose from available municipal warehouse stock"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dropdown selection
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (itemName.isNotEmpty()) "$itemName ($selectedCategory)" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Resource from DA Catalog *") },
                    placeholder = { Text(if (isLoadingInventory) "Loading inventory..." else "Tap to choose item") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(selectedCategory),
                            contentDescription = null,
                            tint = getCategoryThemeColor(selectedCategory)
                        )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    availableInventory.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.item_name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${item.category} • Unit: ${item.unit}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (item.quantity > 10) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                                    ) {
                                        Text(
                                            text = "${item.quantity} in stock",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.quantity > 10) Color(0xFF2E7D32) else Color(0xFFF57F17)
                                        )
                                    }
                                }
                            },
                            onClick = { onSelectItem(item) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getCategoryIcon(item.category),
                                    contentDescription = null,
                                    tint = getCategoryThemeColor(item.category),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Quick Info Pill if selected
            if (itemName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = getCategoryThemeColor(selectedCategory).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Category: $selectedCategory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = getCategoryThemeColor(selectedCategory)
                        )
                        Text(
                            text = "Standard Packaging: $selectedUnit",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. Section 3: Quantity, Farm Size & Cropping Season
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllocationAndSeasonCard(
    quantity: String,
    onQuantityChange: (String) -> Unit,
    farmSize: String,
    onFarmSizeChange: (String) -> Unit,
    selectedUnit: String,
    croppingSeason: String,
    onSeasonChange: (String) -> Unit,
    seasonDropdownExpanded: Boolean,
    onSeasonDropdownChange: (Boolean) -> Unit,
    intendedUsageDate: String,
    onIntendedDateChange: (String) -> Unit
) {
    val seasons = listOf(
        "Wet Season Cropping 2026 (Tag-ulan)",
        "Dry Season Cropping 2026 (Tag-araw)",
        "High-Value Commercial Crop Cycle",
        "Typhoon / Calamity Emergency Replanting"
    )

    val timelines = listOf(
        "Immediate Deployment (Within 7 Days)",
        "Within Next 30 Days",
        "Upcoming Planting Window (Next 60 Days)"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.FormatListNumbered,
                title = "3. Allocation & Farm Specifications",
                subtitle = "Define quantity, land area, and planting schedule"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quantity and Farm Size Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    label = { Text("Quantity ($selectedUnit) *") },
                    placeholder = { Text("e.g. 2") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = farmSize,
                    onValueChange = onFarmSizeChange,
                    label = { Text("Farm Size (Hectares) *") },
                    placeholder = { Text("e.g. 1.5") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cropping Season Dropdown
            ExposedDropdownMenuBox(
                expanded = seasonDropdownExpanded,
                onExpandedChange = onSeasonDropdownChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = croppingSeason,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cropping Season / Cycle *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = seasonDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = seasonDropdownExpanded,
                    onDismissRequest = { onSeasonDropdownChange(false) }
                ) {
                    seasons.forEach { season ->
                        DropdownMenuItem(
                            text = { Text(season) },
                            onClick = {
                                onSeasonChange(season)
                                onSeasonDropdownChange(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Intended Usage Timeline Chips
            Text(
                text = "Target Implementation Timeline:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timelines.forEach { timeline ->
                    val isSelected = intendedUsageDate == timeline
                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntendedDateChange(timeline) },
                        label = { Text(timeline.substringBefore(" ("), fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 5. Section 4: Purpose & Need Justification
 */
@Composable
fun PurposeAndRemarksCard(
    selectedPurpose: String,
    onPurposeSelected: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit
) {
    val purposes = listOf(
        "Regular Season Planting",
        "Typhoon Recovery",
        "Pest Infestation Recovery",
        "Soil Nutrient Boost"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.Description,
                title = "4. Purpose & Justification",
                subtitle = "State the operational purpose of this assistance"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Primary Purpose:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                purposes.take(2).forEach { purpose ->
                    FilterChip(
                        selected = selectedPurpose == purpose,
                        onClick = { onPurposeSelected(purpose) },
                        label = { Text(purpose, fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                purposes.drop(2).forEach { purpose ->
                    FilterChip(
                        selected = selectedPurpose == purpose,
                        onClick = { onPurposeSelected(purpose) },
                        label = { Text(purpose, fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Additional Remarks / Field Details (Optional)") },
                placeholder = { Text("e.g. Palay tillering stage, field located near communal irrigation canal...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * 6. Section 5: Farmland Evidence Photo Upload
 */
@Composable
fun FarmEvidencePhotoCard(
    selectedMediaUri: Uri?,
    onUploadClick: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Default.PhotoCamera,
                title = "5. Farmland Photo Evidence *",
                subtitle = "Recent photo of field plot, seedbed, or prepared land"
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedMediaUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = selectedMediaUri,
                        contentDescription = "Farm Evidence",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clickable { onRemovePhoto() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onUploadClick) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retake / Change Photo")
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUploadClick() },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to Attach Farmland Photo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Camera Capture or Gallery Upload",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. Section 6: RSBSA Compliance Acknowledgment
 */
@Composable
fun ComplianceDeclarationCard(
    accepted: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .clickable { onCheckedChange(!accepted) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I certify under penalty of law that the details provided are true, and all requested DA assistance will be deployed exclusively on my registered farmland.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
