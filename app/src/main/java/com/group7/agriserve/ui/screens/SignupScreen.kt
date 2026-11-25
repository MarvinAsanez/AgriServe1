package com.group7.agriserve.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.recaptcha.RecaptchaAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.group7.agriserve.R
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.RecaptchaManager
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/**
 * AgriServe Streamlined Farmer Sign-Up Screen
 * 
 * Captures core authentication and agricultural profile data:
 * 1. Farmer Email Account
 * 2. Philippine Mobile Phone Number
 * 3. Type of Farmer
 * 4. Crop Type (Rice, Corn, Coconut, High Value Crops, Abacca, etc.)
 * 5. Password & Confirm Password
 * 
 * Subsequent credentials (Full Demographics, RSBSA stub, and Valid Government IDs)
 * are completed within Profile Settings and the Account Verification screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignupScreen(
    userViewModel: UserViewModel? = null,
    onSignupComplete: () -> Unit = {},
    onBackToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Farmer Type selection
    val farmerTypes = listOf(
        "Smallholder Farmer",
        "Tenant Farmer",
        "Farmworker / Laborer",
        "Owner-Cultivator",
        "Fisherfolk / Farmer",
        "Agri-Youth",
        "Others"
    )
    var selectedFarmerType by remember { mutableStateOf(farmerTypes[0]) }
    var farmerTypeExpanded by remember { mutableStateOf(false) }

    // Crop Type selection (multi-select supported, e.g. Rice, Rice and Corn)
    val availableCrops = listOf(
        "Rice",
        "Corn",
        "Coconut",
        "High Value Crops",
        "Abacca",
        "Others"
    )
    var selectedCrops by remember { mutableStateOf(setOf("Rice")) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("63") && digits.length == 12 -> "0" + digits.substring(2)
            digits.startsWith("9") && digits.length == 10 -> "0$digits"
            else -> digits
        }
    }

    fun handleSignUp() {
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = normalizePhone(phone.trim())
        val cleanPassword = password.trim()
        val cleanConfirm = confirmPassword.trim()

        if (cleanEmail.isBlank()) {
            errorMessage = "Please enter your email address."
            return
        }
        if (cleanEmail.equals("agriserve@admin.da.gov.ph", ignoreCase = true) || cleanEmail.endsWith("@admin.da.gov.ph")) {
            errorMessage = "Admin accounts cannot be registered in the Farmer mobile app."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            errorMessage = "Please enter a valid email address (e.g. juan@gmail.com)."
            return
        }
        if (cleanPhone.isBlank()) {
            errorMessage = "Please enter your mobile phone number."
            return
        }
        if (cleanPhone.length < 10 || cleanPhone.length > 12) {
            errorMessage = "Please enter a valid Philippine mobile number (e.g. 09171234567)."
            return
        }
        if (selectedCrops.isEmpty()) {
            errorMessage = "Please select at least one crop type."
            return
        }
        if (cleanPassword.length < 6) {
            errorMessage = "Password must be at least 6 characters long."
            return
        }
        if (cleanPassword != cleanConfirm) {
            errorMessage = "Passwords do not match. Please re-enter."
            return
        }

        if (!NetworkUtils.isOnline(context)) {
            errorMessage = "Internet connection required to create a new farmer account."
            return
        }

        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            val recaptchaResult = RecaptchaManager.verifyWithBackend(RecaptchaAction.SIGNUP, "signup")
            if (recaptchaResult.isFailure) {
                isLoading = false
                errorMessage = recaptchaResult.exceptionOrNull()?.message ?: "Security check failed. Please try again."
                return@launch
            }

            auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid ?: ""

                val cropsList = selectedCrops.toList()
                val cropTypeString = cropsList.joinToString(", ")

                // 1. Master `farmers` document
                val farmerData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "phone" to cleanPhone,
                    "farmerType" to selectedFarmerType,
                    "typeOfFarmer" to selectedFarmerType,
                    "cropsPlanted" to cropsList,
                    "cropType" to cropTypeString,
                    "crops" to cropsList,
                    "contactInfo" to hashMapOf(
                        "phoneNumber" to cleanPhone,
                        "phone" to cleanPhone,
                        "gmail" to cleanEmail
                    ),
                    "personalInfo" to hashMapOf(
                        "farmerType" to selectedFarmerType,
                        "cropsPlanted" to cropsList,
                        "crops" to cropTypeString
                    ),
                    "role" to "guest_farmer",
                    "status" to "not_submitted",
                    "isVerified" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                // 2. Synchronized `users` document
                val userData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "phone" to cleanPhone,
                    "farmerType" to selectedFarmerType,
                    "cropsPlanted" to cropsList,
                    "cropType" to cropTypeString,
                    "role" to "guest_farmer",
                    "status" to "not_submitted",
                    "verificationStatus" to "not_submitted",
                    "isVerified" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firestore.collection("farmers").document(uid).set(farmerData, SetOptions.merge())
                    .addOnCompleteListener {
                        firestore.collection("users").document(uid).set(userData, SetOptions.merge())

                        // 3. Reset session so the new farmer directly logs in through the Login screen
                        try {
                            auth.signOut()
                        } catch (_: Exception) {}
                        SessionManager.clearSession(context)

                        isLoading = false
                        Toast.makeText(context, "Account created successfully! You can now log in with your email and password.", Toast.LENGTH_LONG).show()
                        onSignupComplete()
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = e.localizedMessage ?: "Failed to create account. Please try again."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.farmer_sign_up_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon & Title
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = "AgriServe",
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AgriServe Philippines",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Department of Agriculture Farmer Registration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Notice Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign up with your basic contact and crop info now. You can upload your Valid ID and RSBSA details in Profile Settings for DA verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Display
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 1. Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Farmer Email Account *") },
                placeholder = { Text("e.g. juan.delacruz@gmail.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mobile Number Field
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; errorMessage = null },
                label = { Text("Mobile Phone Number *") },
                placeholder = { Text("09171234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                supportingText = {
                    Text("Philippine 11-digit mobile number", style = MaterialTheme.typography.labelSmall)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Type of Farmer Dropdown
            ExposedDropdownMenuBox(
                expanded = farmerTypeExpanded,
                onExpandedChange = { farmerTypeExpanded = !farmerTypeExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFarmerType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type of Farmer *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmerTypeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = farmerTypeExpanded,
                    onDismissRequest = { farmerTypeExpanded = false }
                ) {
                    farmerTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedFarmerType = type
                                farmerTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Crop Type Multi-Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Crop Type Cultivated *",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select all primary crops you cultivate (e.g. Rice, Corn):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCrops.forEach { crop ->
                        val isSelected = selectedCrops.contains(crop)
                        val emoji = when (crop) {
                            "Rice" -> "🌾 "
                            "Corn" -> "🌽 "
                            "Coconut" -> "🥥 "
                            "High Value Crops" -> "🥬 "
                            "Abacca" -> "🌿 "
                            else -> "📦 "
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCrops = if (isSelected) {
                                    if (selectedCrops.size > 1) selectedCrops - crop else selectedCrops
                                } else {
                                    selectedCrops + crop
                                }
                            },
                            label = { Text("$emoji$crop", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password *") },
                placeholder = { Text("At least 6 characters") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("Confirm Password *") },
                placeholder = { Text("Re-enter password") },
                leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Create Account Button
            Button(
                onClick = { handleSignUp() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.create_farmer_account),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back to Login Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Log In",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onBackToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
