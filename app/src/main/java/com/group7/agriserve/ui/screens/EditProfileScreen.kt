package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.group7.agriserve.ui.components.FaceVerificationDialog
import com.group7.agriserve.ui.components.LocationItem
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Local state initialized from ViewModel
    var name by remember { mutableStateOf(userViewModel.farmerName.value) }
    var phone by remember { mutableStateOf(userViewModel.farmerPhone.value) }

    // --- Dynamic Address State ---
    var regions by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var provinces by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var cities by remember { mutableStateOf<List<LocationItem>>(emptyList()) }
    var barangays by remember { mutableStateOf<List<LocationItem>>(emptyList()) }

    var selectedRegionName by remember { mutableStateOf(userViewModel.region.value) }
    var selectedProvinceName by remember { mutableStateOf(userViewModel.province.value) }
    var selectedCityName by remember { mutableStateOf(userViewModel.city.value) }
    var selectedBarangayName by remember { mutableStateOf(userViewModel.barangay.value) }

    var selectedRegion by remember { mutableStateOf<LocationItem?>(null) }
    var selectedProvince by remember { mutableStateOf<LocationItem?>(null) }
    var selectedCity by remember { mutableStateOf<LocationItem?>(null) }
    var selectedBarangay by remember { mutableStateOf<LocationItem?>(null) }
    var street by remember { mutableStateOf(userViewModel.street.value) }

    var regionExpanded by remember { mutableStateOf(false) }
    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }

    // Fetch Regions on startup
    LaunchedEffect(Unit) {
        regions = fetchPSGCData("https://psgc.gitlab.io/api/regions/")
        selectedRegion = regions.find { it.name == selectedRegionName }
    }

    // Fetch Provinces when Region changes
    LaunchedEffect(selectedRegion) {
        if (selectedRegion != null) {
            provinces = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion?.code}/provinces/")
            selectedProvince = provinces.find { it.name == selectedProvinceName }
            if (provinces.isEmpty()) {
                cities = fetchPSGCData("https://psgc.gitlab.io/api/regions/${selectedRegion?.code}/cities-municipalities/")
            }
        }
    }

    // Fetch Cities when Province changes
    LaunchedEffect(selectedProvince) {
        if (selectedProvince != null) {
            cities = fetchPSGCData("https://psgc.gitlab.io/api/provinces/${selectedProvince?.code}/cities-municipalities/")
            selectedCity = cities.find { it.name == selectedCityName }
        }
    }

    // Fetch Barangays when City changes
    LaunchedEffect(selectedCity) {
        if (selectedCity != null) {
            barangays = fetchPSGCData("https://psgc.gitlab.io/api/cities-municipalities/${selectedCity?.code}/barangays/")
            selectedBarangay = barangays.find { it.name == selectedBarangayName }
        }
    }
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    // TEMPORARY: Bypassing face verification
    var isFaceVerified by remember { mutableStateOf(true) }
    var showCameraDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // showCameraDialog = true // Bypassed
            isFaceVerified = true
            Toast.makeText(context, "Face Verification Bypassed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
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
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(120.dp)
                    .clickable { launcher.launch("image/*") }
            ) {
                AsyncImage(
                    model = selectedImageUri ?: userViewModel.profileImageUrl.value,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop,
                    fallback = rememberVectorPainter(Icons.Filled.AccountCircle),
                    error = rememberVectorPainter(Icons.Filled.AccountCircle),
                    placeholder = rememberVectorPainter(Icons.Filled.AccountCircle)
                )
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = userViewModel.farmerEmail.value,
                onValueChange = {},
                readOnly = true,
                label = { Text("Email Address (Primary)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                enabled = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Address Information", 
                style = MaterialTheme.typography.titleSmall, 
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // --- Region Dropdown ---
            ExposedDropdownMenuBox(expanded = regionExpanded, onExpandedChange = { regionExpanded = !regionExpanded }) {
                OutlinedTextField(
                    value = selectedRegion?.name ?: selectedRegionName, onValueChange = {}, readOnly = true, label = { Text("Region") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
                    regions.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            selectedRegion = item
                            selectedRegionName = item.name
                            selectedProvince = null; selectedProvinceName = ""; selectedCity = null; selectedCityName = ""; selectedBarangay = null; selectedBarangayName = ""
                            regionExpanded = false
                        })
                    }
                }
            }

            // --- Province Dropdown ---
            if (provinces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { provinceExpanded = !provinceExpanded }) {
                    OutlinedTextField(
                        value = selectedProvince?.name ?: selectedProvinceName, onValueChange = {}, readOnly = true, label = { Text("Province") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) {
                        provinces.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = {
                                selectedProvince = item
                                selectedProvinceName = item.name
                                selectedCity = null; selectedCityName = ""; selectedBarangay = null; selectedBarangayName = ""
                                provinceExpanded = false
                            })
                        }
                    }
                }
            }

            // --- City Dropdown ---
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = !cityExpanded }) {
                OutlinedTextField(
                    value = selectedCity?.name ?: selectedCityName, onValueChange = {}, readOnly = true, label = { Text("City/Municipality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = (selectedProvince != null || provinces.isEmpty())
                )
                ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                    cities.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            selectedCity = item
                            selectedCityName = item.name
                            selectedBarangay = null; selectedBarangayName = ""
                            cityExpanded = false
                        })
                    }
                }
            }

            // --- Barangay Dropdown ---
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = barangayExpanded, onExpandedChange = { barangayExpanded = !barangayExpanded }) {
                OutlinedTextField(
                    value = selectedBarangay?.name ?: selectedBarangayName, onValueChange = {}, readOnly = true, label = { Text("Barangay") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = selectedCity != null
                )
                ExposedDropdownMenu(expanded = barangayExpanded, onDismissRequest = { barangayExpanded = false }) {
                    barangays.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            selectedBarangay = item
                            selectedBarangayName = item.name
                            barangayExpanded = false
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = street, onValueChange = { street = it }, label = { Text("Street/House No.") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // Biometric Match Status
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Facial Verification (Bypassed)", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Identity Verified (Bypassed)", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Icon(imageVector = if (isFaceVerified) Icons.Default.CheckCircle else Icons.Default.Face, contentDescription = null, tint = if (isFaceVerified) Color.Green else Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isSaving = true
                    userViewModel.updateProfile(
                        newName = name,
                        newPhone = phone,
                        newRegion = selectedRegionName,
                        newProvince = selectedProvinceName,
                        newCity = selectedCityName,
                        newBarangay = selectedBarangayName,
                        newStreet = street,
                        newImageUri = selectedImageUri
                    ) { success, error ->
                        isSaving = false
                        if (success) {
                            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Update failed: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && name.isNotBlank() && phone.isNotBlank() && isFaceVerified
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                else Text("SAVE PROFILE")
            }
        }
    }

    if (showCameraDialog) {
        FaceVerificationDialog(
            onVerificationComplete = { isFaceVerified = true; showCameraDialog = false },
            onDismiss = { showCameraDialog = false }
        )
    }
}

// --- Helper Functions for PSGC API ---
private suspend fun fetchPSGCData(urlString: String): List<LocationItem> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val data = conn.inputStream.bufferedReader().readText()
        val jsonArray = JSONArray(data)
        val list = mutableListOf<LocationItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(LocationItem(obj.getString("code"), obj.getString("name")))
        }
        list.sortedBy { it.name }
    } catch (e: Exception) {
        Log.e("EditProfile", "Error fetching location: ${e.message}")
        emptyList()
    }
}
