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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerTheme
import com.carbon.prolocker.core.ui.components.PatternLockView
import com.carbon.prolocker.core.ui.components.PinLockView
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppEntryLockActivity : ComponentActivity() {
    private val viewModel: EntryLockViewModel by viewModel()

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
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            ProLockerTheme {
                val lockType by viewModel.lockType.collectAsState()
                val isError by viewModel.isError.collectAsState()
                val unlocked by viewModel.unlocked.collectAsState()
                val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
                val hidePatternPath by viewModel.hidePatternPath.collectAsState()
                val recoveryQuestion by viewModel.recoveryQuestion.collectAsState()
                val failedAttempts by viewModel.failedAttempts.collectAsState()
                val threshold by viewModel.threshold.collectAsState()

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

@Composable
fun EntryLockContent(
    lockType: String?,
    isError: Boolean,
    failedAttempts: Int,
    threshold: Int,
    recoveryQuestion: String?,
    hidePatternPath: Boolean,
    vibrationEnabled: Boolean,
    onPatternComplete: (List<Int>) -> Unit,
    onPinComplete: (String) -> Unit,
    onErrorReset: () -> Unit,
    onVerifyRecoveryAnswer: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var showForgotDialog by remember { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0f172a))
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
                    tint = Color.White,
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

