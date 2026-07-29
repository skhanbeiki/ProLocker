package com.carbon.prolocker.feature.lock

import androidx.compose.runtime.mutableStateOf

class LockOverlayState(
    initialPackage: String,
    val forSettings: Boolean
) {
    val packageName = mutableStateOf(initialPackage)
}
