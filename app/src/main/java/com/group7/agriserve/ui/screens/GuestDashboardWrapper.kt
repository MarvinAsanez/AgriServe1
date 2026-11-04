package com.group7.agriserve.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group7.agriserve.viewmodel.UserViewModel

/**
 * Guest Dashboard Wrapper
 * Applied when farmer status == 'guest' (or 'resubmission_required'):
 * - Restricted access: Allows browsing general announcements, weather, market prices, and community advisories.
 * - Restricts high-level request submissions (e.g. seeds, fertilizers, farm machinery).
 * - Displays a persistent banner: "You are currently browsing as a Guest. [Reason/Feedback from DA Staff].
 *   Tap here to update and resubmit your credentials."
 */
@Composable
fun GuestDashboardWrapper(
    userViewModel: UserViewModel,
    onResubmitClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val adminFeedback by userViewModel.adminFeedback
    var showRestrictedDialog by remember { mutableStateOf(false) }

    val currentTheme = userViewModel.themeMode.value
    val isDark = when (currentTheme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // PERSISTENT GUEST BANNER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable { onResubmitClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF2A2118) else Color(0xFFFFF3E0)
            ),
            border = BorderStroke(1.2.dp, if (isDark) Color(0xFF8D5300) else Color(0xFFFFB74D)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF3E2D16) else Color(0xFFFFE0B2),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Guest Farmer (Restricted Access)",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFB74D) else Color(0xFFD84315),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100),
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val noteText = if (!adminFeedback.isNullOrBlank()) {
                        "DA Feedback: \"$adminFeedback\""
                    } else {
                        "Verification pending correction."
                    }

                    Text(
                        text = "You are currently browsing as a Guest. $noteText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFE0D0C0) else Color(0xFF4E342E),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Tap here to update and resubmit your credentials ➔",
                        color = if (isDark) Color(0xFFFFCC80) else Color(0xFFBF360C),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Host screen content (e.g. Home, Weather, Community, Market)
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }

    // Modal informing Guest farmers when high-level requests are blocked
    if (showRestrictedDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Verified Farmer Status Required",
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Resource requests (seeds, fertilizers, equipment grants) are reserved for verified farmers registered with the DA Ragay RSBSA database.\n\nPlease update and resubmit your credentials to attain verified status.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedDialog = false
                        onResubmitClick()
                    }
                ) {
                    Text("Resubmit Credentials")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedDialog = false }) {
                    Text("Continue as Guest")
                }
            }
        )
    }
}
