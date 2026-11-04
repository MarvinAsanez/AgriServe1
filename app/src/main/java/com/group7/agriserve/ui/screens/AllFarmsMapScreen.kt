package com.group7.agriserve.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.group7.agriserve.R
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.*
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Entity types displayed on the Unified Farmers Map & Disputes Screen
 */
enum class MapEntityType {
    FARM,
    FARMER,
    INCIDENT,
    DISPUTE
}

/**
 * Unified data marker representing any geotagged entity across AgriServe:
 * - Tagged Farms (farms collection)
 * - Registered RSBSA Farmers (farmers collection)
 * - Incident & Outbreak Reports (reports collection)
 * - Land Disputes & Fraud Reports (farm_disputes collection)
 */
data class MapEntityMarker(
    val id: String,
    val type: MapEntityType,
    val title: String,
    val subtitle: String,
    val location: LatLng,
    val ownerOrReporter: String = "",
    val contact: String = "",
    val category: String = "",
    val status: String = "",
    val cropType: String = "",
    val size: String = "",
    val isPublic: Boolean = true,
    val userId: String = "",
    val imageUrls: List<String> = emptyList(),
    val address: String = "",
    val description: String = "",
    val severity: String = "",
    val rsbsaNumber: String = "",
    val isVerified: Boolean = false,
    val timestamp: Timestamp? = null
) {
    // Compatibility accessors for legacy FarmMarker calls
    val name: String get() = title
    val owner: String get() = ownerOrReporter
}

// Backward compatibility alias
typealias FarmMarker = MapEntityMarker

/**
 * In-memory fast cache to ensure 0ms instantaneous loading when revisiting the screen
 */
object AllFarmsMapCache {
    var cachedEntities: List<MapEntityMarker>? = null
}

/**
 * Robustly extract coordinates from multiple possible Firestore formats:
 * 1. GeoPoint object ("farmLocation", "location", "coordinates", "geoPoint", "geo")
 * 2. Nested Map with latitude/longitude, _latitude/_longitude, or lat/lng
 * 3. Direct numeric or string latitude/longitude fields
 * 4. Nested personalInfo/farmInfo map addresses
 * 5. Philippine municipality centroid fallback with deterministic jitter
 */
fun extractFarmCoordinates(doc: DocumentSnapshot): LatLng? {
    try {
        // 1. Native GeoPoint fields
        listOf("farmLocation", "location", "coordinates", "geoPoint", "geo").forEach { field ->
            doc.getGeoPoint(field)?.let {
                if (it.latitude != 0.0 || it.longitude != 0.0) return LatLng(it.latitude, it.longitude)
            }
        }

        // 2. Map-based locations (e.g. from Web SDK or REST)
        val candidateMaps = listOfNotNull(
            doc.get("farmLocation") as? Map<*, *>,
            doc.get("location") as? Map<*, *>,
            doc.get("coordinates") as? Map<*, *>,
            doc.get("geo") as? Map<*, *>
        )
        for (map in candidateMaps) {
            val lat = (map["latitude"] as? Number)?.toDouble()
                ?: (map["_latitude"] as? Number)?.toDouble()
                ?: (map["lat"] as? Number)?.toDouble()
                ?: (map["_lat"] as? Number)?.toDouble()
                ?: (map["latitude"] as? String)?.toDoubleOrNull()
                ?: (map["lat"] as? String)?.toDoubleOrNull()

            val lng = (map["longitude"] as? Number)?.toDouble()
                ?: (map["_longitude"] as? Number)?.toDouble()
                ?: (map["lng"] as? Number)?.toDouble()
                ?: (map["_long"] as? Number)?.toDouble()
                ?: (map["longitude"] as? String)?.toDoubleOrNull()
                ?: (map["lng"] as? String)?.toDoubleOrNull()

            if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) {
                return LatLng(lat, lng)
            }
        }

        // 3. Top-level latitude / longitude numeric or string fields
        val topLat = doc.getDouble("latitude")
            ?: (doc.get("latitude") as? Number)?.toDouble()
            ?: doc.getString("latitude")?.toDoubleOrNull()
            ?: doc.getDouble("lat")
            ?: (doc.get("lat") as? Number)?.toDouble()
            ?: doc.getString("lat")?.toDoubleOrNull()

        val topLng = doc.getDouble("longitude")
            ?: (doc.get("longitude") as? Number)?.toDouble()
            ?: doc.getString("longitude")?.toDoubleOrNull()
            ?: doc.getDouble("lng")
            ?: (doc.get("lng") as? Number)?.toDouble()
            ?: doc.getString("lng")?.toDoubleOrNull()

        if (topLat != null && topLng != null && (topLat != 0.0 || topLng != 0.0)) {
            return LatLng(topLat, topLng)
        }

        // Check inside nested personalInfo or farmInfo maps
        val pInfo = doc.get("personalInfo") as? Map<*, *>
        val fInfo = doc.get("farmInfo") as? Map<*, *>

        // 4. Fallback centroid from municipal address with deterministic jitter
        val addressParts = listOfNotNull(
            doc.getString("farmAddress"),
            doc.getString("farmZone"),
            doc.getString("street"),
            doc.getString("barangay"),
            doc.getString("city"),
            doc.getString("municipality"),
            doc.getString("province"),
            doc.getString("address"),
            doc.getString("incidentLocation"),
            doc.getString("locationName"),
            pInfo?.get("address") as? String,
            pInfo?.get("barangay") as? String,
            pInfo?.get("municipality") as? String,
            fInfo?.get("address") as? String,
            fInfo?.get("farmAddress") as? String
        ).filter { it.isNotBlank() }

        if (addressParts.isNotEmpty()) {
            val combined = addressParts.joinToString(" ")
            val fallback = resolveCentroidFromAddress(combined, doc.id)
            if (fallback != null) return fallback
        }
    } catch (_: Exception) {
    }
    return null
}

