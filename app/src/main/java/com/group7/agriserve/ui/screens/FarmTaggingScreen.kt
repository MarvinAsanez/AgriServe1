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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.OfflineSyncManager
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
import kotlin.math.abs
import kotlin.math.sin

private fun calculatePolygonAreaHectares(points: List<LatLng>): Double {
    if (points.size < 3) return 0.0
    val earthRadius = 6378137.0
    var area = 0.0
    for (i in points.indices) {
        val p1 = points[i]
        val p2 = points[(i + 1) % points.size]
        val lon1 = Math.toRadians(p1.longitude)
        val lat1 = Math.toRadians(p1.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        area += (lon2 - lon1) * (2.0 + sin(lat1) + sin(lat2))
    }
    return abs(area * earthRadius * earthRadius / 2.0) / 10_000.0
}

private fun calculateCentroid(points: List<LatLng>): LatLng? {
    if (points.isEmpty()) return null
    return LatLng(
        points.sumOf { it.latitude } / points.size,
        points.sumOf { it.longitude } / points.size
    )
}

private data class SavedFarmItem(
    val id: String,
    val farmName: String,
    val farmOwner: String,
    val farmAddress: String,
    val farmZone: String,
    val farmSize: String,
    val cropType: String,
    val isPublic: Boolean,
    val farmImages: List<String>,
    val location: LatLng?,
    val boundaryPoints: List<LatLng>
)

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
    
    // States for multi-farm & farm details
    var myFarms by remember { mutableStateOf<List<SavedFarmItem>>(emptyList()) }
    var editingFarmId by remember { mutableStateOf<String?>(null) }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var boundaryPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isBoundaryMode by remember { mutableStateOf(false) }
    val estimatedHectares = remember(boundaryPoints) { calculatePolygonAreaHectares(boundaryPoints) }
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

    fun parseFarmDocument(document: com.google.firebase.firestore.DocumentSnapshot): SavedFarmItem {
        @Suppress("UNCHECKED_CAST")
        val imgs = document.get("farmImages") as? List<String> ?: emptyList()
        val geoPoint = document.getGeoPoint("farmLocation")
        @Suppress("UNCHECKED_CAST")
        val savedBoundary = document.get("farmBoundary") as? List<Map<String, Any>>
        val parsedBoundary = savedBoundary?.mapNotNull { pt ->
            val lat = (pt["lat"] as? Number)?.toDouble() ?: (pt["latitude"] as? Number)?.toDouble()
            val lng = (pt["lng"] as? Number)?.toDouble() ?: (pt["longitude"] as? Number)?.toDouble()
            if (lat != null && lng != null) LatLng(lat, lng) else null
        } ?: emptyList()
        val loc = geoPoint?.let { LatLng(it.latitude, it.longitude) } ?: calculateCentroid(parsedBoundary)
        return SavedFarmItem(
            id = document.id,
            farmName = document.getString("farmName") ?: "",
            farmOwner = document.getString("farmOwner") ?: userViewModel.farmerName.value,
            farmAddress = document.getString("farmAddress") ?: "",
            farmZone = document.getString("farmZone") ?: "",
            farmSize = document.getString("farmSize") ?: "",
            cropType = document.getString("cropType") ?: "",
            isPublic = document.getBoolean("isPublic") ?: true,
            farmImages = imgs,
            location = loc,
            boundaryPoints = parsedBoundary
        )
    }

    fun loadFarmIntoForm(farm: SavedFarmItem?) {
        if (farm == null) {
            editingFarmId = null
            farmName = ""
            farmerName = userViewModel.farmerName.value.takeIf { it != "Loading..." } ?: ""
            farmAddress = ""
            farmZone = ""
            farmSize = ""
            cropType = ""
            isPublic = true
            existingImageUrls = emptyList()
            selectedImageUris = emptyList()
            selectedLocation = null
            boundaryPoints = emptyList()
        } else {
            editingFarmId = farm.id
            farmName = farm.farmName
            farmerName = farm.farmOwner
            farmAddress = farm.farmAddress
            farmZone = farm.farmZone
            farmSize = farm.farmSize
            cropType = farm.cropType
            isPublic = farm.isPublic
            existingImageUrls = farm.farmImages
            selectedImageUris = emptyList()
            boundaryPoints = farm.boundaryPoints
            selectedLocation = farm.location
            farm.location?.let { loc ->
                cameraPositionState.position = CameraPosition.fromLatLngZoom(loc, 15f)
            }
        }
    }

    // Load all existing farms for this farmer with timeout guarantee
    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) {
            isLoadingExisting = false
            return@LaunchedEffect
        }

        val timeoutJob = launch {
            delay(2500L)
            isLoadingExisting = false
        }

        try {
            db.collection("farms").whereEqualTo("userId", uid).get()
                .addOnSuccessListener { querySnapshot ->
                    val loadedFarms = querySnapshot.documents.map { parseFarmDocument(it) }.toMutableList()
                    if (loadedFarms.none { it.id == uid }) {
                        db.collection("farms").document(uid).get()
                            .addOnSuccessListener { legacyDoc ->
                                timeoutJob.cancel()
                                if (legacyDoc.exists()) {
                                    loadedFarms.add(0, parseFarmDocument(legacyDoc))
                                }
                                myFarms = loadedFarms
                                if (loadedFarms.isNotEmpty()) {
                                    loadFarmIntoForm(loadedFarms.first())
                                }
                                isLoadingExisting = false
                            }
                            .addOnFailureListener {
                                timeoutJob.cancel()
                                myFarms = loadedFarms
                                if (loadedFarms.isNotEmpty()) {
                                    loadFarmIntoForm(loadedFarms.first())
                                }
                                isLoadingExisting = false
                            }
                    } else {
                        timeoutJob.cancel()
                        myFarms = loadedFarms
                        if (loadedFarms.isNotEmpty()) {
                            loadFarmIntoForm(loadedFarms.first())
                        }
                        isLoadingExisting = false
                    }
                }
                .addOnFailureListener {
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
                    if (isBoundaryMode) {
                        val updated = boundaryPoints + currentLatLng
                        boundaryPoints = updated
                        selectedLocation = calculateCentroid(updated) ?: currentLatLng
                        if (updated.size >= 3) {
                            val ha = calculatePolygonAreaHectares(updated)
                            if (ha > 0.0) farmSize = String.format(Locale.US, "%.2f", ha)
                        }
                    } else {
                        selectedLocation = currentLatLng
                    }
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f),
                            1000
                        )
                    }
                    Toast.makeText(
                        context,
                        if (isBoundaryMode) "Corner ${boundaryPoints.size} pinned at GPS location" else exactLocationTagged,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            isLocating = false
                            if (lastLoc != null) {
                                val currentLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                if (isBoundaryMode) {
                                    val updated = boundaryPoints + currentLatLng
                                    boundaryPoints = updated
                                    selectedLocation = calculateCentroid(updated) ?: currentLatLng
                                } else {
                                    selectedLocation = currentLatLng
                                }
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

    fun saveFarmData(imageUrls: List<String>, isOfflineSave: Boolean = false) {
        val currentUid = auth.currentUser?.uid ?: SessionManager.getSavedUserId(context)
        val primaryLocation = selectedLocation ?: calculateCentroid(boundaryPoints)
        if (!currentUid.isNullOrBlank() && primaryLocation != null) {
            val serializedBoundary = boundaryPoints.map { pt ->
                mapOf("lat" to pt.latitude, "lng" to pt.longitude)
            }
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
                    primaryLocation.latitude,
                    primaryLocation.longitude
                ),
                "farmBoundary" to serializedBoundary,
                "boundaryPointCount" to boundaryPoints.size,
                "estimatedAreaHectares" to estimatedHectares,
                "latitude" to primaryLocation.latitude,
                "longitude" to primaryLocation.longitude,
                "isGeotagged" to true,
                "isOffline" to isOfflineSave,
                "syncStatus" to if (isOfflineSave) "Queued Offline" else "Synced",
                "timestamp" to Timestamp.now(),
                "lastUpdated" to Timestamp.now()
            )
            val targetFarmId = editingFarmId ?: "farm_${currentUid}_${UUID.randomUUID().toString().take(8)}"
            val writeTask = db.collection("farms").document(targetFarmId)
                .set(farmData, SetOptions.merge())

            if (isOfflineSave || !NetworkUtils.isOnline(context)) {
                isSaving = false
                Toast.makeText(
                    context,
                    "Farm geotag saved offline! Will automatically sync to DA when back online.",
                    Toast.LENGTH_LONG
                ).show()
                onBack()
            } else {
                writeTask
                    .addOnSuccessListener {
                        isSaving = false
                        Toast.makeText(context, farmSaveSuccess, Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                    .addOnFailureListener { e ->
                        isSaving = false
                        Toast.makeText(context, String.format(farmSaveFailed, e.message), Toast.LENGTH_SHORT).show()
                    }
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

        val isOnline = NetworkUtils.isOnline(context)
        if (selectedImageUris.isEmpty()) {
            saveFarmData(existingImageUrls, isOfflineSave = !isOnline)
            return
        }

        isSaving = true
        if (!isOnline) {
            val offlineUrls = existingImageUrls.toMutableList()
            selectedImageUris.forEach { uri ->
                OfflineSyncManager.compressUriToDataUri(context, uri)?.let { offlineUrls.add(it) }
            }
            saveFarmData(offlineUrls, isOfflineSave = true)
            return
        }

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
                        saveFarmData(uploadedUrls, isOfflineSave = false)
                    }
                }.addOnFailureListener {
                    OfflineSyncManager.compressUriToDataUri(context, uri)?.let { uploadedUrls.add(it) }
                    uploadCount++
                    if (uploadCount == selectedImageUris.size) {
                        saveFarmData(uploadedUrls, isOfflineSave = true)
                    }
                }
            }.addOnFailureListener {
                OfflineSyncManager.compressUriToDataUri(context, uri)?.let { uploadedUrls.add(it) }
                uploadCount++
                if (uploadCount == selectedImageUris.size) {
                    saveFarmData(uploadedUrls, isOfflineSave = true)
                }
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

                        // Multi-Farm Switcher Bar (+ Add New Farm or select existing)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = editingFarmId == null,
                                    onClick = { loadFarmIntoForm(null) },
                                    label = { Text("+ Add New Farm", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                            items(myFarms.size) { index ->
                                val itemFarm = myFarms[index]
                                FilterChip(
                                    selected = editingFarmId == itemFarm.id,
                                    onClick = { loadFarmIntoForm(itemFarm) },
                                    label = {
                                        Text(
                                            text = itemFarm.farmName.ifBlank { "Farm #${index + 1}" },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (itemFarm.boundaryPoints.size >= 3) Icons.Default.Timeline else Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
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
                            // Step 1: Map Pin, Boundary & GPS
                            Box(modifier = Modifier.fillMaxSize()) {
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = cameraPositionState,
                                    properties = mapProperties,
                                    onMapClick = { tappedLatLng ->
                                        if (isBoundaryMode) {
                                            val updated = boundaryPoints + tappedLatLng
                                            boundaryPoints = updated
                                            selectedLocation = calculateCentroid(updated) ?: tappedLatLng
                                            if (updated.size >= 3) {
                                                val ha = calculatePolygonAreaHectares(updated)
                                                if (ha > 0.0) {
                                                    farmSize = String.format(Locale.US, "%.2f", ha)
                                                }
                                            }
                                        } else {
                                            selectedLocation = tappedLatLng
                                        }
                                    },
                                    uiSettings = MapUiSettings(myLocationButtonEnabled = false)
                                ) {
                                    // Render Farm Boundary Polygon (3+ corners) or Polyline (2 corners)
                                    if (boundaryPoints.size >= 3) {
                                        Polygon(
                                            points = boundaryPoints,
                                            fillColor = Color(0xFF2E7D32).copy(alpha = 0.28f),
                                            strokeColor = Color(0xFF1B5E20),
                                            strokeWidth = 6f
                                        )
                                    } else if (boundaryPoints.size == 2) {
                                        Polyline(
                                            points = boundaryPoints,
                                            color = Color(0xFF1B5E20),
                                            width = 6f
                                        )
                                    }

                                    // Render Corner-by-Corner Boundary Markers
                                    boundaryPoints.forEachIndexed { index, corner ->
                                        Marker(
                                            state = MarkerState(position = corner),
                                            title = "Corner ${index + 1}",
                                            snippet = "${String.format(Locale.US, "%.5f", corner.latitude)}, ${String.format(Locale.US, "%.5f", corner.longitude)}",
                                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                        )
                                    }

                                    // Primary Farm Pin (Center or Single Pin)
                                    selectedLocation?.let {
                                        Marker(
                                            state = MarkerState(position = it),
                                            title = farmName.ifBlank { "Tagged Farm" },
                                            snippet = if (boundaryPoints.size >= 3) {
                                                "Center • Area: ${String.format(Locale.US, "%.2f", estimatedHectares)} ha"
                                            } else {
                                                "Lat: ${String.format(Locale.US, "%.6f", it.latitude)}, Lng: ${String.format(Locale.US, "%.6f", it.longitude)}"
                                            }
                                        )
                                    }
                                }

                                // Top Controls: Mode Switcher (Pin vs Draw Boundary) + Corner Undo/Clear
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                        shadowElevation = 4.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilterChip(
                                                selected = !isBoundaryMode,
                                                onClick = { isBoundaryMode = false },
                                                label = { Text("Single Pin", style = MaterialTheme.typography.labelSmall) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                            FilterChip(
                                                selected = isBoundaryMode,
                                                onClick = { isBoundaryMode = true },
                                                label = { Text("Draw Boundary", style = MaterialTheme.typography.labelSmall) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                        }
                                    }

                                    Surface(
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
                                                text = if (isBoundaryMode) {
                                                    "Tap corners or GPS button (${boundaryPoints.size} pts)"
                                                } else {
                                                    stringResource(R.string.tap_map_hint)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    if (boundaryPoints.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            SmallFloatingActionButton(
                                                onClick = {
                                                    val updated = boundaryPoints.dropLast(1)
                                                    boundaryPoints = updated
                                                    selectedLocation = calculateCentroid(updated) ?: selectedLocation
                                                    if (updated.size >= 3) {
                                                        val ha = calculatePolygonAreaHectares(updated)
                                                        if (ha > 0.0) farmSize = String.format(Locale.US, "%.2f", ha)
                                                    }
                                                },
                                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                            ) {
                                                Icon(Icons.Default.Undo, contentDescription = "Undo Corner", modifier = Modifier.size(18.dp))
                                            }
                                            SmallFloatingActionButton(
                                                onClick = { boundaryPoints = emptyList() },
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                                            ) {
                                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Boundary", modifier = Modifier.size(18.dp))
                                            }
                                        }
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
                                            if (boundaryPoints.isNotEmpty()) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = if (boundaryPoints.size >= 3) {
                                                            "${boundaryPoints.size} pts • ${String.format(Locale.US, "%.2f", estimatedHectares)} ha"
                                                        } else {
                                                            "${boundaryPoints.size}/3+ corners"
                                                        },
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
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

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Quick Select Crop Category (other crops fall under High Value Crops)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(listOf("Rice", "Corn", "Coconut", "Sugarcane", "High Value Crops")) { stdCrop ->
                                        FilterChip(
                                            selected = cropType.equals(stdCrop, ignoreCase = true),
                                            onClick = { cropType = stdCrop },
                                            label = { Text(stdCrop, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
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
