package com.carbon.prolocker.feature.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.IntruderManager
import com.carbon.prolocker.core.service.FailedAttemptManager
import com.carbon.prolocker.core.service.LockSessionManager
import org.koin.android.ext.android.inject

class BiometricAuthActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun start(context: Context, packageName: String) {
            val intent = Intent(context, BiometricAuthActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)
        }
    }

    private val sessionManager: LockSessionManager by inject()
    private val eventLogManager: EventLogManager by inject()
    private val failedAttemptManager: FailedAttemptManager by inject()
    private val intruderManager: IntruderManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (targetPackage.isNotEmpty()) {
                        sessionManager.unlockApp(targetPackage)
                        eventLogManager.logEvent("UNLOCK_SUCCESS", packageName = targetPackage, details = "Unlocked via Biometric")
                        failedAttemptManager.reset()
                        intruderManager.stopAlarm()
                        LockService.dismiss(this@BiometricAuthActivity)
                    }
                    finish()
                    overridePendingTransition(0, 0)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    finish()
                    overridePendingTransition(0, 0)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
