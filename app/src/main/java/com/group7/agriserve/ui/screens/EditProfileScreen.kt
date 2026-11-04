package com.group7.agriserve.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.ui.components.LocationItem
import com.group7.agriserve.data.repository.AddressRepository
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Locked Fields (Read-Only) - Sourced from UserViewModel & Firestore
    val name = userViewModel.farmerName.value.ifBlank {
        listOf(
            userViewModel.firstName.value,
            userViewModel.middleName.value,
            userViewModel.lastName.value,
            userViewModel.extensionName.value
        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Farmer" }
    }

    var rsbsaNumber by remember { mutableStateOf(userViewModel.rsbsaNumber.value) }
    var emailAccount by remember {
        mutableStateOf(
            userViewModel.gmail.value.ifBlank {
                userViewModel.farmerEmail.value.ifBlank {
                    FirebaseAuth.getInstance().currentUser?.email ?: ""
                }
            }
        )
    }

    // Editable Fields
    var phone by remember {
        mutableStateOf(
            userViewModel.farmerPhone.value.ifBlank { userViewModel.phoneNumber.value }
        )
    }

    // Dropdown Data (Pre-populated with fallback so address list is always available)
    var regions by remember { mutableStateOf<List<LocationItem>>(AddressRepository.fallbackRegions) }
    var provinces by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackProvinces("050000000")) }
    var cities by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackCities("051700000")) }
    var barangays by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackBarangays("051730000")) }

    // Dropdown Selection States
    var selectedRegion by remember { mutableStateOf<LocationItem?>(null) }
    var selectedRegionName by remember { mutableStateOf(userViewModel.region.value) }

    var selectedProvince by remember { mutableStateOf<LocationItem?>(null) }
    var selectedProvinceName by remember { mutableStateOf(userViewModel.province.value) }

    var selectedCity by remember { mutableStateOf<LocationItem?>(null) }
    var selectedCityName by remember { mutableStateOf(userViewModel.city.value) }

    var selectedBarangay by remember { mutableStateOf<LocationItem?>(null) }
    var selectedBarangayName by remember { mutableStateOf(userViewModel.barangay.value) }

    var street by remember { mutableStateOf(userViewModel.street.value) }

    // Expansion States
    var regionExpanded by remember { mutableStateOf(false) }
    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }

    // Image Upload State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                selectedImageUri = uri
            }
        }
    }

    fun createImageUri(): Uri? {
        val directory = File(context.cacheDir, "profile_images")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "profile_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Sync Firestore backup details for RSBSA & Gmail if needed
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: SessionManager.getSavedUserId(context)

        if (!uid.isNullOrBlank()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("farmers").document(uid).get()
                .addOnSuccessListener { snap ->
                    val pInfo = snap.get("personalInfo") as? Map<*, *>
                    val cInfo = snap.get("contactInfo") as? Map<*, *>
                    val rsbsa = (pInfo?.get("rsbsaNumber") as? String)
                        ?: snap.getString("rsbsaNumber")
                    if (!rsbsa.isNullOrBlank() && rsbsaNumber.isBlank()) {
                        rsbsaNumber = rsbsa
                    }
                    val mail = (cInfo?.get("gmail") as? String)
                        ?: snap.getString("gmail")
                        ?: snap.getString("email")
                    if (!mail.isNullOrBlank() && emailAccount.isBlank()) {
                        emailAccount = mail
                    }
                    if (phone.isBlank()) {
                        val p = (cInfo?.get("phoneNumber") as? String)
                            ?: snap.getString("phone")
                            ?: snap.getString("phoneNumber")
                        if (!p.isNullOrBlank()) {
                            phone = p
                        }
                    }
                }
        }
    }

    // Sync Address Fields from ViewModel when available
    LaunchedEffect(userViewModel.region.value, userViewModel.province.value, userViewModel.city.value, userViewModel.barangay.value, userViewModel.street.value) {
        if (selectedRegionName.isBlank() && userViewModel.region.value.isNotBlank()) {
            selectedRegionName = userViewModel.region.value
        }
        if (selectedProvinceName.isBlank() && userViewModel.province.value.isNotBlank()) {
            selectedProvinceName = userViewModel.province.value
        }
        if (selectedCityName.isBlank() && userViewModel.city.value.isNotBlank()) {
            selectedCityName = userViewModel.city.value
        }
        if (selectedBarangayName.isBlank() && userViewModel.barangay.value.isNotBlank()) {
            selectedBarangayName = userViewModel.barangay.value
        }
        if (street.isBlank() && userViewModel.street.value.isNotBlank()) {
            street = userViewModel.street.value
        }
    }

    // Fetch Initial Regions
    LaunchedEffect(Unit) {
        regions = fetchPSGCData("https://psgc.gitlab.io/api/regions/")
        if (selectedRegionName.isNotBlank()) {
            selectedRegion = regions.find { it.name.equals(selectedRegionName, ignoreCase = true) }
        }
    }

    // Match region whenever regions list or selectedRegionName updates
    LaunchedEffect(regions, selectedRegionName) {
        if (selectedRegion == null && selectedRegionName.isNotBlank() && regions.isNotEmpty()) {
            selectedRegion = regions.find { it.name.equals(selectedRegionName, ignoreCase = true) }
        }
    }

    // Fetch Provinces
    LaunchedEffect(selectedRegion) {
        if (selectedRegion != null) {
            val fetchedProvinces = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion!!.code}/provinces/")
            provinces = fetchedProvinces
            if (fetchedProvinces.isEmpty()) {
                cities = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion!!.code}/cities-municipalities/")
            }
            if (selectedProvinceName.isNotBlank()) {
                selectedProvince = fetchedProvinces.find { it.name.equals(selectedProvinceName, ignoreCase = true) }
            }
        }
    }

    // Match province whenever provinces list or selectedProvinceName updates
    LaunchedEffect(provinces, selectedProvinceName) {
        if (selectedProvince == null && selectedProvinceName.isNotBlank() && provinces.isNotEmpty()) {
            selectedProvince = provinces.find { it.name.equals(selectedProvinceName, ignoreCase = true) }
        }
    }

    // Fetch Cities
    LaunchedEffect(selectedProvince) {
        if (selectedProvince != null) {
            val fetchedCities = fetchPSGCData("https://psgc.gitlab.io/api/provinces/${selectedProvince!!.code}/cities-municipalities/")
            cities = fetchedCities
            if (selectedCityName.isNotBlank()) {
                selectedCity = fetchedCities.find { it.name.equals(selectedCityName, ignoreCase = true) }
            }
        }
    }

    // Match city whenever cities list or selectedCityName updates
    LaunchedEffect(cities, selectedCityName) {
        if (selectedCity == null && selectedCityName.isNotBlank() && cities.isNotEmpty()) {
            selectedCity = cities.find { it.name.equals(selectedCityName, ignoreCase = true) }
        }
    }

    // Fetch Barangays
    LaunchedEffect(selectedCity) {
        if (selectedCity != null) {
            val fetchedBarangays = fetchPSGCData("https://psgc.gitlab.io/api/cities-municipalities/${selectedCity!!.code}/barangays/")
            barangays = fetchedBarangays
            if (selectedBarangayName.isNotBlank()) {
                selectedBarangay = fetchedBarangays.find { it.name.equals(selectedBarangayName, ignoreCase = true) }
            }
        }
    }

    // Match barangay whenever barangays list or selectedBarangayName updates
    LaunchedEffect(barangays, selectedBarangayName) {
        if (selectedBarangay == null && selectedBarangayName.isNotBlank() && barangays.isNotEmpty()) {
            selectedBarangay = barangays.find { it.name.equals(selectedBarangayName, ignoreCase = true) }
        }
    }

    fun performSaveProfile() {
        if (phone.isBlank()) {
            Toast.makeText(context, "Please enter your contact phone number.", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        userViewModel.updateProfile(
            newName = name,
            newPhone = phone.trim(),
            newRegion = selectedRegionName,
            newProvince = selectedProvinceName,
            newCity = selectedCityName,
            newBarangay = selectedBarangayName,
            newStreet = street.trim(),
            newImageUri = selectedImageUri
        ) { success, error ->
            isSaving = false
            if (success) {
                Toast.makeText(context, "Profile updated successfully! ✓", Toast.LENGTH_SHORT).show()
                onBack()
            } else {
                Toast.makeText(context, "Update failed: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Select Profile Photo") },
            text = { Text("Choose how you want to update your profile picture.") },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text("Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    val uri = createImageUri()
                    if (uri != null) {
                        tempImageUri = uri
                        cameraLauncher.launch(uri)
                    }
                }) {
                    Text("Camera")
                }
            }
        )
    }

    // Styling for locked (non-editable) fields consistent with onboarding
    val lockedFieldColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
        disabledTrailingIconColor = Color.Gray,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Profile Picture with Edit Icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(116.dp)
                        .clickable { showImageSourceDialog = true }
                ) {
                    AsyncImage(
                        model = selectedImageUri ?: userViewModel.profileImageUrl.value,
                        contentDescription = "Profile Picture",
                        placeholder = rememberVectorPainter(Icons.Default.Person),
                        error = rememberVectorPainter(Icons.Default.Person),
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Change Photo", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guidance Banner - Styled inline with Onboarding
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Official credentials (Full Name, RSBSA Reference, and Google Account) are verified and locked to protect your account. You may update your phone number, address, and profile photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: RSBSA DEMOGRAPHICS (LOCKED)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. RSBSA Demographics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LOCKED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {},
                enabled = false,
                label = { Text("Full Name *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray) },
                supportingText = {
                    Text("Official registered farmer name on file", style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = lockedFieldColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = rsbsaNumber.ifBlank { "Not Registered / NA" },
                onValueChange = {},
                enabled = false,
                label = { Text("RSBSA Reference Number *") },
                leadingIcon = { Icon(Icons.Default.AppRegistration, contentDescription = null) },
                trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray) },
                supportingText = {
                    Text("Official DA RSBSA Reference Number", style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = lockedFieldColors
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: CONTACT & ACCOUNT CREDENTIALS
            Text(
                text = "2. Contact & Account Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { input ->
                    var cleaned = input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                    if (cleaned.startsWith("0909")) {
                        cleaned = "09" + cleaned.substring(4)
                    }
                    phone = cleaned
                },
                label = { Text("Phone Number (Philippine Mobile) *") },
                placeholder = { Text("09171234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = {
                    Text("11-digit mobile number for agricultural advisories & notices", style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = emailAccount.ifBlank { "Not Linked" },
                onValueChange = {},
                enabled = false,
                label = { Text("Google Account (Gmail) *") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray) },
                supportingText = {
                    Text("Authentication account linked to your profile", style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = lockedFieldColors
            )

            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Linked to Google Account (Protected)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: FARM & RESIDENTIAL ADDRESS
            Text(
                text = "3. Farm & Residential Address (All Philippines)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Select your updated Region, Province, City/Municipality, and Barangay across the Philippines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Region Dropdown
            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { regionExpanded = !regionExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedRegion?.name ?: selectedRegionName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Region *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false }
                ) {
                    regions.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                selectedRegion = item
                                selectedRegionName = item.name
                                selectedProvince = null
                                selectedProvinceName = ""
                                selectedCity = null
                                selectedCityName = ""
                                selectedBarangay = null
                                selectedBarangayName = ""
                                regionExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Province Dropdown
            if (provinces.isNotEmpty() || selectedProvince != null) {
                ExposedDropdownMenuBox(
                    expanded = provinceExpanded,
                    onExpandedChange = { provinceExpanded = !provinceExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvince?.name ?: selectedProvinceName.ifBlank { "Select Province" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Province *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = provinceExpanded,
                        onDismissRequest = { provinceExpanded = false }
                    ) {
                        provinces.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = {
                                    selectedProvince = item
                                    selectedProvinceName = item.name
                                    selectedCity = null
                                    selectedCityName = ""
                                    selectedBarangay = null
                                    selectedBarangayName = ""
                                    provinceExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // City Dropdown
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCity?.name ?: selectedCityName.ifBlank { "Select City/Municipality" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("City/Municipality *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = (selectedProvince != null || selectedProvinceName.isNotBlank() || provinces.isEmpty())
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false }
                ) {
                    cities.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                selectedCity = item
                                selectedCityName = item.name
                                selectedBarangay = null
                                selectedBarangayName = ""
                                cityExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barangay Dropdown
            ExposedDropdownMenuBox(
                expanded = barangayExpanded,
                onExpandedChange = { barangayExpanded = !barangayExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedBarangay?.name ?: selectedBarangayName.ifBlank { "Select Barangay" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Barangay *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = (selectedCity != null || selectedCityName.isNotBlank())
                )
                ExposedDropdownMenu(
                    expanded = barangayExpanded,
                    onDismissRequest = { barangayExpanded = false }
                ) {
                    barangays.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = {
                                selectedBarangay = item
                                selectedBarangayName = item.name
                                barangayExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Street / House No. / Farm Address
            OutlinedTextField(
                value = street,
                onValueChange = { street = it },
                label = { Text("Street / Zone / Farm Location") },
                placeholder = { Text("e.g. Purok 4, Sitio Riverside") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Save Button
            Button(
                onClick = { performSaveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isSaving && phone.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAVE PROFILE CHANGES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- Helper Functions for PSGC API ---
private suspend fun fetchPSGCData(urlString: String): List<LocationItem> {
    return AddressRepository.fetchPSGC(urlString)
}
