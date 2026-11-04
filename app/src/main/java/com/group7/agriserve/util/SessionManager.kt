package com.group7.agriserve.util

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/**
 * Manages user login session persistence and duration.
 * Keeps users logged in for 7 days (1 week) to prevent repeated login prompts,
 * enabling seamless offline usage of the AgriServe app.
 */
object SessionManager {
    private const val PREF_NAME = "agriserve_session_prefs"
    private const val KEY_LAST_LOGIN_TIME = "last_login_timestamp"
    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_USER_EMAIL = "session_user_email"
    private const val KEY_FARMER_NAME = "session_farmer_name"
    private const val KEY_USER_ROLE = "session_user_role"
    private const val KEY_IS_VERIFIED = "session_is_verified"
    private const val KEY_ACCOUNT_STATUS = "session_account_status"
    private const val KEY_FARMER_STATUS = "session_farmer_status"
    private const val KEY_THEME_MODE = "session_theme_mode"
    private const val KEY_APP_LANGUAGE = "session_app_language"

    // 7 Days session validity window: 7 * 24 * 60 * 60 * 1000 ms
    const val SESSION_VALIDITY_MS: Long = 7L * 24 * 60 * 60 * 1000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records login timestamp and caches essential farmer data for offline use.
     */
    fun saveLoginSession(
        context: Context,
        userId: String,
        email: String,
        name: String = "",
        role: String = "User",
        isVerified: Boolean = false,
        accountStatus: String = "Pending",
        farmerStatus: String = ""
    ) {
        val resolvedFarmerStatus = when {
            farmerStatus.isNotBlank() -> farmerStatus
            isVerified -> "verified"
            accountStatus.equals("Approved", ignoreCase = true) || accountStatus.equals("Verified", ignoreCase = true) -> "verified"
            accountStatus.equals("Rejected", ignoreCase = true) || accountStatus.equals("Guest", ignoreCase = true) -> "guest"
            accountStatus.equals("Not Submitted", ignoreCase = true) || accountStatus.equals("not_submitted", ignoreCase = true) -> "not_submitted"
            accountStatus.equals("Pending", ignoreCase = true) -> "pending"
            else -> "guest"
        }
        getPrefs(context).edit().apply {
            putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            if (name.isNotBlank() && name != "Loading..." && name != "Guest") {
                putString(KEY_FARMER_NAME, name)
            }
            putString(KEY_USER_ROLE, role)
            putBoolean(KEY_IS_VERIFIED, isVerified)
            putString(KEY_ACCOUNT_STATUS, accountStatus)
            putString(KEY_FARMER_STATUS, resolvedFarmerStatus)
            apply()
        }
    }

    /**
     * Updates locally cached profile information for instant cold-start loading offline.
     */
    fun updateCachedProfile(
        context: Context,
        name: String,
        role: String,
        isVerified: Boolean,
        status: String,
        farmerStatus: String = ""
    ) {
        val resolvedFarmerStatus = when {
            farmerStatus.isNotBlank() -> farmerStatus
            isVerified -> "verified"
            status.equals("Approved", ignoreCase = true) || status.equals("Verified", ignoreCase = true) -> "verified"
            status.equals("Rejected", ignoreCase = true) || status.equals("Guest", ignoreCase = true) -> "guest"
            status.equals("Not Submitted", ignoreCase = true) || status.equals("not_submitted", ignoreCase = true) -> "not_submitted"
            status.equals("Pending", ignoreCase = true) -> "pending"
            else -> "guest"
        }
        getPrefs(context).edit().apply {
            if (name.isNotBlank() && name != "Loading..." && name != "Guest") {
                putString(KEY_FARMER_NAME, name)
            }
            if (role.isNotBlank()) putString(KEY_USER_ROLE, role)
            putBoolean(KEY_IS_VERIFIED, isVerified)
            if (status.isNotBlank()) putString(KEY_ACCOUNT_STATUS, status)
            putString(KEY_FARMER_STATUS, resolvedFarmerStatus)
            apply()
        }
    }

