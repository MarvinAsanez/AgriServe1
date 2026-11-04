package com.group7.agriserve.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.viewmodel.UserViewModel

/**
 * Dedicated Real-Time Approval Result Screen
 * 
 * Invoked when user taps "Check for Real-Time Approval" on ApplicationStatusScreen:
 * - If approved: Celebrates approval and prompts the user to "Proceed to Log In & Access Dashboard".
 * - If not yet approved: Informs user that credentials are being actively reviewed, highlights
 *   automated real-time notification dispatch, displays submitted credentials, and provides
 *   options to check again, go back, or switch accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealTimeApprovalResultScreen(
    userViewModel: UserViewModel,
    onProceedToLogin: () -> Unit,
    onResubmit: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val farmerStatus by userViewModel.farmerStatus
    val farmerName by userViewModel.farmerName
    val phoneNumber by userViewModel.phoneNumber
    val gmail by userViewModel.gmail
    val rsbsaNumber by userViewModel.rsbsaNumber
    val adminFeedback by userViewModel.adminFeedback

    var isChecking by remember { mutableStateOf(false) }

    // Pulsing animation for status indicators
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Trigger fresh profile fetch upon entering
    LaunchedEffect(Unit) {
        userViewModel.fetchUserProfile(context)
    }

    val isApproved = farmerStatus == "verified" || farmerStatus == "approved"
    val isGuestOrResubmit = farmerStatus == "guest" || farmerStatus == "resubmission_required"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approval Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Application Status"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                            SessionManager.clearSession(context)
                            onLogout()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out"
                        )
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isApproved) {
                // ==========================================
                // 1. APPROVED STATE
                // ==========================================
                Surface(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Approved",
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFF2E7D32)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))
                ) {
                    Text(
                        text = "🎉 APPLICATION APPROVED",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Welcome to AgriServe!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your credentials have been successfully approved by the Department of Agriculture staff in Ragay. You now have full access to agricultural services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Verification Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Verified Farmer:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(farmerName.ifBlank { "Farmer" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        if (rsbsaNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("RSBSA Reference:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(rsbsaNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }

                        if (phoneNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Contact Phone:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(phoneNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (gmail.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Verified Account:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(gmail, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Approval Office:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("MAO Ragay, Camarines Sur", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Primary Action: Proceed to Log In
                Button(
                    onClick = onProceedToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Proceed to Log In & Access Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

            } else if (isGuestOrResubmit) {
                // ==========================================
                // 2. GUEST / RESUBMISSION REQUESTED STATE
                // ==========================================
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = Color(0xFFFF9800).copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Resubmission Needed",
                            modifier = Modifier.size(54.dp),
                            tint = Color(0xFFE65100)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "⚠️ ACTION REQUIRED: RESUBMIT CREDENTIALS",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color(0xFFE65100),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Update Needed on Your Application",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = adminFeedback?.ifBlank { null }
                        ?: "DA Staff in Ragay have reviewed your application and requested a clearer ID or updated demographic details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onResubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review & Resubmit Credentials", fontWeight = FontWeight.Bold)
                }

            } else {
                // ==========================================
                // 3. NOT YET APPROVED (STILL PENDING) STATE
                // ==========================================
                Surface(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = "Pending Review",
                            modifier = Modifier.size(56.dp),
                            tint = Color(0xFFE65100)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "⏳ APPLICATION NOT YET APPROVED",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color(0xFFE65100),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Application Under Active Review",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your RSBSA demographic credentials and Valid Government ID have been received and are currently queued for verification by Department of Agriculture staff in Ragay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Prominent Notification Alert Card (as requested)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF90CAF9))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1976D2),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Notification",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Real-Time Notification Enabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You don't need to stay on this screen. You will automatically receive an instant notification on your device as soon as your application is approved or if any update is needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1565C0),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Submitted Credentials Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Applicant:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(farmerName.ifBlank { "Farmer" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        if (rsbsaNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("RSBSA Number:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(rsbsaNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                            }
                        }

                        if (phoneNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Phone:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(phoneNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (gmail.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gmail:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(gmail, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Government ID:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Front & Back Attached", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Re-check button
                Button(
                    onClick = {
                        isChecking = true
                        userViewModel.fetchUserProfile(context)
                        isChecking = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Again (Real-Time)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back to Application Status")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clearSession(context)
                    onLogout()
                }
            ) {
                Text(
                    text = "Log Out / Switch Account",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
