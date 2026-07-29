package com.carbon.prolocker.core.navigation

object RestrictedModeManager {
    @Volatile
    var isRestricted: Boolean = false
        private set

    @Volatile
    var restrictedDestination: String? = null
        private set

    fun enterRestrictedMode(destination: String) {
        isRestricted = true
        restrictedDestination = destination
    }

    fun exitRestrictedMode() {
        isRestricted = false
        restrictedDestination = null
    }
}
