package com.group7.agriserve.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.R
import com.group7.agriserve.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userViewModel: UserViewModel,
    onBackClick: () -> Unit = {},
    onEditProfileClick: () -> Unit,
    onSecurityClick: () -> Unit = {},
    onSupportClick: () -> Unit,
    onFeedbackClick: () -> Unit = {},
    onVerificationClick: () -> Unit = {},
    onTagFarmClick: () -> Unit,
    onAllFarmsMapClick: () -> Unit = {},
    onLogoutClick: () -> Unit
) {
    val isVerified = userViewModel.isVerified.value
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.farmer_profile), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.home),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = userViewModel.profileImageUrl.value,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop,
                            fallback = rememberVectorPainter(Icons.Filled.AccountCircle),
                            error = rememberVectorPainter(Icons.Filled.AccountCircle),
                            placeholder = rememberVectorPainter(Icons.Filled.AccountCircle)
                        )
                        FloatingActionButton(
                            onClick = onEditProfileClick,
                            modifier = Modifier.size(36.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = userViewModel.farmerName.value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        color = (if (isVerified) MaterialTheme.colorScheme.primary else Color.Gray).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isVerified) Icons.Filled.Verified else Icons.Filled.HourglassEmpty,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isVerified) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isVerified) "Verified Farmer (Full Access)" else "Non-Verified Farmer (Limited Access)",
                                color = if (isVerified) MaterialTheme.colorScheme.primary else Color(0xFFE65100),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val context = LocalContext.current
                    val remainingDays = SessionManager.getRemainingDays(context)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Weekly Login: $remainingDays days left (Offline Ready)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Information Sections
            ProfileInfoSection(title = stringResource(R.string.account_details)) {
                InfoRow(icon = Icons.Filled.AppRegistration, label = "RSBSA Number", value = userViewModel.rsbsaNumber.value.ifBlank { "Not provided" })
                InfoRow(icon = Icons.Filled.Email, label = stringResource(R.string.email_address), value = userViewModel.farmerEmail.value.ifBlank { "Not provided" })
                InfoRow(icon = Icons.Filled.Phone, label = "Mobile", value = userViewModel.farmerPhone.value.ifBlank { "Not provided" })
            }

            ProfileInfoSection(title = stringResource(R.string.location_details)) {
                val street = userViewModel.street.value
                val barangay = userViewModel.barangay.value
                val city = userViewModel.city.value
                val province = userViewModel.province.value
                val region = userViewModel.region.value

                val addressParts = listOf(street, barangay, city, province, region).filter { it.isNotBlank() }
                val fullAddress = if (addressParts.isEmpty()) "Not provided" else addressParts.joinToString(", ")
                
                InfoRow(icon = Icons.Filled.LocationOn, label = "Address", value = fullAddress)
                SettingsRow(
                    icon = Icons.Filled.AddLocationAlt, 
                    label = stringResource(R.string.tag_my_farm),
                    onClick = onTagFarmClick
                )
                SettingsRow(
                    icon = Icons.Filled.Map, 
                    label = stringResource(R.string.view_all_farms),
                    onClick = onAllFarmsMapClick
                )
            }

            ProfileInfoSection(title = stringResource(R.string.account_settings)) {
                SettingsRow(icon = Icons.Filled.Edit, label = stringResource(R.string.update_profile_info), onClick = onEditProfileClick)
                val currentLangLabel = when (userViewModel.selectedLanguage.value) { "bcl", "bik" -> stringResource(R.string.bikol); "fil", "tl" -> stringResource(R.string.tagalog); else -> stringResource(R.string.english) }
                SettingsRow(
                    icon = Icons.Filled.Language, 
                    label = "${stringResource(R.string.language)} ($currentLangLabel)", 
                    onClick = { showLanguageDialog = true }
                )
                // Quick 1-Tap Theme Toggle Shortcut
                val isDarkThemeActive = when (userViewModel.themeMode.value) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkThemeActive) Icons.Filled.DarkMode else Icons.Filled.WbSunny,
                            contentDescription = null,
                            tint = if (isDarkThemeActive) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.theme),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (userViewModel.themeMode.value) {
                                    "dark" -> "Dark Mode Active"
                                    "light" -> "Light Mode Active"
                                    else -> "System Default (${if (isDarkThemeActive) "Dark" else "Light"})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isDarkThemeActive,
                            onCheckedChange = { checked ->
                                userViewModel.updateThemeMode(if (checked) "dark" else "light", context)
                            }
                        )
                    }
                }
                SettingsRow(icon = Icons.Filled.Palette, label = "${stringResource(R.string.theme)} Options (System / Light / Dark)", onClick = { showThemeDialog = true })
                SettingsRow(icon = Icons.Filled.StarRate, label = "Feedback & System Evaluation", onClick = onFeedbackClick)
                SettingsRow(icon = Icons.Filled.HelpCenter, label = "Help Center, FAQs & Customer Support", onClick = onSupportClick)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button
            Button(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.logout_account), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.english),
                        selected = userViewModel.selectedLanguage.value == "en",
                        onClick = {
                            userViewModel.updateLanguage("en", context)
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.tagalog),
                        selected = userViewModel.selectedLanguage.value in listOf("fil", "tl"),
                        onClick = {
                            userViewModel.updateLanguage("fil", context)
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.bikol),
                        selected = userViewModel.selectedLanguage.value in listOf("bcl", "bik"),
                        onClick = {
                            userViewModel.updateLanguage("bcl", context)
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.select_theme)) },
            text = {
                Column {
                    ThemeOption(
                        label = stringResource(R.string.light_mode),
                        selected = userViewModel.themeMode.value == "light",
                        onClick = {
                            userViewModel.updateThemeMode("light", context)
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        label = stringResource(R.string.dark_mode),
                        selected = userViewModel.themeMode.value == "dark",
                        onClick = {
                            userViewModel.updateThemeMode("dark", context)
                            showThemeDialog = false
                        }
                    )
                    ThemeOption(
                        label = stringResource(R.string.system_default),
                        selected = userViewModel.themeMode.value == "system",
                        onClick = {
                            userViewModel.updateThemeMode("system", context)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
fun ProfileInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(
                text = if (value.isBlank() || value == "Loading...") "Not provided" else value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}
