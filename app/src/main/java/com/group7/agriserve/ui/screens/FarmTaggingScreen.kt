package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import com.group7.agriserve.R
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmTaggingScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit,
    onViewAllFarmsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: SessionManager.getSavedUserId(context)
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // States for farm details
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var farmName by remember { mutableStateOf("") }
    var farmerName by remember { mutableStateOf("") }
    var farmAddress by remember { mutableStateOf("") }
    var farmZone by remember { mutableStateOf("") }
    var farmSize by remember { mutableStateOf("") }
    var cropType by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoadingExisting by remember { mutableStateOf(true) }
    var isFetchingAddress by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    
    // States for photos
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var existingImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.8797, 121.7740), 5f)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> 
        if (uris.isNotEmpty()) {
            selectedImageUris = selectedImageUris + uris
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { uri ->
                selectedImageUris = selectedImageUris + uri
            }
        }
    }

    fun createImageUri(): Uri? {
        val directory = File(context.cacheDir, "camera_images")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "camera_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Initialize farmer name from viewmodel once it's loaded
    LaunchedEffect(userViewModel.farmerName.value) {
        if (farmerName.isEmpty() && userViewModel.farmerName.value != "Loading...") {
            farmerName = userViewModel.farmerName.value
        }
    }

    // Load existing data with timeout guarantee
    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) {
            isLoadingExisting = false
            return@LaunchedEffect
        }

        // Safety timeout so user is NEVER blocked by an infinite loading icon
        val timeoutJob = launch {
            delay(2500L)
            isLoadingExisting = false
        }

        try {
            db.collection("farms").document(uid).get().addOnSuccessListener { document ->
                timeoutJob.cancel()
                if (document.exists()) {
                    farmName = document.getString("farmName") ?: ""
                    farmerName = document.getString("farmOwner") ?: userViewModel.farmerName.value
                    farmAddress = document.getString("farmAddress") ?: ""
                    farmZone = document.getString("farmZone") ?: ""
                    farmSize = document.getString("farmSize") ?: ""
                    cropType = document.getString("cropType") ?: ""
                    isPublic = document.getBoolean("isPublic") ?: true
                    @Suppress("UNCHECKED_CAST")
                    existingImageUrls = document.get("farmImages") as? List<String> ?: emptyList()
                    
                    val geoPoint = document.getGeoPoint("farmLocation")
                    if (geoPoint != null) {
                        val latLng = LatLng(geoPoint.latitude, geoPoint.longitude)
                        selectedLocation = latLng
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    }
                    isLoadingExisting = false
                } else {
                    db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                        timeoutJob.cancel()
                        if (userDoc.exists()) {
                            farmName = userDoc.getString("farmName") ?: ""
                            farmerName = userDoc.getString("farmOwner") ?: userDoc.getString("name") ?: userViewModel.farmerName.value
                            farmAddress = userDoc.getString("farmAddress") ?: ""
                            farmZone = userDoc.getString("farmZone") ?: ""
                            farmSize = userDoc.getString("farmSize") ?: ""
                            cropType = userDoc.getString("cropType") ?: ""
                            isPublic = userDoc.getBoolean("isPublic") ?: true
                            
                            val geoPoint = userDoc.getGeoPoint("farmLocation")
                            if (geoPoint != null) {
                                val latLng = LatLng(geoPoint.latitude, geoPoint.longitude)
                                selectedLocation = latLng
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                            }
                        }
                        isLoadingExisting = false
                    }.addOnFailureListener {
                        timeoutJob.cancel()
                        isLoadingExisting = false
                    }
                }
            }.addOnFailureListener {
                timeoutJob.cancel()
                isLoadingExisting = false
            }
        } catch (_: Exception) {
            timeoutJob.cancel()
            isLoadingExisting = false
        }
    }

    LaunchedEffect(selectedLocation) {
        selectedLocation?.let { latLng ->
            isFetchingAddress = true
            try {
                val address = withTimeoutOrNull(3500L) {
                    withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                suspendCancellableCoroutine { continuation ->
                                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                                        if (continuation.isActive) {
                                            continuation.resume(addresses.firstOrNull())
                                        }
                                    }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)?.firstOrNull()
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                
                if (address != null) {
                    farmAddress = address.getAddressLine(0) ?: ""
                    farmZone = address.subLocality ?: address.thoroughfare ?: ""
                }
            } catch (_: Exception) {
            } finally {
                isFetchingAddress = false
            }
        }
    }

    val mapProperties by remember {
        mutableStateOf(MapProperties(mapType = MapType.HYBRID))
    }

    val exactLocationTagged = stringResource(R.string.exact_location_tagged)
    val locationErrorGps = stringResource(R.string.location_error_gps)
    val errorGettingLocation = stringResource(R.string.error_getting_location)

    @SuppressLint("MissingPermission")
    fun moveToCurrentLocation() {
        isLocating = true
        val safetyTimer = scope.launch {
            delay(5000L)
            isLocating = false
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                safetyTimer.cancel()
                isLocating = false
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    selectedLocation = currentLatLng
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f),
                            1000
                        )
                    }
                    Toast.makeText(context, exactLocationTagged, Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            isLocating = false
                            if (lastLoc != null) {
                                val currentLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                selectedLocation = currentLatLng
                                scope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
                                }
                            } else {
                                Toast.makeText(context, locationErrorGps, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            isLocating = false
                            Toast.makeText(context, locationErrorGps, Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                safetyTimer.cancel()
                isLocating = false
                Toast.makeText(context, errorGettingLocation, Toast.LENGTH_SHORT).show()
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            moveToCurrentLocation()
        }
    }

    val farmSaveSuccess = stringResource(R.string.farm_save_success)
    val farmSaveFailed = stringResource(R.string.farm_save_failed)

    fun saveFarmData(imageUrls: List<String>) {
        val currentUid = auth.currentUser?.uid ?: SessionManager.getSavedUserId(context)
        if (!currentUid.isNullOrBlank() && selectedLocation != null) {
            val farmData = hashMapOf(
                "userId" to currentUid,
                "farmName" to farmName,
                "farmOwner" to farmerName,
                "farmAddress" to farmAddress,
                "farmZone" to farmZone,
                "farmSize" to farmSize,
                "cropType" to cropType,
                "isPublic" to isPublic,
                "farmImages" to imageUrls,
                "farmLocation" to GeoPoint(
                    selectedLocation!!.latitude,
                    selectedLocation!!.longitude
                ),
                "latitude" to selectedLocation!!.latitude,
                "longitude" to selectedLocation!!.longitude,
                "isGeotagged" to true,
                "timestamp" to Timestamp.now(),
                "lastUpdated" to Timestamp.now()
            )
            db.collection("farms").document(currentUid)
                .set(farmData, SetOptions.merge())
                .addOnSuccessListener {
                    isSaving = false
                    Toast.makeText(context, farmSaveSuccess, Toast.LENGTH_SHORT).show()
                    onBack()
                }
                .addOnFailureListener { e ->
                    isSaving = false
                    Toast.makeText(context, String.format(farmSaveFailed, e.message), Toast.LENGTH_SHORT).show()
                }
        } else {
            isSaving = false
        }
    }

    val enterFarmName = stringResource(R.string.enter_farm_name)
    val tapToPlacePin = stringResource(R.string.tap_to_place_pin)
    fun uploadImagesAndSave() {
        val currentUid = auth.currentUser?.uid ?: SessionManager.getSavedUserId(context)
        if (currentUid.isNullOrBlank()) {
            Toast.makeText(context, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            isSaving = false
            return
        }

        if (selectedLocation == null) {
            Toast.makeText(context, tapToPlacePin, Toast.LENGTH_SHORT).show()
            scope.launch { pagerState.animateScrollToPage(0) }
            isSaving = false
            return
        }

        if (selectedImageUris.isEmpty()) {
            saveFarmData(existingImageUrls)
            return
        }

        isSaving = true
        val uploadedUrls = existingImageUrls.toMutableList()
        var uploadCount = 0
        
        selectedImageUris.forEach { uri ->
            val fileName = "farm_${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child("farms/$currentUid/$fileName")
            
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                    uploadedUrls.add(downloadUrl.toString())
                    uploadCount++
                    if (uploadCount == selectedImageUris.size) {
                        saveFarmData(uploadedUrls)
                    }
                }.addOnFailureListener {
                    isSaving = false
                }
            }.addOnFailureListener { e ->
                isSaving = false
                Toast.makeText(context, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.select_image_source)) },
            text = { Text(stringResource(R.string.image_source_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(R.string.gallery))
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
                    Text(stringResource(R.string.camera))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.farm_tagging_details)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewAllFarmsClick) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = stringResource(R.string.view_all_farms)
                        )
                    }
                    if (pagerState.currentPage == 0) {
                        TextButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            }
                        ) {
                            Text(stringResource(R.string.proceed_to_details), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        val saveLabel = stringResource(R.string.save)
                        TextButton(
                            onClick = {
                                if (farmName.isBlank()) {
                                    Toast.makeText(context, enterFarmName, Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                uploadImagesAndSave()
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(saveLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = { 
                        locationPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION, 
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        Icon(Icons.Default.GpsFixed, contentDescription = stringResource(R.string.get_exact_location))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pager Step Header & Indicators
                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Step 1 Button/Chip
                            Surface(
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                shape = RoundedCornerShape(12.dp),
                                color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PinDrop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.step_map_pin),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Step 2 Button/Chip
                            Surface(
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                shape = RoundedCornerShape(12.dp),
                                color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Agriculture,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.step_farm_details),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Pager Indicator Dots (matching HomeScreen.kt style)
                        Row(
                            Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(2) { iteration ->
                                val isSelected = pagerState.currentPage == iteration
                                val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .size(if (isSelected) 16.dp else 8.dp, 4.dp)
                                )
                            }
                        }

                        if (isLoadingExisting) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // HorizontalPager with 2 Pages
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            // Step 1: Map Pin & GPS
                            Box(modifier = Modifier.fillMaxSize()) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    properties = mapProperties,
                                    onMapClick = { selectedLocation = it },
                                    uiSettings = MapUiSettings(myLocationButtonEnabled = false)
                                ) {
                                    selectedLocation?.let {
                                        Marker(
                                            state = MarkerState(position = it),
                                            title = farmName.ifBlank { "Tagged Farm" },
                                            snippet = "Lat: ${String.format(Locale.US, "%.6f", it.latitude)}, Lng: ${String.format(Locale.US, "%.6f", it.longitude)}"
                                        )
                                    }
                                }

                                // Hint chip overlay at top-start
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shadowElevation = 3.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.tap_map_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Coordinates Overlay at top-end
                                selectedLocation?.let {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(12.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            stringResource(R.string.geotag_label, String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Bottom floating address & Next button card
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (selectedLocation != null) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                                contentDescription = null,
                                                tint = if (selectedLocation != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (selectedLocation != null) stringResource(R.string.location_pinned_title) else stringResource(R.string.no_location_pinned),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isFetchingAddress) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = if (selectedLocation != null) {
                                                if (farmAddress.isNotBlank()) farmAddress
                                                else String.format(Locale.US, "%.6f, %.6f", selectedLocation!!.latitude, selectedLocation!!.longitude)
                                            } else {
                                                stringResource(R.string.tap_to_place_pin)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(stringResource(R.string.proceed_to_details), fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Step 2: Farm Details, Crop Info & Photos
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.farm_details_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (selectedLocation != null) {
                                        AssistChip(
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(0) }
                                            },
                                            label = { Text(stringResource(R.string.geotagged)) },
                                            leadingIcon = { Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                    } else {
                                        AssistChip(
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(0) }
                                            },
                                            label = { Text("Pin on Map", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(stringResource(R.string.farm_photos), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { showImageSourceDialog = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.add_photo), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    // Show newly selected images
                                    items(selectedImageUris) { uri ->
                                        Box(modifier = Modifier.size(100.dp)) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { selectedImageUris = selectedImageUris - uri },
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }

                                    // Show existing images
                                    items(existingImageUrls) { url ->
                                        Box(modifier = Modifier.size(100.dp)) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { existingImageUrls = existingImageUrls - url },
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = farmName,
                                    onValueChange = { farmName = it },
                                    label = { Text(stringResource(R.string.farm_name_label)) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = farmerName,
                                    onValueChange = { farmerName = it },
                                    label = { Text(stringResource(R.string.farmer_owner_label)) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = farmZone,
                                    onValueChange = { farmZone = it },
                                    label = { Text(stringResource(R.string.zone_barangay_label)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.zone_placeholder)) }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = farmAddress,
                                    onValueChange = { farmAddress = it },
                                    label = { Text(stringResource(R.string.farm_address_label)) },
                                    modifier = Modifier.fillMaxWidth(),
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
                                        label = { Text(stringResource(R.string.size_ha_label)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = cropType,
                                        onValueChange = { cropType = it },
                                        label = { Text(stringResource(R.string.primary_crop_label)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Navigation Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.back_to_map))
                                    }

                                    Button(
                                        onClick = {
                                            if (farmName.isBlank()) {
                                                Toast.makeText(context, enterFarmName, Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            uploadImagesAndSave()
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.save_farm_geotag), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
