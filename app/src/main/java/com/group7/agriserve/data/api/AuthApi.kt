package com.group7.agriserve.data.api

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class SendOtpRequest(
    val identifier: String,
    val channel: String // "email" or "sms"
)

data class SendOtpResponse(
    val success: Boolean = false,
    val message: String = "",
    val channel: String? = null,
    val expiresInSeconds: Int? = null
)

data class VerifyOtpRequest(
    val identifier: String,
    val otp: String
)

data class VerifyOtpResponse(
    val success: Boolean = false,
    val message: String = ""
)

/**
 * Retrofit Interface for AgriServe Backend Authentication & Dual-Channel OTP.
 */
interface AuthApi {

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    companion object {
        // Primary LAN IP for physical device testing; secondary for Android Emulator loopback
        const val DEFAULT_LAN_URL = "http://192.168.100.7:5000/"
        const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:5000/"

        // Default to the host PC LAN IP so physical devices work out-of-the-box
        var baseUrl: String = DEFAULT_LAN_URL

        private var instance: AuthApi? = null

        fun create(url: String = baseUrl): AuthApi {
            val cleanUrl = if (url.endsWith("/")) url else "$url/"
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi::class.java)
        }

        fun getInstance(): AuthApi {
            return instance ?: synchronized(this) {
                instance ?: create(baseUrl).also { instance = it }
            }
        }

        fun setBaseUrl(newUrl: String): AuthApi {
            val trimmed = newUrl.trim()
            val formatted = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            val finalUrl = if (formatted.endsWith("/")) formatted else "$formatted/"
            baseUrl = finalUrl
            val newApi = create(finalUrl)
            instance = newApi
            return newApi
        }
    }
}
