package com.carbon.prolocker.feature.callblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.carbon.prolocker.feature.callblocker.data.CallBlockerRepository
import com.carbon.prolocker.feature.callblocker.util.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProLockerCallReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: CallBlockerRepository by inject()
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
            if (incomingNumber.isBlank()) return

            receiverScope.launch {
                val activeRules = repository.getActiveRules()
                val (isBlocked, matchedRule) = PhoneNumberUtils.checkMatch(incomingNumber, activeRules)

                if (isBlocked && matchedRule != null) {
                    endIncomingCall(context)
                    repository.logBlockedCall(
                        phoneNumber = incomingNumber,
                        callerName = matchedRule.displayName,
                        matchedRule = matchedRule.numberOrPattern
                    )
                }
            }
        }
    }

    private fun endIncomingCall(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.endCall()
            } else {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val clazz = Class.forName(telephonyManager?.javaClass?.name ?: "")
                val method = clazz.getDeclaredMethod("getITelephony")
                method.isAccessible = true
                val iTelephony = method.invoke(telephonyManager)
                val endCallMethod = iTelephony?.javaClass?.getDeclaredMethod("endCall")
                endCallMethod?.invoke(iTelephony)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
