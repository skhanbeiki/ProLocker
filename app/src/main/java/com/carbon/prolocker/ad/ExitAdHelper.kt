package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.network.repository.RemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.carbon.prolocker.core.language.findActivity

fun triggerExitInterstitialAd(
    context: Context,
    coroutineScope: CoroutineScope,
    preferencesRepository: PreferencesRepository,
    remoteConfigRepository: RemoteConfigRepository,
    adManager: AdManager,
    placement: String,
    onBack: () -> Unit
) {
    onBack()
}
