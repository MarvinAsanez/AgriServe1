package com.group7.agriserve.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.recaptcha.RecaptchaAction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.databinding.ActivityLoginBinding
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.RecaptchaManager
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun LoginScreen(
    userViewModel: UserViewModel? = null,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit,
    onForgotPasswordClick: () -> Unit = {}
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current
    val currentThemeMode = userViewModel?.themeMode?.value ?: SessionManager.getCachedThemeMode(localContext)
    val isDarkThemeActive = when (currentThemeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val surfaceBgColor = MaterialTheme.colorScheme.background.toArgb()
    val cardBgColor = MaterialTheme.colorScheme.surface.toArgb()
    val primaryTintColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    fun proceedFarmerLogin(db: FirebaseFirestore, user: FirebaseUser, resolvedEmail: String) {
        userViewModel?.resetProfileState()

        SessionManager.saveLoginSession(
            context = localContext,
            userId = user.uid,
            email = resolvedEmail
        )
        userViewModel?.farmerDocId?.value = user.uid
        userViewModel?.farmerEmail?.value = resolvedEmail
        userViewModel?.gmail?.value = resolvedEmail

        // Look up and attach farmer doc
        db.collection("farmers")
            .whereEqualTo("contactInfo.gmail", resolvedEmail)
            .get()
            .addOnSuccessListener { q ->
                if (!q.isEmpty) {
                    val doc = q.documents[0]
                    userViewModel?.bindFarmerDocument(localContext, doc)
                    userViewModel?.attachFarmerListener(localContext, doc.reference)
                } else {
                    db.collection("farmers")
                        .whereEqualTo("email", resolvedEmail)
                        .get()
                        .addOnSuccessListener { q2 ->
                            if (!q2.isEmpty) {
                                val doc2 = q2.documents[0]
                                userViewModel?.bindFarmerDocument(localContext, doc2)
                                userViewModel?.attachFarmerListener(localContext, doc2.reference)
                            } else {
                                db.collection("farmers").document(user.uid).get()
                                    .addOnSuccessListener { directDoc ->
                                        if (directDoc != null && directDoc.exists()) {
                                            userViewModel?.bindFarmerDocument(localContext, directDoc)
                                            userViewModel?.attachFarmerListener(localContext, directDoc.reference)
                                        }
                                    }
                            }
                        }
                }
            }

        onLoginClick()
    }

    fun handleSuccessfulAuth(user: FirebaseUser?, fallbackEmail: String = "") {
        if (user == null) {
            onLoginClick()
            return
        }

        val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
            ?: fallbackEmail.takeIf { it.isNotBlank() }
            ?: "${user.uid}@agriserve.local"

        fun rejectAdminLogin() {
            try {
                auth.signOut()
            } catch (_: Exception) {}
            userViewModel?.resetProfileState()
            SessionManager.clearSession(localContext)
            Toast.makeText(
                localContext,
                "Access Denied: Admin and Super Admin accounts cannot be used in the Farmer mobile app.",
                Toast.LENGTH_LONG
            ).show()
        }

        if (resolvedEmail.equals("agriserve@admin.da.gov.ph", ignoreCase = true)) {
            rejectAdminLogin()
            return
        }

        val db = FirebaseFirestore.getInstance()
        // Strictly verify the account is not an Admin / Super Admin before granting access
        db.collection("admins").document(user.uid).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc != null && adminDoc.exists()) {
                    rejectAdminLogin()
                    return@addOnSuccessListener
                }

                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { userDoc ->
                        val userRole = userDoc?.getString("role")?.trim()?.lowercase() ?: ""
                        if (userRole in listOf("admin", "super admin", "super_admin", "da staff", "staff")) {
                            rejectAdminLogin()
                            return@addOnSuccessListener
                        }

                        proceedFarmerLogin(db, user, resolvedEmail)
                    }
                    .addOnFailureListener {
                        proceedFarmerLogin(db, user, resolvedEmail)
                    }
            }
            .addOnFailureListener {
                // If admins read is denied by security rules (because non-admins cannot read other admins),
                // check users/{uid} role before proceeding.
                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { userDoc ->
                        val userRole = userDoc?.getString("role")?.trim()?.lowercase() ?: ""
                        if (userRole in listOf("admin", "super admin", "super_admin", "da staff", "staff")) {
                            rejectAdminLogin()
                            return@addOnSuccessListener
                        }
                        proceedFarmerLogin(db, user, resolvedEmail)
                    }
                    .addOnFailureListener {
                        proceedFarmerLogin(db, user, resolvedEmail)
                    }
            }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))

            // Theme Toggle Icon on Upper Right
            binding.btnThemeToggle.setOnClickListener {
                val activeMode = userViewModel?.themeMode?.value ?: SessionManager.getCachedThemeMode(context)
                val currentlyDark = when (activeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isDarkThemeActive
                }
                val nextMode = if (currentlyDark) "light" else "dark"
                SessionManager.saveThemeMode(context, nextMode)
                userViewModel?.updateThemeMode(nextMode, context)
            }

            // Language Selector Button on Login Page
            fun currentLangCode(): String {
                val active = userViewModel?.selectedLanguage?.value?.takeIf { it.isNotBlank() }
                    ?: SessionManager.getCachedAppLanguage(context)
                return when (active) {
                    "tl", "fil" -> "fil"
                    "bik", "bcl" -> "bcl"
                    else -> "en"
                }
            }

            fun updateLangButtonLabel() {
                val label = when (currentLangCode()) {
                    "fil" -> "🌐 Tagalog / Filipino"
                    "bcl" -> "🌐 Bikol"
                    else -> "🌐 English"
                }
                binding.btnLanguage.text = label
            }
            updateLangButtonLabel()

            binding.btnLanguage.setOnClickListener {
                val options = arrayOf(
                    context.getString(com.group7.agriserve.R.string.english),
                    "${context.getString(com.group7.agriserve.R.string.tagalog)} (Filipino)",
                    context.getString(com.group7.agriserve.R.string.bikol)
                )
                val codes = arrayOf("en", "fil", "bcl")
                val checked = codes.indexOf(currentLangCode()).coerceAtLeast(0)

                AlertDialog.Builder(context)
                    .setTitle(context.getString(com.group7.agriserve.R.string.select_language))
                    .setSingleChoiceItems(options, checked) { dialog, which ->
                        val chosenCode = codes[which]
                        SessionManager.saveAppLanguage(context, chosenCode)
                        userViewModel?.updateLanguage(chosenCode, context)
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(chosenCode))
                        updateLangButtonLabel()
                        dialog.dismiss()
                    }
                    .setNegativeButton(context.getString(com.group7.agriserve.R.string.close), null)
                    .show()
            }

            // Handle Standard Email & Password Login
            binding.btnLogin.setOnClickListener {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()

                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!NetworkUtils.isOnline(context)) {
                    Toast.makeText(
                        context,
                        "Internet connection required for first-time login. Once signed in, you stay logged in offline and can unlock with your PIN or fingerprint.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                binding.btnLogin.isEnabled = false
                coroutineScope.launch {
                    val recaptchaResult = RecaptchaManager.verifyWithBackend(RecaptchaAction.LOGIN, "login")
                    if (recaptchaResult.isFailure) {
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(
                            context,
                            recaptchaResult.exceptionOrNull()?.message ?: "Security check failed. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            binding.btnLogin.isEnabled = true
                            if (task.isSuccessful) {
                                handleSuccessfulAuth(auth.currentUser, email)
                            } else {
                                Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }

            // Handle Continue with Google
            binding.btnGoogleLogin.setOnClickListener {
                if (!NetworkUtils.isOnline(context)) {
                    Toast.makeText(context, "Internet connection required to log in.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, "Unable to find parent activity.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

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
                            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

                            auth.signInWithCredential(authCredential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        handleSuccessfulAuth(auth.currentUser, googleIdTokenCredential.id)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Google Sign-In failed: ${task.exception?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        } else {
                            // Direct Firebase Auth Google OAuthProvider fallback
                            val googleProvider = OAuthProvider.newBuilder("google.com")
                            auth.startActivityForSignInWithProvider(activity, googleProvider.build())
                                .addOnSuccessListener { authResult ->
                                    handleSuccessfulAuth(authResult.user)
                                }
                                .addOnFailureListener { err ->
                                    Toast.makeText(context, "Google Sign-In failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    } catch (e: GetCredentialCancellationException) {
                        // User dismissed the chooser dialog
                    } catch (e: Exception) {
                        // Direct Firebase Auth Google OAuthProvider fallback
                        val googleProvider = OAuthProvider.newBuilder("google.com")
                        auth.startActivityForSignInWithProvider(activity, googleProvider.build())
                            .addOnSuccessListener { authResult ->
                                handleSuccessfulAuth(authResult.user)
                            }
                            .addOnFailureListener { err ->
                                Toast.makeText(context, "Google Sign-In failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            }

            // Handle Continue with Facebook (via Firebase Auth OAuthProvider)
            binding.btnFacebookLogin.setOnClickListener {
                if (!NetworkUtils.isOnline(context)) {
                    Toast.makeText(context, "Internet connection required to log in.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, "Unable to find host activity for Facebook Sign-In.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    val provider = OAuthProvider.newBuilder("facebook.com")
                    provider.scopes = listOf("email", "public_profile")

                    val pendingResult = auth.pendingAuthResult
                    if (pendingResult != null) {
                        pendingResult.addOnSuccessListener { authResult ->
                            handleSuccessfulAuth(authResult.user)
                        }.addOnFailureListener { e ->
                            Toast.makeText(context, "Facebook Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        auth.startActivityForSignInWithProvider(activity, provider.build())
                            .addOnSuccessListener { authResult ->
                                handleSuccessfulAuth(authResult.user)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Facebook Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Facebook launch error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            // Handle Signup Button Click
            binding.btnSignup.setOnClickListener {
                onSignupClick()
            }

            // Handle Forgot Password Click
            binding.btnForgotPassword.setOnClickListener {
                onForgotPasswordClick()
            }

            binding.root
        },
        update = { rootView ->
            val binding = ActivityLoginBinding.bind(rootView)
            binding.ivThemeIcon.setImageResource(
                if (isDarkThemeActive) com.group7.agriserve.R.drawable.ic_dark_mode
                else com.group7.agriserve.R.drawable.ic_light_mode
            )
            binding.ivThemeIcon.setColorFilter(primaryTintColor)
            binding.btnThemeToggle.setCardBackgroundColor(cardBgColor)
            binding.cardLogo.setCardBackgroundColor(cardBgColor)

            if (isDarkThemeActive) {
                binding.loginScrollRoot.setBackgroundColor(surfaceBgColor)
            } else {
                binding.loginScrollRoot.setBackgroundResource(com.group7.agriserve.R.color.agri_green_light)
            }

            // Ensure crisp text & input contrast in both Light and Dark Mode
            val primaryColorState = android.content.res.ColorStateList.valueOf(primaryTintColor)
            val hintColorState = android.content.res.ColorStateList.valueOf(onSurfaceVariantColor)

            binding.tvAppTitle.setTextColor(primaryTintColor)
            binding.tvAppSlogan.setTextColor(onSurfaceVariantColor)
            binding.tvOrContinueWith.setTextColor(onSurfaceVariantColor)
            binding.btnForgotPassword.setTextColor(primaryTintColor)
            binding.btnSignup.setTextColor(primaryTintColor)

            binding.etEmail.setTextColor(onSurfaceColor)
            binding.etEmail.setHintTextColor(onSurfaceVariantColor)
            binding.tilEmail.boxStrokeColor = primaryTintColor
            binding.tilEmail.defaultHintTextColor = hintColorState
            binding.tilEmail.hintTextColor = primaryColorState
            binding.tilEmail.setStartIconTintList(primaryColorState)

            binding.etPassword.setTextColor(onSurfaceColor)
            binding.etPassword.setHintTextColor(onSurfaceVariantColor)
            binding.tilPassword.boxStrokeColor = primaryTintColor
            binding.tilPassword.defaultHintTextColor = hintColorState
            binding.tilPassword.hintTextColor = primaryColorState
            binding.tilPassword.setStartIconTintList(primaryColorState)
            binding.tilPassword.setEndIconTintList(primaryColorState)

            binding.btnLanguage.setTextColor(onSurfaceColor)
            binding.btnLanguage.backgroundTintList = android.content.res.ColorStateList.valueOf(cardBgColor)
            binding.btnLanguage.strokeColor = primaryColorState
        }
    )
}
