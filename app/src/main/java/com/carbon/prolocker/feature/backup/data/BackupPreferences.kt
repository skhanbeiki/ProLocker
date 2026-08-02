package com.carbon.prolocker.feature.backup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class BackupPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("prolocker_backup_prefs", Context.MODE_PRIVATE)

    private val _treeUriFlow = MutableStateFlow(getBackupTreeUri())
    val backupTreeUriFlow: Flow<String?> = _treeUriFlow.asStateFlow()

    private val _displayPathFlow = MutableStateFlow(getBackupDisplayPath())
    val backupDisplayPathFlow: Flow<String> = _displayPathFlow.asStateFlow()

    fun getDefaultBackupDir(): File {
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ProLockerBackup"
        )
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun getDefaultBackupPath(): String {
        return getDefaultBackupDir().absolutePath
    }

    fun getBackupTreeUri(): String? {
        return prefs.getString("backup_tree_uri", null)
    }

    fun getBackupDisplayPath(): String {
        return prefs.getString("backup_display_path", null) ?: getDefaultBackupPath()
    }

    suspend fun saveBackupLocation(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Permission might already be granted or not persistable
        }

        val displayPath = parseUriToReadablePath(uri)
        prefs.edit()
            .putString("backup_tree_uri", uri.toString())
            .putString("backup_display_path", displayPath)
            .apply()

        _treeUriFlow.value = uri.toString()
        _displayPathFlow.value = displayPath
    }

    private fun parseUriToReadablePath(uri: Uri): String {
        val path = uri.path ?: return uri.toString()
        return when {
            path.contains("primary:") -> {
                "/storage/emulated/0/" + path.substringAfter("primary:")
            }
            path.contains("document/") -> {
                path.substringAfter("document/")
            }
            else -> path
        }
    }
}
