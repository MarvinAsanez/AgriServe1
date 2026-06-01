package com.group7.agriserve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.group7.agriserve.ui.navigation.Screen
import com.group7.agriserve.ui.screens.AccountVerificationScreen
import com.group7.agriserve.ui.screens.AdminDashboardScreen
import com.group7.agriserve.ui.screens.CommunityScreen
import com.group7.agriserve.ui.screens.EditProfileScreen
import com.group7.agriserve.ui.screens.FarmTaggingScreen
import com.group7.agriserve.ui.screens.FarmerStatusScreen
import com.group7.agriserve.ui.screens.HomeScreen
import com.group7.agriserve.ui.screens.InventoryScreen
import com.group7.agriserve.ui.screens.LoginScreen
import com.group7.agriserve.ui.screens.MarketsScreen
import com.group7.agriserve.ui.screens.PendingApprovalScreen
import com.group7.agriserve.ui.screens.ProfileScreen
import com.group7.agriserve.ui.screens.ReportIssueScreen
import com.group7.agriserve.ui.screens.RequestResourceScreen
import com.group7.agriserve.ui.screens.SecurityScreen
import com.group7.agriserve.ui.screens.SignupScreen
import com.group7.agriserve.ui.screens.SupportScreen
import com.group7.agriserve.ui.screens.WeatherScreen
import com.group7.agriserve.ui.theme.AgriServeTheme
import com.group7.agriserve.viewmodel.UserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgriServeTheme {
                AgriServeApp()
            }
        }
    }
}

@Composable
fun AgriServeApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val userViewModel: UserViewModel = viewModel()
    
    val isVerified by userViewModel.isVerified
    val role by userViewModel.role
    val farmerName by userViewModel.farmerName
    val accountStatus by userViewModel.accountStatus
    val currentUser = FirebaseAuth.getInstance().currentUser

    // Redirection logic: If logged in as User but not verified, force them to the PendingApprovalScreen
    LaunchedEffect(currentUser, isVerified, farmerName, role, accountStatus) {
        if (currentUser != null && farmerName != "Loading..." && !isVerified && role == "User") {
            val currentRoute = currentDestination?.route
            if (currentRoute != "pending_approval" && 
                currentRoute != Screen.Login.route &&
                currentRoute != "auth" &&
                currentRoute != "signup_flow" &&
                currentRoute != Screen.Signup.route &&
                currentRoute != "account_verification"
            ) {
                navController.navigate("pending_approval") {
                    popUpTo("main") { inclusive = true }
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

    // Only show bottom bar if on a main screen AND the user is either verified OR an admin
    val showBottomBar = mainScreens.any { screen -> 
        currentDestination?.hierarchy?.any { it.route == screen.route } == true 
    } && (isVerified || role == "Admin")

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
                            label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "auth",
            modifier = Modifier.padding(innerPadding)
        ) {
            navigation(startDestination = Screen.Login.route, route = "auth") {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginClick = { 
                            userViewModel.fetchUserProfile()
                            navController.navigate("main") {
                                popUpTo("auth") { inclusive = true }
                            }
                        },
                        onSignupClick = { navController.navigate(Screen.Signup.route) }
                    )
                }
                navigation(startDestination = Screen.Signup.route, route = "signup_flow") {
                    composable(Screen.Signup.route) {
                        SignupScreen(
                            onSignupComplete = { 
                                navController.navigate(Screen.Login.route) {
                                    popUpTo("signup_flow") { inclusive = true }
                                }
                            },
                            onBackToLogin = { navController.popBackStack() }
                        )
                    }
                }
            }

            composable("pending_approval") {
                PendingApprovalScreen(
                    status = accountStatus,
                    onApprovalDetected = {
                        navController.navigate("main") {
                            popUpTo("pending_approval") { inclusive = true }
                        }
                    },
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            navigation(startDestination = Screen.Home.route, route = "main") {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onInventoryClick = { navController.navigate(Screen.Inventory.route) },
                        onWeatherClick = { navController.navigate(Screen.Weather.route) },
                        onMarketsClick = { navController.navigate(Screen.Markets.route) },
                        onCommunityClick = { navController.navigate(Screen.Community.route) },
                        onStatusClick = { navController.navigate(Screen.Status.route) },
                        onFarmTaggingClick = { navController.navigate(Screen.FarmTagging.route) }
                    )
                }
                composable(Screen.Inventory.route) {
                    InventoryScreen(
                        onBack = { navController.popBackStack() },
                        onRequestResource = { itemName -> 
                            navController.navigate("${Screen.Request.route}?itemName=$itemName")
                        }
                    )
                }
                composable(Screen.Request.route + "?itemName={itemName}") { backStackEntry ->
                    val itemName = backStackEntry.arguments?.getString("itemName") ?: ""
                    RequestResourceScreen(
                        onBack = { navController.popBackStack() },
                        userViewModel = userViewModel,
                        preSelectedType = itemName
                    )
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
                        onEditProfileClick = { navController.navigate(Screen.EditProfile.route) },
                        onSecurityClick = { navController.navigate(Screen.Security.route) },
                        onSupportClick = { navController.navigate(Screen.Support.route) },
                        onVerificationClick = { navController.navigate("account_verification") },
                        onTagFarmClick = { navController.navigate(Screen.FarmTagging.route) },
                        onLogoutClick = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("auth") {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    )
                }
                composable("account_verification") {
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
                    SupportScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Admin.route) {
                    AdminDashboardScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Weather.route) {
                    WeatherScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Markets.route) {
                    MarketsScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Community.route) {
                    CommunityScreen(
                        onBack = { navController.popBackStack() },
                        userName = userViewModel.farmerName.value
                    )
                }
                composable(Screen.Status.route) {
                    FarmerStatusScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.FarmTagging.route) {
                    FarmTaggingScreen(
                        userViewModel = userViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
