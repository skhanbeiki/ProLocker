package com.carbon.prolocker.feature.junkcleaner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carbon.prolocker.R
import com.carbon.prolocker.ad.AdManager
import com.carbon.prolocker.ad.AdPlacement
import com.carbon.prolocker.ad.NativeAdContainer
import com.carbon.prolocker.ad.NativeAdType
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.language.findActivity
import com.carbon.prolocker.core.theme.AppTypography
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

enum class CleanerState {
    IDLE,
    SCANNING,
    REVIEW,
    CLEANING,
    COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkCleanerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adManager: AdManager = org.koin.compose.koinInject()
    val preferencesRepository: PreferencesRepository = org.koin.compose.koinInject()
    val remoteConfigRepository: RemoteConfigRepository = org.koin.compose.koinInject()
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { JunkCleanerEngine() }

    val lifecycleOwner = LocalLifecycleOwner.current

    var cleanerState by remember { mutableStateOf(CleanerState.IDLE) }
    var storageInfo by remember { mutableStateOf(engine.getStorageInfo()) }
    var currentScannedPath by remember { mutableStateOf("") }
    var scanningCategory by remember { mutableStateOf(JunkCategory.APP_CACHE) }
    var completedScanCategories by remember { mutableStateOf<Set<JunkCategory>>(emptySet()) }
    var scanningBytes by remember { mutableLongStateOf(0L) }
    var scanningProgress by remember { mutableFloatStateOf(0f) }

