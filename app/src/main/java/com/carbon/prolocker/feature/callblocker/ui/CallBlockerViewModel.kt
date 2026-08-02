package com.carbon.prolocker.feature.callblocker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.R
import com.carbon.prolocker.feature.callblocker.data.BlockRuleType
import com.carbon.prolocker.feature.callblocker.data.BlockSourceCategory
import com.carbon.prolocker.feature.callblocker.data.BlockedCallLogEntity
import com.carbon.prolocker.feature.callblocker.data.BlockedNumberEntity
import com.carbon.prolocker.feature.callblocker.data.CallBlockerRepository
import com.carbon.prolocker.feature.callblocker.data.PickableCallLogItem
import com.carbon.prolocker.feature.callblocker.data.PickableContactItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CallBlockerUiState(
    val selectedTab: Int = 0, // 0 = Blacklist Rules, 1 = History
    val searchQuery: String = "",
    val blockedRules: List<BlockedNumberEntity> = emptyList(),
    val blockedHistory: List<BlockedCallLogEntity> = emptyList(),
    val totalBlockedCallsCount: Int = 0,
    val deviceContacts: List<PickableContactItem> = emptyList(),
    val deviceCallLogs: List<PickableCallLogItem> = emptyList(),
    val isLoadingPickers: Boolean = false,
    val showAddDialog: Boolean = false,
    val ruleToDelete: BlockedNumberEntity? = null,
    val userMessage: String? = null
)

class CallBlockerViewModel(
    private val context: Context,
    private val repository: CallBlockerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallBlockerUiState())
    val uiState: StateFlow<CallBlockerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.blockedNumbersFlow.collect { rules ->
                _uiState.value = _uiState.value.copy(blockedRules = rules)
            }
        }
        viewModelScope.launch {
            repository.blockedCallLogsFlow.collect { logs ->
                _uiState.value = _uiState.value.copy(blockedHistory = logs)
            }
        }
        viewModelScope.launch {
            repository.blockedCallCountFlow.collect { count ->
                _uiState.value = _uiState.value.copy(totalBlockedCallsCount = count)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, isLoadingPickers = true)
        viewModelScope.launch {
            val contacts = repository.getDeviceContacts()
            val callLogs = repository.getDeviceCallLogs()
            _uiState.value = _uiState.value.copy(
                deviceContacts = contacts,
                deviceCallLogs = callLogs,
                isLoadingPickers = false
            )
        }
    }

    fun closeAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addRule(
        numberOrPattern: String,
        displayName: String,
        ruleType: BlockRuleType,
        sourceCategory: BlockSourceCategory
    ) {
        viewModelScope.launch {
            repository.addBlockedRule(numberOrPattern, displayName, ruleType, sourceCategory)
            _uiState.value = _uiState.value.copy(
                showAddDialog = false,
                userMessage = context.getString(R.string.call_blocker_msg_rule_added)
            )
        }
    }

    fun toggleRule(rule: BlockedNumberEntity) {
        viewModelScope.launch {
            repository.toggleRuleEnabled(rule)
        }
    }

    fun setRuleToDelete(rule: BlockedNumberEntity?) {
        _uiState.value = _uiState.value.copy(ruleToDelete = rule)
    }

    fun confirmDeleteRule() {
        val rule = _uiState.value.ruleToDelete ?: return
        viewModelScope.launch {
            repository.deleteRule(rule)
            _uiState.value = _uiState.value.copy(
                ruleToDelete = null,
                userMessage = context.getString(R.string.call_blocker_msg_rule_deleted)
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearCallLogs()
            _uiState.value = _uiState.value.copy(
                userMessage = context.getString(R.string.call_blocker_msg_history_cleared)
            )
        }
    }

    fun formatJalaliDate(timestampMs: Long): String {
        return repository.formatJalaliDate(timestampMs)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
