package com.group7.agriserve.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.google.firebase.auth.OAuthProvider
import androidx.compose.ui.res.painterResource
import com.group7.agriserve.R
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
    onNavigateToStatus: () -> Unit,
    onNavigateToGuest: () -> Unit,
    onNavigateToVerified: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToSignup: () -> Unit = {},
    onNavigateToSubmission: () -> Unit = {},
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

    // RSBSA Registration Guide Modal State
    var showRsbsaGuideDialog by remember { mutableStateOf(false) }

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
                    statusDialogMessage = "Your farmer application for $email has been submitted and is currently PENDING review by DA staff.\n\n" +
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
                                                                "If you are a new farmer, tap 'Sign Up / Create Account' to begin registration."
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
                lookupAndBindGoogleAccount(selectedEmail)
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

    // Google Sign-In with Firebase Authentication
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
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                val resolvedEmail = user?.email ?: googleEmail
                                lookupAndBindGoogleAccount(resolvedEmail)
                            } else {
                                isLoading = false
                                errorMessage = "Google Sign-In failed: ${task.exception?.message}"
                            }
                        }
                } else {
                    // Firebase Auth OAuthProvider fallback
                    val googleProvider = OAuthProvider.newBuilder("google.com")
                    auth.startActivityForSignInWithProvider(activity, googleProvider.build())
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            val resolvedEmail = user?.email ?: "${user?.uid}@agriserve.local"
                            lookupAndBindGoogleAccount(resolvedEmail)
                        }
                        .addOnFailureListener {
                            launchGoogleAccountPicker()
                        }
                }
            } catch (e: GetCredentialCancellationException) {
                isLoading = false
            } catch (e: Exception) {
                // Direct Firebase Auth OAuthProvider fallback
                val googleProvider = OAuthProvider.newBuilder("google.com")
                auth.startActivityForSignInWithProvider(activity, googleProvider.build())
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        val resolvedEmail = user?.email ?: "${user?.uid}@agriserve.local"
                        lookupAndBindGoogleAccount(resolvedEmail)
                    }
                    .addOnFailureListener {
                        launchGoogleAccountPicker()
                    }
            }
        }
    }

    // Facebook Sign-In via Firebase Auth OAuthProvider
    fun signInWithFacebook() {
        if (!NetworkUtils.isOnline(context)) {
            Toast.makeText(context, "Internet connection required to authenticate.", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = "Unable to locate host activity for Facebook Sign-In."
            return
        }
        try {
            isLoading = true
            errorMessage = null
            val provider = OAuthProvider.newBuilder("facebook.com")
            provider.scopes = listOf("email", "public_profile")

            val pendingResult = auth.pendingAuthResult
            if (pendingResult != null) {
                pendingResult.addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val resolvedEmail = user?.email ?: "${user?.uid}@agriserve.local"
                    lookupAndBindGoogleAccount(resolvedEmail)
                }.addOnFailureListener { e ->
                    isLoading = false
                    errorMessage = "Facebook Sign-In failed: ${e.message}"
                }
            } else {
                auth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        val resolvedEmail = user?.email ?: "${user?.uid}@agriserve.local"
                        lookupAndBindGoogleAccount(resolvedEmail)
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMessage = "Facebook Sign-In failed: ${e.message}"
                    }
            }
        } catch (e: Exception) {
            isLoading = false
            errorMessage = "Facebook launch error: ${e.message}"
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
                text = "Department of Agriculture",
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
                        text = "To safeguard agricultural resources, incoming farmers submit their RSBSA credentials and government ID for review by DA staff.",
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
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PRIMARY ACTION: Continue with Facebook
            Button(
                onClick = { signInWithFacebook() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1877F2)
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_facebook),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Facebook",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

            // Action: Sign Up as New Farmer (Simplified initial registration)
            OutlinedButton(
                onClick = { onNavigateToSignup() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Up / Create Account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // HELPER CARD: Not an RSBSA Enrolled Farmer?
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Not an RSBSA Enrolled Farmer?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Department of Agriculture Official Guide",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Don't have an RSBSA number yet? Learn how to register with the DA, view required documentary proofs, download official PDF forms, and find your local submission office.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showRsbsaGuideDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View RSBSA Enrollment Guide",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            // Municipal / DA Footer
            Text(
                text = "Department of Agriculture\nOfficial Agricultural Verification Service",
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

        // RSBSA Enrollment Guide Dialog
        if (showRsbsaGuideDialog) {
            RsbsaEnrollmentGuideDialog(
                onDismiss = { showRsbsaGuideDialog = false },
                onOpenUrl = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Unable to open link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

/**
 * RSBSA Registration & Updating Interactive Guide Dialog
 * Grounded on official Department of Agriculture guidelines:
 * https://www.da.gov.ph/rsbsa-registration-and-updating/
 */
@Composable
fun RsbsaEnrollmentGuideDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Overview", "Requirements", "How to Register", "Forms & Links")
    val tabIcons = listOf(
        Icons.Default.Info,
        Icons.AutoMirrored.Filled.Assignment,
        Icons.Default.FormatListNumbered,
        Icons.Default.Download
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Banner
                Surface(
                    color = Color(0xFF1B5E20),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DA RSBSA Enrollment Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Registry System for Basic Sectors in Agriculture",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Tab Navigation
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = Color(0xFF2E7D32)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> RsbsaOverviewTab()
                        1 -> RsbsaRequirementsTab()
                        2 -> RsbsaStepsTab()
                        3 -> RsbsaFormsAndLinksTab(onOpenUrl = onOpenUrl)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom Footer Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onOpenUrl("https://www.da.gov.ph/rsbsa-registration-and-updating/") }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DA Portal",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Understood", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RsbsaOverviewTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // What is RSBSA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "What is RSBSA?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "The Registry System for Basic Sectors in Agriculture (RSBSA) is an electronic compilation of basic information of farmers, farmworkers, and fisherfolk in the Philippines. It serves as the official baseline for targeting and accessing all Department of Agriculture (DA) programs, interventions, and subsidies.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
        }

        // Who Can Register Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Who is Eligible to Register?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val eligibleList = listOf(
                    "Farmers (Crop producers: Rice, Corn, Coconut, High Value Crops, Abaca, etc.)",
                    "Farmworkers / Agricultural laborers",
                    "Fisherfolk / Aquaculture operators",
                    "Agri-Youth (Ages 18–30 engaged in farming or agri-studies)",
                    "Filipino citizens of legal age (at least 18 years old)"
                )

                eligibleList.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Direct Benefits Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Direct DA Subsidies & Benefits for RSBSA Farmers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

                val benefits = listOf(
                    "🌱 Free Certified Seeds" to "Hybrid and certified inbred rice seeds, corn seeds, and vegetable planting materials.",
                    "🧪 Fertilizer Discount Vouchers (FDV)" to "Electronic discount vouchers redeemable at accredited agri-merchants.",
                    "⛽ Fuel Subsidy Assistance" to "Fuel cash vouchers for machinery, irrigation pumps, tractors, and harvesters.",
                    "💵 RFFA Financial Assistance" to "Direct cash assistance (Rice Farmers Financial Assistance program).",
                    "🛡️ Free PCIC Crop Insurance" to "Automatic subsidized protection against typhoons, floods, droughts, pests, and plant diseases.",
                    "🚨 Calamity Quick Response Fund" to "Fast emergency indemnities and replacement seeds during natural disasters."
                )

                benefits.forEach { (title, desc) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RsbsaRequirementsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Required Documents for RSBSA Registration",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Text(
            text = "Prepare these documents before visiting your Municipal Agriculture Office (MAO) or City Agriculture Office (CAO):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val requirements = listOf(
            Triple(
                "1. Duly Accomplished RSBSA Form",
                "Official DA RSBSA Enrollment Form completely filled out and signed with thumbmark/signature.",
                Icons.Default.Description
            ),
            Triple(
                "2. Valid Government-Issued Photo ID",
                "PhilID (National ID), Driver's License, UMID, Voter's ID, Postal ID, Senior Citizen ID, or Barangay ID with photo.",
                Icons.Default.Badge
            ),
            Triple(
                "3. Recent 2x2 ID Photo",
                "Two (2) recent 2x2 photographs with white background (some MAO offices capture photos digitally on-site).",
                Icons.Default.AccountBox
            ),
            Triple(
                "4. Proof of Land Ownership or Tenancy",
                "• Landowner: OCT/TCT Title, CLOA, or current Real Property Tax Declaration.\n• Tenant / Lessee: Notarized Lease Contract or Landowner Certification.\n• Farmworker: Certification from Landowner or Barangay Chairman.",
                Icons.Default.Landscape
            ),
            Triple(
                "5. Farm Parcel Location & Crop Info",
                "Exact parcel details including Barangay, farm area in hectares (ha), and crops currently cultivated or scheduled for planting.",
                Icons.Default.Place
            )
        )

        requirements.forEach { (title, desc, icon) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RsbsaStepsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Step-by-Step Registration & Form Guide",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )

        val steps = listOf(
            Pair("Step 1: Obtain the Official Form", "Download the official DA RSBSA Enrollment Form PDF from the 'Forms & Links' tab, or pick up a free physical copy from your Municipal Agriculture Office (MAO) or Barangay Hall."),
            Pair("Step 2: Fill Out Part 1 (Personal Profile)", "Write in BLOCK LETTERS: Full legal name, date and place of birth, gender, civil status, contact mobile number, permanent residential address, household members, and 4Ps / IP status."),
            Pair("Step 3: Fill Out Part 2 (Farm Parcel & Crops)", "Indicate your primary agricultural activity (Farmer, Farmworker, Fisherfolk). For each farm parcel, write the Barangay, land tenure (Owner, Tenant, Lessee), parcel size in hectares, and crops (Rice, Corn, Coconut, HVC, etc.)."),
            Pair("Step 4: Part 3 (Signatures & Endorsements)", "Affix your signature or thumbmark over your printed name under the Sworn Declaration. Obtain the signature of your Barangay Chairman or designated Agricultural Extension Worker (AEW)."),
            Pair("Step 5: Submit to Local Agriculture Office", "Submit the completed form along with your valid ID and land ownership/tenancy proof directly to the Municipal Agriculture Office (MAO) or City Agriculture Office (CAO) in your town."),
            Pair("Step 6: Data Encoding & RSBSA ID Issuance", "MAO staff will validate your documents, verify parcel geotags, and encode your record into the DA National Database. Once verified, you will receive your official RSBSA Number (e.g. 05-20-XX-XXX-XXXXXX)!")
        )

        steps.forEachIndexed { index, (stepTitle, stepDesc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stepTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stepDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RsbsaFormsAndLinksTab(
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Official DA Downloadable Forms & Portals",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )

        Text(
            text = "Access official Department of Agriculture resources directly to download forms, check registration status, or view official advisories:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Download Official Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Official RSBSA Enrollment Form",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Download the official DA RSBSA Enrollment Form & Updating Slips A & B. Print and complete the form before heading to your local Municipal Agriculture Office.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onOpenUrl("https://www.da.gov.ph/rsbsa-registration-and-updating/") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Form from DA Portal", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Online Status Finder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check Status Online (DA RSBSA Finder)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Already submitted your application to MAO? Check if your record is active in the official Department of Agriculture National RSBSA database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onOpenUrl("https://finder-rsbsa.da.gov.ph/") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32))
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open DA RSBSA Finder", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Return to AgriServe Tip
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Once you receive your RSBSA Number from your Municipal Agriculture Office, return to AgriServe and tap 'Submit Credentials as New Farmer' to unlock digital services!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
