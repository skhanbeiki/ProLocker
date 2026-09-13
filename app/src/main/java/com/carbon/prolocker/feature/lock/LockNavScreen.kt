package com.carbon.prolocker.feature.lock

sealed class LockNavScreen {
    data object Lock : LockNavScreen()
    data object Gallery : LockNavScreen()
    data class Preview(val url: String, val id: Int, val fromDownloaded: Boolean = false) : LockNavScreen()
    data object MemoryOptimizer : LockNavScreen()
}
