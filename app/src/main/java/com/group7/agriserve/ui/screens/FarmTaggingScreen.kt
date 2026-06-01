package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.maps.android.compose.*
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmTaggingScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // States for farm details
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var farmName by remember { mutableStateOf("") }
    var farmerName by remember { mutableStateOf("") }
    var farmAddress by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var cropType by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingExisting by remember { mutableStateOf(true) }
    var isFetchingAddress by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.8797, 121.7740), 5f)
    }

    // Initialize farmer name from viewmodel once it's loaded
    LaunchedEffect(userViewModel.farmerName.value) {
        if (farmerName.isEmpty() && userViewModel.farmerName.value != "Loading...") {
            farmerName = userViewModel.farmerName.value
        }
    }

    // Load existing data from Firestore
    LaunchedEffect(uid) {
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { document ->
                if (document.exists()) {
                    farmName = document.getString("farmName") ?: ""
                    // Try to get farmOwner first, then default name if it was tagged before
                    farmerName = document.getString("farmOwner") ?: document.getString("name") ?: userViewModel.farmerName.value
                    farmAddress = document.getString("farmAddress") ?: ""
                    farmSize = document.getString("farmSize") ?: ""
                    cropType = document.getString("cropType") ?: ""
                    
                    val geoPoint = document.getGeoPoint("farmLocation")
                    if (geoPoint != null) {
                        val latLng = LatLng(geoPoint.latitude, geoPoint.longitude)
                        selectedLocation = latLng
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    }
                }
                isLoadingExisting = false
            }.addOnFailureListener {
                isLoadingExisting = false
            }
        }
    }

    // Reverse Geocoding: Automatically update farmAddress when selectedLocation changes (pin dropped)
    LaunchedEffect(selectedLocation) {
        selectedLocation?.let { latLng ->
            isFetchingAddress = true
            try {
                val addressLine = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine<String?> { continuation ->
                            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                                continuation.resume(addresses.firstOrNull()?.getAddressLine(0))
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    }
                }
                if (addressLine != null) {
                    farmAddress = addressLine
                }
            } catch (e: Exception) {
                // Fail silently
            } finally {
                isFetchingAddress = false
            }
        }
    }

    val mapProperties by remember {
        mutableStateOf(MapProperties(mapType = MapType.HYBRID))
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission granted
        }
    }

    @SuppressLint("MissingPermission")
    fun moveToCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
                selectedLocation = currentLatLng
            } else {
                Toast.makeText(context, "Could not detect location. Is GPS on?", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Farm Tagging & Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedLocation != null) {
                        TextButton(
                            onClick = {
                                if (farmName.isBlank()) {
                                    Toast.makeText(context, "Please enter a farm name", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                isSaving = true
                                if (uid != null) {
                                    val farmData = hashMapOf(
                                        "farmName" to farmName,
                                        "farmOwner" to farmerName,
                                        "farmAddress" to farmAddress,
                                        "farmSize" to farmSize,
                                        "cropType" to cropType,
                                        "farmLocation" to GeoPoint(
                                            selectedLocation!!.latitude,
                                            selectedLocation!!.longitude
                                        ),
                                        "lastUpdated" to Timestamp.now()
                                    )
                                    db.collection("users").document(uid)
                                        .set(farmData, SetOptions.merge())
                                        .addOnSuccessListener {
                                            isSaving = false
                                            Toast.makeText(context, "Farm details saved successfully!", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        }
                                        .addOnFailureListener { e ->
                                            isSaving = false
                                            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            else Text("SAVE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                moveToCurrentLocation() 
            }) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
        }
    ) { padding ->
        if (isLoadingExisting) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Map Section (Top Half)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = mapProperties,
                            googleMapOptionsFactory = {
                                GoogleMapOptions().mapId("30140710340f34efb5ac6121")
                            },
                            onMapClick = { latLng ->
                                selectedLocation = latLng
                            },
                            uiSettings = MapUiSettings(myLocationButtonEnabled = false)
                        ) {
                            selectedLocation?.let {
                                Marker(
                                    state = MarkerState(position = it),
                                    title = farmName.ifBlank { "Tagged Farm" },
                                    snippet = "Lat: ${String.format(Locale.US, "%.4f", it.latitude)}, Lng: ${String.format(Locale.US, "%.4f", it.longitude)}"
                                )
                            }
                        }
                        
                        if (selectedLocation == null) {
                            Surface(
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    "Tap on the map to mark your farm",
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Details Section (Bottom Half)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "Farm Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = farmName,
                                onValueChange = { farmName = it },
                                label = { Text("Farm Name") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. Green Valley Farm") }
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = farmerName,
                                onValueChange = { farmerName = it },
                                label = { Text("Farmer/Owner Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = farmAddress,
                                onValueChange = { farmAddress = it },
                                label = { Text("Farm Address (Detected)") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Detecting exact address...") },
                                trailingIcon = {
                                    if (isFetchingAddress) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = farmSize,
                                    onValueChange = { farmSize = it },
                                    label = { Text("Size (Hectares)") },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("1.5") }
                                )
                                OutlinedTextField(
                                    value = cropType,
                                    onValueChange = { cropType = it },
                                    label = { Text("Primary Crop") },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Rice") }
                                )
                            }
                            
                            if (selectedLocation != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Coordinates: ${String.format(Locale.US, "%.6f", selectedLocation!!.latitude)}, ${String.format(Locale.US, "%.6f", selectedLocation!!.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
