package com.group7.agriserve.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

/**
 * Resubmit Credentials Screen
 * Pre-populates previously submitted data, prominently displays the DA Staff
 * review feedback note, and allows farmers to correct demographic details or
 * upload clearer Back-to-Back Valid IDs (with Camera Scanner & Blurriness Verification).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResubmitCredentialsScreen(
    userViewModel: UserViewModel,
    onResubmissionSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val adminFeedback by userViewModel.adminFeedback
    val currentValidIdUrl by userViewModel.validIdUrl
    val currentValidIdBackUrl by userViewModel.validIdBackUrl

    // Pre-populated demographic fields
    var firstName by remember { mutableStateOf(userViewModel.firstName.value) }
    var middleName by remember { mutableStateOf(userViewModel.middleName.value) }
    var lastName by remember { mutableStateOf(userViewModel.lastName.value) }
    var extensionName by remember { mutableStateOf(userViewModel.extensionName.value) }
    var sex by remember { mutableStateOf(if (userViewModel.sex.value.isNotBlank()) userViewModel.sex.value else "Male") }
    var birthdate by remember { mutableStateOf(userViewModel.birthdate.value) }
    var rsbsaNumber by remember { mutableStateOf(userViewModel.rsbsaNumber.value) }

    // PSGC Dynamic Address Cascade across the Philippines
    var regions by remember { mutableStateOf<List<LocationItem>>(AddressRepository.fallbackRegions) }
    var provinces by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackProvinces("050000000")) }
    var cities by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackCities("051700000")) }
    var barangays by remember { mutableStateOf<List<LocationItem>>(AddressRepository.getFallbackBarangays("051730000")) }

    var selectedRegion by remember { mutableStateOf<LocationItem?>(null) }
    var selectedProvince by remember { mutableStateOf<LocationItem?>(null) }
    var selectedCity by remember { mutableStateOf<LocationItem?>(null) }
    var selectedBarangay by remember { mutableStateOf<LocationItem?>(null) }

    var regionName by remember { mutableStateOf(userViewModel.region.value) }
    var provinceName by remember { mutableStateOf(userViewModel.province.value) }
    var cityName by remember { mutableStateOf(userViewModel.city.value) }
    var barangayName by remember { mutableStateOf(userViewModel.barangay.value) }
    var zone by remember { mutableStateOf(userViewModel.street.value.ifBlank { "Zone 1" }) }

    // Dropdown expansion states
    var regionExpanded by remember { mutableStateOf(false) }
    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var barangayExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val fetchedRegions = fetchPSGCData("https://psgc.gitlab.io/api/regions/")
        if (fetchedRegions.isNotEmpty()) {
            regions = fetchedRegions
            val match = fetchedRegions.find { it.name.equals(regionName, ignoreCase = true) }
            if (match != null) {
                selectedRegion = match
                regionName = match.name
            }
        }
    }

    LaunchedEffect(selectedRegion) {
        val reg = selectedRegion
        if (reg != null) {
            val fetchedProvinces = fetchPSGCData("https://psgc.gitlab.io/api/regions/${reg.code}/provinces/")
            if (fetchedProvinces.isNotEmpty()) {
                provinces = fetchedProvinces
                val match = fetchedProvinces.find { it.name.equals(provinceName, ignoreCase = true) }
                if (match != null) {
                    selectedProvince = match
                    provinceName = match.name
                }
            } else {
                provinces = emptyList()
                val fetchedCities = fetchPSGCData("https://psgc.gitlab.io/api/regions/${reg.code}/cities-municipalities/")
                cities = fetchedCities
            }
        }
    }

    LaunchedEffect(selectedProvince) {
        val prov = selectedProvince
        if (prov != null) {
            val fetchedCities = fetchPSGCData("https://psgc.gitlab.io/api/provinces/${prov.code}/cities-municipalities/")
            if (fetchedCities.isNotEmpty()) {
                cities = fetchedCities
                val match = fetchedCities.find { it.name.equals(cityName, ignoreCase = true) }
                if (match != null) {
                    selectedCity = match
                    cityName = match.name
                }
            }
        }
    }

    LaunchedEffect(selectedCity) {
        val cty = selectedCity
        if (cty != null) {
            val fetchedBarangays = fetchPSGCData("https://psgc.gitlab.io/api/cities-municipalities/${cty.code}/barangays/")
            if (fetchedBarangays.isNotEmpty()) {
                barangays = fetchedBarangays
            }
        }
    }

    val zonePresets = remember {
        listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5", "Zone 6", "Zone 7", "Purok 1", "Purok 2", "Purok 3", "Purok 4", "Centro")
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Contact Credentials
    var phoneNumber by remember { mutableStateOf(userViewModel.phoneNumber.value) }
    var phoneErrorText by remember { mutableStateOf<String?>(null) }
    var gmail by remember { mutableStateOf(userViewModel.gmail.value) }

    // BACK-TO-BACK VALID ID ATTACHMENT
    var newIdFrontUri by remember { mutableStateOf<Uri?>(null) }
    var newIdBackUri by remember { mutableStateOf<Uri?>(null) }

    var isCheckingFront by remember { mutableStateOf(false) }
    var isCheckingBack by remember { mutableStateOf(false) }

    var isFrontValid by remember { mutableStateOf(currentValidIdUrl != null) }
    var isBackValid by remember { mutableStateOf(currentValidIdBackUrl != null) }

    var frontStatusText by remember { mutableStateOf<String?>(null) }
    var backStatusText by remember { mutableStateOf<String?>(null) }
    var frontErrorText by remember { mutableStateOf<String?>(null) }
    var backErrorText by remember { mutableStateOf<String?>(null) }

    var showCameraScannerFront by remember { mutableStateOf(false) }
    var showCameraScannerBack by remember { mutableStateOf(false) }

    var uploadProgress by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerFront = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            newIdFrontUri = uri
            isCheckingFront = true
            frontErrorText = null
            frontStatusText = "Analyzing Front: Checking sharpness, blur & government ID markers..."

            IdAndFaceVerificationHelper.validateIdFront(context, uri) { isValid, message, _ ->
                isCheckingFront = false
                if (isValid) {
                    isFrontValid = true
                    frontStatusText = message
                    frontErrorText = null
                } else {
                    isFrontValid = false
                    frontStatusText = null
                    frontErrorText = message
                    newIdFrontUri = null // Decline blurry/invalid
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val photoPickerBack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            newIdBackUri = uri
            isCheckingBack = true
            backErrorText = null
            backStatusText = "Analyzing Back: Checking sharpness, barcode & security text..."

            IdAndFaceVerificationHelper.validateIdBack(context, uri) { isValid, message ->
                isCheckingBack = false
                if (isValid) {
                    isBackValid = true
                    backStatusText = message
                    backErrorText = null
                } else {
                    isBackValid = false
                    backStatusText = null
                    backErrorText = message
                    newIdBackUri = null // Decline blurry/invalid
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // DatePicker setup
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

    fun handleResubmit() {
        if (isSubmitting) return

        if (firstName.trim().isBlank()) {
            errorMessage = "Please enter your First Name."
            return
        }
        if (lastName.trim().isBlank()) {
            errorMessage = "Please enter your Last Name."
            return
        }
        if (regionName.trim().isBlank() || cityName.trim().isBlank() || barangayName.trim().isBlank()) {
            errorMessage = "Please complete your complete Philippine address."
            return
        }

        val validPhone = com.group7.agriserve.util.ValidationUtils.normalizePhilippinePhone(phoneNumber)
        if (validPhone == null) {
            phoneErrorText = "Please enter a valid 11-digit mobile number (e.g. 09171234567)"
            errorMessage = "⚠️ Contact Information: Please enter a valid 11-digit Philippine mobile number (e.g. 09171234567)."
            coroutineScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            return
        }
        phoneNumber = validPhone
        phoneErrorText = null

        isSubmitting = true
        uploadProgress = 0

        userViewModel.resubmitFarmerCredentials(
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
            newIdFrontUri = newIdFrontUri,
            newIdBackUri = newIdBackUri,
            onProgress = { progress -> uploadProgress = progress },
            onComplete = { success, error ->
                isSubmitting = false
                if (success) {
                    Toast.makeText(context, "Updated credentials resubmitted for DA review!", Toast.LENGTH_LONG).show()
                    onResubmissionSuccess()
                } else {
                    errorMessage = error ?: "Resubmission failed. Please try again."
                }
            }
        )
    }

    // Camera Scanner Dialogs
    if (showCameraScannerFront) {
        IdCameraScannerDialog(
            cardSideTitle = "Front of Government ID",
            onCaptured = { uri ->
                showCameraScannerFront = false
                newIdFrontUri = uri
                isCheckingFront = true
                frontErrorText = null
                frontStatusText = "Analyzing Front: Checking sharpness, blur & government ID markers..."

                IdAndFaceVerificationHelper.validateIdFront(context, uri) { isValid, message, _ ->
                    isCheckingFront = false
                    if (isValid) {
                        isFrontValid = true
                        frontStatusText = message
                        frontErrorText = null
                    } else {
                        isFrontValid = false
                        frontStatusText = null
                        frontErrorText = message
                        newIdFrontUri = null // Decline blurry/invalid
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showCameraScannerFront = false }
        )
    }

    if (showCameraScannerBack) {
        IdCameraScannerDialog(
            cardSideTitle = "Back of Government ID",
            onCaptured = { uri ->
                showCameraScannerBack = false
                newIdBackUri = uri
                isCheckingBack = true
                backErrorText = null
                backStatusText = "Analyzing Back: Checking sharpness, barcode & security text..."

                IdAndFaceVerificationHelper.validateIdBack(context, uri) { isValid, message ->
                    isCheckingBack = false
                    if (isValid) {
                        isBackValid = true
                        backStatusText = message
                        backErrorText = null
                    } else {
                        isBackValid = false
                        backStatusText = null
                        backErrorText = message
                        newIdBackUri = null // Decline blurry/invalid
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
                        Text("Update Credentials", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Correct Flagged Information", style = MaterialTheme.typography.bodySmall)
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
            Spacer(modifier = Modifier.height(14.dp))

            // PROMINENT DA STAFF FEEDBACK ALERT CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.5.dp, Color(0xFFFF9800))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Feedback, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DA Staff Review Feedback",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayFeedback = adminFeedback?.ifBlank { null }
                        ?: "Your application requires clearer Back-to-Back Valid ID documents or verified address details. Please review below and resubmit."

                    Text(
                        text = displayFeedback,
                        color = Color(0xFFBF360C),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // SECTION 1: DEMOGRAPHICS
            Text(
                text = "Personal Demographics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = middleName,
                onValueChange = { middleName = it },
                label = { Text("Middle Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name *") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = extensionName,
                    onValueChange = { extensionName = it },
                    label = { Text("Ext") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = birthdate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Birthdate *") },
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() },
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = rsbsaNumber,
                onValueChange = { rsbsaNumber = it },
                label = { Text("RSBSA Reference Number") },
                leadingIcon = { Icon(Icons.Default.AppRegistration, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: PHILIPPINES DYNAMIC ADDRESS SELECTION
            Text(
                text = "Farm & Residential Address (All Philippines)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Region
            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { regionExpanded = !regionExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = regionName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Region") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Province
            if (provinces.isNotEmpty() || selectedProvince != null) {
                ExposedDropdownMenuBox(
                    expanded = provinceExpanded,
                    onExpandedChange = { provinceExpanded = !provinceExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (provinceName.isBlank()) "Select Province" else provinceName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Province") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = provinceExpanded,
                        onDismissRequest = { provinceExpanded = false }
                    ) {
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            // City / Municipality
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = { cityExpanded = !cityExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (cityName.isBlank()) "Select City / Municipality" else cityName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("City / Municipality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded,
                    onDismissRequest = { cityExpanded = false }
                ) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Barangay
            ExposedDropdownMenuBox(
                expanded = barangayExpanded,
                onExpandedChange = { barangayExpanded = !barangayExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (barangayName.isBlank()) "Select Barangay" else barangayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Barangay") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = barangayExpanded,
                    onDismissRequest = { barangayExpanded = false }
                ) {
                    barangays.forEach { brgyItem ->
                        DropdownMenuItem(
                            text = { Text(brgyItem.name) },
                            onClick = {
                                selectedBarangay = brgyItem
                                barangayName = brgyItem.name
                                barangayExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Zone / Purok
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zonePresets.forEach { preset ->
                    val isSelected = (zone.equals(preset, ignoreCase = true))
                    FilterChip(
                        selected = isSelected,
                        onClick = { zone = preset },
                        label = { Text(preset, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it },
                label = { Text("Zone / Purok / Street") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CONTACT DETAILS
            Text(
                text = "Contact & Auth Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { input ->
                    var cleaned = input.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                    if (cleaned.startsWith("0909")) {
                        cleaned = "09" + cleaned.substring(4)
                    }
                    phoneNumber = cleaned
                    phoneErrorText = null
                    errorMessage = null
                },
                label = { Text("Phone Number (Philippine Mobile) *") },
                placeholder = { Text("09171234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                isError = (phoneErrorText != null || errorMessage?.contains("Phone Number", ignoreCase = true) == true || errorMessage?.contains("mobile number", ignoreCase = true) == true),
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = gmail,
                onValueChange = { gmail = it },
                label = { Text("Gmail Address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ====================== BACK-TO-BACK VALID ID ATTACHMENT ======================
            Text(
                text = "Valid Government ID Attachment (Back-to-Back)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Capture or attach sharp, glare-free photos of both the Front and Back of your official Government ID.",
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

            Spacer(modifier = Modifier.height(12.dp))

            // ID FRONT CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, if (isFrontValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ID Front (Portrait & Details)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val frontDisplay = newIdFrontUri ?: currentValidIdUrl
                    if (frontDisplay != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                            AsyncImage(model = frontDisplay, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }

                    if (isCheckingFront) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(frontStatusText ?: "Inspecting sharpness...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else if (frontErrorText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(frontErrorText ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCameraScannerFront = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera (Landscape)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { photoPickerFront.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ID BACK CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, if (isBackValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ID Back (Barcode & Security Text)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val backDisplay = newIdBackUri ?: currentValidIdBackUrl
                    if (backDisplay != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                            AsyncImage(model = backDisplay, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }

                    if (isCheckingBack) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(backStatusText ?: "Inspecting barcode & sharpness...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else if (backErrorText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(backErrorText ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCameraScannerBack = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera (Landscape)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { photoPickerBack.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isSubmitting) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { handleResubmit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resubmit Credentials for DA Approval", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

private suspend fun fetchPSGCData(urlString: String): List<LocationItem> {
    return AddressRepository.fetchPSGC(urlString)
}
