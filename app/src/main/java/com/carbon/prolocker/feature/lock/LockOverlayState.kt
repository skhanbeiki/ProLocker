package com.carbon.prolocker.feature.lock

import androidx.compose.runtime.mutableStateOf

/**
 * Service-level state read by the lock composable. Being a Compose state, switching the
 * locked package in place recomposes the existing overlay instead of re-adding the window.
 */
class LockOverlayState(initialPackage: String = "") {
    val packageName = mutableStateOf(initialPackage)
}
