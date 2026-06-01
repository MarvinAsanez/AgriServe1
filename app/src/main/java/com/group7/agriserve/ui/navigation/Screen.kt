package com.group7.agriserve.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Verification : Screen("verification")
    object LoginFaceVerify : Screen("login_face_verify")
    object Home : Screen("home")
    object Inventory : Screen("inventory")
    object Request : Screen("request")
    object Report : Screen("report")
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Security : Screen("security")
    object Support : Screen("support")
    object Admin : Screen("admin")
    object Weather : Screen("weather")
    object Markets : Screen("markets")
    object Community : Screen("community")
    object Status : Screen("status")
    object FarmTagging : Screen("farm_tagging")
}
