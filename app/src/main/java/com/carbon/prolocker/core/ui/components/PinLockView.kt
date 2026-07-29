package com.carbon.prolocker.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Production-quality PIN lock component (v2).
 *
 * Fixes over the previous version:
 * - Fast typing no longer drops digits. Every keypad button now uses a
 *   single, stable onClick lambda (created once via `remember`, reading the
 *   latest state through `rememberUpdatedState`) instead of a fresh inline
 *   lambda on every recomposition. Recreating the click lambda every
 *   keystroke is what makes Compose tear down and restart each button's
 *   tap-gesture detector, which is exactly what eats fast consecutive taps.
 * - A short internal `isLocked` window opens the instant the pin reaches
 *   full length, so a fast "next attempt" typed before the parent reports
 *   back can't silently leak extra digits into the old pin string — and a
 *   safety timeout guarantees it always unlocks even if the parent never
 *   flips `isError`.
 * - Wrong-pin reset is now a quick shake (~400ms total) instead of a flat
 *   1 second wait, so it visibly and promptly clears even under repeated
 *   fast wrong entries.
 * - Added animation: dots pop when filled, the whole dot row shakes on
 *   error, dots turn red on error, and keypad buttons scale down slightly
 *   on press — all done with `graphicsLayer` scale/translation so the
 *   actual layout size never changes.
 *
 * @param isError When true, shows error state (shake + red dots) and resets PIN.
 * @param vibrationEnabled Whether to trigger haptic feedback on digit input.
 * @param pinLength Number of digits required (default 4).
 * @param onPinComplete Called when the full PIN is entered.
 */
@Composable
fun PinLockView(
    isError: Boolean,
    vibrationEnabled: Boolean = true,
    pinLength: Int = 4,
    onPinComplete: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    // True for the brief window between "pin complete" and the parent
    // telling us whether it was right or wrong. Blocks new taps so a fast
    // next attempt can't leak into the pin we already submitted.
    var isLocked by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val configuration = LocalContext.current.resources.configuration
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }
    val dotScales = remember(pinLength) { List(pinLength) { Animatable(1f) } }

    // Buttons read state through these instead of capturing `enteredPin`
    // directly, so the click lambdas below can stay stable (created once)
    // while still always seeing the freshest value.
    val latestEnteredPin = rememberUpdatedState(enteredPin)
    val latestLocked = rememberUpdatedState(isLocked)

    fun popDot(index: Int) {
        scope.launch {
            dotScales[index].snapTo(0.55f)
            dotScales[index].animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Created exactly once — a stable reference so keypad buttons never
    // need to recompose or restart their gesture detector on every digit.
    val onDigit = remember {
        { digit: String ->
            if (!latestLocked.value && latestEnteredPin.value.length < pinLength) {
                if (vibrationEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                val next = latestEnteredPin.value + digit
                enteredPin = next
                popDot(next.length - 1)
                if (next.length == pinLength) {
                    isLocked = true
                    onPinComplete(next)
                }
            }
        }
    }

    val onDelete = remember {
        {
            if (!latestLocked.value && latestEnteredPin.value.isNotEmpty()) {
                if (vibrationEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                enteredPin = latestEnteredPin.value.dropLast(1)
            }
        }
    }

    LaunchedEffect(isError) {
        if (isError) {
            if (vibrationEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            // Quick shake, then clear — snappier and more reliable than the
            // previous flat 1000ms wait.
            val keyframes = listOf(0f, -18f, 16f, -12f, 8f, -4f, 0f)
            for (target in keyframes) {
                shakeOffset.animateTo(target, animationSpec = tween(45))
            }
            delay(120)
            enteredPin = ""
            isLocked = false
        }
    }

    // Safety valve: guarantees the keypad never stays locked forever even
    // if the parent doesn't report an error back (e.g. correct pin and the
    // screen is about to navigate away).
    LaunchedEffect(isLocked) {
        if (isLocked) {
            delay(800)
            isLocked = false
        }
    }

    val keypadAlpha by animateFloatAsState(
        targetValue = if (isLocked) 0.55f else 1f,
        animationSpec = tween(150),
        label = "keypadLockAlpha"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 16.dp else 32.dp)
        ) {
            // PIN dot indicators
            Row(
                modifier = Modifier.graphicsLayer { translationX = shakeOffset.value },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until pinLength) {
                    val isFilled = i < enteredPin.length
                    val scale = dotScales[i].value
                    val dotColor = if (isError) Color(0xFFFF5252) else Color.White
                    Box(
                        modifier = Modifier
                            .size(if (isLandscape) 16.dp else 24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(if (isFilled) dotColor else Color.Transparent)
                            .border(2.dp, dotColor, CircleShape)
                    )
                }
            }

            // Numeric keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isLandscape) 8.dp else 16.dp)
                    .graphicsLayer { alpha = keypadAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 8.dp)
            ) {
                // Rows 1-3 (1-9)
                for (row in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 1..3) {
                            val number = row * 3 + col
                            PinKeypadButton(
                                text = number.toString(),
                                onClick = onDigit
                            )
                        }
                    }
                }

                // Row 4: empty, 0, delete
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.size(64.dp))
                    PinKeypadButton(
                        text = "0",
                        onClick = onDigit
                    )
                    PinKeypadDeleteButton(onClick = onDelete)
                }
            }
        }
    }
}

/**
 * Numeric keypad button with consistent styling and a press-scale
 * animation. `onClick` is expected to be a stable (remembered) reference
 * from the caller — this button additionally memoizes its own wrapper
 * lambda keyed on [text] so its internal gesture detector never restarts
 * across recompositions.
 */
@Composable
fun PinKeypadButton(text: String, onClick: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "keypadButtonPress"
    )
    val handleClick = remember(text, onClick) { { onClick(text) } }

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = handleClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            color = Color.White
        )
    }
}

/**
 * Delete ("clear last digit") button with the same press-scale feedback
 * as the numeric keys.
 */
@Composable
fun PinKeypadDeleteButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "deleteButtonPress"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Clear,
            contentDescription = "Delete",
            tint = Color.White
        )
    }
}
