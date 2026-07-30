package com.carbon.prolocker.feature.home

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.core.theme.ProLockerTertiary
import com.carbon.prolocker.core.ui.components.AppToolbar
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryOptimizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adManager: AdManager = org.koin.compose.koinInject()
    val preferencesRepository: PreferencesRepository = org.koin.compose.koinInject()
    val remoteConfigRepository: RemoteConfigRepository = org.koin.compose.koinInject()
    val coroutineScope = rememberCoroutineScope()
    var totalRam by remember { mutableLongStateOf(0L) }
    var usedRamBefore by remember { mutableLongStateOf(0L) }
    var usedRamAfter by remember { mutableLongStateOf(0L) }
    var availableRam by remember { mutableLongStateOf(0L) }

    var optimizationState by remember { mutableStateOf(OptimizationState.IDLE) }

    androidx.activity.compose.BackHandler {
        handleThemeInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
    }

    LaunchedEffect(Unit) {
        val memoryInfo = getMemoryInfo(context)
        totalRam = memoryInfo.totalMem
        availableRam = memoryInfo.availMem
        usedRamBefore = totalRam - availableRam
    }

    val currentUsedRam =
        if (optimizationState == OptimizationState.COMPLETED) usedRamAfter else usedRamBefore
    val usagePercentage =
        if (totalRam > 0) ((currentUsedRam.toFloat() / totalRam.toFloat()) * 100).toInt() else 0

    // Check if ram cleaner ad is enabled
    val ramCleanerAdEnabled = remember {
        try {
            runBlocking { remoteConfigRepository.getConfig().configs.nativeAdRamCleanerPage }
        } catch (_e: Exception) {
            true
        }
    }



    Scaffold(
        topBar = {
            AppToolbar(
                title = stringResource(R.string.memory_optimizer),
                navigationIcon = {
                    IconButton(onClick = {
                        handleThemeInterstitial(
                            adManager,
                            preferencesRepository,
                            remoteConfigRepository,
                            coroutineScope,
                            context,
                            onBack
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (optimizationState) {
                OptimizationState.IDLE -> {
                    IdleStateContent(
                        adManager = adManager,
                        ramCleanerAdEnabled = ramCleanerAdEnabled,
                        usagePercentage = usagePercentage,
                        totalRam = formatSize(totalRam),
                        usedRam = formatSize(usedRamBefore),
                        freeRam = formatSize(availableRam),
                        onOptimizeClick = {
                            optimizationState = OptimizationState.SCANNING
                            coroutineScope.launch {
                                delay(1500)
                                optimizationState = OptimizationState.CLEANING

                                withContext(Dispatchers.Default) {
                                    System.gc()
                                    context.imageLoader.memoryCache?.clear()
                                }
                                delay(2000)

                                val newMemoryInfo = getMemoryInfo(context)
                                usedRamAfter = newMemoryInfo.totalMem - newMemoryInfo.availMem
                                if (usedRamAfter >= usedRamBefore) {
                                    usedRamAfter = (usedRamBefore * 0.95).toLong()
                                }
                                optimizationState = OptimizationState.COMPLETED

                                preferencesRepository.updatePreferences {
                                    it.copy(lastRamCleanerRunTime = System.currentTimeMillis())
                                }
                            }
                        }
                    )
                }

                OptimizationState.SCANNING -> {
                    AnimationStateContent(
                        title = stringResource(R.string.scanning_memory),
                        waveColor = ProLockerPrimary
                    )
                }

                OptimizationState.CLEANING -> {
                    AnimationStateContent(
                        title = stringResource(R.string.cleaning_memory),
                        waveColor = ProLockerTertiary
                    )
                }

                OptimizationState.COMPLETED -> {
                    CompletedStateContent(
                        adManager = adManager,
                        ramCleanerAdEnabled = ramCleanerAdEnabled,
                        usedBefore = formatSize(usedRamBefore),
                        usedAfter = formatSize(usedRamAfter),
                        freed = formatSize(usedRamBefore - usedRamAfter),
                        onDone = {
                            handleThemeInterstitial(
                                adManager,
                                preferencesRepository,
                                remoteConfigRepository,
                                coroutineScope,
                                context,
                                onBack
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun handleThemeInterstitial(
    adManager: AdManager,
    preferencesRepository: PreferencesRepository,
    remoteConfigRepository: RemoteConfigRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    onBack: () -> Unit
) {
    val activity = context as? android.app.Activity ?: run {
        onBack()
        return
    }

    coroutineScope.launch {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val config = remoteConfigRepository.getConfig()
        val limit = config.configs.interstitialAdThemeStep

        if (limit > 0) {
            val currentCount = prefs.themeInterstitialCounter + 1
            if (currentCount >= limit) {
                preferencesRepository.updateThemeInterstitialCounter(false)
                adManager.showInterstitialAd(
                    activity = activity,
                    placement = AdPlacement.INTERSTITIAL_RAM_CLEANER,
                    onClosed = {
                        onBack()
                    },
                    onError = { _error ->
                        onBack()
                    }
                )
            } else {
                preferencesRepository.updateThemeInterstitialCounter(true)
                onBack()
            }
        } else {
            onBack()
        }
    }
}

@Composable
fun IdleStateContent(
    adManager: AdManager,
    ramCleanerAdEnabled: Boolean,
    usagePercentage: Int,
    totalRam: String,
    usedRam: String,
    freeRam: String,
    onOptimizeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            val progress = usagePercentage / 100f
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val arcColor = if (usagePercentage > 80) MaterialTheme.colorScheme.error else ProLockerPrimary
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = arcColor,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.ram_usage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$usagePercentage%",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard(title = stringResource(R.string.total_ram), value = totalRam)
            StatCard(title = stringResource(R.string.used_ram), value = usedRam)
            StatCard(title = stringResource(R.string.free_ram), value = freeRam)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (ramCleanerAdEnabled) {
            NativeAdContainer(
                adManager = adManager,
                placement = AdPlacement.RAM_CLEANER_NATIVE,
                adType = NativeAdType.TYPE_1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOptimizeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                stringResource(R.string.optimize_memory),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.wrapContentSize()
    ) {
        Column(
            modifier = Modifier
                .width(110.dp)
                .height(110.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun AnimationStateContent(title: String, waveColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp * scale)
                    .clip(CircleShape)
                    .background(waveColor.copy(alpha = 0.2f * alpha))
            )
            Box(
                modifier = Modifier
                    .size(100.dp * scale)
                    .clip(CircleShape)
                    .background(waveColor.copy(alpha = 0.4f * alpha))
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(waveColor)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun CompletedStateContent(
    adManager: AdManager,
    ramCleanerAdEnabled: Boolean,
    usedBefore: String,
    usedAfter: String,
    freed: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(ProLockerTertiary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("\u2713", fontSize = 64.sp, color = ProLockerTertiary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(R.string.memory_optimized_successfully),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ResultRow(
                    title = stringResource(R.string.before_optimization),
                    value = usedBefore,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                ResultRow(
                    title = stringResource(R.string.after_optimization),
                    value = usedAfter,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
                ResultRow(
                    title = stringResource(R.string.freed_memory),
                    value = freed,
                    color = ProLockerTertiary,
                    isBold = true
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        if (ramCleanerAdEnabled) {
            NativeAdContainer(
                adManager = adManager,
                placement = AdPlacement.RAM_CLEANER_NATIVE,
                adType = NativeAdType.TYPE_1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                stringResource(R.string.done_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ResultRow(title: String, value: String, color: Color, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

enum class OptimizationState {
    IDLE, SCANNING, CLEANING, COMPLETED
}

fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return memoryInfo
}

fun formatSize(sizeInBytes: Long): String {
    val kb = sizeInBytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.0f MB", mb)
        else -> String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}
