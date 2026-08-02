package com.carbon.prolocker.feature.callblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carbon.prolocker.R
import com.carbon.prolocker.core.theme.ProLockerPrimary
import com.carbon.prolocker.feature.callblocker.data.BlockRuleType
import com.carbon.prolocker.feature.callblocker.data.BlockSourceCategory
import com.carbon.prolocker.feature.callblocker.data.PickableCallLogItem
import com.carbon.prolocker.feature.callblocker.data.PickableContactItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockRuleDialog(
    contacts: List<PickableContactItem>,
    callLogs: List<PickableCallLogItem>,
    isLoadingPickers: Boolean,
    onDismiss: () -> Unit,
    onAddRule: (numberOrPattern: String, displayName: String, ruleType: BlockRuleType, sourceCategory: BlockSourceCategory) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) } // 0: Contacts, 1: Call Logs, 2: Specific Number, 3: Pattern

    var searchQuery by remember { mutableStateOf("") }
    var customNumber by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    var patternText by remember { mutableStateOf("") }
    var patternName by remember { mutableStateOf("") }
    var selectedPatternType by remember { mutableStateOf(BlockRuleType.STARTS_WITH) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.call_blocker_add_rule),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ProLockerPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ProLockerPrimary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.call_blocker_mode_contacts), fontSize = 12.sp, maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.call_blocker_mode_call_logs), fontSize = 12.sp, maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.call_blocker_mode_custom), fontSize = 12.sp, maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(stringResource(R.string.call_blocker_mode_pattern), fontSize = 12.sp, maxLines = 1) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> ContactsPickerTabContent(
                    contacts = contacts,
                    isLoading = isLoadingPickers,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    onSelectContact = { contact ->
                        onAddRule(contact.number, contact.name, BlockRuleType.EXACT, BlockSourceCategory.CONTACT)
                    }
                )
                1 -> CallLogsPickerTabContent(
                    callLogs = callLogs,
                    isLoading = isLoadingPickers,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    onSelectCallLog = { callLog ->
                        onAddRule(callLog.number, callLog.name ?: callLog.number, BlockRuleType.EXACT, BlockSourceCategory.CALL_LOG)
                    }
                )
                2 -> CustomNumberTabContent(
                    customNumber = customNumber,
                    customName = customName,
                    onNumberChanged = { customNumber = it },
                    onNameChanged = { customName = it },
                    onSave = {
                        if (customNumber.isNotBlank()) {
                            onAddRule(customNumber, customName, BlockRuleType.EXACT, BlockSourceCategory.MANUAL)
                        }
                    }
                )
                3 -> PatternTabContent(
                    patternText = patternText,
                    patternName = patternName,
                    selectedPatternType = selectedPatternType,
                    onPatternTextChanged = { patternText = it },
                    onPatternNameChanged = { patternName = it },
                    onPatternTypeSelected = { selectedPatternType = it },
                    onSave = {
                        if (patternText.isNotBlank()) {
                            val ruleTypeName = when (selectedPatternType) {
                                BlockRuleType.STARTS_WITH -> "Starts with: $patternText"
                                BlockRuleType.ENDS_WITH -> "Ends with: $patternText"
                                BlockRuleType.CONTAINS -> "Contains: $patternText"
                                else -> patternText
                            }
                            onAddRule(patternText, patternName.ifBlank { ruleTypeName }, selectedPatternType, BlockSourceCategory.PATTERN)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ContactsPickerTabContent(
    contacts: List<PickableContactItem>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSelectContact: (PickableContactItem) -> Unit
) {
    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProLockerPrimary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.number + it.name }) { item ->
                    PickerRowItem(
                        icon = Icons.Outlined.Contacts,
                        title = item.name.ifBlank { item.number },
                        subtitle = item.number,
                        onClick = { onSelectContact(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogsPickerTabContent(
    callLogs: List<PickableCallLogItem>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSelectCallLog: (PickableCallLogItem) -> Unit
) {
    val filtered = remember(callLogs, searchQuery) {
        if (searchQuery.isBlank()) callLogs else callLogs.filter {
            (it.name ?: "").contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProLockerPrimary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.number + it.timestampMs }) { item ->
                    PickerRowItem(
                        icon = Icons.Outlined.Call,
                        title = item.name ?: item.number,
                        subtitle = item.number,
                        onClick = { onSelectCallLog(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomNumberTabContent(
    customNumber: String,
    customName: String,
    onNumberChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        OutlinedTextField(
            value = customNumber,
            onValueChange = onNumberChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_enter_number)) },
            leadingIcon = { Icon(Icons.Outlined.Dialpad, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = customName,
            onValueChange = onNameChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_enter_name)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            enabled = customNumber.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(stringResource(R.string.call_blocker_btn_save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PatternTabContent(
    patternText: String,
    patternName: String,
    selectedPatternType: BlockRuleType,
    onPatternTextChanged: (String) -> Unit,
    onPatternNameChanged: (String) -> Unit,
    onPatternTypeSelected: (BlockRuleType) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Select Pattern Criteria:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onPatternTypeSelected(BlockRuleType.STARTS_WITH) }) {
                RadioButton(
                    selected = selectedPatternType == BlockRuleType.STARTS_WITH,
                    onClick = { onPatternTypeSelected(BlockRuleType.STARTS_WITH) },
                    colors = RadioButtonDefaults.colors(selectedColor = ProLockerPrimary)
                )
                Text(stringResource(R.string.call_blocker_pattern_starts_with), fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onPatternTypeSelected(BlockRuleType.ENDS_WITH) }) {
                RadioButton(
                    selected = selectedPatternType == BlockRuleType.ENDS_WITH,
                    onClick = { onPatternTypeSelected(BlockRuleType.ENDS_WITH) },
                    colors = RadioButtonDefaults.colors(selectedColor = ProLockerPrimary)
                )
                Text(stringResource(R.string.call_blocker_pattern_ends_with), fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onPatternTypeSelected(BlockRuleType.CONTAINS) }) {
                RadioButton(
                    selected = selectedPatternType == BlockRuleType.CONTAINS,
                    onClick = { onPatternTypeSelected(BlockRuleType.CONTAINS) },
                    colors = RadioButtonDefaults.colors(selectedColor = ProLockerPrimary)
                )
                Text(stringResource(R.string.call_blocker_pattern_contains), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = patternText,
            onValueChange = onPatternTextChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_enter_pattern)) },
            leadingIcon = { Icon(Icons.Outlined.Pattern, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = patternName,
            onValueChange = onPatternNameChanged,
            placeholder = { Text(stringResource(R.string.call_blocker_enter_name)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            enabled = patternText.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProLockerPrimary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(stringResource(R.string.call_blocker_btn_save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PickerRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(ProLockerPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProLockerPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
