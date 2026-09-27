package com.carbon.prolocker.feature.lock

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.InterstitialAdActivity
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.model.SuggestionButtonConfig
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LockScreenSuggestionButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val remoteConfigRepository: RemoteConfigRepository = remember {
        KoinJavaComponent.get(RemoteConfigRepository::class.java)
    }
    val preferencesRepository: PreferencesRepository = remember {
        KoinJavaComponent.get(PreferencesRepository::class.java)
    }

    var isVisibleState by remember { mutableStateOf(false) }
    var buttonCaption by remember { mutableStateOf<String?>(null) }
    var buttonConfig by remember { mutableStateOf<SuggestionButtonConfig?>(null) }
    var isHandlingClick by remember { mutableStateOf(false) }

    val slideAnim = remember { Animatable(160f) }

    LaunchedEffect(Unit) {
        try {
            val config = remoteConfigRepository.getSuggestionButtonConfig() ?: return@LaunchedEffect
            if (!config.isVisible) return@LaunchedEffect

            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val prefs = preferencesRepository.safeFirst()
            if (prefs.suggestionButtonLastClickedDate == today) {
                return@LaunchedEffect
            }

            val currentCounter = preferencesRepository.incrementSuggestionButtonDisplayCounter()
            val step = config.displayStep.coerceAtLeast(1)
            if (currentCounter % step != 0) {
                return@LaunchedEffect
            }

            buttonCaption = if (config.captionList.isNotEmpty()) {
                config.captionList.random()
            } else {
                null
            }
            buttonConfig = config
            isVisibleState = true

            slideAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = FastOutSlowInEasing
                )
            )
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    if (!isVisibleState) return

    val buttonShape = RoundedCornerShape(
        topStart = 20.dp,
        bottomStart = 20.dp,
        topEnd = 0.dp,
        bottomEnd = 0.dp
    )
    val displayCaption = buttonCaption ?: stringResource(R.string.special_suggestion)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .offset(x = slideAnim.value.dp)
        ) {
            // Main Green Pill Button
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .shadow(elevation = 6.dp, shape = buttonShape)
                    .background(Color(0xFF1E824C), shape = buttonShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isHandlingClick
                    ) {
                        if (isHandlingClick) return@clickable
                        isHandlingClick = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        coroutineScope.launch {
                            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            preferencesRepository.recordSuggestionButtonClicked(today)

                            slideAnim.animateTo(
                                targetValue = 160f,
                                animationSpec = tween(
                                    durationMillis = 250,
                                    easing = FastOutLinearInEasing
                                )
                            )
                            isVisibleState = false

                            InterstitialAdActivity.start(
                                context = context,
                                placement = AdPlacement.INTERSTITIAL_SUGGESTION,
                                providerName = buttonConfig?.adType
                            )
                        }
                    }
                    .padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = displayCaption,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Circular Red Notification Badge with number "5"
//            Box(
//                modifier = Modifier
//                    .align(Alignment.TopEnd)
//                    .offset(x = (-2).dp, y = 0.dp)
//                    .size(18.dp)
//                    .background(Color(0xFFE53935), CircleShape)
//                    .border(1.2.dp, Color.White, CircleShape),
//                contentAlignment = Alignment.Center
//            ) {
//                Text(
//                    text = "5",
//                    color = Color.White,
//                    fontSize = 10.sp,
//                    fontWeight = FontWeight.Bold,
//                    lineHeight = 10.sp,
//                    textAlign = TextAlign.Center
//                )
//            }
        }
    }
}
