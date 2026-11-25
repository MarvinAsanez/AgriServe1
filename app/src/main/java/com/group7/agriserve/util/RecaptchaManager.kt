package com.group7.agriserve.util

import android.app.Application
import android.util.Log
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Google Cloud reCAPTCHA Enterprise for Login, Signup, and OTP flows.
 *
 * Replace [SITE_KEY] with your Android reCAPTCHA Enterprise Site Key from Google Cloud Console.
 */
object RecaptchaManager {
    private const val TAG = "RecaptchaManager"

    const val SITE_KEY = "6LfYEzEuAAAAAFa4bJEKY8l1vd1lFhDFZWjnCHsM"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appRef: Application? = null

    @Volatile
    private var recaptchaClient: RecaptchaClient? = null

    fun isConfigured(): Boolean =
        SITE_KEY.isNotBlank() && SITE_KEY != "YOUR_ANDROID_RECAPTCHA_SITE_KEY"

    /**
     * Pre-initializes the reCAPTCHA Enterprise client on app startup.
     */
    fun init(application: Application) {
        appRef = application
        if (!isConfigured() || recaptchaClient != null) return

        scope.launch {
            getOrFetchClient()
        }
    }

    private suspend fun getOrFetchClient(): RecaptchaClient? {
        recaptchaClient?.let { return it }
        val app = appRef ?: return null
        if (!isConfigured()) return null

        return try {
            Recaptcha.fetchClient(app, SITE_KEY).also { client ->
                recaptchaClient = client
                Log.d(TAG, "reCAPTCHA Enterprise client initialized")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize reCAPTCHA client: ${e.message}")
            null
        }
    }

    /**
     * Executes a reCAPTCHA Enterprise action and returns the assessment token.
     * Returns empty string when [SITE_KEY] is not yet configured.
     */
    suspend fun executeAction(action: RecaptchaAction): Result<String> {
        if (!isConfigured()) {
            return Result.success("")
        }

        val client = withContext(Dispatchers.IO) { getOrFetchClient() }
            ?: return Result.failure(IllegalStateException("Security check unavailable. Please check your internet connection."))

        return client.execute(action)
    }

    /**
     * Executes reCAPTCHA Enterprise and verifies the token on the Firebase Cloud Function backend.
     */
    suspend fun verifyWithBackend(action: RecaptchaAction, actionName: String): Result<Unit> {
        if (!isConfigured()) {
            return Result.success(Unit)
        }

        val tokenResult = executeAction(action)
        val token = tokenResult.getOrElse { return Result.failure(it) }
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("Empty reCAPTCHA token returned."))
        }

        return try {
            val payload = hashMapOf(
                "token" to token,
                "action" to actionName
            )
            FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyRecaptchaToken")
                .call(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = (e as? FirebaseFunctionsException)?.message
                ?: e.message
                ?: "Security verification failed. Please try again."
            Result.failure(Exception(msg))
        }
    }
}
