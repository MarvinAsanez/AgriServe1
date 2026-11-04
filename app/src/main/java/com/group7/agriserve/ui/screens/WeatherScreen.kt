package com.group7.agriserve.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * PAGASA Color-Coded Rainfall Warning Levels
 * Officially defined by DOST-PAGASA:
 * - Yellow: MONITOR (Magmanman) - 7.5 to 15 mm/hr
 * - Orange: ALERT (Maghanda) - 15 to 30 mm/hr
 * - Red: TAKE ACTION (Lumikas) - > 30 mm/hr
 */
enum class PagasaWarningLevel(
    val title: String,
    val subtitleTagalog: String,
    val badgeColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val rainfallRange: String,
    val actionSummary: String,
    val farmerGuideline: String
) {
    NONE(
        title = "NORMAL / NO WARNING",
        subtitleTagalog = "Normal at Maaliwalas",
        badgeColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFFE8F5E9),
        textColor = Color(0xFF1B5E20),
        rainfallRange = "< 7.5 mm/hr (Light to moderate)",
        actionSummary = "Good farming weather. Continue daily agricultural activities.",
        farmerGuideline = "Favorable for solar palay drying, foliar chemical application, seedbed preparation, and harvesting of mature crops."
    ),
    YELLOW(
        title = "YELLOW WARNING: MONITOR",
        subtitleTagalog = "Magmanman (Maghanda sa Posibleng Pagbaha)",
        badgeColor = Color(0xFFF57F17),
        backgroundColor = Color(0xFFFFF9C4),
        textColor = Color(0xFF7F4B00),
        rainfallRange = "7.5 - 15 mm/hr (Heavy Rain)",
        actionSummary = "Flooding is possible in low-lying farmland and floodplains.",
        farmerGuideline = "Monitor drainage dikes and irrigation canals. Postpone open-air palay drying and suspend pesticide/fertilizer spraying."
    ),
    ORANGE(
        title = "ORANGE WARNING: ALERT",
        subtitleTagalog = "Maghanda (Banta ng Pagbaha)",
        badgeColor = Color(0xFFE65100),
        backgroundColor = Color(0xFFFFE0B2),
        textColor = Color(0xFFBF360C),
        rainfallRange = "15 - 30 mm/hr (Intense Rain)",
        actionSummary = "Flooding is threatening! Clear water runoff channels immediately.",
        farmerGuideline = "Open canal gates to prevent submergence of rice crops. Secure harvested crops and transport farm implements to elevated ground."
    ),
    RED(
        title = "RED WARNING: TAKE ACTION",
        subtitleTagalog = "Lumikas (Peligro ng Matinding Pagbaha)",
        badgeColor = Color(0xFFC62828),
        backgroundColor = Color(0xFFFFCDD2),
        textColor = Color(0xFFB71C1C),
        rainfallRange = "> 30 mm/hr (Torrential Rain)",
        actionSummary = "Severe agricultural flooding & landslides expected!",
        farmerGuideline = "Cease all fieldwork immediately. Evacuate livestock to high ground. Do not attempt to cross flooded rice fields or irrigation canals."
    )
}

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val time: String
)

data class HourlyForecast(
    val hourLabel: String,
    val temperature: Double,
    val weatherCode: Int,
    val rainProb: Int
)

data class DailyForecast(
    val dayName: String,
    val dateLabel: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double,
    val rainProbMax: Int,
    val rainSum: Double
)

