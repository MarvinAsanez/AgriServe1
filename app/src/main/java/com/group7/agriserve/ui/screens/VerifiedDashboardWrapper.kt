package com.group7.agriserve.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group7.agriserve.viewmodel.UserViewModel

/**
 * Verified Dashboard Wrapper
 * Applied when farmer status == 'verified':
 * Unlocks complete access to all AgriServe services, high-level resource requests,
 * and distribution records with official DA verification attribution.
 */
@Composable
fun VerifiedDashboardWrapper(
    userViewModel: UserViewModel,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Unlocked content (Full AgriServe App)
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