/**
 * Resolves municipal centroid with deterministic offset so multiple markers in the same town don't overlap
 */
private fun resolveCentroidFromAddress(addressText: String, idSeed: String): LatLng? {
    val clean = addressText.lowercase()

    var hash = 0
    val seed = idSeed + addressText
    for (i in seed.indices) {
        hash = ((hash shl 5) - hash) + seed[i].code
    }
    val absHash = kotlin.math.abs(hash)
    val latJitter = (((absHash % 1000) / 1000.0) - 0.5) * 0.016
    val lngJitter = ((((absHash * 7) % 1000) / 1000.0) - 0.5) * 0.016

    val centroids = mapOf(
        "ragay" to LatLng(13.8167, 122.7833),
        "sipocot" to LatLng(13.7667, 122.9667),
        "libmanan" to LatLng(13.7000, 123.0667),
        "lupi" to LatLng(13.8000, 122.8833),
        "del gallego" to LatLng(13.9167, 122.6000),
        "pamplona" to LatLng(13.5833, 123.1000),
        "pasacao" to LatLng(13.5167, 123.0333),
        "san fernando" to LatLng(13.5667, 123.1500),
        "minalabac" to LatLng(13.5500, 123.1833),
        "milaor" to LatLng(13.6000, 123.1833),
        "gainza" to LatLng(13.6167, 123.1333),
        "camaligan" to LatLng(13.6167, 123.1667),
        "canaman" to LatLng(13.6500, 123.1667),
        "magarao" to LatLng(13.6667, 123.1833),
        "bombon" to LatLng(13.6833, 123.2000),
        "calabanga" to LatLng(13.7000, 123.2167),
        "tinambac" to LatLng(13.8167, 123.3167),
        "siruma" to LatLng(14.0167, 123.2333),
        "goa" to LatLng(13.7000, 123.4833),
        "san jose" to LatLng(13.7167, 123.5167),
        "tigaon" to LatLng(13.6333, 123.5000),
        "sagnay" to LatLng(13.6000, 123.5333),
        "sagñay" to LatLng(13.6000, 123.5333),
        "bula" to LatLng(13.4667, 123.2833),
        "baao" to LatLng(13.4500, 123.3667),
        "nabua" to LatLng(13.4000, 123.3667),
        "bato" to LatLng(13.3500, 123.3667),
        "balatan" to LatLng(13.3167, 123.2333),
        "iriga" to LatLng(13.4167, 123.4167),
        "buhi" to LatLng(13.4333, 123.5167),
        "caramoan" to LatLng(13.7667, 123.8667),
        "garchitorena" to LatLng(13.8833, 123.7000),
        "presentacion" to LatLng(13.7167, 123.7333),
        "naga" to LatLng(13.6218, 123.1948),
        "pili" to LatLng(13.5833, 123.2833),
        "camarines sur" to LatLng(13.6218, 123.1948),
        "albay" to LatLng(13.1391, 123.7438),
        "legazpi" to LatLng(13.1391, 123.7438),
        "sorsogon" to LatLng(12.9742, 124.0058),
        "nueva ecija" to LatLng(15.4859, 120.9673),
        "cabanatuan" to LatLng(15.4859, 120.9673),
        "pangasinan" to LatLng(15.9758, 120.5708),
        "tarlac" to LatLng(15.4802, 120.5979),
        "isabela" to LatLng(16.9754, 121.8107),
        "cagayan" to LatLng(17.6131, 121.7269),
        "iloilo" to LatLng(10.7202, 122.5621),
        "cebu" to LatLng(10.3157, 123.8854),
        "davao" to LatLng(7.1907, 125.4553),
        "bukidnon" to LatLng(8.1575, 125.1278)
    )

    for ((key, coord) in centroids) {
        if (clean.contains(key)) {
            return LatLng(coord.latitude + latJitter, coord.longitude + lngJitter)
        }
    }

    val defaultHub = LatLng(12.8797, 121.7740) // Philippines Central Hub
    return LatLng(defaultHub.latitude + latJitter, defaultHub.longitude + lngJitter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFarmsMapScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid ?: SessionManager.getSavedUserId(context) ?: ""

    // In-memory cache pre-hydration: if available, renders immediately (0ms)
    var allEntities by remember { mutableStateOf(AllFarmsMapCache.cachedEntities ?: emptyList()) }
    var isLoading by remember { mutableStateOf(AllFarmsMapCache.cachedEntities == null) }

    // Fallback safety timeout so progress bar never gets permanently stuck
    LaunchedEffect(Unit) {
        delay(2500L)
        isLoading = false
    }

    var selectedEntity by remember { mutableStateOf<MapEntityMarker?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "FARMS", "FARMERS", "INCIDENTS", "DISPUTES", "MY_ITEMS"

    // Dispute / Fraud Report Dialog State
    var showDisputeDialog by remember { mutableStateOf(false) }
    var disputingTarget by remember { mutableStateOf<MapEntityMarker?>(null) }
    var disputeCategory by remember { mutableStateOf("Unauthorized Farm Tagging / Land Dispute") }
    var disputeDescription by remember { mutableStateOf("") }
    var isSubmittingDispute by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    val disputeCategories = listOf(
        "Unauthorized Farm Tagging / Land Dispute",
        "Fraudulent Farmer / Fake Identity",
        "Inaccurate Boundaries / Exaggerated Size",
        "Non-Agricultural / Inactive Land Claim",
        "Duplicate / Multiple RSBSA Claimants",
        "Other Fraudulent / Suspicious Activity"
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(13.8167, 122.7833), 10f)
    }

    // Parallel Real-Time Listeners across all 4 collections:
    // 1. farms
    // 2. farmers
    // 3. reports (incidents)
    // 4. farm_disputes (fraud / land disputes)
    DisposableEffect(currentUid) {
        val farmsMap = mutableMapOf<String, MapEntityMarker>()
        val farmersMap = mutableMapOf<String, MapEntityMarker>()
        val reportsMap = mutableMapOf<String, MapEntityMarker>()
        val disputesMap = mutableMapOf<String, MapEntityMarker>()

        fun syncAllEntities() {
            val merged = mutableListOf<MapEntityMarker>()
            merged.addAll(farmsMap.values)
            merged.addAll(farmersMap.values)
            merged.addAll(reportsMap.values)
            merged.addAll(disputesMap.values)

            AllFarmsMapCache.cachedEntities = merged
            allEntities = merged
            isLoading = false

            // Frame camera on user's farm/item or default initial focus if at default overview zoom
            if (cameraPositionState.position.zoom <= 10.5f && merged.isNotEmpty()) {
                val myItem = merged.firstOrNull { it.userId == currentUid && currentUid.isNotBlank() }
                val target = myItem ?: merged.firstOrNull { it.type == MapEntityType.FARM } ?: merged.first()
                cameraPositionState.position = CameraPosition.fromLatLngZoom(target.location, 13f)
            }
        }

        // 1. Tagged Farms Listener
        val farmsSub = db.collection("farms").addSnapshotListener { snap, err ->
            if (err == null && snap != null) {
                farmsMap.clear()
                for (doc in snap.documents) {
                    val coords = extractFarmCoordinates(doc) ?: continue
                    val isFarmPublic = doc.getBoolean("isPublic") ?: true
                    val farmUserId = doc.getString("userId") ?: doc.getString("uid") ?: doc.getString("farmerId") ?: doc.getString("ownerId") ?: doc.id
                    @Suppress("UNCHECKED_CAST")
                    val images = (doc.get("farmImages") as? List<String>)
                        ?: (doc.getString("farmImage")?.let { listOf(it) })
                        ?: emptyList()
                    val farmName = doc.getString("farmName") ?: doc.getString("name") ?: "Tagged Farm"
                    val farmOwner = doc.getString("farmOwner") ?: doc.getString("owner") ?: "Registered Farmer"
                    val cropType = doc.getString("cropType") ?: doc.getString("crops") ?: "Agricultural Crop"
                    val farmSize = doc.getString("farmSize") ?: doc.getString("size") ?: "1.0"
                    val farmAddress = doc.getString("farmAddress") ?: doc.getString("address") ?: ""
                    val desc = doc.getString("description") ?: ""

                    farmsMap[doc.id] = MapEntityMarker(
                        id = doc.id,
                        type = MapEntityType.FARM,
                        title = farmName,
                        subtitle = "Claimed by $farmOwner • $cropType",
                        location = coords,
                        ownerOrReporter = farmOwner,
                        cropType = cropType,
                        size = farmSize,
                        isPublic = isFarmPublic,
                        userId = farmUserId,
                        imageUrls = images,
                        address = farmAddress,
                        description = desc,
                        category = cropType,
                        status = if (isFarmPublic) "Public Plot" else "Private Plot",
                        timestamp = doc.getTimestamp("timestamp")
                    )
                }
                syncAllEntities()
            }
        }

        // 2. Registered Farmers Listener
        val farmersSub = db.collection("farmers").addSnapshotListener { snap, err ->
            if (err == null && snap != null) {
                farmersMap.clear()
                for (doc in snap.documents) {
                    val coords = extractFarmCoordinates(doc) ?: continue
                    val pInfo = doc.get("personalInfo") as? Map<*, *>
                    val firstName = pInfo?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
                    val lastName = pInfo?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
                    val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
                        doc.getString("fullName") ?: doc.getString("name") ?: "Registered Farmer"
                    }
                    val cInfo = doc.get("contactInfo") as? Map<*, *>
                    val phone = cInfo?.get("phoneNumber") as? String
                        ?: doc.getString("phoneNumber")
                        ?: doc.getString("phone")
                        ?: ""
                    val rsbsa = doc.getString("rsbsaNumber") ?: doc.getString("rsbsa") ?: "Enrolled RSBSA"
                    val isVerif = doc.getBoolean("isVerified") == true ||
                        doc.getString("verificationStatus") == "Verified" ||
                        doc.getString("status") == "Approved"
                    val farmName = doc.getString("farmName") ?: "$fullName's Farm"
                    val crop = doc.getString("cropType") ?: "Mixed Crops"
                    val size = doc.getString("farmSize") ?: "1.0"
                    val addr = doc.getString("farmAddress") ?: doc.getString("address") ?: ""
                    val uid = doc.getString("uid") ?: doc.id

                    farmersMap[doc.id] = MapEntityMarker(
                        id = doc.id,
                        type = MapEntityType.FARMER,
                        title = fullName,
                        subtitle = "RSBSA: $rsbsa • ${if (isVerif) "Verified Farmer" else "Registered"}",
                        location = coords,
                        ownerOrReporter = fullName,
                        contact = phone,
                        cropType = crop,
                        size = size,
                        userId = uid,
                        address = addr,
                        category = farmName,
                        rsbsaNumber = rsbsa,
                        isVerified = isVerif,
                        status = if (isVerif) "Verified" else "Registered",
                        timestamp = doc.getTimestamp("timestamp")
                    )
                }
                syncAllEntities()
            }
        }

        // 3. Incident Reports Listener
        val reportsSub = db.collection("reports").addSnapshotListener { snap, err ->
            if (err == null && snap != null) {
                reportsMap.clear()
                for (doc in snap.documents) {
                    val coords = extractFarmCoordinates(doc) ?: continue
                    val category = doc.getString("category")
                        ?: doc.getString("reportCategory")
                        ?: doc.getString("type")
                        ?: "Farm Incident"
                    val severity = doc.getString("severity") ?: doc.getString("urgency") ?: "Moderate"
                    val description = doc.getString("description")
                        ?: doc.getString("details")
                        ?: "Field incident reported"
                    val status = doc.getString("status") ?: "Pending Review"
                    val reporter = doc.getString("farmerName")
                        ?: doc.getString("reporterName")
                        ?: doc.getString("name")
                        ?: "Concerned Farmer"
                    val contact = doc.getString("contact")
                        ?: doc.getString("contactNumber")
                        ?: doc.getString("phone")
                        ?: ""
                    val uid = doc.getString("userId") ?: doc.getString("farmerId") ?: ""
                    val addr = doc.getString("address") ?: doc.getString("farmAddress") ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val images = (doc.get("imageUrls") as? List<String>)
                        ?: (doc.get("images") as? List<String>)
                        ?: (doc.getString("imageUrl")?.let { listOf(it) })
                        ?: emptyList()

                    reportsMap[doc.id] = MapEntityMarker(
                        id = doc.id,
                        type = MapEntityType.INCIDENT,
                        title = "$category ($severity)",
                        subtitle = "Reported by: $reporter • Status: $status",
                        location = coords,
                        ownerOrReporter = reporter,
                        contact = contact,
                        category = category,
                        severity = severity,
                        status = status,
                        description = description,
                        userId = uid,
                        imageUrls = images,
                        address = addr,
                        timestamp = doc.getTimestamp("timestamp") ?: doc.getTimestamp("createdAt")
                    )
                }
                syncAllEntities()
            }
        }

        // 4. Farm Disputes & Fraud Reports Listener
        val disputesSub = db.collection("farm_disputes").addSnapshotListener { snap, err ->
            if (err == null && snap != null) {
                disputesMap.clear()
                for (doc in snap.documents) {
                    val coords = extractFarmCoordinates(doc) ?: continue
                    val disputedFarm = doc.getString("farmName") ?: "Disputed Farm Parcel"
                    val suspectOwner = doc.getString("farmOwner") ?: doc.getString("suspectClaimant") ?: "Suspect Claimant"
                    val category = doc.getString("category")
                        ?: doc.getString("reportCategory")
                        ?: "Unauthorized Farm Tagging / Land Dispute"
                    val description = doc.getString("description")
                        ?: "Ownership dispute or fraudulent boundary claim submitted to DA."
                    val status = doc.getString("status") ?: "Pending Investigation"
                    val reporterName = doc.getString("reporterName") ?: "Complainant Farmer"
                    val reporterPhone = doc.getString("reporterPhone") ?: doc.getString("contactNumber") ?: ""
                    val reporterId = doc.getString("reporterId") ?: ""
                    val crop = doc.getString("cropType") ?: "Disputed Crops"
                    val size = doc.getString("farmSize") ?: "N/A"

                    disputesMap[doc.id] = MapEntityMarker(
                        id = doc.id,
                        type = MapEntityType.DISPUTE,
                        title = "Dispute: $disputedFarm",
                        subtitle = "Claimed by $suspectOwner • Complainant: $reporterName",
                        location = coords,
                        ownerOrReporter = suspectOwner,
                        contact = reporterPhone,
                        category = category,
                        description = description,
                        status = status,
                        cropType = crop,
                        size = size,
                        userId = reporterId,
                        timestamp = doc.getTimestamp("timestamp") ?: doc.getTimestamp("created_at")
                    )
                }
                syncAllEntities()
            }
        }

        onDispose {
            farmsSub.remove()
            farmersSub.remove()
            reportsSub.remove()
            disputesSub.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.view_all_farms), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedEntity != null && selectedEntity?.type == MapEntityType.FARM && selectedEntity?.userId != currentUid) {
                        IconButton(
                            onClick = {
                                disputingTarget = selectedEntity
                                disputeDescription = ""
                                showDisputeDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Report,
                                contentDescription = "Report Farm",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        val visibleEntities = remember(allEntities, searchQuery, selectedFilter, currentUid) {
            allEntities.filter { entity ->
                val matchesFilter = when (selectedFilter) {
                    "FARMS" -> entity.type == MapEntityType.FARM
                    "FARMERS" -> entity.type == MapEntityType.FARMER
                    "INCIDENTS" -> entity.type == MapEntityType.INCIDENT
                    "DISPUTES" -> entity.type == MapEntityType.DISPUTE
                    "MY_ITEMS" -> entity.userId == currentUid && currentUid.isNotBlank()
                    else -> true
                }
                val matchesSearch = searchQuery.isBlank() ||
                    entity.title.contains(searchQuery, ignoreCase = true) ||
                    entity.subtitle.contains(searchQuery, ignoreCase = true) ||
                    entity.ownerOrReporter.contains(searchQuery, ignoreCase = true) ||
                    entity.cropType.contains(searchQuery, ignoreCase = true) ||
                    entity.category.contains(searchQuery, ignoreCase = true) ||
                    entity.address.contains(searchQuery, ignoreCase = true) ||
                    entity.description.contains(searchQuery, ignoreCase = true) ||
                    entity.rsbsaNumber.contains(searchQuery, ignoreCase = true)

                matchesFilter && matchesSearch
            }
        }

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.HYBRID),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true)
            ) {
                visibleEntities.forEach { entity ->
                    val isMyItem = entity.userId == currentUid && currentUid.isNotBlank()
                    val markerHue = when (entity.type) {
                        MapEntityType.FARM -> if (isMyItem) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_GREEN
                        MapEntityType.FARMER -> BitmapDescriptorFactory.HUE_CYAN
                        MapEntityType.INCIDENT -> BitmapDescriptorFactory.HUE_RED
                        MapEntityType.DISPUTE -> BitmapDescriptorFactory.HUE_ORANGE
                    }

                    val typeBadge = when (entity.type) {
                        MapEntityType.FARM -> "🌾 Farm: "
                        MapEntityType.FARMER -> "🧑‍🌾 Farmer: "
                        MapEntityType.INCIDENT -> "🔴 Incident: "
                        MapEntityType.DISPUTE -> "⚖️ Dispute: "
                    }

                    Marker(
                        state = MarkerState(position = entity.location),
                        title = "$typeBadge${entity.title}",
                        snippet = "${entity.subtitle} • Tap for details",
                        icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                        onClick = {
                            selectedEntity = entity
                            false
                        }
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Top Search & Interactive Filter Bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Search Input Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search farms, farmers, incidents, disputes...", fontSize = 13.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Unified Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allCount = allEntities.size
                    val farmsCount = allEntities.count { it.type == MapEntityType.FARM }
                    val farmersCount = allEntities.count { it.type == MapEntityType.FARMER }
                    val incidentsCount = allEntities.count { it.type == MapEntityType.INCIDENT }
                    val disputesCount = allEntities.count { it.type == MapEntityType.DISPUTE }
                    val myItemsCount = allEntities.count { it.userId == currentUid && currentUid.isNotBlank() }

                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All ($allCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == "FARMS",
                        onClick = { selectedFilter = "FARMS" },
                        label = { Text("🌾 Farms ($farmsCount)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Agriculture, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == "FARMERS",
                        onClick = { selectedFilter = "FARMERS" },
                        label = { Text("🧑‍🌾 Farmers ($farmersCount)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == "INCIDENTS",
                        onClick = { selectedFilter = "INCIDENTS" },
                        label = { Text("🔴 Incidents ($incidentsCount)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == "DISPUTES",
                        onClick = { selectedFilter = "DISPUTES" },
                        label = { Text("⚖️ Disputes ($disputesCount)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilter == "MY_ITEMS",
                        onClick = { selectedFilter = "MY_ITEMS" },
                        label = { Text("📌 My Items ($myItemsCount)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            // Bottom Dynamic Detail Card
            selectedEntity?.let { entity ->
                val isOwnItem = entity.userId == currentUid && currentUid.isNotBlank()
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (typeIcon, typeColor) = when (entity.type) {
                                    MapEntityType.FARM -> Pair(Icons.Default.Agriculture, Color(0xFF2E7D32))
                                    MapEntityType.FARMER -> Pair(Icons.Default.Person, Color(0xFF0288D1))
                                    MapEntityType.INCIDENT -> Pair(Icons.Default.Warning, Color(0xFFD32F2F))
                                    MapEntityType.DISPUTE -> Pair(Icons.Default.ReportProblem, Color(0xFFE65100))
                                }
                                Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(entity.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    if (entity.address.isNotBlank()) {
                                        Text(entity.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                                    } else if (entity.subtitle.isNotBlank()) {
                                        Text(entity.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (entity.type == MapEntityType.FARM && !isOwnItem) {
                                    IconButton(
                                        onClick = {
                                            disputingTarget = entity
                                            disputeDescription = ""
                                            showDisputeDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Report,
                                            contentDescription = "Report Farm",
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                // Type Tag Pill
                                Surface(
                                color = when (entity.type) {
                                    MapEntityType.FARM -> if (isOwnItem) Color(0xFFE1F5FE) else Color(0xFFE8F5E9)
                                    MapEntityType.FARMER -> Color(0xFFE0F7FA)
                                    MapEntityType.INCIDENT -> Color(0xFFFFEBEE)
                                    MapEntityType.DISPUTE -> Color(0xFFFFF3E0)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = when (entity.type) {
                                        MapEntityType.FARM -> if (isOwnItem) "My Farm Plot" else "Farm Plot"
                                        MapEntityType.FARMER -> if (entity.isVerified) "Verified Farmer" else "Farmer"
                                        MapEntityType.INCIDENT -> "Incident Report"
                                        MapEntityType.DISPUTE -> "Land Dispute"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (entity.type) {
                                        MapEntityType.FARM -> if (isOwnItem) Color(0xFF0277BD) else Color(0xFF2E7D32)
                                        MapEntityType.FARMER -> Color(0xFF00838F)
                                        MapEntityType.INCIDENT -> Color(0xFFC62828)
                                        MapEntityType.DISPUTE -> Color(0xFFEF6C00)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Images Carousel
                        if (entity.imageUrls.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(entity.imageUrls) { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Entity Photo",
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Entity-Specific Content
                        when (entity.type) {
                            MapEntityType.FARM -> {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Claimed Owner", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.ownerOrReporter, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Primary Crop", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.cropType, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Column {
                                        Text("Declared Size", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("${entity.size} Ha", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            MapEntityType.FARMER -> {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Farmer Name", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("RSBSA Number", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.rsbsaNumber, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Column {
                                        Text("Status", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(if (entity.isVerified) "Verified ✅" else "Registered", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (entity.contact.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("📞 Contact: ${entity.contact}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                }
                            }
                            MapEntityType.INCIDENT -> {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Severity", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        val sevColor = when (entity.severity.lowercase()) {
                                            "critical", "severe" -> Color(0xFFD32F2F)
                                            "high" -> Color(0xFFE65100)
                                            else -> Color(0xFF388E3C)
                                        }
                                        Text(entity.severity, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = sevColor)
                                    }
                                    Column {
                                        Text("Current Status", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Reporter", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.ownerOrReporter, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (entity.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(entity.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                            }
                            MapEntityType.DISPUTE -> {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Suspect Claimant", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.ownerOrReporter, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("Category", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.category, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    }
                                    Column {
                                        Text("Status", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(entity.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    }
                                }
                                if (entity.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Dispute Details: ${entity.description}", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "📍 Coordinates: ${String.format(Locale.US, "%.5f, %.5f", entity.location.latitude, entity.location.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entity.type == MapEntityType.FARM && !isOwnItem) {
                                Button(
                                    onClick = {
                                        disputingTarget = entity
                                        disputeDescription = ""
                                        disputeCategory = "Unauthorized Farm Tagging / Land Dispute"
                                        showDisputeDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Report, contentDescription = "Report", modifier = Modifier.size(18.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REPORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(entity.location, 16f)
                                }
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Center on map", tint = MaterialTheme.colorScheme.primary)
                            }

                            OutlinedButton(
                                onClick = { selectedEntity = null },
                                shape = RoundedCornerShape(10.dp),
                                modifier = if (entity.type != MapEntityType.FARM || isOwnItem) Modifier.weight(1f) else Modifier
                            ) {
                                Text("CLOSE")
                            }
                        }
                    }
                }
            }
        }
    }

    // Report Farm Dialog
    if (showDisputeDialog) {
        val target = disputingTarget ?: allEntities.firstOrNull { it.type == MapEntityType.FARM }
        var manualFarmName by remember { mutableStateOf(target?.title ?: "") }
        var manualClaimedOwner by remember { mutableStateOf(target?.ownerOrReporter ?: "") }
        var hasAgreedToLegalTerms by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingDispute) {
                    showDisputeDialog = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Report Farm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (target != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Disputed Entity: ${target.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Claimed Owner: ${target.ownerOrReporter}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Coordinates: ${String.format(Locale.US, "%.4f, %.4f", target.location.latitude, target.location.longitude)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = manualFarmName,
                            onValueChange = { manualFarmName = it },
                            label = { Text("Disputed Farm Name / Location") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualClaimedOwner,
                            onValueChange = { manualClaimedOwner = it },
                            label = { Text("Claimed Farmer (Suspect)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Reason for Report / Violation:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showCategoryDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = disputeCategory,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            disputeCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category, fontSize = 13.sp) },
                                    onClick = {
                                        disputeCategory = category
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Explanation & Ownership Details:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = disputeDescription,
                        onValueChange = { disputeDescription = it },
                        placeholder = {
                            Text(
                                "Describe the land dispute or fraud details (e.g. \"This parcel is my legitimate property under Title/Tax Dec #... but someone falsely tagged it.\")",
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Complainant Info (Submitted to DA):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Name: ${userViewModel.farmerName.value.ifBlank { "Registered Farmer" }}",
                                fontSize = 11.sp
                            )
                            Text(
                                text = "• Contact: ${userViewModel.farmerPhone.value.ifBlank { "Active Contact" }}",
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legal Notice, Rules & Philippine Laws Agreement Box
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Legal Terms, Rules & Applicable Laws",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Filing a formal farm dispute or fraud report is an official administrative proceeding. Please review the governing Philippine laws and DA regulations below:",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF3E2723)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 1. RA 3815 (Perjury)
                            Surface(
                                color = Color.White.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "⚖️ Revised Penal Code (Art. 183 - Perjury & False Testimony)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                    Text(
                                        text = "Knowingly submitting a false, malicious, or fabricated farm dispute under oath or in official government filings constitutes criminal perjury punishable by imprisonment under Philippine law.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFF424242)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 2. RA 10175 (Cybercrime Prevention Act)
                            Surface(
                                color = Color.White.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "🌐 Cybercrime Prevention Act of 2012 (R.A. 10175)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                    Text(
                                        text = "Prohibits computerized fraud, intentional tampering or falsification of digital farm geotags, and cyber-harassment against legitimate land claimants through digital systems.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFF424242)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 3. DA RSBSA Regulations & Penalties
                            Surface(
                                color = Color.White.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "🌾 DA Administrative Orders & RSBSA Regulations",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                    Text(
                                        text = "Frivolous reports filed to disrupt legitimate farmers will lead to immediate cancellation of DA seed/fertilizer subsidies, revocation of RSBSA registry, and blacklisting from DA programs.",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFF424242)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 4. Ground Adjudication Protocol
                            Surface(
                                color = Color.White.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "📍 Field Investigation & Agrarian Adjudication",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB71C1C)
                                    )
                                    Text(
                                        text = "All submitted disputes trigger a mandatory physical land audit by the DA Municipal Agriculture Office (MAO) and the Barangay Agrarian Reform Committee (BARC).",
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFF424242)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Agreement Checkbox
                    Surface(
                        color = if (hasAgreedToLegalTerms) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (hasAgreedToLegalTerms) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { hasAgreedToLegalTerms = !hasAgreedToLegalTerms }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = hasAgreedToLegalTerms,
                                onCheckedChange = { hasAgreedToLegalTerms = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I have read, understood, and agreed to the legal terms, rules, and laws. I solemnly swear under penalty of perjury (RA 3815) that this report is true, lawful, and submitted in good faith.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasAgreedToLegalTerms) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (disputeDescription.isBlank()) {
                            Toast.makeText(context, "Please provide an explanation or proof description", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!hasAgreedToLegalTerms) {
                            Toast.makeText(context, "You must agree to the legal terms and laws before submitting.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        isSubmittingDispute = true
                        val targetUserId = target?.userId ?: ""
                        val record = hashMapOf<String, Any>(
                            "farmId" to (target?.id ?: ""),
                            "farmName" to (target?.title ?: manualFarmName.ifBlank { "Disputed Farm Parcel" }),
                            "farmOwner" to (target?.ownerOrReporter ?: manualClaimedOwner.ifBlank { "Suspect Claimant" }),
                            "farmUserId" to targetUserId,
                            "farmerId" to targetUserId,
                            "cropType" to (target?.cropType ?: "Unspecified"),
                            "farmSize" to (target?.size ?: "N/A"),
                            "latitude" to (target?.location?.latitude ?: 12.8797),
                            "longitude" to (target?.location?.longitude ?: 121.7740),
                            "category" to disputeCategory,
                            "reportCategory" to disputeCategory,
                            "description" to disputeDescription,
                            "reporterId" to currentUid,
                            "reporterName" to userViewModel.farmerName.value.ifBlank { "Registered Farmer" },
                            "reporterPhone" to userViewModel.farmerPhone.value,
                            "reporterEmail" to userViewModel.farmerEmail.value,
                            "agreedToLegalTerms" to true,
                            "legalSwornDeclaration" to "Sworn under RA 3815 (Perjury) and RA 10175 (Cybercrime Prevention Act)",
                            "legalNoticeVersion" to "2026.1-DA-PH",
                            "status" to "Pending Investigation",
                            "farmerNotified" to true,
                            "farmerDisputed" to false,
                            "farmerDisputeExplanation" to "",
                            "farmerDisputeEvidence" to "",
                            "timestamp" to Timestamp.now(),
                            "created_at" to Timestamp.now()
                        )
                        db.collection("farm_disputes").add(record)
                            .addOnSuccessListener {
                                isSubmittingDispute = false
                                showDisputeDialog = false

                                // Automatically notify the registered farm owner so they can dispute if false/fraudulent
                                if (targetUserId.isNotBlank()) {
                                    val notif = hashMapOf<String, Any>(
                                        "userId" to targetUserId,
                                        "recipientId" to targetUserId,
                                        "type" to "FARM_REPORTED",
                                        "title" to "⚠️ Farm Report Alert",
                                        "message" to "Your farm '${record["farmName"]}' has been reported to the DA for: $disputeCategory. You have the right to respond and contest if fraudulent.",
                                        "farmId" to (target?.id ?: ""),
                                        "farmName" to (record["farmName"] ?: ""),
                                        "disputeCategory" to disputeCategory,
                                        "description" to disputeDescription,
                                        "timestamp" to Timestamp.now(),
                                        "read" to false
                                    )
                                    db.collection("notifications").add(notif)
                                }

                                Toast.makeText(context, "Report submitted to DA. The farm owner has been notified and may file a counter-dispute.", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                isSubmittingDispute = false
                                Toast.makeText(context, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isSubmittingDispute && hasAgreedToLegalTerms && disputeDescription.isNotBlank()
                ) {
                    if (isSubmittingDispute) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("SUBMIT REPORT")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisputeDialog = false },
                    enabled = !isSubmittingDispute
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}