package com.group7.agriserve.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group7.agriserve.viewmodel.SignUpUiState
import com.group7.agriserve.viewmodel.SignUpViewModel

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * AgriServe Registration Screen powered by Firebase Authentication
 * 
 * Step 1: User Demographics & Channel Selection (Firebase Phone Auth vs Firebase Gmail/Google)
 * Step 2: 6-Digit OTP Verification Screen with Cooldown and Resend
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: SignUpViewModel = viewModel(),
    onSignupComplete: () -> Unit = {},
    onBackToLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scrollState = rememberScrollState()

    // Trigger success callback when verified
    LaunchedEffect(uiState.isOtpVerified) {
        if (uiState.isOtpVerified) {
            Toast.makeText(context, "Identity verified successfully via Firebase!", Toast.LENGTH_SHORT).show()
            onSignupComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.currentStep == 1) "Create Account" else "Verify Identity",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == 2) {
                            viewModel.setStep(1)
                        } else {
                            onBackToLogin()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Step Progress Indicator
            StepProgressBar(currentStep = uiState.currentStep)

            Spacer(modifier = Modifier.height(20.dp))

            // Inline Error Banner
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Inline Success Banner
            AnimatedVisibility(visible = uiState.successMessage != null && uiState.errorMessage == null) {
                uiState.successMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // STEP 1: Registration Details & Channel Selection
            if (uiState.currentStep == 1) {
                StepOneForm(
                    uiState = uiState,
                    onFullNameChange = { viewModel.updateFullName(it) },
                    onPhoneChange = { viewModel.updatePhoneNumber(it) },
                    onEmailChange = { viewModel.updateEmail(it) },
                    onChannelSelect = { viewModel.selectChannel(it) },
                    onSendPhoneCode = {
                        activity?.let { act ->
                            viewModel.sendFirebasePhoneOtp(act, uiState.phoneNumber)
                        } ?: Toast.makeText(context, "Activity context unavailable", Toast.LENGTH_SHORT).show()
                    },
                    onGoogleAuth = {
                        activity?.let { act ->
                            viewModel.authenticateWithGoogle(act)
                        } ?: Toast.makeText(context, "Activity context unavailable", Toast.LENGTH_SHORT).show()
                    },
                    onSendEmailCode = {
                        viewModel.sendFirebaseEmailOtp(uiState.email)
                    },
                    onBackToLogin = onBackToLogin
                )
            } else {
                // STEP 2: 6-Digit OTP Verification Screen
                StepTwoOtpVerification(
                    uiState = uiState,
                    onOtpChange = { viewModel.updateOtpCode(it) },
                    onVerifyClick = {
                        if (uiState.selectedChannel == "sms") {
                            viewModel.verifyFirebasePhoneOtp(uiState.otpCode)
                        } else {
                            viewModel.verifyFirebaseEmailOtp(uiState.email, uiState.otpCode)
                        }
                    },
                    onResendClick = {
                        if (uiState.selectedChannel == "sms") {
                            activity?.let { act ->
                                viewModel.sendFirebasePhoneOtp(act, uiState.phoneNumber)
                            }
                        } else {
                            viewModel.sendFirebaseEmailOtp(uiState.email)
                        }
                    },
                    onChangeIdentifier = { viewModel.setStep(1) }
                )
            }
        }
    }
}

/**
 * Step Progress Indicator: Step 1 (Account Info) -> Step 2 (Dual-Channel OTP)
 */
