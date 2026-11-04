package com.group7.agriserve.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.SignUpViewModel
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.launch

/**
 * AgriServe Verification Gate & Onboarding Entry Point
 * Farmers authenticate via Google / Gmail with OTP verification
 * to enter the credential submission flow or inspect application status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationGateScreen(
    userViewModel: UserViewModel,
    onNavigateToSubmission: () -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToGuest: () -> Unit,
    onNavigateToVerified: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val signUpViewModel: SignUpViewModel = viewModel()
    val signUpUiState by signUpViewModel.uiState.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var inputGmail by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var showManualLookup by remember { mutableStateOf(false) }
    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var dialogEmail by remember { mutableStateOf("") }

    // Google OTP verification modal state
    var pendingGoogleEmail by remember { mutableStateOf("") }
    var showGoogleOtpDialog by remember { mutableStateOf(false) }

    // Application Status result modal state (Only inspects approval status, NEVER directs to homescreen)
    var showStatusResultDialog by remember { mutableStateOf(false) }
    var statusDialogTitle by remember { mutableStateOf("") }
    var statusDialogMessage by remember { mutableStateOf("") }
    var statusDialogIcon by remember { mutableStateOf(Icons.Default.Info) }
    var statusDialogIconColor by remember { mutableStateOf(Color(0xFF2E7D32)) }

    val farmerStatus by userViewModel.farmerStatus

    LaunchedEffect(farmerStatus) {
        when (farmerStatus) {
            "verified" -> onNavigateToVerified()
            "guest", "resubmission_required" -> onNavigateToGuest()
            "pending" -> onNavigateToStatus()
            else -> Unit
        }
    }

    // Comprehensive Firestore lookup for Google / Gmail account across all collections
    fun lookupAndBindGoogleAccount(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid Google / Gmail address."
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = null

        // Prepopulate email in UserViewModel so submission / dashboard has it
        userViewModel.farmerEmail.value = email
        userViewModel.gmail.value = email

        fun doFirestoreLookup(effectiveUid: String) {
            val db = FirebaseFirestore.getInstance()

            fun runEmailQueries() {
                // 1. Search farmers by contactInfo.gmail
                db.collection("farmers")
                    .whereEqualTo("contactInfo.gmail", email)
                    .get()
                .addOnSuccessListener { q1 ->
                    if (!q1.isEmpty) {
                        val doc = q1.documents[0]
                        userViewModel.bindFarmerDocument(context, doc)
                        userViewModel.attachFarmerListener(context, doc.reference)
                        isLoading = false
                    } else {
                        // 2. Search farmers by root email
                        db.collection("farmers")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener { q2 ->
                                if (!q2.isEmpty) {
                                    val doc = q2.documents[0]
                                    userViewModel.bindFarmerDocument(context, doc)
                                    userViewModel.attachFarmerListener(context, doc.reference)
                                    isLoading = false
                                } else {
                                    // 3. Search users collection by email
                                    db.collection("users")
                                        .whereEqualTo("email", email)
                                        .get()
                                        .addOnSuccessListener { q3 ->
                                            if (!q3.isEmpty) {
                                                val doc = q3.documents[0]
                                                userViewModel.bindFarmerDocument(context, doc)
                                                userViewModel.attachFarmerListener(context, doc.reference)
                                                isLoading = false
                                            } else {
                                                // 4. Search farmer_applications collection
                                                db.collection("farmer_applications")
                                                    .whereEqualTo("contactInfo.gmail", email)
                                                    .get()
                                                    .addOnSuccessListener { q4 ->
                                                        if (!q4.isEmpty) {
                                                            val doc = q4.documents[0]
                                                            userViewModel.bindFarmerDocument(context, doc)
                                                            userViewModel.attachFarmerListener(context, doc.reference)
                                                            isLoading = false
                                                        } else {
                                                            // Brand new farmer: proceed to credential submission
                                                            SessionManager.saveLoginSession(
                                                                context = context,
                                                                userId = effectiveUid,
                                                                email = email
                                                            )
                                                            userViewModel.farmerStatus.value = "not_submitted"
                                                            isLoading = false
                                                            onNavigateToSubmission()
                                                        }
                                                    }
                                                    .addOnFailureListener {
                                                        userViewModel.farmerStatus.value = "not_submitted"
                                                        isLoading = false
                                                        onNavigateToSubmission()
                                                    }
                                            }
                                        }
                                        .addOnFailureListener {
                                            userViewModel.farmerStatus.value = "not_submitted"
                                            isLoading = false
                                            onNavigateToSubmission()
                                        }
                                }
                            }
                            .addOnFailureListener {
                                userViewModel.farmerStatus.value = "not_submitted"
                                isLoading = false
                                onNavigateToSubmission()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    isLoading = false
                    errorMessage = "Lookup error: ${e.message}"
                }
            }

            if (effectiveUid.isNotBlank() && !effectiveUid.startsWith("google_")) {
                db.collection("farmers").document(effectiveUid).get()
                    .addOnSuccessListener { directDoc ->
                        if (directDoc != null && directDoc.exists()) {
                            userViewModel.bindFarmerDocument(context, directDoc)
                            userViewModel.attachFarmerListener(context, directDoc.reference)
                            isLoading = false
                        } else {
                            runEmailQueries()
                        }
                    }
                    .addOnFailureListener {
                        runEmailQueries()
                    }
            } else {
                runEmailQueries()
            }
        }

        // Ensure active Firebase Auth session so security rules pass
        val currentUser = auth.currentUser
        if (currentUser != null) {
            SessionManager.saveLoginSession(
                context = context,
                userId = currentUser.uid,
                email = email
            )
            doFirestoreLookup(currentUser.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: "google_${System.currentTimeMillis()}"
                    SessionManager.saveLoginSession(
                        context = context,
                        userId = uid,
                        email = email
                    )
                    doFirestoreLookup(uid)
                }
                .addOnFailureListener {
                    val uid = "google_${System.currentTimeMillis()}"
                    SessionManager.saveLoginSession(
                        context = context,
                        userId = uid,
                        email = email
                    )
                    doFirestoreLookup(uid)
                }
        }
    }

    // Trigger 6-digit OTP verification for Google account sign-in
    fun startGoogleOtpVerification(rawEmail: String) {
        val clean = rawEmail.trim().lowercase()
        if (clean.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()) {
            errorMessage = "Invalid Google account address."
            isLoading = false
            return
        }

        pendingGoogleEmail = clean
        isLoading = false
        signUpViewModel.resetOtpState()
        signUpViewModel.sendFirebaseEmailOtp(clean, forceSend = true) {
            showGoogleOtpDialog = true
        }
        showGoogleOtpDialog = true
    }

    // ONLY checks whether an application is approved or not without logging in or directing to homescreen
    fun checkOnlyApplicationStatus(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid Gmail address to check status."
            return
        }

        isCheckingStatus = true
        errorMessage = null

        val db = FirebaseFirestore.getInstance()

        fun handleFoundDoc(doc: DocumentSnapshot) {
            isCheckingStatus = false
            val status = doc.getString("status")
                ?: doc.getString("accountStatus")
                ?: (doc.get("adminReview") as? Map<*, *>)?.get("status")?.toString()
                ?: (doc.get("verificationStatus") as? String)
                ?: "pending"

            val pInfo = doc.get("personalInfo") as? Map<*, *>
            val fName = pInfo?.get("firstName") as? String ?: doc.getString("firstName") ?: ""
            val lName = pInfo?.get("lastName") as? String ?: doc.getString("lastName") ?: ""
            val fullName = if (fName.isNotBlank()) "$fName $lName".trim() else "Farmer"

            val remarks = (doc.get("adminReview") as? Map<*, *>)?.get("remarks")?.toString()
                ?: doc.getString("rejectionReason")
                ?: ""

            when (status.lowercase()) {
                "approved", "verified" -> {
                    statusDialogTitle = "Application Approved ✓"
                    statusDialogMessage = "Your farmer application for $email has been APPROVED by the Municipal Agriculture Office (MAO).\n\n" +
                            "Applicant: $fullName\n" +
                            "Status: Officially Verified Farmer\n\n" +
                            "To access your farm records and services, please log in with your account credentials."
                    statusDialogIcon = Icons.Default.CheckCircle
                    statusDialogIconColor = Color(0xFF2E7D32)
                }
                "rejected", "resubmission_required" -> {
                    statusDialogTitle = "Action Required / Rejected ⚠️"
                    statusDialogMessage = "Your farmer application for $email is currently marked as '$status'.\n\n" +
                            (if (remarks.isNotBlank()) "Office Remarks: $remarks\n\n" else "") +
                            "Please visit the Municipal Agriculture Office or re-submit your credentials with updated ID documents."
                    statusDialogIcon = Icons.Default.Cancel
                    statusDialogIconColor = Color(0xFFD32F2F)
                }
                else -> {
                    statusDialogTitle = "Application Under Review ⏳"
                    statusDialogMessage = "Your farmer application for $email has been submitted and is currently PENDING review by DA Ragay staff.\n\n" +
                            "Applicant: $fullName\n" +
                            "Status: Pending Verification\n\n" +
                            "You will be notified once your RSBSA and ID credentials have been reviewed."
                    statusDialogIcon = Icons.Default.HourglassTop
                    statusDialogIconColor = Color(0xFFF57C00)
                }
            }
            showStatusResultDialog = true
        }

        db.collection("farmers")
            .whereEqualTo("contactInfo.gmail", email)
            .get()
            .addOnSuccessListener { q1 ->
                if (!q1.isEmpty) {
                    handleFoundDoc(q1.documents[0])
                } else {
                    db.collection("farmers")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { q2 ->
                            if (!q2.isEmpty) {
                                handleFoundDoc(q2.documents[0])
                            } else {
                                db.collection("farmer_applications")
                                    .whereEqualTo("contactInfo.gmail", email)
                                    .get()
                                    .addOnSuccessListener { q3 ->
                                        if (!q3.isEmpty) {
                                            handleFoundDoc(q3.documents[0])
                                        } else {
                                            db.collection("users")
                                                .whereEqualTo("email", email)
                                                .get()
                                                .addOnSuccessListener { q4 ->
                                                    isCheckingStatus = false
                                                    if (!q4.isEmpty) {
                                                        handleFoundDoc(q4.documents[0])
                                                    } else {
                                                        statusDialogTitle = "No Application Found"
                                                        statusDialogMessage = "No application record was found for '$email'.\n\n" +
                                                                "If you are a new farmer, tap 'Submit Credentials as New Farmer' to begin onboarding."
                                                        statusDialogIcon = Icons.Default.Info
                                                        statusDialogIconColor = Color(0xFF1976D2)
                                                        showStatusResultDialog = true
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    isCheckingStatus = false
                                                    statusDialogTitle = "No Application Found"
                                                    statusDialogMessage = "No record found for '$email'."
                                                    statusDialogIcon = Icons.Default.Info
                                                    statusDialogIconColor = Color(0xFF1976D2)
                                                    showStatusResultDialog = true
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        isCheckingStatus = false
                                        errorMessage = "Error checking application status: ${it.localizedMessage}"
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isCheckingStatus = false
                            errorMessage = "Error checking application status: ${it.localizedMessage}"
                        }
                }
            }
            .addOnFailureListener {
                isCheckingStatus = false
                errorMessage = "Error checking application status: ${it.localizedMessage}"
            }
    }

    // System Google Account Chooser launcher
    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                startGoogleOtpVerification(selectedEmail)
            }
        }
    }

    // Launch Android System Google Account Chooser
    fun launchGoogleAccountPicker() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            isLoading = true
            errorMessage = null
            googleAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w("VerificationGate", "AccountManager chooser failed: ${e.message}")
            isLoading = false
            dialogEmail = inputGmail.ifBlank { "" }
            showGoogleAccountDialog = true
        }
    }

    fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // Google Sign-In with Credential Manager and seamless Account Picker fallback
    fun signInWithGoogle() {
        if (!NetworkUtils.isOnline(context)) {
            Toast.makeText(context, "Internet connection required to authenticate.", Toast.LENGTH_SHORT).show()
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            launchGoogleAccountPicker()
            return
        }

        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("23714483028-g6sobka0kn0cgh72k3q8c0bpjoo9c1tt.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = activity, request = request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleIdToken = googleIdTokenCredential.idToken
                    val googleEmail = googleIdTokenCredential.id
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

                    auth.signInWithCredential(authCredential)
                        .addOnCompleteListener { task ->
                            val user = auth.currentUser
                            val resolvedEmail = user?.email ?: googleEmail
                            startGoogleOtpVerification(resolvedEmail)
                        }
                } else {
                    launchGoogleAccountPicker()
                }
            } catch (e: GetCredentialCancellationException) {
                isLoading = false
                // User dismissed chooser dialog
            } catch (e: Exception) {
                Log.w("VerificationGate", "CredentialManager fallback to Account Chooser: ${e.message}")
                launchGoogleAccountPicker()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Government / DA Seal Badge
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Agriculture,
                        contentDescription = "AgriServe Seal",
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "AgriServe",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Text(
                text = "Department of Agriculture • Ragay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Informative Onboarding Gate Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Official Farmer Verification",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "To safeguard agricultural resources, incoming farmers submit their RSBSA credentials and government ID for review by DA Ragay staff.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PRIMARY ACTION: Continue with Google
            Button(
                onClick = { signInWithGoogle() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
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
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google Account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECONDARY ACTION: Log In
            OutlinedButton(
                onClick = { onNavigateToLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Alternate Action: Direct Credential Submission as New Farmer
            OutlinedButton(
                onClick = { onNavigateToSubmission() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Submit Credentials as New Farmer",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Toggle for Manual Gmail Application Status Check
            TextButton(
                onClick = { showManualLookup = !showManualLookup }
            ) {
                Text(
                    text = if (showManualLookup) "Hide status check" else "Check status with Gmail address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showManualLookup) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputGmail,
                    onValueChange = { inputGmail = it },
                    label = { Text("Gmail Address") },
                    placeholder = { Text("e.g. juan.delacruz@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { checkOnlyApplicationStatus(inputGmail) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    enabled = !isCheckingStatus && inputGmail.isNotBlank()
                ) {
                    if (isCheckingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking Status...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Application Status", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Municipal Footer
            Text(
                text = "Municipality of Ragay, Camarines Sur\nOfficial Agricultural Verification Service",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Google Account OTP Verification Dialog
        if (showGoogleOtpDialog) {
            AlertDialog(
                onDismissRequest = {
                    showGoogleOtpDialog = false
                    signUpViewModel.resetOtpState()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Google Account OTP Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "To protect your agricultural account, a 6-digit OTP verification code was sent to:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingGoogleEmail,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

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
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (signUpUiState.cooldownSeconds > 0) {
                                Text(
                                    text = "Resend code in ${signUpUiState.cooldownSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        signUpViewModel.sendFirebaseEmailOtp(pendingGoogleEmail, forceSend = true)
                                    },
                                    enabled = !signUpUiState.isSendingOtp
                                ) {
                                    Text(
                                        text = "Resend 6-Digit Code",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Inline Error
                        signUpUiState.errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Inline Success
                        signUpUiState.successMessage?.let { succ ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = succ,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            signUpViewModel.verifyFirebaseEmailOtp(pendingGoogleEmail, signUpUiState.otpCode) {
                                showGoogleOtpDialog = false
                                lookupAndBindGoogleAccount(pendingGoogleEmail)
                            }
                        },
                        enabled = !signUpUiState.isVerifyingOtp && signUpUiState.otpCode.length == 6
                    ) {
                        if (signUpUiState.isVerifyingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...")
                        } else {
                            Text("Verify & Continue", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGoogleOtpDialog = false
                            signUpViewModel.resetOtpState()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Application Status Result Dialog (Never auto-directs to homescreen)
        if (showStatusResultDialog) {
            AlertDialog(
                onDismissRequest = { showStatusResultDialog = false },
                icon = {
                    Icon(
                        imageVector = statusDialogIcon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = statusDialogIconColor
                    )
                },
                title = {
                    Text(
                        text = statusDialogTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = statusDialogMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showStatusResultDialog = false }
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Google Account fallback dialog
        if (showGoogleAccountDialog) {
            AlertDialog(
                onDismissRequest = { showGoogleAccountDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Continue with Google Account", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Enter your Google / Gmail account to authenticate with OTP verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = dialogEmail,
                            onValueChange = { dialogEmail = it },
                            label = { Text("Google Account (Gmail) *") },
                            placeholder = { Text("farmer@gmail.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toLookup = dialogEmail.trim().lowercase()
                            if (toLookup.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(toLookup).matches()) {
                                showGoogleAccountDialog = false
                                startGoogleOtpVerification(toLookup)
                            } else {
                                Toast.makeText(context, "Please enter a valid Gmail address", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = dialogEmail.isNotBlank()
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
