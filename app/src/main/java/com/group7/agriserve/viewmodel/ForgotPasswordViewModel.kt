package com.group7.agriserve.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.group7.agriserve.data.api.AuthApi
import com.group7.agriserve.data.api.ResetPasswordRequest
import com.group7.agriserve.data.api.SendOtpRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class ForgotPasswordStep {
    INPUT_IDENTIFIER,
    VERIFY_OTP_AND_RESET,
    SUCCESS
}

class ForgotPasswordViewModel(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val authApi: AuthApi = AuthApi.getInstance()
) : ViewModel() {

    private val _step = MutableStateFlow(ForgotPasswordStep.INPUT_IDENTIFIER)
    val step: StateFlow<ForgotPasswordStep> = _step.asStateFlow()

    val identifier = MutableStateFlow("")
    val channel = MutableStateFlow("email") // "email" or "sms"
    val otp = MutableStateFlow("")
    val newPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _maskedDestination = MutableStateFlow("")
    val maskedDestination: StateFlow<String> = _maskedDestination.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _canResend = MutableStateFlow(true)
    val canResend: StateFlow<Boolean> = _canResend.asStateFlow()

    private var timerJob: Job? = null

    fun selectChannel(selectedChannel: String) {
        channel.value = selectedChannel
        _errorMessage.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }

    fun sendOtp(onSuccess: () -> Unit = {}) {
        val rawInput = identifier.value.trim()
        if (rawInput.isBlank()) {
            _errorMessage.value = if (channel.value == "sms") {
                "Please enter your Philippine mobile number (e.g. 09123456789)."
            } else {
                "Please enter your registered email address."
            }
            return
        }

        if (channel.value == "email" && (!rawInput.contains("@") || !rawInput.contains("."))) {
            _errorMessage.value = "Please enter a valid email address (e.g. farmer@agriserve.gov.ph)."
            return
        }

        if (channel.value == "sms") {
            val digits = rawInput.replace(Regex("\\D"), "")
            if (digits.length < 10) {
                _errorMessage.value = "Please enter a valid 11-digit Philippine mobile number starting with 09."
                return
            }
        }

        _isLoading.value = true
        _errorMessage.value = null
        _statusMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Primary: Call Cloud Function sendPasswordResetOtp
                val payload = hashMapOf(
                    "identifier" to rawInput,
                    "channel" to channel.value
                )

                var destinationMasked = ""
                try {
                    val result = functions.getHttpsCallable("sendPasswordResetOtp")
                        .call(payload)
                        .await()

                    val data = result.getData() as? Map<*, *>
                    destinationMasked = (data?.get("maskedDestination") as? String) ?: rawInput
                } catch (cfErr: Exception) {
                    if (cfErr is FirebaseFunctionsException) {
                        throw cfErr
                    }
                    // 2. Fallback to REST AuthApi only on network failure
                    val response = try {
                        authApi.sendOtp(
                            SendOtpRequest(
                                identifier = rawInput,
                                channel = channel.value
                            )
                        )
                    } catch (_: Exception) { null }

                    if (response?.isSuccessful == true && response.body()?.success == true) {
                        destinationMasked = rawInput
                    } else {
                        val errMsg = response?.body()?.message
                            ?: cfErr.message
                            ?: "Failed to send verification code. Please verify your account credentials."
                        throw Exception(errMsg)
                    }
                }

                _maskedDestination.value = destinationMasked
                _statusMessage.value = "Verification code successfully sent to $destinationMasked."
                _step.value = ForgotPasswordStep.VERIFY_OTP_AND_RESET
                startCountdownTimer(300)
                onSuccess()

            } catch (e: Exception) {
                val msg = (e as? FirebaseFunctionsException)?.message ?: e.message ?: "An unexpected error occurred."
                _errorMessage.value = msg.replace("INTERNAL: ", "").replace("INVALID_ARGUMENT: ", "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPassword(onSuccess: () -> Unit = {}) {
        val cleanOtp = otp.value.trim()
        val pass = newPassword.value
        val confirmPass = confirmPassword.value

        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            _errorMessage.value = "Please enter the 6-digit numeric verification code."
            return
        }

        if (pass.length < 6) {
            _errorMessage.value = "New password must be at least 6 characters long."
            return
        }

        if (pass != confirmPass) {
            _errorMessage.value = "New password and Confirm Password do not match."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
        _statusMessage.value = null

        viewModelScope.launch {
            try {
                val rawInput = identifier.value.trim()
                val payload = hashMapOf(
                    "identifier" to rawInput,
                    "otp" to cleanOtp,
                    "newPassword" to pass
                )

                try {
                    functions.getHttpsCallable("resetPasswordWithOtp")
                        .call(payload)
                        .await()
                } catch (cfErr: Exception) {
                    if (cfErr is FirebaseFunctionsException) {
                        throw cfErr
                    }
                    // Fallback to AuthApi REST only on network failure
                    val response = try {
                        authApi.resetPassword(
                            ResetPasswordRequest(
                                identifier = rawInput,
                                otp = cleanOtp,
                                newPassword = pass
                            )
                        )
                    } catch (_: Exception) { null }

                    if (response?.isSuccessful != true || response.body()?.success != true) {
                        val errMsg = response?.body()?.message
                            ?: cfErr.message
                            ?: "Failed to reset password. Please check your OTP code."
                        throw Exception(errMsg)
                    }
                }

                _step.value = ForgotPasswordStep.SUCCESS
                _statusMessage.value = "Password has been reset successfully!"
                timerJob?.cancel()
                onSuccess()

            } catch (e: Exception) {
                val msg = (e as? FirebaseFunctionsException)?.message ?: e.message ?: "Failed to reset password."
                _errorMessage.value = msg.replace("INTERNAL: ", "").replace("INVALID_ARGUMENT: ", "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resendOtp() {
        if (!_canResend.value) return
        sendOtp()
    }

    private fun startCountdownTimer(seconds: Int) {
        timerJob?.cancel()
        _countdownSeconds.value = seconds
        _canResend.value = false

        timerJob = viewModelScope.launch {
            var current = seconds
            while (current > 0) {
                delay(1000)
                current--
                _countdownSeconds.value = current
                if (current <= seconds - 30) {
                    _canResend.value = true
                }
            }
            _canResend.value = true
        }
    }

    fun formattedCountdown(): String {
        val totalSec = _countdownSeconds.value
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d", mins, secs)
    }

    fun resetToStart() {
        timerJob?.cancel()
        _step.value = ForgotPasswordStep.INPUT_IDENTIFIER
        otp.value = ""
        newPassword.value = ""
        confirmPassword.value = ""
        _errorMessage.value = null
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

