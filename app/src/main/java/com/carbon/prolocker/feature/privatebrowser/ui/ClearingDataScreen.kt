package com.carbon.prolocker.feature.privatebrowser.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.privatebrowser.PrivateBrowserGradient
import com.carbon.prolocker.feature.privatebrowser.data.CleanupStep
import kotlinx.coroutines.delay

/** What the cleanup screen has to wipe, and whether the browser closes afterwards. */
data class ClearingRequest(val steps: List<CleanupStep>, val exitAfter: Boolean)

@Composable
fun ClearingDataScreen(
    request: ClearingRequest,
    runStep: suspend (CleanupStep) -> Unit,
    onFinished: () -> Unit,
    /** Native ad slot under the step list, while the user waits for the cleanup. */
    ad: (@Composable () -> Unit)? = null
) {
    var current by remember(request) { mutableIntStateOf(0) }
    var done by remember(request) { mutableStateOf(false) }
    val latestRunStep by rememberUpdatedState(runStep)
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(request) {
        request.steps.forEachIndexed { index, step ->
            current = index
            val startedAt = System.currentTimeMillis()
            try {
                latestRunStep(step)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_STEP_MS) delay(MIN_STEP_MS - elapsed)
        }
        current = request.steps.size
        done = true
        delay(700)
        latestOnFinished()
    }

    val transition = rememberInfiniteTransition(label = "clearing")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "clearingRotation"
    )
    val accent = PrivateBrowserGradient.first()
    val success = Color(0xFF10B981)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (!done) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation)
                    ) {
                        val stroke = 6.dp.toPx()
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, accent, PrivateBrowserGradient.last())),
                            startAngle = 0f,
                            sweepAngle = 300f,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .background(
                            if (done) Brush.linearGradient(listOf(success, success.copy(alpha = 0.8f)))
                            else Brush.linearGradient(PrivateBrowserGradient),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (done) Icons.Filled.Check else Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(if (done) R.string.pb_clearing_done else R.string.pb_clearing_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pb_clearing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    request.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                when {
                                    index < current -> Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = success,
                                        modifier = Modifier.size(22.dp)
                                    )

                                    index == current -> CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = accent
                                    )

                                    else -> Icon(
                                        Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(stepLabel(step)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (index <= current) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (ad != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ad()
            }
        }
    }
}

private const val MIN_STEP_MS = 450L

private fun stepLabel(step: CleanupStep): Int = when (step) {
    CleanupStep.HISTORY -> R.string.pb_step_history
    CleanupStep.COOKIES -> R.string.pb_step_cookies
    CleanupStep.CACHE -> R.string.pb_step_cache
    CleanupStep.SITE_DATA -> R.string.pb_step_site_data
    CleanupStep.TABS -> R.string.pb_step_tabs
}
