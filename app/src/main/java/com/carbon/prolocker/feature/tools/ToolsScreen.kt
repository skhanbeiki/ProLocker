package com.carbon.prolocker.feature.tools

import android.content.res.Configuration
import androidx.annotation.RawRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.core.ui.ToolbarLottieIcon
import kotlinx.coroutines.launch

enum class FeatureId {
    HIDE_FILES,
    INTRUDER,
    BACKUP,
    CALL_BLOCKER,
    MEMORY_OPTIMIZER,
    PRIVACY_AUDITOR
}

data class ToolsFeatureItem(
    val id: FeatureId = FeatureId.HIDE_FILES,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val available: Boolean,
    @RawRes val lottieRes: Int? = null
)

@Composable
fun FeatureCard(
    item: ToolsFeatureItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "featureCardScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(item.gradient))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = true,
                    onClick = onClick
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.24f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.lottieRes != null) {
                        ToolbarLottieIcon(
                            animationRes = item.lottieRes,
                            onClick = onClick,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AppLockHeroCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "appLockHeroScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF9333EA))
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onClick
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ابزار اصلی برنامه",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ورود و مدیریت برنامه‌ها",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsGrid(
    features: List<ToolsFeatureItem>,
    heroTitle: String,
    heroSubtitle: String,
    onHeroClick: () -> Unit,
    onFeatureClick: (ToolsFeatureItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            AppLockHeroCard(
                title = heroTitle,
                subtitle = heroSubtitle,
                onClick = onHeroClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }
        features.forEach { item ->
            item(key = item.id.name) {
                FeatureCard(
                    item = item,
                    onClick = { onFeatureClick(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                )
            }
        }

        item(span = { GridItemSpan(2) }) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToHideFiles: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToMemoryOptimizer: () -> Unit = {},
    onNavigateToCallBlocker: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonLabel = stringResource(R.string.coming_soon)
    val appLockTitle = stringResource(R.string.tools_app_lock)

    val features = listOf(
        ToolsFeatureItem(
            id = FeatureId.HIDE_FILES,
            title = stringResource(R.string.tools_hide_files),
            subtitle = stringResource(R.string.tools_hide_files_subtitle),
            icon = Icons.Outlined.VisibilityOff,
            gradient = listOf(Color(0xFF7B61FF), Color(0xFF5B6CFF)),
            available = true
        ),
        ToolsFeatureItem(
            id = FeatureId.INTRUDER,
            title = stringResource(R.string.tools_intruder_detection),
            subtitle = stringResource(R.string.tools_intruder_detection_subtitle),
            icon = Icons.Outlined.Security,
            gradient = listOf(Color(0xFF00D1B2), Color(0xFF00A896)),
            available = true
        ),
        ToolsFeatureItem(
            id = FeatureId.BACKUP,
            title = stringResource(R.string.tools_backup),
            subtitle = stringResource(R.string.tools_backup_subtitle),
            icon = Icons.Outlined.Backup,
            gradient = listOf(Color(0xFFF472B6), Color(0xFFEC4899)),
            available = true
        ),
        ToolsFeatureItem(
            id = FeatureId.CALL_BLOCKER,
            title = stringResource(R.string.tools_app_blocking),
            subtitle = stringResource(R.string.tools_app_blocking_subtitle),
            icon = Icons.Outlined.PhoneDisabled,
            gradient = listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)),
            available = true
        ),
        ToolsFeatureItem(
            id = FeatureId.MEMORY_OPTIMIZER,
            title = stringResource(R.string.tools_memory_optimizer),
            subtitle = stringResource(R.string.tools_memory_optimizer_subtitle),
            icon = Icons.Outlined.Speed,
            gradient = listOf(Color(0xFF34D399), Color(0xFF10B981)),
            available = true,
            lottieRes = R.raw.trash_clean
        ),
        ToolsFeatureItem(
            id = FeatureId.PRIVACY_AUDITOR,
            title = stringResource(R.string.tools_privacy_auditor),
            subtitle = stringResource(R.string.tools_privacy_auditor_subtitle),
            icon = Icons.Outlined.PrivacyTip,
            gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
            available = true
        )
    )

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "toolsContentAlpha"
    )

    Scaffold(
        modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(ProLockerPrimary, ProLockerSecondary)
                                    ),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.tools),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.tools_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        ToolsGrid(
            features = features,
            heroTitle = appLockTitle,
            heroSubtitle = stringResource(R.string.tools_app_lock_subtitle),
            onHeroClick = onNavigateToAppLock,
            onFeatureClick = { item ->
                when (item.id) {
                    FeatureId.HIDE_FILES -> onNavigateToHideFiles()
                    FeatureId.INTRUDER -> onNavigateToSecurity()
                    FeatureId.BACKUP -> onNavigateToBackup()
                    FeatureId.CALL_BLOCKER -> onNavigateToCallBlocker()
                    FeatureId.MEMORY_OPTIMIZER -> onNavigateToMemoryOptimizer()
                    FeatureId.PRIVACY_AUDITOR -> onNavigateToAudit()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer { alpha = contentAlpha }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ToolsScreenPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        ToolsScreen(onNavigateToSecurity = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ToolsScreenDarkPreview() {
    com.carbon.prolocker.core.theme.ProLockerTheme {
        ToolsScreen(onNavigateToSecurity = {})
    }
}