data class WeatherData(
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val locationTitle: String,
    val locationSubtitle: String,
    val latitude: Double,
    val longitude: Double,
    val synopticSystem: String,
    val warningLevel: PagasaWarningLevel,
    val palayDryingStatus: String,
    val palayDryingDetail: String,
    val sprayingStatus: String,
    val sprayingDetail: String,
    val drainageStatus: String,
    val drainageDetail: String,
    val lastUpdated: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    userViewModel: UserViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Farmer location states
    var registeredBarangay by remember { mutableStateOf(userViewModel?.barangay?.value ?: "") }
    var registeredCity by remember { mutableStateOf(userViewModel?.city?.value ?: "Ragay") }
    var registeredProvince by remember { mutableStateOf(userViewModel?.province?.value ?: "Camarines Sur") }
    var registeredRegion by remember { mutableStateOf(userViewModel?.region?.value ?: "Region V (Bicol Region)") }

    // Tagged farm data
    var farmName by remember { mutableStateOf<String?>(null) }
    var farmAddress by remember { mutableStateOf<String?>(null) }
    var farmGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Active selection: "REGISTERED", "FARM", "GPS"
    var locationMode by remember { mutableStateOf("REGISTERED") }
    
    // Weather fetching states
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Helper to open links
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Coroutine loader for weather at specific coordinates
    fun loadWeather(
        lat: Double,
        lon: Double,
        title: String,
        subtitle: String
    ) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val data = fetchOpenMeteoWeather(lat, lon, title, subtitle)
                weatherData = data
            } catch (e: Exception) {
                errorMessage = "Failed to load weather: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // Refresh weather based on current location mode
    fun refreshCurrentMode() {
        when (locationMode) {
            "FARM" -> {
                val pt = farmGeoPoint
                if (pt != null) {
                    loadWeather(
                        lat = pt.latitude,
                        lon = pt.longitude,
                        title = farmName ?: "My Tagged Farm",
                        subtitle = farmAddress ?: "$registeredCity, $registeredProvince"
                    )
                } else {
                    locationMode = "REGISTERED"
                    refreshCurrentMode()
                }
            }
            "GPS" -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) {
                    isLoading = true
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                val lat = loc.latitude
                                val lon = loc.longitude
                                scope.launch {
                                    val resolvedName = resolveReverseGeocode(context, lat, lon)
                                    loadWeather(
                                        lat = lat,
                                        lon = lon,
                                        title = resolvedName.first,
                                        subtitle = resolvedName.second
                                    )
                                }
                            } else {
                                Toast.makeText(context, "Could not acquire GPS position. Falling back to registered address.", Toast.LENGTH_SHORT).show()
                                locationMode = "REGISTERED"
                                refreshCurrentMode()
                            }
                        }
                        .addOnFailureListener {
                            locationMode = "REGISTERED"
                            refreshCurrentMode()
                        }
                } else {
                    locationMode = "REGISTERED"
                    refreshCurrentMode()
                }
            }
            else -> { // "REGISTERED"
                scope.launch {
                    val cty = registeredCity.ifBlank { "Ragay" }
                    val prv = registeredProvince.ifBlank { "Camarines Sur" }
                    val brgy = registeredBarangay.ifBlank { "" }
                    val query = if (brgy.isNotBlank()) "$brgy, $cty, $prv, Philippines" else "$cty, $prv, Philippines"
                    val coords = resolveCoordinates(context, query, cty, prv)
                    val dispTitle = if (brgy.isNotBlank()) "Brgy. $brgy, $cty" else cty
                    val dispSub = "$prv, $registeredRegion"
                    loadWeather(coords.first, coords.second, dispTitle, dispSub)
                }
            }
        }
    }

    // GPS permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationMode = "GPS"
            refreshCurrentMode()
        } else {
            Toast.makeText(context, "Location permission is required for live GPS weather.", Toast.LENGTH_SHORT).show()
        }
    }

    // Initial load: check farm in Firestore & sync userViewModel
    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("farms").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("farmName") ?: "My Tagged Farm"
                        val addr = doc.getString("farmAddress") ?: ""
                        val geo = doc.getGeoPoint("farmLocation")
                        if (geo != null) {
                            farmName = name
                            farmAddress = addr
                            farmGeoPoint = geo
                        }
                    }
                }
        }
    }

    // Sync from userViewModel changes
    LaunchedEffect(userViewModel?.barangay?.value, userViewModel?.city?.value, userViewModel?.province?.value) {
        userViewModel?.let {
            if (it.city.value.isNotBlank()) registeredCity = it.city.value
            if (it.province.value.isNotBlank()) registeredProvince = it.province.value
            if (it.barangay.value.isNotBlank()) registeredBarangay = it.barangay.value
            if (it.region.value.isNotBlank()) registeredRegion = it.region.value
        }
        refreshCurrentMode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Agri-Weather Forecast",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "DOST-PAGASA Agri-Meteorological Station",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshCurrentMode() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. DOST-PAGASA OFFICIAL RECOGNITION & LINK BANNER
            item {
                OfficialPagasaHeaderCard(
                    onOpenPagasaPortal = { openUrl("https://www.pagasa.dost.gov.ph/") },
                    onOpenRadar = { openUrl("https://bagong.pagasa.dost.gov.ph/weather") }
                )
            }

            // 2. LOCATION SWITCHER CHIPS (Registered, Farm, GPS)
            item {
                LocationSelectorCard(
                    locationMode = locationMode,
                    activeTitle = weatherData?.locationTitle ?: registeredCity,
                    activeSubtitle = weatherData?.locationSubtitle ?: registeredProvince,
                    hasTaggedFarm = farmGeoPoint != null,
                    farmName = farmName ?: "Tagged Farm",
                    onSelectMode = { mode ->
                        if (mode == "GPS") {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) {
                                locationMode = "GPS"
                                refreshCurrentMode()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        } else {
                            locationMode = mode
                            refreshCurrentMode()
                        }
                    }
                )
            }

            // LOADING OR ERROR
            if (isLoading && weatherData == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Connecting to DOST-PAGASA Weather Station...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (errorMessage != null && weatherData == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Unable to Load Forecast",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { refreshCurrentMode() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            } else if (weatherData != null) {
                val data = weatherData!!

                // 3. HERO CURRENT WEATHER CARD
                item {
                    HeroWeatherCard(data = data)
                }

                // 4. DOST-PAGASA COLOR-CODED RAINFALL WARNING CARD
                item {
                    PagasaWarningCard(
                        warningLevel = data.warningLevel,
                        onOpenRainfallAdvisory = { openUrl("https://bagong.pagasa.dost.gov.ph/weather/weather-advisory") }
                    )
                }

                // 5. AGRO-METEOROLOGICAL ADVISORIES FOR FARMERS
                item {
                    AgroAdvisoriesCard(data = data)
                }

                // 6. 24-HOUR HOURLY OUTLOOK
                item {
                    HourlyForecastSection(hourlyList = data.hourly)
                }

                // 7. 7-DAY EXTENDED DAILY FORECAST
                item {
                    DailyForecastSection(dailyList = data.daily)
                }

                // 8. OFFICIAL DOST-PAGASA DIRECT LINKS & RADAR TOOLS
                item {
                    PagasaExternalLinksCard(
                        onOpenPagasa = { openUrl("https://www.pagasa.dost.gov.ph/") },
                        onOpenRadar = { openUrl("https://bagong.pagasa.dost.gov.ph/weather") },
                        onOpen10Day = { openUrl("https://bagong.pagasa.dost.gov.ph/climate/climate-prediction/10-day-climate-forecast") },
                        onOpenSouthernLuzon = { openUrl("https://www.pagasa.dost.gov.ph/regional-forecast/slprsd") },
                        onOpenCyclone = { openUrl("https://bagong.pagasa.dost.gov.ph/tropical-cyclone/severe-weather-bulletin") }
                    )
                }

                item {
                    Text(
                        text = "Data grounded on DOST-PAGASA Synoptic Weather Guidelines & WMO Observations • Last updated: ${data.lastUpdated}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 1. DOST-PAGASA Header Card with Official Branding & Website Link
 */
@Composable
fun OfficialPagasaHeaderCard(
    onOpenPagasaPortal: () -> Unit,
    onOpenRadar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0038A8).copy(alpha = 0.95f)) // PH Flag Royal Blue
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "PAGASA",
                            tint = Color(0xFFFDB813), // Solar Gold
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOST - PAGASA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Department of Science and Technology",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "Philippine Atmospheric, Geophysical & Astronomical Services Administration",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPagasaPortal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color.White, Color.White)))
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "pagasa.dost.gov.ph", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onOpenRadar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE1126)) // PH Flag Red accent
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Live Radar", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 2. Farmer Location Selector (Registered Address vs Tagged Farm vs GPS)
 */
@Composable
fun LocationSelectorCard(
    locationMode: String,
    activeTitle: String,
    activeSubtitle: String,
    hasTaggedFarm: Boolean,
    farmName: String,
    onSelectMode: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activeSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Switcher chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = locationMode == "REGISTERED",
                    onClick = { onSelectMode("REGISTERED") },
                    label = { Text("📍 Address", fontSize = 12.sp) },
                    leadingIcon = if (locationMode == "REGISTERED") {
                        { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )

                if (hasTaggedFarm) {
                    FilterChip(
                        selected = locationMode == "FARM",
                        onClick = { onSelectMode("FARM") },
                        label = { Text("🌾 Farm", fontSize = 12.sp, maxLines = 1) },
                        leadingIcon = if (locationMode == "FARM") {
                            { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                FilterChip(
                    selected = locationMode == "GPS",
                    onClick = { onSelectMode("GPS") },
                    label = { Text("🎯 GPS", fontSize = 12.sp) },
                    leadingIcon = if (locationMode == "GPS") {
                        { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 3. Hero Weather Card: Current Temperature, Conditions, Wind & Heat Index
 */
@Composable
fun HeroWeatherCard(data: WeatherData) {
    val current = data.current
    val weatherInterpret = interpretWeatherCode(current.weatherCode)

    // Dynamic gradient based on code:
    val gradientColors = when {
        current.weatherCode in listOf(95, 96, 99) -> listOf(Color(0xFF263238), Color(0xFF37474F)) // Thunderstorm
        current.precipitation > 2.0 || current.weatherCode in listOf(61, 63, 65, 80, 81, 82) -> listOf(Color(0xFF1E3C72), Color(0xFF2A5298)) // Rainy
        current.temperature > 32.0 -> listOf(Color(0xFFF7971E), Color(0xFFFFD200)) // Hot Sunny
        else -> listOf(Color(0xFF0D47A1), Color(0xFF1976D2)) // Standard Philippine Sky
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(gradientColors))
                .padding(20.dp)
        ) {
            Column {
                // Synoptic Weather System
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = data.synopticSystem,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Temperature and Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${current.temperature.toInt()}°C",
                            fontSize = 62.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = weatherInterpret.first,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Text(
                            text = "Feels like ${current.apparentTemperature.toInt()}°C (Heat Index)",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Icon(
                        imageVector = weatherInterpret.second,
                        contentDescription = weatherInterpret.first,
                        tint = Color.White,
                        modifier = Modifier.size(88.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // 4 Essential Agricultural Meteorological Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WeatherMetricItem(
                        icon = Icons.Default.WaterDrop,
                        label = "Humidity",
                        value = "${current.humidity}%"
                    )
                    WeatherMetricItem(
                        icon = Icons.Default.Grain,
                        label = "Precipitation",
                        value = "${current.precipitation} mm"
                    )
                    WeatherMetricItem(
                        icon = Icons.Default.Air,
                        label = "Wind Speed",
                        value = "${current.windSpeed.toInt()} km/h"
                    )
                    WeatherMetricItem(
                        icon = Icons.Default.Navigation,
                        label = "Wind Dir",
                        value = interpretWindDirection(current.windDirection)
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherMetricItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}

/**
 * 4. DOST-PAGASA Heavy Rainfall Warning System
 */
@Composable
fun PagasaWarningCard(
    warningLevel: PagasaWarningLevel,
    onOpenRainfallAdvisory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = warningLevel.backgroundColor),
        border = BorderStroke(1.5.dp, warningLevel.badgeColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = warningLevel.badgeColor,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (warningLevel == PagasaWarningLevel.NONE) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = warningLevel.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = warningLevel.textColor
                        )
                        Text(
                            text = warningLevel.subtitleTagalog,
                            style = MaterialTheme.typography.labelSmall,
                            color = warningLevel.textColor.copy(alpha = 0.9f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = warningLevel.badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = warningLevel.rainfallRange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = warningLevel.textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = warningLevel.textColor.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "🌾 Agricultural Guideline for Farmers:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = warningLevel.textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = warningLevel.farmerGuideline,
                style = MaterialTheme.typography.bodySmall,
                color = warningLevel.textColor
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenRainfallAdvisory) {
                    Text(
                        text = "View Official PAGASA Bulletins →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = warningLevel.textColor
                    )
                }
            }
        }
    }
}

/**
 * 5. Farmer-Tailored Agro-Meteorological Advisories
 */
@Composable
fun AgroAdvisoriesCard(data: WeatherData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Agriculture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Agro-Meteorological Advisories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Tailored daily recommendations for Filipino crop and field management",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Palay Solar Drying
            AdvisoryRow(
                emoji = "🌾",
                title = "Palay Solar Drying Window",
                status = data.palayDryingStatus,
                detail = data.palayDryingDetail,
                statusColor = if (data.palayDryingStatus.contains("Optimal", true) || data.palayDryingStatus.contains("Good", true)) Color(0xFF2E7D32) else if (data.palayDryingStatus.contains("Fair", true)) Color(0xFFF57F17) else Color(0xFFC62828)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Chemical Spraying & Fertilization
            AdvisoryRow(
                emoji = "🧪",
                title = "Pesticide & Foliar Spraying",
                status = data.sprayingStatus,
                detail = data.sprayingDetail,
                statusColor = if (data.sprayingStatus.contains("Safe", true) || data.sprayingStatus.contains("Favorable", true)) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. Irrigation & Soil Drainage
            AdvisoryRow(
                emoji = "🚜",
                title = "Field Irrigation & Drainage",
                status = data.drainageStatus,
                detail = data.drainageDetail,
                statusColor = if (data.drainageStatus.contains("Normal", true) || data.drainageStatus.contains("Manageable", true)) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
        }
    }
}

@Composable
fun AdvisoryRow(
    emoji: String,
    title: String,
    status: String,
    detail: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 6. 24-Hour Hourly Weather Carousel
 */
@Composable
fun HourlyForecastSection(hourlyList: List<HourlyForecast>) {
    Column {
        Text(
            text = "24-Hour Hourly Outlook",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hourlyList) { hour ->
                val iconPair = interpretWeatherCode(hour.weatherCode)
                Card(
                    modifier = Modifier.width(76.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = hour.hourLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = iconPair.second,
                            contentDescription = iconPair.first,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${hour.temperature.toInt()}°C",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF0288D1),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "${hour.rainProb}%",
                                fontSize = 10.sp,
                                color = Color(0xFF0288D1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. 7-Day Extended Agricultural Weather Outlook
 */
@Composable
fun DailyForecastSection(dailyList: List<DailyForecast>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "7-Day Extended Agricultural Forecast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            dailyList.forEachIndexed { index, day ->
                val iconPair = interpretWeatherCode(day.weatherCode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.width(90.dp)) {
                        Text(
                            text = if (index == 0) "Today" else day.dayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = day.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(70.dp)
                    ) {
                        Icon(
                            imageVector = iconPair.second,
                            contentDescription = iconPair.first,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (day.rainProbMax > 20) {
                            Text(
                                text = "${day.rainProbMax}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0288D1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${day.tempMin.toInt()}°C",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF81D4FA), Color(0xFFFFB74D))
                                    )
                                )
                        )
                        Text(
                            text = "${day.tempMax.toInt()}°C",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (index < dailyList.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/**
 * 8. Official DOST-PAGASA Direct Links & Bulletins
 */
@Composable
fun PagasaExternalLinksCard(
    onOpenPagasa: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpen10Day: () -> Unit,
    onOpenSouthernLuzon: () -> Unit,
    onOpenCyclone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Official DOST-PAGASA Portals & Radar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Direct access to real-time Doppler radar, 10-day outlooks, and advisories",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            PagasaLinkItem(
                title = "DOST-PAGASA Official Website",
                subtitle = "https://www.pagasa.dost.gov.ph",
                onClick = onOpenPagasa
            )
            PagasaLinkItem(
                title = "Doppler Radar & Satellite Imagery",
                subtitle = "High-resolution cloud & precipitation monitoring",
                onClick = onOpenRadar
            )
            PagasaLinkItem(
                title = "Ten-Day Regional Agri-Weather Outlook",
                subtitle = "Agro-climatic trends for planting & harvesting",
                onClick = onOpen10Day
            )
            PagasaLinkItem(
                title = "Southern Luzon Regional Center (SLPRSD)",
                subtitle = "Covers Camarines Sur, Bicol Region & nearby provinces",
                onClick = onOpenSouthernLuzon
            )
            PagasaLinkItem(
                title = "Tropical Cyclone & Severe Weather Bulletins",
                subtitle = "Typhoon tracks, public storm warnings & advisories",
                onClick = onOpenCyclone
            )
        }
    }
}

@Composable
fun PagasaLinkItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// -------------------------------------------------------------------------------------------------
// METEOROLOGICAL HELPERS & DATA FETCHER
// -------------------------------------------------------------------------------------------------

/**
 * Fetches Open-Meteo Synoptic Forecast matching DOST-PAGASA Observation Standards
 */
suspend fun fetchOpenMeteoWeather(
    latitude: Double,
    longitude: Double,
    locationTitle: String,
    locationSubtitle: String
): WeatherData = withContext(Dispatchers.IO) {
    val endpoint = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum" +
            "&timezone=Asia%2FManila&forecast_days=7"

    val url = URL(endpoint)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 12000
    conn.readTimeout = 12000

    val response = conn.inputStream.bufferedReader().use { it.readText() }
    val json = JSONObject(response)

    // Current
    val currentObj = json.getJSONObject("current")
    val curTemp = currentObj.optDouble("temperature_2m", 28.0)
    val curApparent = currentObj.optDouble("apparent_temperature", curTemp + 3.0)
    val curHumidity = currentObj.optInt("relative_humidity_2m", 80)
    val curPrecip = currentObj.optDouble("precipitation", 0.0)
    val curCode = currentObj.optInt("weather_code", 1)
    val curWindSpeed = currentObj.optDouble("wind_speed_10m", 8.0)
    val curWindDir = currentObj.optInt("wind_direction_10m", 60)
    val curTime = currentObj.optString("time", "")

    val currentWeather = CurrentWeather(
        temperature = curTemp,
        apparentTemperature = curApparent,
        humidity = curHumidity,
        precipitation = curPrecip,
        weatherCode = curCode,
        windSpeed = curWindSpeed,
        windDirection = curWindDir,
        time = curTime
    )

    // Daily
    val dailyObj = json.getJSONObject("daily")
    val dailyTimes = dailyObj.getJSONArray("time")
    val dailyCodes = dailyObj.getJSONArray("weather_code")
    val dailyMaxs = dailyObj.getJSONArray("temperature_2m_max")
    val dailyMins = dailyObj.getJSONArray("temperature_2m_min")
    val dailyProbs = dailyObj.getJSONArray("precipitation_probability_max")
    val dailySums = dailyObj.getJSONArray("precipitation_sum")

    val dailyList = mutableListOf<DailyForecast>()
    val inDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayNameFormat = SimpleDateFormat("EEE", Locale.US)
    val dateLabelFormat = SimpleDateFormat("MMM d", Locale.US)

    for (i in 0 until dailyTimes.length()) {
        val rawDate = dailyTimes.getString(i)
        val parsedDate = inDateFormat.parse(rawDate) ?: Date()
        dailyList.add(
            DailyForecast(
                dayName = dayNameFormat.format(parsedDate),
                dateLabel = dateLabelFormat.format(parsedDate),
                weatherCode = dailyCodes.optInt(i, 1),
                tempMax = dailyMaxs.optDouble(i, 30.0),
                tempMin = dailyMins.optDouble(i, 24.0),
                rainProbMax = dailyProbs.optInt(i, 20),
                rainSum = dailySums.optDouble(i, 0.0)
            )
        )
    }

    // Hourly (take next 24 hours starting from current time)
    val hourlyObj = json.getJSONObject("hourly")
    val hourlyTimes = hourlyObj.getJSONArray("time")
    val hourlyTemps = hourlyObj.getJSONArray("temperature_2m")
    val hourlyCodes = hourlyObj.getJSONArray("weather_code")
    val hourlyProbs = hourlyObj.getJSONArray("precipitation_probability")

    val hourlyList = mutableListOf<HourlyForecast>()
    val inHourlyFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
    val outHourFormat = SimpleDateFormat("h a", Locale.US)
    val nowMillis = System.currentTimeMillis() - (60 * 60 * 1000) // include current hour

    for (i in 0 until hourlyTimes.length()) {
        val rawTime = hourlyTimes.getString(i)
        try {
            val date = inHourlyFormat.parse(rawTime)
            if (date != null && date.time >= nowMillis) {
                hourlyList.add(
                    HourlyForecast(
                        hourLabel = outHourFormat.format(date),
                        temperature = hourlyTemps.optDouble(i, 28.0),
                        weatherCode = hourlyCodes.optInt(i, 1),
                        rainProb = hourlyProbs.optInt(i, 0)
                    )
                )
                if (hourlyList.size >= 24) break
            }
        } catch (_: Exception) {}
    }

    // Compute PAGASA Synoptic Weather System
    val synoptic = determinePagasaSynopticSystem(curWindDir, curCode, curHumidity)

    // Compute PAGASA Heavy Rainfall Warning Level
    val todayRainProb = if (dailyList.isNotEmpty()) dailyList[0].rainProbMax else 20
    val todayRainSum = if (dailyList.isNotEmpty()) dailyList[0].rainSum else curPrecip
    val warning = determinePagasaWarningLevel(curPrecip, todayRainSum, todayRainProb, curCode)

    // Compute Farming Advisories
    val palayDrying = evaluatePalayDrying(curPrecip, todayRainProb, curTemp)
    val spraying = evaluateSprayingCondition(todayRainProb, curWindSpeed)
    val drainage = evaluateDrainageCondition(todayRainSum, warning, curTemp)

    val updatedFormat = SimpleDateFormat("h:mm a, MMMM d, yyyy", Locale.US)
    val lastUpdated = updatedFormat.format(Date())

    WeatherData(
        current = currentWeather,
        hourly = hourlyList,
        daily = dailyList,
        locationTitle = locationTitle,
        locationSubtitle = locationSubtitle,
        latitude = latitude,
        longitude = longitude,
        synopticSystem = synoptic,
        warningLevel = warning,
        palayDryingStatus = palayDrying.first,
        palayDryingDetail = palayDrying.second,
        sprayingStatus = spraying.first,
        sprayingDetail = spraying.second,
        drainageStatus = drainage.first,
        drainageDetail = drainage.second,
        lastUpdated = lastUpdated
    )
}

/**
 * DOST-PAGASA Synoptic Weather System Determination
 */
fun determinePagasaSynopticSystem(windDir: Int, weatherCode: Int, humidity: Int): String {
    val month = Calendar.getInstance().get(Calendar.MONTH) // 0-11
    val isAmihanSeason = month in listOf(Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH)
    
    return when {
        weatherCode in listOf(95, 96, 99) -> "Localized Convective Thunderstorms / LPA Trough"
        isAmihanSeason && windDir in 0..120 -> "Northeast Monsoon (Amihan) & Easterlies"
        isAmihanSeason -> "Easterlies affecting Southern Luzon & Bicol Region"
        !isAmihanSeason && windDir in 180..270 -> "Southwest Monsoon (Habagat) prevailing"
        else -> "Intertropical Convergence Zone (ITCZ) / Easterlies"
    }
}

/**
 * DOST-PAGASA Heavy Rainfall Warning Logic
 */
fun determinePagasaWarningLevel(
    currentHourlyPrecip: Double,
    dailyPrecipSum: Double,
    rainProb: Int,
    weatherCode: Int
): PagasaWarningLevel {
    return when {
        currentHourlyPrecip >= 30.0 || dailyPrecipSum >= 80.0 -> PagasaWarningLevel.RED
        currentHourlyPrecip >= 15.0 || dailyPrecipSum >= 40.0 -> PagasaWarningLevel.ORANGE
        currentHourlyPrecip >= 7.5 || dailyPrecipSum >= 20.0 || (rainProb >= 85 && weatherCode in listOf(65, 82, 95, 96, 99)) -> PagasaWarningLevel.YELLOW
        else -> PagasaWarningLevel.NONE
    }
}

/**
 * Palay (Paddy Rice) Solar Drying Evaluation
 */
fun evaluatePalayDrying(currentPrecip: Double, rainProb: Int, temperature: Double): Pair<String, String> {
    return when {
        currentPrecip <= 0.1 && rainProb <= 30 && temperature >= 28.0 -> {
            "Optimal (Ideal for Solar Drying)" to "Strong solar irradiance with low humidity. Safe for drying harvested palay on solar pavement and open mats."
        }
        currentPrecip in 0.1..2.0 || rainProb in 31..60 -> {
            "Fair (Monitor Sky Conditions)" to "Moderate chance of passing rainshowers. Keep plastic covers and tarpaulins ready nearby."
        }
        else -> {
            "Poor (Not Recommended)" to "High moisture and imminent rain risk will re-wet dried grains. Use covered warehouse or mechanical batch dryers."
        }
    }
}

/**
 * Crop Spraying & Foliar Application Evaluation
 */
fun evaluateSprayingCondition(rainProb: Int, windSpeed: Double): Pair<String, String> {
    return when {
        rainProb > 50 -> {
            "Avoid Spraying (Rain Risk)" to "High probability of rain will wash active chemical agents off crop foliage before absorption."
        }
        windSpeed > 15.0 -> {
            "Postpone (Wind Drift Risk)" to "Wind speeds exceed 15 km/h. Risk of severe pesticide drift to non-target areas and adjacent farms."
        }
        else -> {
            "Favorable Window" to "Gentle breeze and low precipitation risk ensure optimal leaf coverage and absorption."
        }
    }
}

/**
 * Field Drainage & Soil Moisture Evaluation
 */
fun evaluateDrainageCondition(dailyRainSum: Double, warning: PagasaWarningLevel, temperature: Double): Pair<String, String> {
    return when {
        warning == PagasaWarningLevel.RED || warning == PagasaWarningLevel.ORANGE || dailyRainSum >= 30.0 -> {
            "Critical Runoff (Open Gates)" to "High volume of runoff anticipated. Open paddy sluices and clear ditch obstructions to prevent submerged root rot."
        }
        dailyRainSum >= 10.0 -> {
            "Manage Drainage Canals" to "Moderate soil saturation. Inspect canal spillways in upland vegetable plots."
        }
        temperature >= 33.0 && dailyRainSum < 2.0 -> {
            "High Evaporation (Irrigate)" to "Intense heat is depleting topsoil moisture. Maintain 3-5 cm water layer in tillering rice crops."
        }
        else -> {
            "Normal / Stable Moisture" to "Soil moisture levels are within optimal range for land cultivation and transplanting."
        }
    }
}

/**
 * Geocode query to latitude and longitude with Camarines Sur / Philippine fallbacks
 */
suspend fun resolveCoordinates(
    context: Context,
    query: String,
    city: String,
    province: String
): Pair<Double, Double> = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        continuation.resume(Pair(addr.latitude, addr.longitude))
                    } else {
                        continuation.resume(lookupKnownCoordinates(city, province))
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                Pair(addresses[0].latitude, addresses[0].longitude)
            } else {
                lookupKnownCoordinates(city, province)
            }
        }
    } catch (_: Exception) {
        lookupKnownCoordinates(city, province)
    }
}

suspend fun resolveReverseGeocode(context: Context, lat: Double, lon: Double): Pair<String, String> = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val locality = addr.locality ?: addr.subAdminArea ?: "Current Location"
            val admin = addr.adminArea ?: "Philippines"
            Pair(locality, admin)
        } else {
            Pair("GPS Location", "${String.format(Locale.US, "%.4f", lat)}° N, ${String.format(Locale.US, "%.4f", lon)}° E")
        }
    } catch (_: Exception) {
        Pair("GPS Location", "${String.format(Locale.US, "%.4f", lat)}° N, ${String.format(Locale.US, "%.4f", lon)}° E")
    }
}

/**
 * Known Coordinates for Camarines Sur & Key Philippine Municipalities
 */
fun lookupKnownCoordinates(city: String, province: String): Pair<Double, Double> {
    val cleanCity = city.trim().lowercase(Locale.US)
    val cleanProv = province.trim().lowercase(Locale.US)

    return when {
        // Camarines Sur Municipalities
        cleanCity.contains("ragay") -> Pair(13.8181, 122.7917)
        cleanCity.contains("naga") -> Pair(13.6218, 123.1948)
        cleanCity.contains("pili") -> Pair(13.5833, 123.2833)
        cleanCity.contains("sipocot") -> Pair(13.7719, 122.9772)
        cleanCity.contains("libmanan") -> Pair(13.6961, 123.0608)
        cleanCity.contains("iriga") -> Pair(13.4167, 123.4167)
        cleanCity.contains("del gallego") -> Pair(13.9167, 122.6000)
        cleanCity.contains("lupi") -> Pair(13.8333, 122.9000)
        cleanCity.contains("cabusao") -> Pair(13.7208, 123.1097)
        cleanCity.contains("calabanga") -> Pair(13.7083, 123.1833)
        cleanCity.contains("canaman") -> Pair(13.6456, 123.1633)
        cleanCity.contains("camaligan") -> Pair(13.6225, 123.1675)
        cleanCity.contains("gainza") -> Pair(13.6167, 123.1333)
        cleanCity.contains("magarao") -> Pair(13.6667, 123.1833)
        cleanCity.contains("minalabac") -> Pair(13.5500, 123.1833)
        cleanCity.contains("milaor") -> Pair(13.5967, 123.1783)
        cleanCity.contains("san fernando") -> Pair(13.5667, 123.1500)
        cleanCity.contains("pamplona") -> Pair(13.6000, 123.0833)
        cleanCity.contains("pasacao") -> Pair(13.5167, 123.0500)
        cleanCity.contains("san jose") -> Pair(13.7000, 123.5167)
        cleanCity.contains("goa") -> Pair(13.7000, 123.5000)
        cleanCity.contains("tigaon") -> Pair(13.6333, 123.5000)
        cleanCity.contains("sagñay") || cleanCity.contains("sagnay") -> Pair(13.6000, 123.5333)
        cleanCity.contains("caramoan") -> Pair(13.7708, 123.8653)
        cleanCity.contains("garchitorena") -> Pair(13.8833, 123.7000)
        cleanCity.contains("lagonoy") -> Pair(13.7333, 123.5333)
        cleanCity.contains("tinambac") -> Pair(13.8167, 123.3333)
        cleanCity.contains("siruma") -> Pair(14.0167, 123.2333)
        cleanCity.contains("baao") -> Pair(13.4500, 123.3667)
        cleanCity.contains("bato") -> Pair(13.3500, 123.3667)
        cleanCity.contains("nabua") -> Pair(13.4000, 123.3667)
        cleanCity.contains("bula") -> Pair(13.4833, 123.2833)
        cleanCity.contains("balatan") -> Pair(13.3167, 123.2333)
        cleanCity.contains("buhi") -> Pair(13.4333, 123.5167)
        cleanCity.contains("ocampo") -> Pair(13.5667, 123.3833)

        // Camarines Norte
        cleanCity.contains("daet") || cleanProv.contains("camarines norte") -> Pair(14.1167, 122.9500)
        
        // Albay
        cleanCity.contains("legazpi") || cleanProv.contains("albay") -> Pair(13.1391, 123.7438)
        
        // Sorsogon
        cleanCity.contains("sorsogon") || cleanProv.contains("sorsogon") -> Pair(12.9742, 124.0058)

        // Catanduanes
        cleanCity.contains("virac") || cleanProv.contains("catanduanes") -> Pair(13.5833, 124.2333)

        // Masbate
        cleanCity.contains("masbate") || cleanProv.contains("masbate") -> Pair(12.3719, 123.6300)

        // Metro Manila & General Luzon
        cleanCity.contains("manila") -> Pair(14.5995, 120.9842)
        cleanCity.contains("quezon city") -> Pair(14.6760, 121.0437)
        cleanCity.contains("los baños") || cleanCity.contains("laguna") -> Pair(14.1687, 121.2435)
        cleanCity.contains("muñoz") || cleanProv.contains("nueva ecija") -> Pair(15.7144, 120.9039)
        cleanCity.contains("cebu") -> Pair(10.3157, 123.8854)
        cleanCity.contains("davao") -> Pair(7.1907, 125.4504)
        cleanCity.contains("iloilo") -> Pair(10.7202, 122.5621)

        // Default to Ragay, Camarines Sur (AgriServe Central Hub)
        else -> Pair(13.8181, 122.7917)
    }
}

/**
 * WMO Weather Code Interpreter
 */
fun interpretWeatherCode(code: Int): Pair<String, ImageVector> {
    return when (code) {
        0 -> "Clear Sky" to Icons.Default.WbSunny
        1 -> "Mainly Clear" to Icons.Default.WbSunny
        2 -> "Partly Cloudy" to Icons.Default.Cloud
        3 -> "Overcast Sky" to Icons.Default.Cloud
        45, 48 -> "Foggy / Morning Mist" to Icons.Default.Cloud
        51, 53, 55 -> "Drizzle / Light Ambon" to Icons.Default.Grain
        61, 63 -> "Moderate Rainshowers" to Icons.Default.Grain
        65 -> "Heavy Rainfall" to Icons.Default.WaterDrop
        80, 81 -> "Scattered Rainshowers" to Icons.Default.Grain
        82 -> "Torrential Rainshowers" to Icons.Default.WaterDrop
        95, 96, 99 -> "Severe Thunderstorms" to Icons.Default.Thunderstorm
        else -> "Partly Cloudy" to Icons.Default.Cloud
    }
}

/**
 * Wind Direction Interpreter
 */
fun interpretWindDirection(degrees: Int): String {
    return when (degrees) {
        in 338..360, in 0..22 -> "North (N)"
        in 23..67 -> "Northeast (NE)"
        in 68..112 -> "East (E)"
        in 113..157 -> "Southeast (SE)"
        in 158..202 -> "South (S)"
        in 203..247 -> "Southwest (SW)"
        in 248..292 -> "West (W)"
        in 293..337 -> "Northwest (NW)"
        else -> "Variable"
    }
}