    var deletingCurrentPath by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf(JunkCategory.APP_CACHE) }
    var completedDeleteCategories by remember { mutableStateOf<Set<JunkCategory>>(emptySet()) }
    var deletingRemainingBytes by remember { mutableLongStateOf(0L) }
    var deletingProgress by remember { mutableFloatStateOf(0f) }

    val junkGroups = remember { mutableStateListOf<JunkCategoryGroup>() }
    var selectedJunkItems by remember { mutableStateOf<Set<JunkItem>>(emptySet()) }
    var freedBytes by remember { mutableLongStateOf(0L) }
    var hasStoragePermission by remember {
        mutableStateOf(checkStoragePermission(context))
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = checkStoragePermission(context)
        hasStoragePermission = granted
        if (granted) {
            storageInfo = engine.getStorageInfo()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = checkStoragePermission(context)
        hasStoragePermission = granted
        if (granted) {
            storageInfo = engine.getStorageInfo()
        }
    }

    val requestStoragePermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                allFilesLauncher.launch(intent)
            } catch (_e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                allFilesLauncher.launch(intent)
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = checkStoragePermission(context)
                if (granted != hasStoragePermission) {
                    hasStoragePermission = granted
                    if (granted) {
                        storageInfo = engine.getStorageInfo()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cleanerAdEnabled = remember {
        try {
            runBlocking { remoteConfigRepository.getConfig().configs.nativeAdRamCleanerPage }
        } catch (_e: Exception) {
            true
        }
    }

    BackHandler {
        if (!hasStoragePermission) {
            onBack()
        } else if (cleanerState == CleanerState.REVIEW) {
            cleanerState = CleanerState.IDLE
        } else if (cleanerState == CleanerState.CLEANING) {
            // Cannot interrupt while actively deleting
        } else {
            handleJunkInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
        }
    }

    LaunchedEffect(Unit) {
        storageInfo = engine.getStorageInfo()
        hasStoragePermission = checkStoragePermission(context)
    }

    if (!hasStoragePermission) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.junk_remover),
                            style = AppTypography.titleLarge,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                JunkPermissionFullScreenContent(
                    cleanerAdEnabled = cleanerAdEnabled,
                    adManager = adManager,
                    onGrantPermission = requestStoragePermission
                )
            }
        }
    } else {
        when (cleanerState) {
            CleanerState.IDLE -> {
                IdleStateContent(
                    storageInfo = storageInfo,
                    cleanerAdEnabled = cleanerAdEnabled,
                    adManager = adManager,
                    onStartScan = {
                        cleanerState = CleanerState.SCANNING
                        coroutineScope.launch {
                            val results = engine.scanJunk(context) { scanProg ->
                                currentScannedPath = scanProg.currentPath
                                scanningCategory = scanProg.currentCategory
                                completedScanCategories = scanProg.completedCategories
                                scanningBytes = scanProg.totalFoundBytes
                                scanningProgress = scanProg.progress
                            }
                            junkGroups.clear()
                            junkGroups.addAll(results)
                            selectedJunkItems = results.flatMap { it.items }.toSet()
                            cleanerState = CleanerState.REVIEW
                        }
                    },
                    onBack = {
                        handleJunkInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
                    }
                )
            }

            CleanerState.SCANNING -> {
                ScanningStateContent(
                    scanningBytes = scanningBytes,
                    scannedPath = currentScannedPath,
                    progress = scanningProgress,
                    activeCategory = scanningCategory,
                    completedCategories = completedScanCategories,
                    onBack = {
                        cleanerState = CleanerState.IDLE
                    }
                )
            }

            CleanerState.REVIEW -> {
                ReviewStateContent(
                    groups = junkGroups,
                    selectedItems = selectedJunkItems,
                    onItemToggle = { item ->
                        selectedJunkItems = if (selectedJunkItems.contains(item)) {
                            selectedJunkItems - item
                        } else {
                            selectedJunkItems + item
                        }
                    },
                    onGroupToggle = { group, isSelected ->
                        val items = group.items
                        selectedJunkItems = if (isSelected) {
                            selectedJunkItems + items
                        } else {
                            selectedJunkItems - items.toSet()
                        }
                    },
                    onCleanClick = {
                        cleanerState = CleanerState.CLEANING
                        deletingRemainingBytes = selectedJunkItems.sumOf { it.size }
                        coroutineScope.launch {
                            val totalFreed = engine.deleteJunkItems(selectedJunkItems.toList()) { delProg ->
                                deletingCurrentPath = delProg.currentPath
                                deletingCategory = delProg.currentCategory
                                completedDeleteCategories = delProg.completedCategories
                                deletingRemainingBytes = delProg.remainingBytes
                                deletingProgress = delProg.progress
                            }
                            freedBytes = totalFreed
                            storageInfo = engine.getStorageInfo()
                            preferencesRepository.updatePreferences {
                                it.copy(lastRamCleanerRunTime = System.currentTimeMillis())
                            }
                            cleanerState = CleanerState.COMPLETED
                        }
                    },
                    onBack = {
                        cleanerState = CleanerState.IDLE
                    }
                )
            }

            CleanerState.CLEANING -> {
                CleaningStateContent(
                    remainingBytes = deletingRemainingBytes,
                    currentPath = deletingCurrentPath,
                    progress = deletingProgress,
                    activeCategory = deletingCategory,
                    completedCategories = completedDeleteCategories,
                    onBack = { }
                )
            }

            CleanerState.COMPLETED -> {
                CompletedStateContent(
                    freedBytes = freedBytes,
                    storageInfo = storageInfo,
                    cleanerAdEnabled = cleanerAdEnabled,
                    adManager = adManager,
                    onDone = {
                        handleJunkInterstitial(adManager, preferencesRepository, remoteConfigRepository, coroutineScope, context, onBack)
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// IMAGE 1: Scanning State Content
// -------------------------------------------------------------
@Composable
private fun ScanningStateContent(
    scanningBytes: Long,
    scannedPath: String,
    progress: Float,
    activeCategory: JunkCategory,
    completedCategories: Set<JunkCategory>,
    onBack: () -> Unit
) {
    val (sizeValue, sizeUnit) = splitSize(scanningBytes)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "scanProg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFE2C55), Color(0xFFFF5277))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.junk_remover),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = sizeValue,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sizeUnit,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.junk_files_detected),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = scannedPath.ifEmpty { "..." },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1422))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                JunkCategory.values().forEach { cat ->
                    val isDone = completedCategories.contains(cat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(cat.titleRes),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (cat == JunkCategory.TEMP_FILES) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (cat == JunkCategory.RESIDUAL_JUNK) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isDone) {
                            CustomBlueCheckbox(checked = true)
                        } else {
                            CategoryProgressSpinner()
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// IMAGE 2: Review State Content
// -------------------------------------------------------------
@Composable
private fun ReviewStateContent(
    groups: List<JunkCategoryGroup>,
    selectedItems: Set<JunkItem>,
    onItemToggle: (JunkItem) -> Unit,
    onGroupToggle: (JunkCategoryGroup, Boolean) -> Unit,
    onCleanClick: () -> Unit,
    onBack: () -> Unit
) {
    val totalFoundBytes = groups.sumOf { it.totalSize }
    val selectedBytes = selectedItems.sumOf { it.size }
    val (sizeValue, sizeUnit) = splitSize(totalFoundBytes)

    var expandedCategories by remember {
        mutableStateOf(setOf(JunkCategory.RESIDUAL_JUNK))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFE2C55), Color(0xFFFF5277))))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.junk_remover),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = sizeValue,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sizeUnit,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.junk_files_detected),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.scanning_completed),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.private_data_safe),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1422))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groups, key = { it.category.name }) { group ->
                        val isExpanded = expandedCategories.contains(group.category)
                        val items = group.items
                        val allSelected = items.isNotEmpty() && items.all { selectedItems.contains(it) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategories = if (isExpanded) {
                                            expandedCategories - group.category
                                        } else {
                                            expandedCategories + group.category
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(group.titleRes),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (group.category == JunkCategory.TEMP_FILES) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.HelpOutline,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formatSize(group.totalSize),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    CustomBlueCheckbox(
                                        checked = allSelected,
                                        onCheckedChange = { isChecked ->
                                            onGroupToggle(group, isChecked)
                                        }
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items.forEach { item ->
                                        val isItemSelected = selectedItems.contains(item)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onItemToggle(item) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = Color(0xFF8A99AD),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatSize(item.size),
                                                color = Color(0xFF8A99AD),
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            CustomBlueCheckbox(
                                                checked = isItemSelected,
                                                onCheckedChange = { onItemToggle(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }

        // Floating Action Button at Bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
        ) {
            Button(
                onClick = onCleanClick,
                enabled = selectedItems.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(27.dp), spotColor = Color(0xFF00B0FF)),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color(0xFF1E3A5F)
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (selectedItems.isNotEmpty()) {
                                Brush.horizontalGradient(listOf(Color(0xFF2962FF), Color(0xFF00B0FF)))
                            } else {
                                Brush.horizontalGradient(listOf(Color(0xFF1E3A5F), Color(0xFF1E3A5F)))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.remove_junk_btn, formatSize(selectedBytes)),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// IMAGE 3: Cleaning State Content
// -------------------------------------------------------------
@Composable
private fun CleaningStateContent(
    remainingBytes: Long,
    currentPath: String,
    progress: Float,
    activeCategory: JunkCategory,
    completedCategories: Set<JunkCategory>,
    onBack: () -> Unit
) {
    val (sizeValue, sizeUnit) = splitSize(remainingBytes)
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "cleanProg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E6BFF), Color(0xFF2979FF))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.junk_remover),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = sizeValue,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sizeUnit,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.junk_files_detected),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = currentPath.ifEmpty { "..." },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1422))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                JunkCategory.values().forEach { cat ->
                    val isDone = completedCategories.contains(cat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(cat.titleRes),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (cat == JunkCategory.TEMP_FILES) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isDone) {
                            CustomBlueCheckbox(checked = true)
                        } else {
                            CategoryProgressSpinner()
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// IDLE State Content
// -------------------------------------------------------------
@Composable
private fun IdleStateContent(
    storageInfo: StorageInfo,
    cleanerAdEnabled: Boolean,
    adManager: AdManager,
    onStartScan: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFE2C55), Color(0xFFFF5277))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.junk_remover),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${storageInfo.usagePercentage}%",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.storage_usage),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatSize(storageInfo.usedBytes)} / ${formatSize(storageInfo.totalBytes)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1422))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E30))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StorageDetailItem(
                                label = stringResource(R.string.used_storage),
                                value = formatSize(storageInfo.usedBytes),
                                color = Color(0xFFFF5277)
                            )
                            StorageDetailItem(
                                label = stringResource(R.string.free_storage),
                                value = formatSize(storageInfo.freeBytes),
                                color = Color(0xFF00E676)
                            )
                            StorageDetailItem(
                                label = stringResource(R.string.total_storage),
                                value = formatSize(storageInfo.totalBytes),
                                color = Color.White
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.junk_cleaner_desc),
                        color = Color(0xFF8A99AD),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (cleanerAdEnabled) {
                        NativeAdContainer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(135.dp)
                                .padding(bottom = 12.dp),
                            placement = AdPlacement.RAM_CLEANER_NATIVE,
                            adType = NativeAdType.TYPE_5,
                            adManager = adManager
                        )
                    }

                    Button(
                        onClick = onStartScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(27.dp), spotColor = Color(0xFFFF5277)),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFE2C55), Color(0xFFFF5277)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.scan_junk),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// COMPLETED State Content
// -------------------------------------------------------------
@Composable
private fun CompletedStateContent(
    freedBytes: Long,
    storageInfo: StorageInfo,
    cleanerAdEnabled: Boolean,
    adManager: AdManager,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1422))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.clean_complete),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.junk_cleaned_successfully),
                fontSize = 14.sp,
                color = Color(0xFF8A99AD)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E30))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.freed_storage),
                        fontSize = 13.sp,
                        color = Color(0xFF8A99AD)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSize(freedBytes),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF253046))
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.free_storage),
                            fontSize = 14.sp,
                            color = Color(0xFF8A99AD)
                        )
                        Text(
                            text = formatSize(storageInfo.freeBytes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cleanerAdEnabled) {
                NativeAdContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .padding(bottom = 12.dp),
                    placement = AdPlacement.RAM_CLEANER_NATIVE,
                    adType = NativeAdType.TYPE_5,
                    adManager = adManager
                )
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
            ) {
                Text(
                    text = stringResource(R.string.done_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// -------------------------------------------------------------
// PERMISSION Full Screen Content
// -------------------------------------------------------------
@Composable
private fun JunkPermissionFullScreenContent(
    cleanerAdEnabled: Boolean,
    adManager: AdManager,
    onGrantPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.junk_perm_full_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.junk_perm_full_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionFeatureRow(
                        icon = Icons.Default.DeleteSweep,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.junk_perm_feature_cache_title),
                        description = stringResource(R.string.junk_perm_feature_cache_desc)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    PermissionFeatureRow(
                        icon = Icons.Default.InsertDriveFile,
                        iconTint = Color(0xFF00A896),
                        title = stringResource(R.string.junk_perm_feature_apk_title),
                        description = stringResource(R.string.junk_perm_feature_apk_desc)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    PermissionFeatureRow(
                        icon = Icons.Default.Security,
                        iconTint = Color(0xFF10B981),
                        title = stringResource(R.string.junk_perm_feature_privacy_title),
                        description = stringResource(R.string.junk_perm_feature_privacy_desc)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cleanerAdEnabled) {
                NativeAdContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .padding(bottom = 12.dp),
                    placement = AdPlacement.RAM_CLEANER_NATIVE,
                    adType = NativeAdType.TYPE_5,
                    adManager = adManager
                )
            }

            Button(
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.junk_perm_grant_btn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// UI Utility Components
// -------------------------------------------------------------
@Composable
private fun StorageDetailItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF8A99AD))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CustomBlueCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Color(0xFF2979FF) else Color(0xFF21283B))
            .then(
                if (onCheckedChange != null) Modifier.clickable { onCheckedChange(!checked) } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun CategoryProgressSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Canvas(modifier = modifier.size(20.dp)) {
        drawArc(
            color = Color(0xFF2979FF),
            startAngle = angle,
            sweepAngle = 260f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun handleJunkInterstitial(
    adManager: AdManager,
    preferencesRepository: PreferencesRepository,
    remoteConfigRepository: RemoteConfigRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    onBack: () -> Unit
) {
    val activity = context.findActivity() ?: run {
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
                    onClosed = { onBack() },
                    onError = { _error -> onBack() }
                )
                return@launch
            } else {
                preferencesRepository.updateThemeInterstitialCounter(true)
            }
        }
        onBack()
    }
}

private fun splitSize(bytes: Long): Pair<String, String> {
    if (bytes <= 0) return Pair("0", "B")
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    val formattedVal = if (digitGroups == 0) String.format(Locale.US, "%d", bytes) else String.format(Locale.US, "%.2f", value)
    return Pair(formattedVal, units[digitGroups])
}

private fun formatSize(bytes: Long): String {
    val (value, unit) = splitSize(bytes)
    return "$value$unit"
}
