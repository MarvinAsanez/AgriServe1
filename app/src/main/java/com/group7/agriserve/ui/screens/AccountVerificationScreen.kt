package com.group7.agriserve.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.group7.agriserve.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountVerificationScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // These would ideally be tracked in the ViewModel and Firestore
    var isPhoneVerified by remember { mutableStateOf(false) }
    var isEmailVerified by remember { mutableStateOf(false) }
    var isIdUploaded by remember { mutableStateOf(false) }

    val idPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isIdUploaded = true
            Toast.makeText(context, "ID Uploaded successfully", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Verification") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Complete your profile verification to access all features.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            VerificationCard(
                title = "ID Verification",
                description = "Upload a valid government ID (RSBSA, Driver's License, etc.)",
                icon = Icons.Default.Badge,
                status = if (isIdUploaded) "Uploaded" else "Not Started",
                statusColor = if (isIdUploaded) Color(0xFF4CAF50) else Color.Gray,
                onAction = { idPickerLauncher.launch("image/*") },
                actionLabel = if (isIdUploaded) "Change ID" else "Upload ID"
            )

            Spacer(modifier = Modifier.height(16.dp))

            VerificationCard(
                title = "Mobile Phone Verification",
                description = "Verify your contact number ${userViewModel.farmerPhone.value}",
                icon = Icons.Default.PhoneAndroid,
                status = if (isPhoneVerified) "Verified" else "Unverified",
                statusColor = if (isPhoneVerified) Color(0xFF4CAF50) else Color.Gray,
                onAction = { 
                    // Simplified: Logic to send SMS OTP
                    isPhoneVerified = true
                    Toast.makeText(context, "Phone number verified!", Toast.LENGTH_SHORT).show()
                },
                actionLabel = "Verify Now",
                enabled = !isPhoneVerified
            )

            Spacer(modifier = Modifier.height(16.dp))

            VerificationCard(
                title = "Gmail Account Verification",
                description = "Verify your email address ${userViewModel.farmerEmail.value}",
                icon = Icons.Default.Email,
                status = if (isEmailVerified) "Verified" else "Unverified",
                statusColor = if (isEmailVerified) Color(0xFF4CAF50) else Color.Gray,
                onAction = { 
                    // Simplified: Logic to send Email verification
                    isEmailVerified = true
                    Toast.makeText(context, "Email address verified!", Toast.LENGTH_SHORT).show()
                },
                actionLabel = "Verify Now",
                enabled = !isEmailVerified
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isIdUploaded && isPhoneVerified && isEmailVerified) {
                        Toast.makeText(context, "All information submitted for final review", Toast.LENGTH_LONG).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "Please complete all verification steps", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SUBMIT FOR REVIEW", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VerificationCard(
    title: String,
    description: String,
    icon: ImageVector,
    status: String,
    statusColor: Color,
    onAction: () -> Unit,
    actionLabel: String,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
