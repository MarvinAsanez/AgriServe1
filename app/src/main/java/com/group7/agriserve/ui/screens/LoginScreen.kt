package com.group7.agriserve.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))

            fun handleSuccessfulAuth(user: FirebaseUser?, fallbackEmail: String = "") {
                if (user == null) {
                    onLoginClick()
                    return
                }

                val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
                    ?: fallbackEmail.takeIf { it.isNotBlank() }
                    ?: "${user.uid}@agriserve.local"

                SessionManager.saveLoginSession(
                    context = context,
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
                            userViewModel?.bindFarmerDocument(context, doc)
                            userViewModel?.attachFarmerListener(context, doc.reference)
                        } else {
                            db.collection("farmers")
                                .whereEqualTo("email", resolvedEmail)
                                .get()
                                .addOnSuccessListener { q2 ->
                                    if (!q2.isEmpty) {
                                        val doc2 = q2.documents[0]
                                        userViewModel?.bindFarmerDocument(context, doc2)
                                        userViewModel?.attachFarmerListener(context, doc2.reference)
                                    } else {
                                        db.collection("farmers").document(user.uid).get()
                                            .addOnSuccessListener { directDoc ->
                                                if (directDoc != null && directDoc.exists()) {
                                                    userViewModel?.bindFarmerDocument(context, directDoc)
                                                    userViewModel?.attachFarmerListener(context, directDoc.reference)
                                                }
                                            }
                                    }
                                }
                        }
                    }

                onLoginClick()
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

            // Handle Continue with Facebook
            binding.btnFacebookLogin.setOnClickListener {
                if (!NetworkUtils.isOnline(context)) {
                    Toast.makeText(context, "Internet connection required to log in.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, "Unable to find parent activity.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val provider = OAuthProvider.newBuilder("facebook.com")
                provider.setScopes(listOf("email", "public_profile"))

                val pendingResultTask = auth.pendingAuthResult
                if (pendingResultTask != null) {
                    pendingResultTask
                        .addOnSuccessListener { authResult ->
                            handleSuccessfulAuth(authResult.user)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Facebook Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    return@setOnClickListener
                }

                auth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener { authResult ->
                        handleSuccessfulAuth(authResult.user)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Facebook Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
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
