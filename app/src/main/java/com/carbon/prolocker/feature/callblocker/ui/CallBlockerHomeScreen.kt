package com.carbon.prolocker.feature.callblocker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerError
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.core.theme.ProLockerSecondary
import com.carbon.prolocker.feature.callblocker.data.BlockRuleType
import com.carbon.prolocker.feature.callblocker.data.BlockSourceCategory
import com.carbon.prolocker.feature.callblocker.data.BlockedCallLogEntity
import com.carbon.prolocker.feature.callblocker.data.BlockedNumberEntity
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockerHomeScreen(
    onBack: () -> Unit,
    viewModel: CallBlockerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Unblock Confirmation Dialog
    uiState.ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { viewModel.setRuleToDelete(null) },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    text = stringResource(R.string.call_blocker_unblock_confirm_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.call_blocker_unblock_confirm_msg, rule.displayName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteRule() },
                    colors = ButtonDefaults.buttonColors(containerColor = ProLockerError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.call_blocker_unblock))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setRuleToDelete(null) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.showAddDialog) {
        AddBlockRuleDialog(
            contacts = uiState.deviceContacts,
            callLogs = uiState.deviceCallLogs,
            isLoadingPickers = uiState.isLoadingPickers,
            onDismiss = { viewModel.closeAddDialog() },
            onAddRule = { num, name, ruleType, src ->
                viewModel.addRule(num, name, ruleType, src)
            }
        )
    }

    CallBlockerPermissionGate {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.call_blocker_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                if (uiState.selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { viewModel.openAddDialog() },
                        containerColor = ProLockerPrimary,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.call_blocker_add_rule))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Statistics Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53))),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = stringResource(R.string.call_blocker_stat_rules),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${uiState.blockedRules.size}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        Brush.linearGradient(listOf(ProLockerPrimary, ProLockerSecondary)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneCallback,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = stringResource(R.string.call_blocker_stat_calls),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${uiState.totalBlockedCallsCount}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Tabs Header
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = ProLockerPrimary,
                    indicator = { tabPositions ->
                        if (uiState.selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                                color = ProLockerPrimary,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = {
                            Text(
                                stringResource(R.string.call_blocker_tab_rules),
                                fontWeight = if (uiState.selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            Text(
                                stringResource(R.string.call_blocker_tab_history),
                                fontWeight = if (uiState.selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.call_blocker_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    singleLine = true
                )

                when (uiState.selectedTab) {
                    0 -> BlockedRulesTabContent(
                        rules = uiState.blockedRules,
                        searchQuery = uiState.searchQuery,
                        onToggleRule = { rule -> viewModel.toggleRule(rule) },
                        onDeleteRule = { rule -> viewModel.setRuleToDelete(rule) }
                    )
                    1 -> BlockedHistoryTabContent(
                        history = uiState.blockedHistory,
                        searchQuery = uiState.searchQuery,
                        onClearHistory = { viewModel.clearHistory() },
                        formatJalaliDate = { viewModel.formatJalaliDate(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockedRulesTabContent(
    rules: List<BlockedNumberEntity>,
    searchQuery: String,
    onToggleRule: (BlockedNumberEntity) -> Unit,
    onDeleteRule: (BlockedNumberEntity) -> Unit
) {
    val filtered = remember(rules, searchQuery) {
        if (searchQuery.isBlank()) rules else rules.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.numberOrPattern.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filtered.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.call_blocker_empty_rules),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.call_blocker_empty_rules_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { rule ->
                BlockedRuleRow(
                    rule = rule,
                    onToggle = { onToggleRule(rule) },
                    onDelete = { onDeleteRule(rule) }
                )
            }
        }
    }
}

@Composable
fun BlockedRuleRow(
    rule: BlockedNumberEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val icon: ImageVector = when (rule.sourceCategory) {
        BlockSourceCategory.CONTACT -> Icons.Outlined.Contacts
        BlockSourceCategory.CALL_LOG -> Icons.Outlined.Call
        BlockSourceCategory.MANUAL -> Icons.Outlined.Dialpad
        BlockSourceCategory.PATTERN -> Icons.Outlined.Pattern
    }

    val ruleBadge: String = when (rule.ruleType) {
        BlockRuleType.EXACT -> rule.numberOrPattern
        BlockRuleType.STARTS_WITH -> "Starts with: ${rule.numberOrPattern}"
        BlockRuleType.ENDS_WITH -> "Ends with: ${rule.numberOrPattern}"
        BlockRuleType.CONTAINS -> "Contains: ${rule.numberOrPattern}"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (rule.isEnabled) ProLockerPrimary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (rule.isEnabled) ProLockerPrimary else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ruleBadge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ProLockerPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.call_blocker_unblock),
                    tint = ProLockerError
                )
            }
        }
    }
}

@Composable
fun BlockedHistoryTabContent(
    history: List<BlockedCallLogEntity>,
    searchQuery: String,
    onClearHistory: () -> Unit,
    formatJalaliDate: (Long) -> String
) {
    val filtered = remember(history, searchQuery) {
        if (searchQuery.isBlank()) history else history.filter {
            (it.callerName ?: "").contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.call_blocker_clear_history), color = ProLockerError, fontSize = 13.sp)
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.call_blocker_empty_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.call_blocker_empty_history_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        ProLockerError.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = ProLockerError,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.callerName ?: item.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.phoneNumber} · Jalali: ${formatJalaliDate(item.timestampMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
