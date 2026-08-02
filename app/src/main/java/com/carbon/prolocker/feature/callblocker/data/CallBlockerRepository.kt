package com.carbon.prolocker.feature.callblocker.data

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.carbon.prolocker.feature.callblocker.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat

data class PickableContactItem(
    val name: String,
    val number: String
)

data class PickableCallLogItem(
    val number: String,
    val name: String?,
    val timestampMs: Long
)

class CallBlockerRepository(
    private val context: Context,
    private val dao: CallBlockerDao
) {
    val blockedNumbersFlow: Flow<List<BlockedNumberEntity>> = dao.getAllBlockedNumbersFlow()
    val blockedCallLogsFlow: Flow<List<BlockedCallLogEntity>> = dao.getAllBlockedCallLogsFlow()
    val blockedCallCountFlow: Flow<Int> = dao.getBlockedCallCountFlow()

    suspend fun getActiveRules(): List<BlockedNumberEntity> = withContext(Dispatchers.IO) {
        dao.getActiveBlockedNumbersList()
    }

    suspend fun addBlockedRule(
        numberOrPattern: String,
        displayName: String,
        ruleType: BlockRuleType,
        sourceCategory: BlockSourceCategory
    ) = withContext(Dispatchers.IO) {
        val entity = BlockedNumberEntity(
            numberOrPattern = numberOrPattern.trim(),
            displayName = displayName.ifBlank { numberOrPattern.trim() },
            ruleType = ruleType,
            sourceCategory = sourceCategory,
            isEnabled = true
        )
        dao.insertBlockedNumber(entity)
    }

    suspend fun toggleRuleEnabled(rule: BlockedNumberEntity) = withContext(Dispatchers.IO) {
        dao.updateBlockedNumber(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun deleteRule(rule: BlockedNumberEntity) = withContext(Dispatchers.IO) {
        dao.deleteBlockedNumber(rule)
    }

    suspend fun deleteRuleById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteBlockedNumberById(id)
    }

    suspend fun logBlockedCall(phoneNumber: String, callerName: String?, matchedRule: String) = withContext(Dispatchers.IO) {
        val entity = BlockedCallLogEntity(
            phoneNumber = phoneNumber,
            callerName = callerName,
            matchedRule = matchedRule
        )
        dao.insertBlockedCallLog(entity)
    }

    suspend fun deleteCallLog(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteBlockedCallLogById(id)
    }

    suspend fun clearCallLogs() = withContext(Dispatchers.IO) {
        dao.deleteAllBlockedCallLogs()
    }

    /**
     * Queries device Contacts database
     */
    suspend fun getDeviceContacts(): List<PickableContactItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PickableContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                    if (number.isNotBlank()) {
                        list.add(PickableContactItem(name = name, number = number))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list.distinctBy { PhoneNumberUtils.normalize(it.number) }
    }

    /**
     * Queries device Call Logs database
     */
    suspend fun getDeviceCallLogs(): List<PickableCallLogItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PickableCallLogItem>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)

                while (cursor.moveToNext()) {
                    val number = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L

                    if (number.isNotBlank()) {
                        list.add(PickableCallLogItem(number = number, name = name, timestampMs = date))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list.distinctBy { PhoneNumberUtils.normalize(it.number) }
    }

    fun formatJalaliDate(timestampMs: Long): String {
        if (timestampMs <= 0) return "—"
        return try {
            val pDate = PersianDate(timestampMs)
            val pdFormater = PersianDateFormat("Y/m/d H:i")
            pdFormater.format(pDate)
        } catch (e: Exception) {
            "—"
        }
    }
}
