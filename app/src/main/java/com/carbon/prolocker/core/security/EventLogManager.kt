package com.carbon.prolocker.core.security

import com.carbon.prolocker.core.database.SecurityEventDao
import com.carbon.prolocker.core.database.SecurityEventEntity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class EventLogManager(private val securityEventDao: SecurityEventDao) {

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.IO + scopeExceptionHandler)

    fun logEvent(eventType: String, packageName: String? = null, details: String? = null) {
        scope.launch {
            securityEventDao.insert(
                SecurityEventEntity(
                    eventType = eventType,
                    packageName = packageName,
                    details = details
                )
            )
            // Trim old events periodically
            if (Math.random() < 0.1) {
                securityEventDao.trimOldEvents()
            }
        }
    }

    fun getRecentEvents(): Flow<List<SecurityEventEntity>> {
        return securityEventDao.getRecentEvents()
    }

    suspend fun getEventCount(): Int {
        return securityEventDao.getEventCount()
    }

    suspend fun deleteAllEvents() {
        securityEventDao.deleteAllEvents()
    }
}
