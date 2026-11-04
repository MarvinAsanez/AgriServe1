package com.group7.agriserve.ui.navigation

sealed class Screen(val route: String) {
    // New Onboarding & Verification Routes
    object VerificationGate : Screen("verification_gate")
    object CredentialSubmission : Screen("credential_submission")
    object ApplicationStatus : Screen("application_status")
    object GuestDashboard : Screen("guest_dashboard")
    object ResubmitCredentials : Screen("resubmit_credentials")
    object VerifiedDashboard : Screen("verified_dashboard")
    object RealTimeApprovalResult : Screen("real_time_approval_result")

    // Legacy Auth Routes mapped to Verification Gate
    object Login : Screen("login")
    object Signup : Screen("credential_submission")
    object AccountVerification : Screen("account_verification")
    object Verification : Screen("account_verification")

    // Main App Functionality
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
    object AllFarmsMap : Screen("all_farms_map")
    object Feedback : Screen("feedback")
    object AiChat : Screen("ai_chat")
}
