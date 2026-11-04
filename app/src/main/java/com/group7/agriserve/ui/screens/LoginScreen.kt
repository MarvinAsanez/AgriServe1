package com.group7.agriserve.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
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
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.databinding.ActivityLoginBinding
import com.group7.agriserve.util.NetworkUtils
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
    onSignupClick: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current

    val callbackManager = remember { CallbackManager.Factory.create() }

    fun handleSuccessfulAuth(user: FirebaseUser?, fallbackEmail: String = "") {
        if (user == null) {
            onLoginClick()
            return
        }

        val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
            ?: fallbackEmail.takeIf { it.isNotBlank() }
            ?: "${user.uid}@agriserve.local"

        SessionManager.saveLoginSession(
            context = localContext,
            userId = user.uid,
            email = resolvedEmail
        )
        userViewModel?.farmerEmail?.value = resolvedEmail
        userViewModel?.gmail?.value = resolvedEmail

        // Look up and attach farmer doc
        val db = FirebaseFirestore.getInstance()
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

    // Modern ActivityResultContract for Facebook Login in Compose
    val facebookLauncher = rememberLauncherForActivityResult(
        contract = LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null)
    ) {
        // Handled via callbackManager
    }

    DisposableEffect(Unit) {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                handleSuccessfulAuth(auth.currentUser)
                            } else {
                                Toast.makeText(
                                    localContext,
                                    "Facebook Sign-In to Firebase failed: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }

                override fun onCancel() {
                    // User dismissed Facebook login
                }

                override fun onError(error: FacebookException) {
                    Toast.makeText(
                        localContext,
                        "Facebook error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        onDispose {
            LoginManager.getInstance().unregisterCallback(callbackManager)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))

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
                        "Internet connection required to log in. Once logged in, your session remains active offline for 7 days.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            handleSuccessfulAuth(auth.currentUser, email)
                        } else {
                            Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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

            // Handle Continue with Facebook (via Facebook Login SDK + Firebase Auth)
            binding.btnFacebookLogin.setOnClickListener {
                if (!NetworkUtils.isOnline(context)) {
                    Toast.makeText(context, "Internet connection required to log in.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    facebookLauncher.launch(listOf("email", "public_profile"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Facebook launch error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            // Handle Signup / Submit Credentials Button Click
            binding.btnSignup.setOnClickListener {
                onSignupClick()
            }

            binding.root
        }
    )
}
