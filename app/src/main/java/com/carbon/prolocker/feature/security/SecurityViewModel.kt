package com.carbon.prolocker.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbon.prolocker.core.database.IntruderEventDao
import com.carbon.prolocker.core.database.IntruderEventEntity
import com.carbon.prolocker.core.database.SecurityEventEntity
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.datastore.UserPreferences
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.IntruderManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecurityViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val intruderEventDao: IntruderEventDao,
    private val intruderManager: IntruderManager,
    private val eventLogManager: EventLogManager
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val intruderEvents: StateFlow<List<IntruderEventEntity>> = intruderEventDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val lockHistoryEvents: StateFlow<List<SecurityEventEntity>> = eventLogManager.getRecentEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleCaptureSelfie(capture: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(captureIntruderSelfie = capture) }
        }
    }

    fun toggleAlarm(trigger: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePreferences { it.copy(triggerAlarm = trigger) }
            if (!trigger) {
                intruderManager.stopAlarm()
            }
        }
    }

    fun deleteIntruderEvent(event: IntruderEventEntity) {
        viewModelScope.launch {
            intruderEventDao.deleteEvent(event)
        }
    }

    fun clearAllIntruderEvents() {
        viewModelScope.launch {
            intruderEventDao.deleteAllEvents()
        }
    }

    fun clearAllLockHistory() {
        viewModelScope.launch {
            eventLogManager.deleteAllEvents()
        }
    }
}
