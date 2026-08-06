package com.carbon.prolocker.feature.tools

import android.content.res.Configuration
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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import kotlinx.coroutines.launch

data class ToolsFeatureItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val available: Boolean,
    val gifRes: Int? = null
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

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(item.gradient))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = true,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

//        if (!item.available) {
//            Surface(
//                color = Color.Black.copy(alpha = 0.28f),
//                shape = RoundedCornerShape(50),
//                modifier = Modifier.align(Alignment.TopEnd)
//            ) {
//                Text(
//                    text = stringResource(R.string.coming_soon),
//                    fontSize = 9.sp,
//                    fontWeight = FontWeight.SemiBold,
//                    color = Color.White,
//                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
//                )
//            }
//        }
    }
}

@Composable
fun MemoryOptimizerCard(
    item: ToolsFeatureItem,
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
        label = "memoryOptimizerCardScale"
    )
    val context = LocalContext.current
    val gifRequest = remember(item.gifRes) {
        ImageRequest.Builder(context)
            .data("android.resource://${context.packageName}/${item.gifRes}")
            .decoderFactory(coil.decode.GifDecoder.Factory())
            .build()
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(item.gradient))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
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
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ProLockerPrimary, ProLockerSecondary)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {

        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                    .padding(bottom = 4.dp)
            )
        }
        features.forEach { item ->
            if (item.gifRes == null) {
                item(key = item.title) {
                    FeatureCard(
                        item = item,
                        onClick = { onFeatureClick(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.3f)
                    )
                }

            } else {
                item(span = { GridItemSpan(2) }, key = item.title) {
                    MemoryOptimizerCard(
                        item = item,
                        onClick = { onFeatureClick(item) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
    onNavigateToCallBlocker: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonLabel = stringResource(R.string.coming_soon)
    val hideFilesTitle = stringResource(R.string.tools_hide_files)
    val backupTitle = stringResource(R.string.tools_backup)
    val memoryOptimizerTitle = stringResource(R.string.tools_memory_optimizer)
    val appLockTitle = stringResource(R.string.tools_app_lock)
    val callBlockerTitle = stringResource(R.string.tools_app_blocking)

    val features = listOf(
        ToolsFeatureItem(
            title = stringResource(R.string.tools_hide_files),
            subtitle = stringResource(R.string.tools_hide_files_subtitle),
            icon = Icons.Outlined.VisibilityOff,
            gradient = listOf(Color(0xFF7B61FF), Color(0xFF5B6CFF)),
            available = true
        ),
        ToolsFeatureItem(
            title = stringResource(R.string.tools_intruder_detection),
            subtitle = stringResource(R.string.tools_intruder_detection_subtitle),
            icon = Icons.Outlined.Security,
            gradient = listOf(Color(0xFF00D1B2), Color(0xFF00A896)),
            available = true
        ),
        ToolsFeatureItem(
            title = stringResource(R.string.tools_backup),
            subtitle = stringResource(R.string.tools_backup_subtitle),
            icon = Icons.Outlined.Backup,
            gradient = listOf(Color(0xFFF472B6), Color(0xFFEC4899)),
            available = true
        ),
        ToolsFeatureItem(
            title = stringResource(R.string.tools_app_blocking),
            subtitle = stringResource(R.string.tools_app_blocking_subtitle),
            icon = Icons.Outlined.PhoneDisabled,
            gradient = listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)),
            available = true
        ),
        ToolsFeatureItem(
            title = stringResource(R.string.tools_memory_optimizer),
            subtitle = stringResource(R.string.tools_memory_optimizer_subtitle),
            icon = Icons.Outlined.Speed,
            gradient = listOf(Color(0xFF34D399), Color(0xFF10B981)),
            available = true,
            gifRes = R.raw.gif_guide
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
                if (item.available && item.title == hideFilesTitle) {
                    onNavigateToHideFiles()
                } else if (item.available && item.title == backupTitle) {
                    onNavigateToBackup()
                } else if (item.available && item.title == memoryOptimizerTitle) {
                    onNavigateToMemoryOptimizer()
                } else if (item.available && item.title == callBlockerTitle) {
                    onNavigateToCallBlocker()
                } else if (item.available) {
                    onNavigateToSecurity()
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("$comingSoonLabel · ${item.title}")
                    }
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
