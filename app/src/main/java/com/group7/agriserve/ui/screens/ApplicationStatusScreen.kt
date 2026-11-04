package com.group7.agriserve.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
 * Application Status Screen
 * Displayed while farmer status is 'pending':
 * "Application submitted. Department of Agriculture staff in Ragay are reviewing your credentials."
 * Listens in real-time to Firestore and transitions immediately when reviewed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationStatusScreen(
    userViewModel: UserViewModel,
    onApprovedVerified: () -> Unit,
    onApprovedGuest: () -> Unit,
    onCheckApproval: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val farmerStatus by userViewModel.farmerStatus
    val farmerName by userViewModel.farmerName
    val phoneNumber by userViewModel.phoneNumber
    val gmail by userViewModel.gmail
    var isRefreshing by remember { mutableStateOf(false) }

    // Pulsing animation for the pending hourglass
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

    // Real-time reactive navigation
    LaunchedEffect(farmerStatus) {
        when (farmerStatus) {
            "verified" -> onApprovedVerified()
            "guest", "resubmission_required" -> onApprovedGuest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Status", fontWeight = FontWeight.Bold) },
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
            // Animated Status Icon
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
                        contentDescription = "Pending DA Review",
                        modifier = Modifier.size(56.dp),
                        tint = Color(0xFFE65100)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "⏳ PENDING DA STAFF REVIEW",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = Color(0xFFE65100),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Application Submitted",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Official Notice Message
            Text(
                text = "Department of Agriculture staff in Ragay are reviewing your credentials.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Applicant Summary Card
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
                        Text(
                            text = "Applicant:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = farmerName.ifBlank { "Farmer" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (phoneNumber.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Contact:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (gmail.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Gmail:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = gmail,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Municipal Office:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "MAO Ragay, Camarines Sur",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Check Real-Time Approval Button (Navigates to Approval Verification Page)
            Button(
                onClick = onCheckApproval,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Real-Time Approval", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    SessionManager.clearSession(context)
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Log Out / Switch Account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
