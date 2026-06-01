package com.group7.agriserve.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun VerificationScreen(
    onVerifyClick: () -> Unit,
    onBack: () -> Unit
) {
    var otpCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    // Hardcoded valid OTP for demonstration
    val validOtp = "123456"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Verification (Bypassed)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enter any code or just click Verify", 
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = otpCode,
            onValueChange = { if (it.length <= 6) otpCode = it },
            label = { Text("OTP Code") },
            placeholder = { Text("123456") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // TEMPORARY: Bypassing OTP verification
                Toast.makeText(context, "OTP Verified (Bypassed)", Toast.LENGTH_SHORT).show()
                onVerifyClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VERIFY")
        }

        TextButton(onClick = onBack) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = {
            Toast.makeText(context, "A new OTP has been sent!", Toast.LENGTH_SHORT).show()
        }) {
            Text("Resend OTP")
        }
    }
}
