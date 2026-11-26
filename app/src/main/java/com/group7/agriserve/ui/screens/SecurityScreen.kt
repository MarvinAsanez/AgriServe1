package com.group7.agriserve.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.group7.agriserve.util.OfflineLockManager
import com.group7.agriserve.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit,
    onLockApp: () -> Unit = {}
) {
    val context = LocalContext.current
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    val isPasswordValid = newPassword.length >= 8

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Change Password", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("Old Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                isError = newPassword.isNotEmpty() && !isPasswordValid,
                supportingText = {
                    if (newPassword.isNotEmpty() && !isPasswordValid) {
                        Text("Password must be at least 8 characters")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm New Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isLoading = true
                    userViewModel.changePassword(oldPassword, newPassword) { success, error ->
                        isLoading = false
                        if (success) {
                            Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isPasswordValid && newPassword == confirmPassword && oldPassword.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("CHANGE PASSWORD")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // Offline PIN & Fingerprint Quick Lock Section
            val activeDocId by userViewModel.farmerDocId
            var isPinEnabled by remember(activeDocId) { mutableStateOf(OfflineLockManager.isPinEnabled(context)) }
            val canUseBiometric = remember { OfflineLockManager.canUseBiometric(context) }
            var isBiometricEnabled by remember(activeDocId) { mutableStateOf(OfflineLockManager.isBiometricEnabled(context)) }
            var newPin by remember { mutableStateOf("") }
            var confirmPin by remember { mutableStateOf("") }

            LaunchedEffect(activeDocId) {
                isPinEnabled = OfflineLockManager.isPinEnabled(context)
                isBiometricEnabled = OfflineLockManager.isBiometricEnabled(context)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Quick Lock (PIN & Fingerprint)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isPinEnabled || isBiometricEnabled) {
                            "Configured & remembered across sign-outs. Lock your app without signing out so queued submissions keep syncing."
                        } else {
                            "Set up once — your Offline PIN and Fingerprint preference remain saved across logouts and re-logins."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text(if (isPinEnabled) "New 4–6 Digit PIN" else "Set 4–6 Digit Offline PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirm Offline PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (newPin.length < 4) {
                                    Toast.makeText(context, "PIN must be 4 to 6 digits.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (newPin != confirmPin) {
                                    Toast.makeText(context, "PINs do not match.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                OfflineLockManager.setPin(context, newPin)
                                isPinEnabled = true
                                newPin = ""
                                confirmPin = ""
                                Toast.makeText(context, "Offline PIN saved!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isPinEnabled) "UPDATE PIN" else "ENABLE PIN")
                        }

                        if (isPinEnabled) {
                            OutlinedButton(
                                onClick = {
                                    OfflineLockManager.disablePin(context)
                                    isPinEnabled = false
                                    Toast.makeText(context, "Offline PIN disabled.", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("DISABLE")
                            }
                        }
                    }

                    if (canUseBiometric) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fingerprint / Biometric Unlock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { checked ->
                                    OfflineLockManager.setBiometricEnabled(context, checked)
                                    isBiometricEnabled = checked
                                }
                            )
                        }
                    }

                    if (isPinEnabled || isBiometricEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = {
                                OfflineLockManager.setAppLocked(context, true)
                                onLockApp()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LOCK APP NOW (KEEP OFFLINE SESSION)")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
