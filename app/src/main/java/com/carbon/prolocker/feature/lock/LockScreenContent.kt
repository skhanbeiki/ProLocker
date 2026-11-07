package com.carbon.prolocker.feature.lock

import androidx.compose.foundation.layout.BoxWithConstraints
import com.carbon.prolocker.core.ui.isTablet
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.LanguageManager
import com.carbon.prolocker.core.theme.AppTypography
import com.carbon.prolocker.core.theme.ProLockerCardBorder
import com.carbon.prolocker.core.theme.ProLockerError
import com.carbon.prolocker.core.theme.ProLockerOnBackground
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSurfaceVariant
import com.carbon.prolocker.core.ui.components.PatternLockView
import com.carbon.prolocker.core.ui.components.PinLockView
import com.carbon.prolocker.core.utils.toSafeBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreenContent(
    packageName: String,
    lockType: String?,
    selectedBackgroundUrl: String?,
    isError: Boolean,
    failedAttemptsCount: Int,
    threshold: Int,
    recoveryQuestion: String?,
    hidePatternPath: Boolean,
    vibrationEnabled: Boolean,
    lockScreenAdPlace: String,
    adManager: AdManager?,
    preloadedAdView: android.view.View? = null,
    fingerprintUnlockEnabled: Boolean = false,
    onFingerprintClick: () -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit,
    onPinComplete: (String) -> Unit,
    onErrorReset: () -> Unit,
    onVerifyRecoveryAnswer: (String, (Boolean) -> Unit) -> Unit,
    onRecoverySuccess: () -> Unit,
    onBackClick: () -> Unit,
    onNavigateToBackgrounds: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {}
) {
    val iconContext = LocalContext.current
    var appEntry by remember(packageName) { mutableStateOf(AppIconCache.peek(packageName)) }
    val appName = appEntry?.label.orEmpty()
    val appIcon = appEntry?.icon
    var showForgotDialog by remember { mutableStateOf(false) }
    var isFingerprintMode by remember(fingerprintUnlockEnabled) { mutableStateOf(fingerprintUnlockEnabled) }
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(packageName) {
        if (appEntry == null) {
            appEntry = AppIconCache.load(iconContext, packageName)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (!selectedBackgroundUrl.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = selectedBackgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0f172a))
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            appIcon?.let { icon ->
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = appName.ifEmpty { stringResource(R.string.locked_app) },
                                style = AppTypography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        com.carbon.prolocker.core.ui.ToolbarLottieIcon(
                            animationRes = R.raw.background,
                            onClick = {
                                onNavigateToBackgrounds()
                            }
                        )
                        com.carbon.prolocker.core.ui.ToolbarLottieIcon(
                            animationRes = R.raw.trash_clean,
                            modifier = Modifier.padding(end = 8.dp, start = 12.dp),
                            onClick = {
                                onNavigateToMemory()
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            val configuration = LocalContext.current.resources.configuration
            val isLandscapeConfig =
                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            // The overlay's context keeps the configuration it was created with, so on tablets the
            // orientation is read from the real size of the window instead (phones keep the old check).
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = if (isTablet()) maxWidth > maxHeight else isLandscapeConfig
            val isRtl =
                LocalLayoutDirection.current == LayoutDirection.Rtl
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val appInfoContent = @Composable {
                        Column(
                            modifier = Modifier.weight(0.4f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.3f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val currentLockTypeRaw = lockType?.lowercase() ?: "credential"
                                val credentialStr =
                                    if (currentLockTypeRaw == "pattern") stringResource(R.string.pattern_text) else if (currentLockTypeRaw == "pin") stringResource(
                                        R.string.pin_text
                                    ) else stringResource(R.string.credential_text)
                                Text(
                                    text = if (isError) stringResource(
                                        R.string.incorrect_credential_prefix,
                                        credentialStr
                                    ) else stringResource(
                                        R.string.enter_credential_prefix,
                                        credentialStr
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isError) ProLockerError else Color.White.copy(
                                        alpha = 0.7f
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                LaunchedEffect(isError) {
                                    if (isError) {
                                        if (vibrationEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        for (i in 0..5) {
                                            offsetX.animateTo(
                                                targetValue = if (i % 2 == 0) 15f else -15f,
                                                animationSpec = tween(
                                                    durationMillis = 50,
                                                    easing = LinearEasing
                                                )
                                            )
                                        }
                                        offsetX.animateTo(0f)
                                        delay(1000)
                                        onErrorReset()
                                    }
                                }
                                if (failedAttemptsCount >= threshold) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Card(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        TextButton(onClick = { showForgotDialog = true }) {
                                            Text(
                                                stringResource(R.string.forgot_password),
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.7f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                when (lockScreenAdPlace) {
                                    "top" -> {
                                        if (adManager != null) {
                                            NativeAdContainer(
                                                adManager = adManager,
                                                placement = AdPlacement.LOCKSCREEN_TOP,
                                                adType = NativeAdType.TYPE_2,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                            )
                                        }
                                    }

                                    "topBanner" -> {
                                        if (adManager != null) {
                                            NativeAdContainer(
                                                adManager = adManager,
                                                placement = AdPlacement.LOCKSCREEN_TOP,
                                                adType = NativeAdType.TYPE_4,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                            )
                                        }
                                    }

                                    "bottom" -> {
                                        if (adManager != null) {
                                            NativeAdContainer(
                                                adManager = adManager,
                                                placement = AdPlacement.LOCKSCREEN_BOTTOM,
                                                adType = NativeAdType.TYPE_3,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                            )
                                        }
                                    }
                                }
                            }


                        }
                    }


                    val lockAreaContent = @Composable {
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .offset(x = offsetX.value.dp),
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
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = stringResource(R.string.fingerprint_unlock),
                                                tint = ProLockerPrimary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.touch_to_unlock),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ProLockerOnBackground.copy(alpha = 0.85f),
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
                                        modifier = Modifier.height(36.dp)
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
                                when (lockType) {
                                    "PATTERN" -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(horizontal = 48.dp)
                                                .offset(x = offsetX.value.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PatternLockView(
                                                onPatternDrawn = onPatternComplete,
                                                onInteractionStarted = { },
                                                isError = isError,
                                                vibrationEnabled = vibrationEnabled,
                                                hidePatternPath = hidePatternPath
                                            )
                                        }
                                    }

                                    else -> {
                                        PinLockView(
                                            isError = isError,
                                            onPinComplete = onPinComplete,
                                            vibrationEnabled = vibrationEnabled
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isRtl) {
                        lockAreaContent()
                        appInfoContent()
                    } else {
                        appInfoContent()
                        lockAreaContent()
                    }
                }
            } else {
                // ── PORTRAIT — unified layout for Pattern, PIN & Fingerprint ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ════ TOP AREA — Top Ad centered vertically in remaining upper space ════
                    if (lockScreenAdPlace == "top" || lockScreenAdPlace == "topBanner") {
                        Spacer(modifier = Modifier.weight(1f))

                        when (lockScreenAdPlace) {
                            "top" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(148.dp)
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (preloadedAdView != null) {
                                        NativeAdContainer(
                                            preloadedAd = preloadedAdView,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    } else if (adManager != null) {
                                        NativeAdContainer(
                                            adManager = adManager,
                                            placement = AdPlacement.LOCKSCREEN_TOP,
                                            adType = NativeAdType.TYPE_2,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    }
                                }
                            }

                            "topBanner" -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(212.dp)
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (preloadedAdView != null) {
                                        NativeAdContainer(
                                            preloadedAd = preloadedAdView,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    } else if (adManager != null) {
                                        NativeAdContainer(
                                            adManager = adManager,
                                            placement = AdPlacement.LOCKSCREEN_TOP,
                                            adType = NativeAdType.TYPE_4,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        // When no top ad, flexible spacer pushes content toward bottom
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // ════ CONTENT AREA ════
                    if (isFingerprintMode) {
                        // ── Fingerprint unlock ──
                        Surface(
                            onClick = onFingerprintClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = stringResource(R.string.fingerprint_unlock),
                                    tint = Color.White,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.touch_to_unlock),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        OutlinedButton(
                            onClick = { isFingerprintMode = false },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            val switchText = if (lockType == "PIN") stringResource(R.string.use_pin) else stringResource(R.string.use_pattern)
                            Text(
                                text = switchText,
                                style = AppTypography.labelMedium,
                                color = Color.White
                            )
                        }

                        // Second weight spacer → centers fingerprint between top ad & bottom
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        // ── Pattern / PIN unlock ──
                        val currentLockTypeRaw = lockType?.lowercase() ?: "pattern"
                        val credentialStr = if (currentLockTypeRaw == "pattern") {
                            stringResource(R.string.pattern_text)
                        } else {
                            stringResource(R.string.pin_text)
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
                            color = if (isError) Color.Red else Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        LaunchedEffect(isError) {
                            if (isError) {
                                if (vibrationEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                for (i in 0..5) {
                                    offsetX.animateTo(
                                        targetValue = if (i % 2 == 0) 15f else -15f,
                                        animationSpec = tween(
                                            durationMillis = 50,
                                            easing = LinearEasing
                                        )
                                    )
                                }
                                offsetX.animateTo(0f)
                                delay(1000)
                                onErrorReset()
                            }
                        }

                        if (failedAttemptsCount >= threshold) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { showForgotDialog = true }) {
                                Text(
                                    stringResource(R.string.forgot_password),
                                    color = Color.White
                                )
                            }
                        }

                        // 8dp gap pushes helper text 8dp higher relative to suggestion view
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            contentAlignment = AbsoluteAlignment.CenterRight
                        ) {
                            LockScreenSuggestionButton()
                        }

                        // In PIN mode, buttons move 16dp higher
                        val isPattern = lockType == "PATTERN"
                        val inputOffset = if (isPattern) 0.dp else (-16).dp

                        // Lock input with shake animation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(x = offsetX.value.dp, y = inputOffset),
                            contentAlignment = Alignment.Center
                        ) {
                            when (lockType) {
                                "PATTERN" -> {
                                    PatternLockView(
                                        onPatternDrawn = onPatternComplete,
                                        onInteractionStarted = { },
                                        isError = isError,
                                        vibrationEnabled = vibrationEnabled,
                                        hidePatternPath = hidePatternPath
                                    )
                                }

                                else -> {
                                    PinLockView(
                                        isError = isError,
                                        onPinComplete = onPinComplete,
                                        vibrationEnabled = vibrationEnabled
                                    )
                                }
                            }
                        }

                        if (fingerprintUnlockEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { isFingerprintMode = true },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.use_fingerprint),
                                    style = AppTypography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        // Compensate the -16dp offset so bottom section stays in place
                        if (!isPattern) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // ════ BOTTOM SECTION — forgot password OR bottom ad ════
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (failedAttemptsCount >= threshold) {
                            if (isFingerprintMode) {
                                TextButton(onClick = { showForgotDialog = true }) {
                                    Text(
                                        stringResource(R.string.forgot_password),
                                        color = Color.White
                                    )
                                }
                            }
                        } else if (lockScreenAdPlace == "bottom") {
                            if (preloadedAdView != null) {
                                NativeAdContainer(
                                    preloadedAd = preloadedAdView,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            } else if (adManager != null) {
                                NativeAdContainer(
                                    adManager = adManager,
                                    placement = AdPlacement.LOCKSCREEN_BOTTOM,
                                    adType = NativeAdType.TYPE_3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    }
                }



            }
            }
        }
        if (showForgotDialog) {
            var recoveryAnswer by remember { mutableStateOf("") }
            var recoveryError by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showForgotDialog = false
                        recoveryError = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.recover_lock),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (recoveryQuestion == null) {
                            Text(stringResource(R.string.no_recovery_question))
                        } else {
                            Text(
                                stringResource(R.string.question_prefix, recoveryQuestion),
                                style = AppTypography.labelLarge
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
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (recoveryQuestion != null) {
                                TextButton(onClick = {
                                    if (recoveryAnswer.isBlank()) {
                                        recoveryError = true
                                    } else {
                                        onVerifyRecoveryAnswer(recoveryAnswer) { success ->
                                            if (success) {
                                                showForgotDialog = false
                                                onRecoverySuccess()
                                            } else {
                                                recoveryError = true
                                            }
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.submit))
                                }
                            } else {
                                TextButton(onClick = { showForgotDialog = false }) {
                                    Text(stringResource(R.string.ok))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}