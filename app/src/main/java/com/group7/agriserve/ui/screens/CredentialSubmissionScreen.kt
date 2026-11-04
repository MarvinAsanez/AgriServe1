package com.group7.agriserve.ui.screens

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.ui.components.IdCameraScannerDialog
import com.group7.agriserve.ui.components.LocationItem
import com.group7.agriserve.data.repository.AddressRepository
import com.group7.agriserve.util.IdAndFaceVerificationHelper
import com.group7.agriserve.viewmodel.UserViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group7.agriserve.viewmodel.SignUpViewModel
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Farmer Onboarding / Credential Submission Screen
 * Captures official RSBSA Demographics, Complete Philippine Address, Contact Credentials,
 * and Back-to-Back Valid Government ID (with Camera Scanner & Blurriness Verification)
 * for review by the Department of Agriculture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialSubmissionScreen(
    userViewModel: UserViewModel,
    signUpViewModel: SignUpViewModel = viewModel(),
    onSubmissionSuccess: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val farmerStatus by userViewModel.farmerStatus
    val signUpUiState by signUpViewModel.uiState.collectAsState()

    // Prevent repeated submission if already pending, verified, or guest
    LaunchedEffect(farmerStatus) {
        if (farmerStatus == "pending") {
            Toast.makeText(context, "You already have a submitted application pending review.", Toast.LENGTH_LONG).show()
            onSubmissionSuccess()
        } else if (farmerStatus == "verified") {
            onNavigateToHome()
        } else if (farmerStatus == "guest") {
            onNavigateToHome()
        }
    }

    // RSBSA Demographic form fields
    var firstName by remember { mutableStateOf(userViewModel.firstName.value) }
    var middleName by remember { mutableStateOf(userViewModel.middleName.value) }
    var lastName by remember { mutableStateOf(userViewModel.lastName.value) }
    var extensionName by remember { mutableStateOf(userViewModel.extensionName.value) }
    var sex by remember { mutableStateOf(if (userViewModel.sex.value.isNotBlank()) userViewModel.sex.value else "Male") }
    var birthdate by remember { mutableStateOf(userViewModel.birthdate.value) }
    var rsbsaNumber by remember { mutableStateOf(userViewModel.rsbsaNumber.value) }

    // PSGC Dynamic Address Cascade across the Philippines (Instant local fallback + live API cache)
    val defaultRegionItem = remember {
        AddressRepository.fallbackRegions.find { it.name.contains("Bicol", ignoreCase = true) || it.code == "050000000" }
            ?: AddressRepository.fallbackRegions.first()
    }
    val defaultProvinceList = remember(defaultRegionItem) { AddressRepository.getFallbackProvinces(defaultRegionItem.code) }
    val defaultProvinceItem = remember(defaultProvinceList) {
        defaultProvinceList.find { it.name.contains("Camarines Sur", ignoreCase = true) } ?: defaultProvinceList.firstOrNull()
    }
    val defaultCityList = remember(defaultProvinceItem) {
        if (defaultProvinceItem != null) AddressRepository.getFallbackCities(defaultProvinceItem.code) else emptyList()
    }
    val defaultCityItem = remember(defaultCityList) {
        defaultCityList.find { it.name.contains("Ragay", ignoreCase = true) } ?: defaultCityList.firstOrNull()
    }
    val defaultBarangayList = remember(defaultCityItem) {
        if (defaultCityItem != null) AddressRepository.getFallbackBarangays(defaultCityItem.code) else emptyList()
    }

    var regions by remember { mutableStateOf<List<LocationItem>>(AddressRepository.fallbackRegions) }
    var provinces by remember { mutableStateOf<List<LocationItem>>(defaultProvinceList) }
    var cities by remember { mutableStateOf<List<LocationItem>>(defaultCityList) }
    var barangays by remember { mutableStateOf<List<LocationItem>>(defaultBarangayList) }

    var selectedRegion by remember { mutableStateOf<LocationItem?>(defaultRegionItem) }
    var selectedProvince by remember { mutableStateOf<LocationItem?>(defaultProvinceItem) }
    var selectedCity by remember { mutableStateOf<LocationItem?>(defaultCityItem) }
    var selectedBarangay by remember { mutableStateOf<LocationItem?>(null) }

    var regionName by remember { mutableStateOf(userViewModel.region.value.ifBlank { defaultRegionItem.name }) }
    var provinceName by remember { mutableStateOf(userViewModel.province.value.ifBlank { defaultProvinceItem?.name ?: "Camarines Sur" }) }
    var cityName by remember { mutableStateOf(userViewModel.city.value.ifBlank { defaultCityItem?.name ?: "Ragay" }) }
    var barangayName by remember { mutableStateOf(userViewModel.barangay.value) }
    var zone by remember { mutableStateOf(userViewModel.street.value.ifBlank { "Zone 1" }) }

    // Dropdown expansion states
    var regionExpanded by remember { mutableStateOf(false) }
    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }

    // Sync Address Fields from ViewModel when available
    LaunchedEffect(userViewModel.region.value, userViewModel.province.value, userViewModel.city.value, userViewModel.barangay.value, userViewModel.street.value) {
        if (userViewModel.region.value.isNotBlank() && regionName != userViewModel.region.value) {
            regionName = userViewModel.region.value
            val match = regions.find { it.name.equals(regionName, ignoreCase = true) }
            if (match != null) selectedRegion = match
        }
        if (userViewModel.province.value.isNotBlank() && provinceName != userViewModel.province.value) {
            provinceName = userViewModel.province.value
            val match = provinces.find { it.name.equals(provinceName, ignoreCase = true) }
            if (match != null) selectedProvince = match
        }
        if (userViewModel.city.value.isNotBlank() && cityName != userViewModel.city.value) {
            cityName = userViewModel.city.value
            val match = cities.find { it.name.equals(cityName, ignoreCase = true) }
            if (match != null) selectedCity = match
        }
        if (userViewModel.barangay.value.isNotBlank() && barangayName.isBlank()) {
            barangayName = userViewModel.barangay.value
            val match = barangays.find { it.name.equals(barangayName, ignoreCase = true) }
            if (match != null) selectedBarangay = match
        }
        if (userViewModel.street.value.isNotBlank()) {
            zone = userViewModel.street.value
        }
    }

    // Fetch Regions from PSGC API on startup with fallback
    LaunchedEffect(Unit) {
        val fetchedRegions = AddressRepository.fetchPSGC(
            "https://psgc.gitlab.io/api/regions/",
            AddressRepository.fallbackRegions
        )
        if (fetchedRegions.isNotEmpty()) {
            regions = fetchedRegions
            val defaultRegion = fetchedRegions.find {
                it.name.contains("Bicol", ignoreCase = true) || it.name.contains("Region V", ignoreCase = true)
            }
            if (defaultRegion != null && (selectedRegion == null || selectedRegion?.code == "050000000")) {
                selectedRegion = defaultRegion
                regionName = defaultRegion.name
            }
        }
    }

    // Fetch Provinces or Cities when Region changes
    LaunchedEffect(selectedRegion) {
        val reg = selectedRegion
        if (reg != null) {
            val fallbackProvs = AddressRepository.getFallbackProvinces(reg.code)
            val fetchedProvinces = AddressRepository.fetchPSGC(
                "https://psgc.gitlab.io/api/regions/${reg.code}/provinces/",
                fallbackProvs
            )
            if (fetchedProvinces.isNotEmpty()) {
                provinces = fetchedProvinces
                if (provinceName.isNotBlank()) {
                    val match = fetchedProvinces.find { it.name.equals(provinceName, ignoreCase = true) }
                    if (match != null) selectedProvince = match
                }
            } else {
                provinces = emptyList()
                val fallbackCities = AddressRepository.getFallbackCities(reg.code)
                val fetchedCities = AddressRepository.fetchPSGC(
                    "https://psgc.gitlab.io/api/regions/${reg.code}/cities-municipalities/",
                    fallbackCities
                )
                cities = fetchedCities
                if (cityName.isNotBlank()) {
                    val match = fetchedCities.find { it.name.equals(cityName, ignoreCase = true) }
                    if (match != null) selectedCity = match
                }
            }
        }
    }

    // Fetch Cities/Municipalities when Province changes
    LaunchedEffect(selectedProvince) {
        val prov = selectedProvince
        if (prov != null) {
            val fallbackCities = AddressRepository.getFallbackCities(prov.code)
            val fetchedCities = AddressRepository.fetchPSGC(
                "https://psgc.gitlab.io/api/provinces/${prov.code}/cities-municipalities/",
                fallbackCities
            )
            if (fetchedCities.isNotEmpty()) {
                cities = fetchedCities
                if (cityName.isNotBlank()) {
                    val match = fetchedCities.find { it.name.equals(cityName, ignoreCase = true) }
                    if (match != null) selectedCity = match
                }
            }
        }
    }

    // Fetch Barangays when City changes
    LaunchedEffect(selectedCity) {
        val cty = selectedCity
        if (cty != null) {
            val fallbackBrgys = AddressRepository.getFallbackBarangays(cty.code)
            val fetchedBarangays = AddressRepository.fetchPSGC(
                "https://psgc.gitlab.io/api/cities-municipalities/${cty.code}/barangays/",
                fallbackBrgys
            )
            if (fetchedBarangays.isNotEmpty()) {
                barangays = fetchedBarangays
                if (barangayName.isNotBlank()) {
                    val match = fetchedBarangays.find { it.name.equals(barangayName, ignoreCase = true) }
                    if (match != null) selectedBarangay = match
                }
            }
        }
    }

    // Zone / Purok Chip Presets
    val zonePresets = remember {
        listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5", "Zone 6", "Zone 7", "Purok 1", "Purok 2", "Purok 3", "Purok 4", "Centro")
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Contact & Auth fields
    var phoneNumber by remember { mutableStateOf(userViewModel.phoneNumber.value) }
    var phoneErrorText by remember { mutableStateOf<String?>(null) }
    var gmail by remember {
        mutableStateOf(userViewModel.gmail.value.ifBlank { currentUser?.email ?: "" })
    }
    var accountPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // BACK-TO-BACK VALID GOVERNMENT ID STATE
    var idFrontUri by remember { mutableStateOf<Uri?>(null) }
    var idBackUri by remember { mutableStateOf<Uri?>(null) }

    var isCheckingFront by remember { mutableStateOf(false) }
    var isCheckingBack by remember { mutableStateOf(false) }

    var isIdFrontValid by remember { mutableStateOf(false) }
    var isIdBackValid by remember { mutableStateOf(false) }

    var frontStatusText by remember { mutableStateOf<String?>(null) }
    var backStatusText by remember { mutableStateOf<String?>(null) }
    var frontErrorText by remember { mutableStateOf<String?>(null) }
    var backErrorText by remember { mutableStateOf<String?>(null) }

    // Camera scanner modal triggers
    var showCameraScannerFront by remember { mutableStateOf(false) }
    var showCameraScannerBack by remember { mutableStateOf(false) }

    // BIOMETRIC FACIAL IDENTIFICATION STATE (Min 60% Match with ID) - ALL FACE CORNERS

    var uploadProgress by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var generalErrorMessage by remember { mutableStateOf<String?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    // Photo picker launcher for ID Front
    val photoPickerFront = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            idFrontUri = uri
            isCheckingFront = true
            frontErrorText = null
            frontStatusText = "Analyzing Front: Checking sharpness, blur & government ID markers..."

            IdAndFaceVerificationHelper.validateIdFront(context, uri) { isValid, message, _ ->
                isCheckingFront = false
                if (isValid) {
                    isIdFrontValid = true
                    frontStatusText = message
                    frontErrorText = null
                } else {
                    isIdFrontValid = false
                    frontStatusText = null
                    frontErrorText = message
                    idFrontUri = null
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Photo picker launcher for ID Back
    val photoPickerBack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            idBackUri = uri
            isCheckingBack = true
            backErrorText = null
            backStatusText = "Analyzing Back: Checking sharpness, barcode & security text..."

            IdAndFaceVerificationHelper.validateIdBack(context, uri) { isValid, message ->
                isCheckingBack = false
                if (isValid) {
                    isIdBackValid = true
                    backStatusText = message
                    backErrorText = null
                } else {
                    isIdBackValid = false
                    backStatusText = null
                    backErrorText = message
                    idBackUri = null // Decline blurry or invalid ID
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // DatePicker Dialog Setup
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(year, month, dayOfMonth)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            birthdate = format.format(selectedCalendar.time)
        },
        calendar.get(Calendar.YEAR) - 25,
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Form Validation
    fun validateForm(): Boolean {
        if (firstName.trim().isBlank()) {
            generalErrorMessage = "Please enter your First Name."
            return false
        }
        if (lastName.trim().isBlank()) {
            generalErrorMessage = "Please enter your Last Name."
            return false
        }
        if (birthdate.trim().isBlank()) {
            generalErrorMessage = "Please select your Birthdate."
            return false
        }
        if (regionName.trim().isBlank()) {
            generalErrorMessage = "Please select your Region."
            return false
        }
        if (cityName.trim().isBlank()) {
            generalErrorMessage = "Please select your City / Municipality."
            return false
        }
        if (barangayName.trim().isBlank()) {
            generalErrorMessage = "Please select your Barangay."
            return false
        }
        if (zone.trim().isBlank()) {
            generalErrorMessage = "Please select or enter your Zone / Purok / Street."
            return false
        }

        val validPhone = com.group7.agriserve.util.ValidationUtils.normalizePhilippinePhone(phoneNumber)
        if (validPhone == null) {
            phoneErrorText = "Please enter a valid 11-digit mobile number (e.g. 09171234567)"
            generalErrorMessage = "⚠️ Section 3 (Phone Number): Please enter a valid 11-digit Philippine mobile number (e.g. 09171234567) under Section 3 below."
            coroutineScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            return false
        }
        phoneNumber = validPhone
        phoneErrorText = null

        val cleanGmail = gmail.trim().lowercase()
        if (cleanGmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanGmail).matches()) {
            generalErrorMessage = "Please provide a valid Gmail address (e.g. farmer@gmail.com)."
            return false
        }

        val isGoogleAuth = (currentUser != null && !currentUser.isAnonymous && currentUser.email.equals(cleanGmail, ignoreCase = true))
        if (!isGoogleAuth && accountPassword.isNotBlank() && accountPassword.length < 6) {
            generalErrorMessage = "⚠️ Section 3 (Account Password): Password must be at least 6 characters for Firebase Authentication."
            return false
        }

        if (!signUpUiState.isOtpVerified) {
            generalErrorMessage = "⚠️ Section 3 (Security Verification): Please complete 6-digit OTP verification (via SMS or Gmail) before submitting your credentials."
            return false
        }

        if (idFrontUri == null || !isIdFrontValid) {
            generalErrorMessage = "Please capture or attach a clear, valid FRONT picture of your Government ID."
            return false
        }

        if (idBackUri == null || !isIdBackValid) {
            generalErrorMessage = "Please capture or attach a clear, valid BACK picture of your Government ID."
            return false
        }


        generalErrorMessage = null
        return true
    }

    // Submission handler
    fun handleSubmit() {
        if (isSubmitting) return

        if (farmerStatus == "pending") {
            generalErrorMessage = "You already have an application submitted and pending review. Repeated submissions are not allowed."
            Toast.makeText(context, generalErrorMessage, Toast.LENGTH_LONG).show()
            return
        }
        if (farmerStatus == "verified") {
            generalErrorMessage = "Your account is already verified! Repeated submissions are not permitted."
            Toast.makeText(context, generalErrorMessage, Toast.LENGTH_LONG).show()
            return
        }

        if (!validateForm()) return

        isSubmitting = true
        uploadProgress = 0

        userViewModel.submitFarmerCredentials(
            context = context,
            firstNameInput = firstName,
            middleNameInput = middleName,
            lastNameInput = lastName,
            extensionNameInput = extensionName,
            sexInput = sex,
            birthdateInput = birthdate,
            phoneInput = phoneNumber,
            gmailInput = gmail,
            rsbsaNumberInput = rsbsaNumber,
            zoneInput = zone,
            barangayInput = barangayName,
            municipalityInput = cityName,
            provinceInput = provinceName,
            regionInput = regionName,
            idFrontUri = idFrontUri!!,
            idBackUri = idBackUri!!,
            liveFaceBitmap = null,
            leftFaceBitmap = null,
            rightFaceBitmap = null,
            facialCornersData = null,
            centerScore = 100,
            leftScore = 100,
            rightScore = 100,
            faceMatchScoreInput = 100,
            passwordInput = accountPassword,
            onProgress = { progress -> uploadProgress = progress },
            onComplete = { success, error ->
                isSubmitting = false
                if (success) {
                    Toast.makeText(context, "Credentials, ID, and Facial Identity submitted successfully for DA review!", Toast.LENGTH_LONG).show()
                    onSubmissionSuccess()
                } else {
                    generalErrorMessage = error ?: "Failed to submit credentials. Please try again."
                }
            }
        )
    }

    // Camera Scanner Dialog for ID Front
    if (showCameraScannerFront) {
        IdCameraScannerDialog(
            cardSideTitle = "Front of Government ID",
            onCaptured = { uri ->
                showCameraScannerFront = false
                idFrontUri = uri
                isCheckingFront = true
                frontErrorText = null
                frontStatusText = "Analyzing Front: Checking sharpness, blur & government ID markers..."

                IdAndFaceVerificationHelper.validateIdFront(context, uri) { isValid, message, _ ->
                    isCheckingFront = false
                    if (isValid) {
                        isIdFrontValid = true
                        frontStatusText = message
                        frontErrorText = null
                    } else {
                        isIdFrontValid = false
                        frontStatusText = null
                        frontErrorText = message
                        idFrontUri = null
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showCameraScannerFront = false }
        )
    }

    // Camera Scanner Dialog for ID Back
    if (showCameraScannerBack) {
        IdCameraScannerDialog(
            cardSideTitle = "Back of Government ID",
            onCaptured = { uri ->
                showCameraScannerBack = false
                idBackUri = uri
                isCheckingBack = true
                backErrorText = null
                backStatusText = "Analyzing Back: Checking sharpness, barcode & security text..."

                IdAndFaceVerificationHelper.validateIdBack(context, uri) { isValid, message ->
                    isCheckingBack = false
                    if (isValid) {
                        isIdBackValid = true
                        backStatusText = message
                        backErrorText = null
                    } else {
                        isIdBackValid = false
                        backStatusText = null
                        backErrorText = message
                        idBackUri = null // Decline blurry/invalid
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showCameraScannerBack = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Farmer Onboarding",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "RSBSA Credentials & Back-to-Back ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Guidance Banner
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
                        text = "Submitted credentials and Back-to-Back Valid IDs will be verified by DA staff. Approved accounts receive Verified Farmer status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error display banner
            if (generalErrorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = generalErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // SECTION 1: RSBSA DEMOGRAPHICS
            Text(
                text = "1. RSBSA Demographics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; generalErrorMessage = null },
                label = { Text("First Name *") },
                placeholder = { Text("Juan") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = middleName,
                onValueChange = { middleName = it },
                label = { Text("Middle Name (Optional)") },
                placeholder = { Text("Santos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it; generalErrorMessage = null },
                    label = { Text("Last Name *") },
                    placeholder = { Text("Dela Cruz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = extensionName,
                    onValueChange = { extensionName = it },
                    label = { Text("Ext (Jr./Sr.)") },
                    placeholder = { Text("Jr.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sex Selection (Radio Buttons)
            Text(
                text = "Sex *",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sex = "Male" }
                ) {
                    RadioButton(selected = (sex == "Male"), onClick = { sex = "Male" })
                    Text("Male", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.width(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sex = "Female" }
                ) {
                    RadioButton(selected = (sex == "Female"), onClick = { sex = "Female" })
                    Text("Female", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Birthdate Picker Field
            OutlinedTextField(
                value = birthdate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Birthdate *") },
                placeholder = { Text("YYYY-MM-DD") },
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() },
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // RSBSA Reference Number
            OutlinedTextField(
                value = rsbsaNumber,
                onValueChange = { rsbsaNumber = it; generalErrorMessage = null },
                label = { Text("RSBSA Reference Number") },
                placeholder = { Text("e.g. 05-05-01-001-000001 or 12-34-56-789") },
                leadingIcon = { Icon(Icons.Default.AppRegistration, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Standard Format: RR-PP-MM-BBB-NNNNNN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://finder-rsbsa.da.gov.ph/ph"))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Find on DA RSBSA Finder", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: PHILIPPINES DYNAMIC ADDRESS SELECTION
            Text(
                text = "2. Farm & Residential Address (All Philippines)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Select your Region, Province, City/Municipality, and Barangay across the Philippines.",
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
                    value = selectedRegion?.name ?: regionName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Region *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false },
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    regions.forEach { regItem ->
                        DropdownMenuItem(
                            text = { Text(regItem.name) },
                            onClick = {
                                selectedRegion = regItem
                                regionName = regItem.name
                                selectedProvince = null
                                provinceName = ""
                                selectedCity = null
                                cityName = ""
                                selectedBarangay = null
                                barangayName = ""
                                regionExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Province Dropdown (Visible for all provinces, hidden only for NCR)
            if (selectedRegion?.code != "130000000") {
                ExposedDropdownMenuBox(
                    expanded = provinceExpanded,
                    onExpandedChange = { provinceExpanded = !provinceExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvince?.name ?: provinceName.ifBlank { "Select Province" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Province *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = (provinces.isNotEmpty() || provinceName.isNotBlank())
                    )
                    ExposedDropdownMenu(
                        expanded = provinceExpanded,
                        onDismissRequest = { provinceExpanded = false },
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        if (provinces.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Loading provinces...") },
                                onClick = { provinceExpanded = false }
                            )
                        } else {
                            provinces.forEach { provItem ->
                                DropdownMenuItem(
                                    text = { Text(provItem.name) },
                                    onClick = {
                                        selectedProvince = provItem
                                        provinceName = provItem.name
                                        selectedCity = null
                                        cityName = ""
                                        selectedBarangay = null
                                        barangayName = ""
                                        provinceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // City / Municipality Dropdown
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCity?.name ?: cityName.ifBlank { "Select City / Municipality" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("City / Municipality *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = (cities.isNotEmpty() || cityName.isNotBlank())
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false },
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    if (cities.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Loading cities / municipalities...") },
                            onClick = { cityExpanded = false }
                        )
                    } else {
                        cities.forEach { cityItem ->
                            DropdownMenuItem(
                                text = { Text(cityItem.name) },
                                onClick = {
                                    selectedCity = cityItem
                                    cityName = cityItem.name
                                    selectedBarangay = null
                                    barangayName = ""
                                    cityExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barangay Dropdown (Select from list or type custom)
            ExposedDropdownMenuBox(
                expanded = barangayExpanded,
                onExpandedChange = { barangayExpanded = !barangayExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = barangayName,
                    onValueChange = { input ->
                        barangayName = input
                        selectedBarangay = LocationItem(code = "", name = input)
                        generalErrorMessage = null
                    },
                    readOnly = false,
                    label = { Text("Barangay * (Tap dropdown or type)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = (barangays.isNotEmpty() || cityName.isNotBlank()),
                    placeholder = { Text("Select or type Barangay") }
                )
                ExposedDropdownMenu(
                    expanded = barangayExpanded,
                    onDismissRequest = { barangayExpanded = false },
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    if (barangays.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No barangays found. Type manually above.") },
                            onClick = { barangayExpanded = false }
                        )
                    } else {
                        barangays.forEach { brgyItem ->
                            DropdownMenuItem(
                                text = { Text(brgyItem.name) },
                                onClick = {
                                    selectedBarangay = brgyItem
                                    barangayName = brgyItem.name
                                    generalErrorMessage = null
                                    barangayExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Zone / Purok (Click-ready Chips + Input)
            Text(
                text = "Zone / Purok / Street * (Tap preset or type below):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zonePresets.forEach { preset ->
                    val isSelected = (zone.equals(preset, ignoreCase = true))
                    FilterChip(
                        selected = isSelected,
                        onClick = { zone = preset; generalErrorMessage = null },
                        label = { Text(preset, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it; generalErrorMessage = null },
                label = { Text("Zone / Purok / Street *") },
                placeholder = { Text("e.g. Zone 1, Purok 3, Centro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: CONTACT & AUTH CREDENTIALS
            Text(
                text = "3. Contact & Identity Credentials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { input ->
                    var cleaned = input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                    if (cleaned.startsWith("0909")) {
                        cleaned = "09" + cleaned.substring(4)
                    }
                    phoneNumber = cleaned
                    phoneErrorText = null
                    generalErrorMessage = null
                },
                label = { Text("Phone Number (Philippine Mobile) *") },
                placeholder = { Text("09171234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                isError = (phoneErrorText != null || generalErrorMessage?.contains("Phone Number", ignoreCase = true) == true),
                supportingText = {
                    if (phoneErrorText != null) {
                        Text(
                            text = phoneErrorText ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Accepts: 09XXXXXXXXX, 9XXXXXXXXX, or +63 9XXXXXXXXX (11 digits)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = gmail,
                onValueChange = { gmail = it; generalErrorMessage = null },
                label = { Text("Gmail Address *") },
                placeholder = { Text("juan.delacruz@gmail.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            // Firebase Authentication Integration Check & Password Field
            val isGoogleAuth = (currentUser != null && !currentUser.isAnonymous && currentUser.email.equals(gmail.trim(), ignoreCase = true))
            if (isGoogleAuth) {
                Spacer(modifier = Modifier.height(8.dp))
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
                            text = "Linked to Google Account: ${currentUser?.email} (Stored in Firebase Authentication ✓)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = accountPassword,
                    onValueChange = { accountPassword = it; generalErrorMessage = null },
                    label = { Text("Account Password (Firebase Authentication) *") },
                    placeholder = { Text("Set a password (min. 6 characters)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(
                            text = "Every farmer account is securely stored in Firebase Authentication for system login.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // DUAL-CHANNEL OTP VERIFICATION (GMAIL & SMS)
            OnboardingOtpVerificationCard(
                signUpViewModel = signUpViewModel,
                signUpUiState = signUpUiState,
                phoneNumber = phoneNumber,
                gmail = gmail,
                onGmailResolved = { gmail = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 4: BACK-TO-BACK GOVERNMENT VALID ID ATTACHMENT
            Text(
                text = "4. Government Valid ID Attachment (Back-to-Back)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Both Front and Back photos are required. Acceptable: PhilSys National ID, Driver's License, UMID, Postal ID, PRC, or RSBSA stub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tip: The camera automatically opens in Landscape mode so your ID is larger, sharper, and easier to auto-capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================== ID FRONT CARD ======================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isIdFrontValid) Color(0xFF2E7D32) else if (frontErrorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIdFrontValid) Icons.Default.CheckCircle else Icons.Default.Badge,
                                contentDescription = null,
                                tint = if (isIdFrontValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ID Front (Portrait & Details) *",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isIdFrontValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isIdFrontValid) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Verified ✓",
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Front Image Preview or Box
                    if (idFrontUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = idFrontUri,
                                contentDescription = "ID Front Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    if (isCheckingFront) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = frontStatusText ?: "Inspecting ID clarity & authenticity...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (frontStatusText != null && isIdFrontValid) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = frontStatusText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    } else if (frontErrorText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = frontErrorText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Front Action Buttons: Camera (with Auto-Capture) or Gallery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCameraScannerFront = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera (Landscape)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { photoPickerFront.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Gallery", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================== ID BACK CARD ======================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (isIdBackValid) Color(0xFF2E7D32) else if (backErrorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIdBackValid) Icons.Default.CheckCircle else Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = if (isIdBackValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ID Back (Barcode & Security Text) *",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isIdBackValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isIdBackValid) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Verified ✓",
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Back Image Preview or Box
                    if (idBackUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = idBackUri,
                                contentDescription = "ID Back Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    if (isCheckingBack) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = backStatusText ?: "Inspecting barcode, serial & sharpness...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (backStatusText != null && isIdBackValid) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = backStatusText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    } else if (backErrorText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = backErrorText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Back Action Buttons: Camera (with Auto-Capture) or Gallery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCameraScannerBack = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera (Landscape)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { photoPickerBack.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Gallery", fontSize = 12.sp)
                        }
                    }
                }
            }
            // Upload Progress Bar
            if (isSubmitting) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Uploading Government ID credentials to secure cloud...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "$uploadProgress%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DATA PRIVACY NOTICE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Data Privacy Protection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Privacy & Information Protection Notice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your personal information, RSBSA details, and government-issued ID credentials will be safe and protected in accordance with the AgriServe Privacy Policy and Republic Act 10173 (Data Privacy Act of 2012). All submitted data is encrypted and strictly used by the Municipal Agriculture Office (MAO) exclusively for identity verification and agricultural assistance distribution.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showPrivacyPolicyDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Read Privacy Policy Details →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SUBMIT BUTTON
            Button(
                onClick = { handleSubmit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isSubmitting && isIdFrontValid && isIdBackValid && signUpUiState.isOtpVerified
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Submit Credentials & Government ID for Review",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Privacy Policy Dialog
        if (showPrivacyPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyPolicyDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "AgriServe Privacy Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Protection under the Data Privacy Act of 2012 (RA 10173):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Information Safety: Your personal identity details, RSBSA numbers, contact numbers, and ID attachments are stored with AES-256 cloud encryption.\n\n" +
                                   "2. Official Purpose: Collected information is used solely by the Municipal Agriculture Office (MAO) of Ragay, Camarines Sur for genuine farmer verification, disaster relief subsidies, crop insurance, and official seed/fertilizer distribution.\n\n" +
                                   "3. No Unauthorized Sharing: AgriServe will never sell, rent, or disclose your sensitive personal data to commercial third parties or external marketers.\n\n" +
                                   "4. Farmer Rights: You retain the legal right to request inspection, modification, or rectification of your submitted records at the Municipal Agriculture Office.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showPrivacyPolicyDialog = false }
                    ) {
                        Text("I Understand & Agree", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/**
 * Helper to fetch Philippine Standard Geographic Code (PSGC) API data
 */
private suspend fun fetchPSGCData(urlString: String): List<LocationItem> = AddressRepository.fetchPSGC(urlString)

/**
 * Helper to traverse ContextWrapper and retrieve the host Activity
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 100% Firebase-Powered Dual-Channel Authentication Card for Farmer Onboarding.
 * - SMS: Firebase PhoneAuthProvider with 6-digit SMS OTP
 * - Gmail: Firebase Google One-Tap Sign-In (GoogleAuthProvider) & Cloud Firestore 6-digit Email OTP
 */
@Composable
private fun OnboardingOtpVerificationCard(
    signUpViewModel: SignUpViewModel,
    signUpUiState: com.group7.agriserve.viewmodel.SignUpUiState,
    phoneNumber: String,
    gmail: String,
    onGmailResolved: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var selectedChannel by remember { mutableStateOf("sms") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (signUpUiState.isOtpVerified) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (signUpUiState.isOtpVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (signUpUiState.isOtpVerified) Icons.Default.CheckCircle else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (signUpUiState.isOtpVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (signUpUiState.isOtpVerified) "Firebase Identity Verified ✓" else "Firebase Security Authentication *",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (signUpUiState.isOtpVerified) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (signUpUiState.isOtpVerified)
                            "Authenticated via Firebase (${if (signUpUiState.verifiedMethod == "firebase_phone") "Phone SMS" else "Google / Gmail"})."
                        else "Authenticate your contact info directly via Firebase Authentication:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (signUpUiState.isOtpVerified) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { signUpViewModel.resetOtpState() }) {
                        Text("Re-verify / Change", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))

                // Channel options (SMS vs Gmail)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SMS Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedChannel = "sms" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedChannel == "sms") 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) 
                        else 
                            MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selectedChannel == "sms") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedChannel == "sms",
                                onClick = { selectedChannel = "sms" },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "SMS Phone",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = phoneNumber.ifBlank { "Mobile #" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Gmail Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedChannel = "email" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedChannel == "email") 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) 
                        else 
                            MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selectedChannel == "email") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedChannel == "email",
                                onClick = { selectedChannel = "email" },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Gmail / Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = gmail.ifBlank { "Google Account" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== CHANNEL 1: FIREBASE PHONE SMS ====================
                if (selectedChannel == "sms") {
                    if (!signUpUiState.isOtpSent) {
                        Button(
                            onClick = {
                                if (activity != null) {
                                    signUpViewModel.sendFirebasePhoneOtp(activity, phoneNumber)
                                } else {
                                    Toast.makeText(context, "Activity context unavailable for Phone Auth", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !signUpUiState.isSendingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (signUpUiState.isSendingOtp) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending SMS via Firebase Phone Auth...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send 6-Digit SMS Code (Firebase)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Phone OTP Input
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Enter 6-digit SMS code sent to $phoneNumber",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = signUpUiState.otpCode,
                                onValueChange = { signUpViewModel.updateOtpCode(it) },
                                placeholder = {
                                    Text(
                                        text = "• • • • • •",
                                        style = TextStyle(
                                            textAlign = TextAlign.Center,
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            letterSpacing = 8.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                textStyle = TextStyle(
                                    textAlign = TextAlign.Center,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        signUpViewModel.verifyFirebasePhoneOtp(signUpUiState.otpCode)
                                    },
                                    enabled = !signUpUiState.isVerifyingOtp && signUpUiState.otpCode.length == 6,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    if (signUpUiState.isVerifyingOtp) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verifying...", fontSize = 14.sp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verify SMS Code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (activity != null) {
                                            signUpViewModel.sendFirebasePhoneOtp(activity, phoneNumber)
                                        }
                                    },
                                    enabled = signUpUiState.cooldownSeconds == 0 && !signUpUiState.isSendingOtp,
                                    modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (signUpUiState.cooldownSeconds > 0) {
                                        Text(
                                            "${signUpUiState.cooldownSeconds}s",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text("Resend", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ==================== CHANNEL 2: FIREBASE GMAIL / GOOGLE ====================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Action A: Google One-Tap Sign In with Firebase
                        Button(
                            onClick = {
                                if (activity != null) {
                                    signUpViewModel.authenticateWithGoogle(activity, onGmailResolved)
                                } else {
                                    Toast.makeText(context, "Activity context unavailable for Google Auth", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !signUpUiState.isSendingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (signUpUiState.isSendingOtp) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to Google Firebase...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verify with Google Account (1-Tap)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "— or use 6-digit email code —",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!signUpUiState.isOtpSent) {
                            OutlinedButton(
                                onClick = {
                                    signUpViewModel.sendFirebaseEmailOtp(gmail)
                                },
                                enabled = !signUpUiState.isSendingOtp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send 6-Digit Code to Gmail", fontSize = 13.sp)
                            }
                        } else {
                            // Email OTP Code Input
                            OutlinedTextField(
                                value = signUpUiState.otpCode,
                                onValueChange = { signUpViewModel.updateOtpCode(it) },
                                placeholder = {
                                    Text(
                                        text = "• • • • • •",
                                        style = TextStyle(
                                            textAlign = TextAlign.Center,
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            letterSpacing = 8.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                textStyle = TextStyle(
                                    textAlign = TextAlign.Center,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        signUpViewModel.verifyFirebaseEmailOtp(gmail, signUpUiState.otpCode)
                                    },
                                    enabled = !signUpUiState.isVerifyingOtp && signUpUiState.otpCode.length == 6,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    if (signUpUiState.isVerifyingOtp) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verifying...", fontSize = 14.sp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verify Code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        signUpViewModel.sendFirebaseEmailOtp(gmail)
                                    },
                                    enabled = signUpUiState.cooldownSeconds == 0 && !signUpUiState.isSendingOtp,
                                    modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (signUpUiState.cooldownSeconds > 0) {
                                        Text("${signUpUiState.cooldownSeconds}s", fontSize = 13.sp)
                                    } else {
                                        Text("Resend", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Banner
                signUpUiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Success Banner
                signUpUiState.successMessage?.let { success ->
                    if (signUpUiState.errorMessage == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = success,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
    }
}
}
