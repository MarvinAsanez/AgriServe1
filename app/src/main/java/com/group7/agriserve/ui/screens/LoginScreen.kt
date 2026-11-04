package com.group7.agriserve.ui.screens

import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.util.NetworkUtils
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.databinding.ActivityLoginBinding
import com.group7.agriserve.viewmodel.UserViewModel

@Composable
fun LoginScreen(
    userViewModel: UserViewModel? = null,
    onLoginClick: () -> Unit,
    onSignupClick: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))
            
            // Handle Login Button Click
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

                // Authentication Logic
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            val resolvedEmail = user?.email ?: email
                            if (user != null) {
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
                                        }
                                    }
                            }
                            onLoginClick()
                        } else {
                            Toast.makeText(context, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }

            // Handle Signup Button Click
            binding.btnSignup.setOnClickListener {
                onSignupClick()
            }
            
            binding.root
        }
    )
}