@Composable
private fun StepProgressBar(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Step 1 Circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (currentStep >= 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "1", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Connecting Line
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(3.dp)
                .background(if (currentStep >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        )

        // Step 2 Circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (currentStep >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "2", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Text(
            text = "Details & Channel",
            fontSize = 12.sp,
            fontWeight = if (currentStep == 1) FontWeight.Bold else FontWeight.Normal,
            color = if (currentStep == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Firebase Verification",
            fontSize = 12.sp,
            fontWeight = if (currentStep == 2) FontWeight.Bold else FontWeight.Normal,
            color = if (currentStep == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Step 1: User Form with Name, Phone, Email & Channel Radio Selection
 */
@Composable
private fun StepOneForm(
    uiState: SignUpUiState,
    onFullNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onSendPhoneCode: () -> Unit,
    onGoogleAuth: () -> Unit,
    onSendEmailCode: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Personal Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Authenticate your credentials using Firebase Authentication.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Full Name Input
            OutlinedTextField(
                value = uiState.fullName,
                onValueChange = onFullNameChange,
                label = { Text("Full Name") },
                placeholder = { Text("e.g. Juan Dela Cruz") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Phone Number Input
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = onPhoneChange,
                label = { Text("Philippine Mobile Number") },
                placeholder = { Text("09171234567") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Email Input
            OutlinedTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = { Text("Gmail Address") },
                placeholder = { Text("farmer@gmail.com") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Channel Selection Header
            Text(
                text = "Choose Firebase Authentication Method",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Select your preferred verification method:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // SMS Option
            ChannelSelectionCard(
                title = "Firebase Phone SMS",
                subtitle = if (uiState.phoneNumber.isNotBlank()) "Send SMS code to ${uiState.phoneNumber}" else "Standard Philippine mobile text via Firebase",
                icon = Icons.Default.Sms,
                isSelected = uiState.selectedChannel == "sms",
                onClick = { onChannelSelect("sms") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Gmail Option
            ChannelSelectionCard(
                title = "Firebase Gmail / Google",
                subtitle = if (uiState.email.isNotBlank()) "Authenticate with Google or receive email code" else "Official Google Firebase Authentication",
                icon = Icons.Default.Mail,
                isSelected = uiState.selectedChannel == "email",
                onClick = { onChannelSelect("email") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons based on channel
            if (uiState.selectedChannel == "sms") {
                Button(
                    onClick = onSendPhoneCode,
                    enabled = !uiState.isSendingOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (uiState.isSendingOtp) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sending Firebase SMS...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send SMS Code via Firebase",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Gmail options: Google 1-Tap vs Email code
                Button(
                    onClick = onGoogleAuth,
                    enabled = !uiState.isSendingOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (uiState.isSendingOtp) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting to Google...", fontSize = 14.sp)
                    } else {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verify with Google Account (1-Tap)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSendEmailCode,
                    enabled = !uiState.isSendingOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send 6-Digit Email Code", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Back to Login Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = "Sign In",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onBackToLogin() }
                )
            }
        }
    }
}

/**
 * Step 2: 6-Digit Centered OTP Input Screen with Cooldown and Resend
 */
@Composable
private fun StepTwoOtpVerification(
    uiState: SignUpUiState,
    onOtpChange: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    onChangeIdentifier: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enter 6-Digit Code",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            val targetIdentifier = if (uiState.selectedChannel == "email") uiState.email else uiState.phoneNumber
            val channelName = if (uiState.selectedChannel == "email") "Gmail" else "Firebase SMS"

            Text(
                text = "We sent a 6-digit verification code via $channelName to:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = targetIdentifier.ifBlank { "your contact" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            TextButton(onClick = onChangeIdentifier) {
                Text(
                    text = "Change Channel or Contact",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6-Digit OTP Text Field with Centered Text and Letter Spacing
            OutlinedTextField(
                value = uiState.otpCode,
                onValueChange = onOtpChange,
                placeholder = {
                    Text(
                        text = "• • • • • •",
                        style = TextStyle(
                            textAlign = TextAlign.Center,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 10.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Strictly 6 numeric digits required",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm & Continue / Verify Button
            Button(
                onClick = onVerifyClick,
                enabled = !uiState.isVerifyingOtp && uiState.otpCode.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (uiState.isVerifyingOtp) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Verifying via Firebase...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm & Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 60-Second Cooldown Resend Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.cooldownSeconds > 0) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resend code in ${uiState.cooldownSeconds}s",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Didn't receive the code? ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Resend Code",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onResendClick() }
                    )
                }
            }
        }
    }
}

/**
 * Interactive Selection Card for OTP Channel (SMS vs Gmail)
 */
@Composable
private fun ChannelSelectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
