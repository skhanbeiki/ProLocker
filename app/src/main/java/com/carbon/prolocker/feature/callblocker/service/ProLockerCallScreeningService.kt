package com.carbon.prolocker.feature.callblocker.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.carbon.prolocker.feature.callblocker.data.CallBlockerRepository
import com.carbon.prolocker.feature.callblocker.util.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RequiresApi(Build.VERSION_CODES.Q)
class ProLockerCallScreeningService : CallScreeningService(), KoinComponent {

    private val repository: CallBlockerRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawHandle = callDetails.handle?.schemeSpecificPart ?: ""
        if (rawHandle.isBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        serviceScope.launch {
            val activeRules = repository.getActiveRules()
            val (isBlocked, matchedRule) = PhoneNumberUtils.checkMatch(rawHandle, activeRules)

            if (isBlocked && matchedRule != null) {
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .setSkipCallLog(false)
                    .build()

                respondToCall(callDetails, response)

                // Log the blocked call event
                repository.logBlockedCall(
                    phoneNumber = rawHandle,
                    callerName = matchedRule.displayName,
                    matchedRule = matchedRule.numberOrPattern
                )
            } else {
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }
}
