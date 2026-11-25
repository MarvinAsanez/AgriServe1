package com.group7.agriserve.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages the Offline PIN and Fingerprint/Biometric Quick Lock.
 * Keeps the farmer's Firebase Auth session intact so queued offline
 * submissions can auto-sync when internet connectivity returns.
 */
object OfflineLockManager {
    private const val PREFS_NAME = "agriserve_offline_lock_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_IS_LOCKED = "is_locked"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPinEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false) && canUseBiometric(context)

    fun canUseBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context.applicationContext)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasAnyLockConfigured(context: Context): Boolean =
        isPinEnabled(context) || isBiometricEnabled(context)

    fun shouldRequireLockOnLaunch(context: Context): Boolean =
        hasAnyLockConfigured(context)

    fun isAppLocked(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IS_LOCKED, false)

    fun setAppLocked(context: Context, locked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_LOCKED, locked).apply()
    }

    fun setPin(context: Context, pin: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, salt)
        getPrefs(context).edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    fun disablePin(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_PIN_ENABLED, false)
            .putBoolean(KEY_IS_LOCKED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expectedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val actualHash = hashPin(pin, salt)
        val match = MessageDigest.isEqual(
            expectedHash.toByteArray(Charsets.UTF_8),
            actualHash.toByteArray(Charsets.UTF_8)
        )
        if (match) {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putBoolean(KEY_IS_LOCKED, false).apply()
        } else {
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        }
        return match
    }

    fun getFailedAttempts(context: Context): Int =
        getPrefs(context).getInt(KEY_FAILED_ATTEMPTS, 0)

    fun clearLockOnLogout(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
