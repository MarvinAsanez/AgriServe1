package com.group7.agriserve

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.util.SessionManager
import com.group7.agriserve.ui.navigation.Screen
import com.group7.agriserve.ui.screens.*
import com.group7.agriserve.ui.theme.AgriServeTheme
import com.group7.agriserve.viewmodel.UserViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Firebase Firestore Offline Persistence
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (e: Exception) {
            // Firestore settings already locked/initialized
        }

        // Synchronously load cached user profile and session BEFORE composing UI to prevent cold-start route bouncing
        val userViewModel: UserViewModel = ViewModelProvider(this)[UserViewModel::class.java]
        userViewModel.loadCachedSession(this)
        userViewModel.fetchUserProfile(this)

        enableEdgeToEdge()
        setContent {
            val themeMode by userViewModel.themeMode
            
            AgriServeTheme(themeMode = themeMode) {
                AgriServeApp(userViewModel)
            }
        }
    }
}

@Composable
fun AgriServeApp(userViewModel: UserViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val farmerStatus by userViewModel.farmerStatus
    val isVerified by userViewModel.isVerified
    val role by userViewModel.role
    val farmerName by userViewModel.farmerName
    val accountStatus by userViewModel.accountStatus
    val selectedLanguage by userViewModel.selectedLanguage
    val currentUser = FirebaseAuth.getInstance().currentUser
    val isSessionValid = remember { SessionManager.isSessionValid(context) }

    // Instant local cache load for offline cold-start
    LaunchedEffect(Unit) {
        userViewModel.loadCachedSession(context)
        userViewModel.fetchUserProfile(context)
    }

    // Apply language changes to app's locale safely without recreation loops
    LaunchedEffect(selectedLanguage) {
        if (selectedLanguage.isNotBlank()) {
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val currentTag = currentLocales.get(0)?.language ?: ""
            val normCurrent = when (currentTag) { "tl" -> "fil"; "bik" -> "bcl"; else -> currentTag }
            val targetTag = when (selectedLanguage) { "tl" -> "fil"; "bik" -> "bcl"; else -> selectedLanguage }
            
            if (normCurrent.isNotBlank() && normCurrent != targetTag) {
                val appLocale = LocaleListCompat.forLanguageTags(targetTag)
                AppCompatDelegate.setApplicationLocales(appLocale)
            } else if (normCurrent.isBlank()) {
                val systemDefault = java.util.Locale.getDefault().language
                val normSystem = if (systemDefault == "tl") "fil" else systemDefault
                if (normSystem != targetTag) {
                    val appLocale = LocaleListCompat.forLanguageTags(targetTag)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
            }
        }
    }

    val mainScreens = listOf(
        Screen.Home,
        Screen.Inventory,
        Screen.Request,
        Screen.Report,
        Screen.Profile
    )

    // Show bottom bar if on one of the main dashboard destinations
    val showBottomBar = mainScreens.any { screen -> 
        currentDestination?.hierarchy?.any { it.route == screen.route } == true 
    }

    // When on any secondary dashboard tab (e.g. Profile, Inventory, Request, Report), system back returns to Home
    val isNotOnHomeScreen = currentDestination?.route != null && 
        currentDestination?.route != Screen.Home.route && 
        showBottomBar
    BackHandler(enabled = isNotOnHomeScreen) {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    mainScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    when(screen) {
                                        Screen.Home -> Icons.Filled.Home
                                        Screen.Inventory -> Icons.Filled.Inventory
                                        Screen.Request -> Icons.Filled.AddCircle
                                        Screen.Report -> Icons.Filled.Report
                                        Screen.Profile -> Icons.Filled.Person
                                        else -> Icons.Filled.Home
                                    }, 
                                    contentDescription = screen.route 
                                ) 
                            },
                            label = {
                                Text(
                                    when(screen) {
                                        Screen.Home -> stringResource(R.string.home)
                                        Screen.Inventory -> stringResource(R.string.inventory)
                                        Screen.Request -> stringResource(R.string.request)
                                        Screen.Report -> stringResource(R.string.report_incident)
                                        Screen.Profile -> stringResource(R.string.profile)
                                        else -> screen.route
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            },
                            alwaysShowLabel = true,
                            selected = selected,
                            onClick = {
                                if (screen == Screen.Home) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val startDestinationRoute = remember {
            if (!SessionManager.isSessionValid(context)) {
                Screen.VerificationGate.route
            } else {
                val cachedStatus = SessionManager.getCachedFarmerStatus(context)
                when (cachedStatus) {
                    "pending" -> Screen.ApplicationStatus.route
                    "not_submitted" -> Screen.VerificationGate.route
                    else -> "main"
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestinationRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Entry / Verification Gate Screen (Replaces old Login/Registration)
            composable(Screen.VerificationGate.route) {
                VerificationGateScreen(
                    userViewModel = userViewModel,
                    onNavigateToSubmission = {
                        navController.navigate(Screen.CredentialSubmission.route)
                    },
                    onNavigateToStatus = {
                        navController.navigate(Screen.ApplicationStatus.route) {
                            popUpTo(Screen.VerificationGate.route) { inclusive = true }
                        }
                    },
                    onNavigateToGuest = {
                        navController.navigate("main") {
                            popUpTo(Screen.VerificationGate.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route)
                    },
                    onNavigateToVerified = {
                        navController.navigate("main") {
                            popUpTo(Screen.VerificationGate.route) { inclusive = true }
                        }
                    }
                )
            }

            // 0. Login Screen (Email/Password Login)
            composable(Screen.Login.route) {
                LoginScreen(
                    userViewModel = userViewModel,
                    onLoginClick = {
                        navController.navigate("main") {
                            popUpTo(Screen.VerificationGate.route) { inclusive = true }
                        }
                    },
                    onSignupClick = {
                        navController.navigate(Screen.CredentialSubmission.route)
                    },
                    onForgotPasswordClick = {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                )
            }

            // Forgot Password Screen (Dual-Channel OTP)
            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onResetSuccess = {
                        navController.popBackStack()
                        Toast.makeText(context, "Password reset successfully. Please log in with your new password.", Toast.LENGTH_LONG).show()
                    }
                )
            }

            // 2. Onboarding / Credential Submission Screen
            composable(Screen.CredentialSubmission.route) {
                CredentialSubmissionScreen(
                    userViewModel = userViewModel,
                    onSubmissionSuccess = {
                        navController.navigate(Screen.ApplicationStatus.route) {
                            popUpTo(Screen.CredentialSubmission.route) { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate("main") {
                            popUpTo(Screen.CredentialSubmission.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 3. Application Status Screen (Pending DA Review)
            composable(Screen.ApplicationStatus.route) {
                ApplicationStatusScreen(
                    userViewModel = userViewModel,
                    onApprovedVerified = {
                        navController.navigate("main") {
                            popUpTo(Screen.ApplicationStatus.route) { inclusive = true }
                        }
                    },
                    onApprovedGuest = {
                        navController.navigate("main") {
                            popUpTo(Screen.ApplicationStatus.route) { inclusive = true }
                        }
                    },
                    onCheckApproval = {
                        navController.navigate(Screen.RealTimeApprovalResult.route)
                    },
                    onLogout = {
                        userViewModel.logout(context)
                        navController.navigate(Screen.VerificationGate.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // 3b. Real-Time Approval Result Screen
            composable(Screen.RealTimeApprovalResult.route) {
                RealTimeApprovalResultScreen(
                    userViewModel = userViewModel,
                    onProceedToLogin = {
                        navController.navigate("main") {
                            popUpTo(Screen.ApplicationStatus.route) { inclusive = true }
                        }
                    },
                    onResubmit = {
                        navController.navigate(Screen.ResubmitCredentials.route)
                    },
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        userViewModel.logout(context)
                        navController.navigate(Screen.VerificationGate.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // 4. Resubmit Credentials Screen (Guest correction flow)
            composable(Screen.ResubmitCredentials.route) {
                ResubmitCredentialsScreen(
                    userViewModel = userViewModel,
                    onResubmissionSuccess = {
                        navController.navigate(Screen.ApplicationStatus.route) {
                            popUpTo(Screen.ResubmitCredentials.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 5. Main Dashboard Graph (Wrapped conditionally for Guest or Verified)
            navigation(startDestination = Screen.Home.route, route = "main") {
                composable(Screen.Home.route) {
                    val homeContent: @Composable () -> Unit = {
                        HomeScreen(
                            userViewModel = userViewModel,
                            onInventoryClick = { navController.navigate(Screen.Inventory.route) },
                            onWeatherClick = { navController.navigate(Screen.Weather.route) },
                            onMarketsClick = { navController.navigate(Screen.Markets.route) },
                            onCommunityClick = { navController.navigate(Screen.Community.route) },
                            onStatusClick = { navController.navigate(Screen.Status.route) },
                            onStatusFilterClick = { filter -> navController.navigate(Screen.Status.route + "?filter=$filter") },
                            onFarmTaggingClick = { navController.navigate(Screen.FarmTagging.route) },
                            onAllFarmsMapClick = { navController.navigate(Screen.AllFarmsMap.route) },
                            onFeedbackClick = { navController.navigate(Screen.Feedback.route) },
                            onSupportClick = { navController.navigate(Screen.Support.route) },
                            onAiChatClick = { navController.navigate(Screen.AiChat.route) }
                        )
                    }

                    if (farmerStatus == "guest" || (!isVerified && role != "Admin")) {
                        GuestDashboardWrapper(
                            userViewModel = userViewModel,
                            onResubmitClick = { navController.navigate(Screen.ResubmitCredentials.route) },
                            content = homeContent
                        )
                    } else if (farmerStatus == "verified" || isVerified) {
                        VerifiedDashboardWrapper(
                            userViewModel = userViewModel,
                            content = homeContent
                        )
                    } else {
                        homeContent()
                    }
                }

                composable(Screen.Inventory.route) {
                    InventoryScreen(
                        onBack = { navController.popBackStack() },
                        onRequestResource = { itemName -> 
                            navController.navigate("${Screen.Request.route}?itemName=$itemName")
                        }
                    )
                }

                composable(Screen.Request.route) {
                    if ((farmerStatus == "guest" || !isVerified) && role != "Admin") {
                        UnverifiedFarmerGate(
                            onVerifyClick = { navController.navigate(Screen.ResubmitCredentials.route) },
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        RequestResourceScreen(
                            onBack = { navController.popBackStack() },
                            userViewModel = userViewModel,
                            preSelectedType = ""
                        )
                    }
                }

                composable(Screen.Request.route + "?itemName={itemName}") { backStackEntry ->
                    val itemName = backStackEntry.arguments?.getString("itemName") ?: ""
                    if ((farmerStatus == "guest" || !isVerified) && role != "Admin") {
                        UnverifiedFarmerGate(
                            onVerifyClick = { navController.navigate(Screen.ResubmitCredentials.route) },
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        RequestResourceScreen(
                            onBack = { navController.popBackStack() },
                            userViewModel = userViewModel,
                            preSelectedType = itemName
                        )
                    }
                }

                composable(Screen.Report.route) {
                    ReportIssueScreen(
                        onBack = { navController.popBackStack() },
                        userViewModel = userViewModel
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        userViewModel = userViewModel,
                        onBackClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onEditProfileClick = { navController.navigate(Screen.EditProfile.route) },
                        onSupportClick = { navController.navigate(Screen.Support.route) },
                        onFeedbackClick = { navController.navigate(Screen.Feedback.route) },
                        onVerificationClick = { 
                            if (farmerStatus == "guest") {
                                navController.navigate(Screen.ResubmitCredentials.route)
                            } else if (farmerStatus == "pending") {
                                navController.navigate(Screen.ApplicationStatus.route)
                            } else if (farmerStatus == "not_submitted") {
                                navController.navigate(Screen.CredentialSubmission.route)
                            } else {
                                navController.navigate(Screen.AccountVerification.route)
                            }
                        },
                        onTagFarmClick = { navController.navigate(Screen.FarmTagging.route) },
                        onAllFarmsMapClick = { navController.navigate(Screen.AllFarmsMap.route) },
                        onLogoutClick = {
                            userViewModel.logout(context)
                            navController.navigate(Screen.VerificationGate.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.AccountVerification.route) {
                    AccountVerificationScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.EditProfile.route) {
                    EditProfileScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }


                composable(Screen.Security.route) {
                    SecurityScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Support.route) {
                    SupportScreen(userViewModel = userViewModel, onBack = { navController.popBackStack() })
                }

                composable(Screen.AiChat.route) {
                    com.group7.agriserve.ui.chat.ChatScreen(
                        onBack = { navController.popBackStack() },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }

                composable(Screen.Feedback.route) {
                    FeedbackScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Admin.route) {
                    AdminDashboardScreen(onBack = { navController.popBackStack() })
                }

                composable(Screen.Weather.route) {
                    WeatherScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Markets.route) {
                    MarketsScreen(onBack = { navController.popBackStack() })
                }

                composable(Screen.Community.route) {
                    CommunityScreen(
                        onBack = { navController.popBackStack() },
                        userName = userViewModel.farmerName.value,
                        isUserVerified = userViewModel.isVerified.value
                    )
                }

                composable(
                    route = Screen.Status.route + "?filter={filter}",
                    arguments = listOf(
                        navArgument("filter") {
                            type = NavType.StringType
                            defaultValue = "All"
                        }
                    )
                ) { backStackEntry ->
                    val filter = backStackEntry.arguments?.getString("filter") ?: "All"
                    FarmerStatusScreen(
                        userViewModel = userViewModel,
                        initialFilter = filter,
                        onBack = { navController.popBackStack() },
                        onRequestClick = { navController.navigate(Screen.Request.route + "?itemName=") },
                        onReportClick = { navController.navigate(Screen.Report.route) }
                    )
                }

                composable(Screen.FarmTagging.route) {
                    FarmTaggingScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() },
                        onViewAllFarmsClick = { navController.navigate(Screen.AllFarmsMap.route) }
                    )
                }

                composable(Screen.AllFarmsMap.route) {
                    AllFarmsMapScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

            }
        }
    }
}