    /**
     * Retrieves stored farmer/user ID from active session
     */
    fun getSavedUserId(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ID, null)
    }

    /**
     * Retrieves stored farmer email from active session
     */
    fun getSavedUserEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    /**
     * Checks if current user is logged in (via Firebase Auth or saved session) and within 7-day window.
     */
    fun isSessionValid(context: Context): Boolean {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val prefs = getPrefs(context)
        val savedUserId = prefs.getString(KEY_USER_ID, null)
        val savedEmail = prefs.getString(KEY_USER_EMAIL, null)

        // If active Firebase Auth user is present and not yet cached, save session
        if (currentUser != null && (savedUserId != currentUser.uid || prefs.getLong(KEY_LAST_LOGIN_TIME, 0L) == 0L)) {
            val existingFarmerStatus = prefs.getString(KEY_FARMER_STATUS, "") ?: ""
            val existingAccountStatus = prefs.getString(KEY_ACCOUNT_STATUS, "Pending") ?: "Pending"
            val existingIsVerified = prefs.getBoolean(KEY_IS_VERIFIED, false)
            saveLoginSession(
                context = context,
                userId = currentUser.uid,
                email = currentUser.email ?: "",
                isVerified = existingIsVerified,
                accountStatus = existingAccountStatus,
                farmerStatus = existingFarmerStatus
            )
            return true
        }

        // If neither user ID nor email is stored, session is invalid
        if (savedUserId.isNullOrBlank() && savedEmail.isNullOrBlank()) {
            return false
        }

        val lastLogin = prefs.getLong(KEY_LAST_LOGIN_TIME, 0L)
        if (lastLogin == 0L) return false

        val elapsed = System.currentTimeMillis() - lastLogin

        // Check if within the 7-day period
        if (elapsed in 0..SESSION_VALIDITY_MS) {
            return true
        }

        // If offline and session just expired, allow graceful extension rather than sudden lockout in the field
        if (!NetworkUtils.isOnline(context)) {
            return true
        }

        // Expired after 7 days when online: clear session and sign out
        clearSession(context)
        try {
            auth.signOut()
        } catch (_: Exception) {
            // ignore
        }
        return false
    }

    /**
     * Refreshes the 7-day session window on active online use.
     */
    fun touchSession(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_LAST_LOGIN_TIME)) {
            prefs.edit().putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Clears all session information upon logout, while preserving user theme & language preferences.
     */
    fun clearSession(context: Context) {
        val currentTheme = getCachedThemeMode(context)
        val currentLang = getCachedAppLanguage(context)
        getPrefs(context).edit().clear().apply()
        saveThemeMode(context, currentTheme)
        saveAppLanguage(context, currentLang)
    }

    fun saveThemeMode(context: Context, mode: String) {
        if (mode in listOf("system", "light", "dark")) {
            getPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
        }
    }

    fun getCachedThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun saveAppLanguage(context: Context, language: String) {
        val norm = if (language == "tl") "fil" else language
        getPrefs(context).edit().putString(KEY_APP_LANGUAGE, norm).apply()
    }

    fun getCachedAppLanguage(context: Context): String {
        val stored = getPrefs(context).getString(KEY_APP_LANGUAGE, null)
        if (!stored.isNullOrBlank()) return stored
        val systemTag = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"
        return if (systemTag == "tl") "fil" else systemTag
    }

    fun getCachedFarmerName(context: Context): String {
        return getPrefs(context).getString(KEY_FARMER_NAME, "") ?: ""
    }

    fun getCachedUserRole(context: Context): String {
        return getPrefs(context).getString(KEY_USER_ROLE, "User") ?: "User"
    }

    fun getCachedIsVerified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_VERIFIED, false)
    }

    fun getCachedAccountStatus(context: Context): String {
        return getPrefs(context).getString(KEY_ACCOUNT_STATUS, "Pending") ?: "Pending"
    }

    fun getCachedFarmerStatus(context: Context): String {
        val prefs = getPrefs(context)
        val stored = prefs.getString(KEY_FARMER_STATUS, null)
        if (!stored.isNullOrBlank()) return stored
        if (getCachedIsVerified(context)) return "verified"
        val accStatus = getCachedAccountStatus(context)
        return when (accStatus.lowercase()) {
            "approved", "verified" -> "verified"
            "rejected", "guest" -> "guest"
            "not submitted", "not_submitted" -> "not_submitted"
            "pending" -> "pending"
            else -> if (isSessionValid(context)) "guest" else "not_submitted"
        }
    }

    /**
     * Returns how many days remain in the 7-day login window.
     */
    fun getRemainingDays(context: Context): Int {
        val lastLogin = getPrefs(context).getLong(KEY_LAST_LOGIN_TIME, 0L)
        if (lastLogin == 0L) return 7
        val elapsed = System.currentTimeMillis() - lastLogin
        val remaining = SESSION_VALIDITY_MS - elapsed
        return if (remaining > 0) ((remaining / (24 * 60 * 60 * 1000L)) + 1).toInt().coerceAtMost(7) else 0
    }
}
