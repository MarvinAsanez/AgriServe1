package com.group7.agriserve.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages the Offline PIN and Fingerprint/Biometric Quick Lock.
 * Settings (PIN hash/salt & biometric toggle) are persisted per-user across logouts
 * so farmers only set them up once and do not have to reconfigure them on every login.
 */
object OfflineLockManager {
    private const val PREFS_NAME = "agriserve_offline_lock_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_IS_LOCKED = "is_locked"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LEGACY_MIGRATED = "legacy_migrated_uid"

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun resolveUserId(context: Context): String? {
        val authUid = try {
            FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return authUid ?: SessionManager.getSavedUserId(context)?.takeIf { it.isNotBlank() }
    }

    private fun scopedKey(uid: String?, baseKey: String): String =
        if (!uid.isNullOrBlank()) "u_${uid}_$baseKey" else baseKey

    /**
     * One-time migration for any existing unprefixed lock config into the current user's namespace.
     */
    private fun ensureLegacyMigrated(context: Context, prefs: SharedPreferences, uid: String?) {
        if (uid.isNullOrBlank()) return
        val hasScopedPin = prefs.contains(scopedKey(uid, KEY_PIN_ENABLED)) || prefs.contains(scopedKey(uid, KEY_PIN_HASH))
        val hasScopedBio = prefs.contains(scopedKey(uid, KEY_BIOMETRIC_ENABLED))
        if (hasScopedPin || hasScopedBio) return

        val legacyHash = prefs.getString(KEY_PIN_HASH, null)
        val legacySalt = prefs.getString(KEY_PIN_SALT, null)
        val legacyPinEnabled = prefs.getBoolean(KEY_PIN_ENABLED, false)
        val legacyBioEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        val alreadyMigratedTo = prefs.getString(KEY_LEGACY_MIGRATED, null)

        if (alreadyMigratedTo == null && (legacyPinEnabled || legacyBioEnabled)) {
            prefs.edit()
                .putString(scopedKey(uid, KEY_PIN_HASH), legacyHash)
                .putString(scopedKey(uid, KEY_PIN_SALT), legacySalt)
                .putBoolean(scopedKey(uid, KEY_PIN_ENABLED), legacyPinEnabled)
                .putBoolean(scopedKey(uid, KEY_BIOMETRIC_ENABLED), legacyBioEnabled)
                .putString(KEY_LEGACY_MIGRATED, uid)
                .apply()
        }
    }

    fun isPinEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        ensureLegacyMigrated(context, prefs, uid)
        if (uid.isNullOrBlank()) return false
        return prefs.getBoolean(scopedKey(uid, KEY_PIN_ENABLED), false) &&
            !prefs.getString(scopedKey(uid, KEY_PIN_HASH), null).isNullOrBlank()
    }

    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        ensureLegacyMigrated(context, prefs, uid)
        if (uid.isNullOrBlank()) return false
        return prefs.getBoolean(scopedKey(uid, KEY_BIOMETRIC_ENABLED), false) && canUseBiometric(context)
    }

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

    fun isAppLocked(context: Context): Boolean {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context) ?: return false
        return prefs.getBoolean(scopedKey(uid, KEY_IS_LOCKED), false)
    }

    fun setAppLocked(context: Context, locked: Boolean) {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context) ?: return
        prefs.edit().putBoolean(scopedKey(uid, KEY_IS_LOCKED), locked).apply()
    }

    fun setPin(context: Context, pin: String) {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(scopedKey(uid, KEY_PIN_SALT), salt)
            .putString(scopedKey(uid, KEY_PIN_HASH), hash)
            .putBoolean(scopedKey(uid, KEY_PIN_ENABLED), true)
            .putInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0)
            .apply()
    }

    fun disablePin(context: Context) {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        prefs.edit()
            .remove(scopedKey(uid, KEY_PIN_HASH))
            .remove(scopedKey(uid, KEY_PIN_SALT))
            .putBoolean(scopedKey(uid, KEY_PIN_ENABLED), false)
            .putBoolean(scopedKey(uid, KEY_IS_LOCKED), false)
            .putInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0)
            .apply()
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        prefs.edit().putBoolean(scopedKey(uid, KEY_BIOMETRIC_ENABLED), enabled).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        ensureLegacyMigrated(context, prefs, uid)
        val salt = prefs.getString(scopedKey(uid, KEY_PIN_SALT), null) ?: return false
        val expectedHash = prefs.getString(scopedKey(uid, KEY_PIN_HASH), null) ?: return false
        val actualHash = hashPin(pin, salt)
        val match = MessageDigest.isEqual(
            expectedHash.toByteArray(Charsets.UTF_8),
            actualHash.toByteArray(Charsets.UTF_8)
        )
        if (match) {
            prefs.edit()
                .putInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0)
                .putBoolean(scopedKey(uid, KEY_IS_LOCKED), false)
                .apply()
        } else {
            val attempts = prefs.getInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0) + 1
            prefs.edit().putInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), attempts).apply()
        }
        return match
    }

    fun getFailedAttempts(context: Context): Int {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        return prefs.getInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0)
    }

    /**
     * Resets only the active lock state on explicit sign-out while preserving the user's
     * configured Offline PIN and Biometric unlock preference for future logins.
     */
    fun clearLockOnLogout(context: Context) {
        val prefs = getPrefs(context)
        val uid = resolveUserId(context)
        val editor = prefs.edit()
            .putBoolean(KEY_IS_LOCKED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
        if (!uid.isNullOrBlank()) {
            editor
                .putBoolean(scopedKey(uid, KEY_IS_LOCKED), false)
                .putInt(scopedKey(uid, KEY_FAILED_ATTEMPTS), 0)
        }
        editor.apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
