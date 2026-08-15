package com.carbon.prolocker.core.security

import android.content.Context
import androidx.biometric.BiometricManager

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED
}

object BiometricHelper {

    fun getBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return getBiometricStatus(context) == BiometricStatus.AVAILABLE
    }

    fun showBiometricPrompt(
        activity: androidx.fragment.app.FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == androidx.biometric.BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancel?.invoke()
                } else {
                    onError?.invoke(errString.toString())
                }
            }
        }

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(com.carbon.prolocker.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
