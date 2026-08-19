package com.carbon.prolocker.feature.entrylock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.carbon.prolocker.R
import com.carbon.prolocker.core.security.BiometricHelper
import com.carbon.prolocker.core.theme.AppTypography
import com.carbon.prolocker.core.theme.ProLockerBackground
import com.carbon.prolocker.core.theme.ProLockerOnBackground
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.core.ui.components.PatternLockView
import com.carbon.prolocker.core.ui.components.PinLockView
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppEntryLockActivity : FragmentActivity() {
    private val viewModel: EntryLockViewModel by viewModel()
    private val preferencesRepository: com.carbon.prolocker.core.datastore.PreferencesRepository by inject()
    private val languageManager: com.carbon.prolocker.core.language.LanguageManager by inject()

    companion object {
        @Volatile
        private var needsAuthentication = true

        fun markNeedsAuthentication() {
            needsAuthentication = true
        }

        fun markAuthenticated() {
            needsAuthentication = false
        }

        fun requiresAuthentication(): Boolean = needsAuthentication
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.carbon.prolocker.BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            val currentPrefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val isDarkMode = currentPrefs?.isDarkMode ?: true
            val language = languageManager.getEffectiveLanguageTag(currentPrefs?.language)
            val layoutDirection = languageManager.getLayoutDirection(language)
            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val localizedContext = androidx.compose.runtime.remember(baseContext, language) {
                languageManager.createLocalizedContext(baseContext, language)
            }

            ProLockerTheme(useDarkTheme = isDarkMode) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalContext provides localizedContext,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides this@AppEntryLockActivity,
                    androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
                ) {
                    val lockType by viewModel.lockType.collectAsState()
                    val isError by viewModel.isError.collectAsState()
                    val unlocked by viewModel.unlocked.collectAsState()
                    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
                    val hidePatternPath by viewModel.hidePatternPath.collectAsState()
                    val recoveryQuestion by viewModel.recoveryQuestion.collectAsState()
                    val failedAttempts by viewModel.failedAttempts.collectAsState()
                    val threshold by viewModel.threshold.collectAsState()
                    val fingerprintUnlockEnabled by viewModel.fingerprintUnlockEnabled.collectAsState()

                    LaunchedEffect(unlocked) {
                        if (unlocked) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }

                    EntryLockContent(
                        lockType = lockType,
                        isError = isError,
                        failedAttempts = failedAttempts,
                        threshold = threshold,
                        recoveryQuestion = recoveryQuestion,
                        hidePatternPath = hidePatternPath,
                        vibrationEnabled = vibrationEnabled,
                        fingerprintUnlockEnabled = fingerprintUnlockEnabled,
                        onFingerprintClick = {
                            BiometricHelper.showBiometricPrompt(
                                activity = this@AppEntryLockActivity,
                                title = getString(R.string.biometric_prompt_title),
                                subtitle = getString(R.string.biometric_prompt_subtitle),
                                onSuccess = { viewModel.onBiometricSuccess() }
                            )
                        },
                        onPatternComplete = { viewModel.verifyPattern(it) },
                        onPinComplete = { viewModel.verifyPin(it) },
                        onErrorReset = { viewModel.resetError() },
                        onVerifyRecoveryAnswer = { answer, onResult ->
                            viewModel.verifyRecoveryAnswer(answer, onResult)
                        },
                        onBack = {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EntryLockContent(
    lockType: String?,
    isError: Boolean,
    failedAttempts: Int,
    threshold: Int,
    recoveryQuestion: String?,
    hidePatternPath: Boolean,
    vibrationEnabled: Boolean,
    fingerprintUnlockEnabled: Boolean = false,
    onFingerprintClick: () -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit,
    onPinComplete: (String) -> Unit,
    onErrorReset: () -> Unit,
    onVerifyRecoveryAnswer: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var showForgotDialog by remember { mutableStateOf(false) }
    var isFingerprintMode by remember(fingerprintUnlockEnabled) { mutableStateOf(fingerprintUnlockEnabled) }
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProLockerBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = ProLockerPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                val currentLockTypeRaw = lockType?.lowercase() ?: "credential"
                val credentialStr = when (currentLockTypeRaw) {
                    "pattern" -> stringResource(R.string.pattern_text)
                    "pin" -> stringResource(R.string.pin_text)
                    else -> stringResource(R.string.credential_text)
                }
                Text(
                    text = if (isError) stringResource(
                        R.string.incorrect_credential_prefix,
                        credentialStr
                    ) else stringResource(
                        R.string.enter_credential_prefix,
                        credentialStr
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isError) Color.Red else Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x = offsetX.value.dp)
                )
                LaunchedEffect(isError) {
                    if (isError) {
                        if (vibrationEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        for (i in 0..5) {
                            offsetX.animateTo(
                                targetValue = if (i % 2 == 0) 15f else -15f,
                                animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                            )
                        }
                        offsetX.animateTo(0f)
                        delay(1000)
                        onErrorReset()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFingerprintMode) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(
                            onClick = onFingerprintClick,
                            shape = CircleShape,
                            color = ProLockerPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.5.dp, ProLockerPrimary),
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = stringResource(R.string.fingerprint_unlock),
                                    tint = ProLockerPrimary,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.touch_to_unlock),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = ProLockerOnBackground.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        OutlinedButton(
                            onClick = { isFingerprintMode = false },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, ProLockerPrimary.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = ProLockerPrimary
                            ),
                            modifier = Modifier.height(36.dp).padding(horizontal = 24.dp)
                        ) {
                            val switchText = if (lockType == "PIN") stringResource(R.string.use_pin) else stringResource(R.string.use_pattern)
                            Text(
                                text = switchText,
                                style = AppTypography.labelMedium,
                                color = ProLockerPrimary
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (lockType) {
                            "PATTERN" -> {
                                PatternLockView(
                                    onPatternDrawn = onPatternComplete,
                                    onInteractionStarted = {},
                                    isError = isError,
                                    vibrationEnabled = vibrationEnabled,
                                    hidePatternPath = hidePatternPath
                                )
                            }
                            "PIN" -> {
                                PinLockView(
                                    isError = isError,
                                    onPinComplete = onPinComplete,
                                    vibrationEnabled = vibrationEnabled
                                )
                            }
                        }

                        if (fingerprintUnlockEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { isFingerprintMode = true },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.5.dp, ProLockerPrimary.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = ProLockerPrimary
                                ),
                                modifier = Modifier.height(36.dp).padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.use_fingerprint),
                                    style = AppTypography.labelMedium,
                                    color = ProLockerPrimary
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (failedAttempts >= threshold && recoveryQuestion != null) {
                    TextButton(onClick = { showForgotDialog = true }) {
                        Text(
                            stringResource(R.string.forgot_password),
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (showForgotDialog) {
            var recoveryAnswer by remember { mutableStateOf("") }
            var recoveryError by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = {
                    showForgotDialog = false
                    recoveryError = false
                },
                title = { Text(stringResource(R.string.recover_lock)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.question_prefix, recoveryQuestion ?: ""),
                            style = com.carbon.prolocker.core.theme.AppTypography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = recoveryAnswer,
                            onValueChange = {
                                recoveryAnswer = it
                                recoveryError = false
                            },
                            label = { Text(stringResource(R.string.answer)) },
                            isError = recoveryError,
                            singleLine = true
                        )
                        if (recoveryError) {
                            Text(
                                stringResource(R.string.incorrect_answer),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (recoveryAnswer.isBlank()) {
                            recoveryError = true
                        } else {
                            onVerifyRecoveryAnswer(recoveryAnswer) { success ->
                                if (success) {
                                    showForgotDialog = false
                                } else {
                                    recoveryError = true
                                }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.submit))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

