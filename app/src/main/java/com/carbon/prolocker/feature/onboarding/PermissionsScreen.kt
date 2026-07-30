package com.carbon.prolocker.feature.onboarding

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.carbon.prolocker.R
import com.carbon.prolocker.core.permissions.PermissionManager
import com.carbon.prolocker.core.security.TrustedReturnManager
import com.carbon.prolocker.core.theme.ProLockerTertiary
import com.carbon.prolocker.core.ui.components.AppToolbar
import com.carbon.prolocker.core.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class BatteryPermissionLaunchPhase {
    IDLE,
    DIRECT_REQUEST,
    FALLBACK_SETTINGS
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    pendingPackage: String = "",
    onPermissionsGranted: () -> Unit
) {
    val isInspection = LocalInspectionMode.current
    val viewModel: PermissionsViewModel? = if (isInspection) null else koinViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val usageState by viewModel?.usageState?.collectAsState() ?: remember {
        mutableStateOf(
            PermissionState.IDLE
        )
    }
    val overlayState by viewModel?.overlayState?.collectAsState() ?: remember {
        mutableStateOf(
            PermissionState.IDLE
        )
    }
    val batteryState by viewModel?.batteryState?.collectAsState() ?: remember {
        mutableStateOf(
            PermissionState.IDLE
        )
    }

    if (!isInspection) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel?.checkPermissions(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            viewModel?.checkPermissions(context)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val slides = listOf(
        PermissionSlideModel(
            type = PermissionType.USAGE_ACCESS,
            title = stringResource(R.string.usage_access_title),
            description = stringResource(R.string.usage_access_desc),
            reason = stringResource(R.string.usage_access_reason),
            icon = Icons.Default.QueryStats,
            isRequired = true
        ),
        PermissionSlideModel(
            type = PermissionType.OVERLAY,
            title = stringResource(R.string.display_over_apps_title),
            description = stringResource(R.string.display_over_apps_desc),
            reason = stringResource(R.string.display_over_apps_reason),
            icon = Icons.Default.Layers,
            isRequired = true
        ),
        PermissionSlideModel(
            type = PermissionType.BATTERY,
            title = stringResource(R.string.battery_opt_title),
            description = stringResource(R.string.battery_opt_desc),
            reason = stringResource(R.string.battery_opt_reason),
            icon = Icons.Default.BatteryAlert,
            isRequired = true
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()
    var batteryPermissionPhase by remember { mutableStateOf(BatteryPermissionLaunchPhase.IDLE) }

    val refreshPermissionStates = {
        if (!isInspection) {
            viewModel?.checkPermissions(context)
        }
    }

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionStates()
        batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
    }

    val batteryRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            refreshPermissionStates()
            val granted = PermissionManager.isIgnoringBatteryOptimizations(context)
            when (batteryPermissionPhase) {
                BatteryPermissionLaunchPhase.DIRECT_REQUEST -> {
                    if (!granted) {
                        val fallbackIntent =
                            PermissionManager.getBatteryOptimizationSettingsIntent()
                        if (PermissionManager.canResolveIntent(context, fallbackIntent)) {
                            batteryPermissionPhase = BatteryPermissionLaunchPhase.FALLBACK_SETTINGS
                            batterySettingsLauncher.launch(fallbackIntent)
                        } else {
                            batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                        }
                    } else {
                        batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                    }
                }

                BatteryPermissionLaunchPhase.FALLBACK_SETTINGS -> {
                    batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                }

                BatteryPermissionLaunchPhase.IDLE -> {
                    batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                }
            }
        }
    )

    Scaffold(
        topBar = { AppToolbar(title = stringResource(R.string.setup_permissions)) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in slides.indices) {
                    val isActive = i == pagerState.currentPage
                    val isCompleted = i < pagerState.currentPage
                    val color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isActive) 28.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val slide = slides[page]
                val state = when (slide.type) {
                    PermissionType.USAGE_ACCESS -> usageState
                    PermissionType.OVERLAY -> overlayState
                    PermissionType.BATTERY -> batteryState
                }

                PermissionSlideContent(
                    slide = slide,
                    state = state,
                    isLastPage = page == slides.size - 1,
                    onRequest = {
                        if (!isInspection) {
                            when (slide.type) {
                                PermissionType.USAGE_ACCESS -> {
                                    batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                                    TrustedReturnManager.startTrustedReturn()
                                    batteryRequestLauncher.launch(PermissionManager.getUsageAccessIntent())
                                }

                                PermissionType.OVERLAY -> {
                                    batteryPermissionPhase = BatteryPermissionLaunchPhase.IDLE
                                    TrustedReturnManager.startTrustedReturn()
                                    batteryRequestLauncher.launch(
                                        PermissionManager.getOverlayPermissionIntent(
                                            context
                                        )
                                    )
                                }

                                PermissionType.BATTERY -> {
                                    val alreadyGranted =
                                        PermissionManager.isIgnoringBatteryOptimizations(context)
                                    if (alreadyGranted) {
                                        refreshPermissionStates()
                                    } else {
                                        val requestIntent =
                                            PermissionManager.getBatteryOptimizationRequestIntent(
                                                context
                                            )
                                        if (PermissionManager.canResolveIntent(
                                                context,
                                                requestIntent
                                            )
                                        ) {
                                            batteryPermissionPhase =
                                                BatteryPermissionLaunchPhase.DIRECT_REQUEST
                                            TrustedReturnManager.startTrustedReturn()
                                            batteryRequestLauncher.launch(requestIntent)
                                        } else {
                                            val fallbackIntent =
                                                PermissionManager.getBatteryOptimizationSettingsIntent()
                                            if (PermissionManager.canResolveIntent(
                                                    context,
                                                    fallbackIntent
                                                )
                                            ) {
                                                batteryPermissionPhase =
                                                    BatteryPermissionLaunchPhase.FALLBACK_SETTINGS
                                                TrustedReturnManager.startTrustedReturn()
                                                batterySettingsLauncher.launch(fallbackIntent)
                                            } else {
                                                refreshPermissionStates()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onNext = {
                        if (page < slides.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page + 1)
                            }
                        } else {
                            onPermissionsGranted()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GifFromRaw() {
    val context = LocalContext.current

    val request = ImageRequest.Builder(context)
        .data("android.resource://${context.packageName}/${R.raw.gif_guide}")
        .decoderFactory(coil.decode.GifDecoder.Factory())
        .build()

    AsyncImage(
        modifier = Modifier.padding(vertical = 8.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        model = request,
        contentDescription = "GIF"
    )
}

@Composable
fun PermissionSlideContent(
    slide: PermissionSlideModel,
    state: PermissionState,
    isLastPage: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = slide.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = slide.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (!isLastPage) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                GifFromRaw()
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state == PermissionState.GRANTED) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ProLockerTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.permission_granted),
                            color = ProLockerTertiary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryButton(
                        text = stringResource(if (isLastPage) R.string.finish else R.string.next),
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.grant_permission),
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionsScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        PermissionsScreen(onPermissionsGranted = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PermissionsScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        PermissionsScreen(onPermissionsGranted = {})
    }
}

