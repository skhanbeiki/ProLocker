package com.carbon.prolocker.feature.lock

sealed class LockNavScreen {
    data object Lock : LockNavScreen()
    data object Gallery : LockNavScreen()
    data class Preview(val url: String, val id: Int) : LockNavScreen()
    data object MemoryOptimizer : LockNavScreen()
}
