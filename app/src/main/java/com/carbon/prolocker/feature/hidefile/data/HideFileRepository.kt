package com.carbon.prolocker.feature.hidefile.data

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class HideFileRepository(
    private val database: HideFileDatabase,
    private val storage: HideFileStorage
) {

    private val _items = MutableStateFlow<List<HideItem>>(emptyList())
    val items: StateFlow<List<HideItem>> = _items.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    init {
        refresh()
    }

    fun itemsFor(type: String): List<HideItem> = _items.value.filter { it.type == type }

    fun hasStorageAccess(): Boolean = storage.hasStorageAccess()

    fun needsAllFilesAccess(): Boolean = storage.needsAllFilesAccess()

    fun refresh() {
        var loaded = database.getAllItems()
        if (loaded.isEmpty()) {
            restoreFromJsonBackup()
            loaded = database.getAllItems()
        }
        _items.value = loaded
    }

    /**
     * Hides the given real file paths. Each entry is (absolutePath, type).
     */
    suspend fun hide(selected: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        for ((path, type) in selected) {
            val from = File(path)
            if (!from.exists() || !from.isFile) continue
            val size = from.length()
            val artBytes = if (type == HideItem.TYPE_AUDIO) {
                try {
                    storage.extractAudioArt(path)
                } catch (e: Exception) {
                    null
                }
            } else null
            val item = storage.hideFile(path, type, artBytes) ?: continue
            database.addItem(item)
            storage.removeFromMediaStore(type, path, size)
        }
        refresh()
        writeJsonBackup()
    }

    suspend fun unhide(item: HideItem): Boolean = withContext(Dispatchers.IO) {
        val restored = storage.restore(item)
        if (restored) {
            database.deleteEntry(item.name)
        }
        refresh()
        writeJsonBackup()
        restored
    }

    suspend fun delete(item: HideItem): Boolean = withContext(Dispatchers.IO) {
        val deleted = storage.deleteHiddenFile(item)
        if (deleted) {
            database.deleteEntry(item.name)
        }
        refresh()
        writeJsonBackup()
        deleted
    }

    fun open(item: HideItem) = storage.open(item)

    fun share(item: HideItem) = storage.share(item)

    // ---------------------------------------------------------------- legacy JSON backup

    private val backupFile: File
        get() = File(
            File(Environment.getExternalStorageDirectory(), "Prolocker/backupAppData"),
            "hide.json"
        )

    private fun writeJsonBackup() {
        try {
            val file = backupFile
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ListSerializer(HideItem.serializer()), _items.value))
        } catch (e: Exception) {
            // backup is best-effort
        }
    }

    private fun restoreFromJsonBackup() {
        try {
            val file = backupFile
            if (!file.exists()) return
            val text = file.readText()
            if (text.isBlank()) return
            val items = json.decodeFromString<List<HideItem>>(text)
            val dir = storage.hiddenDir
            for (item in items) {
                val hidden = File(dir, ".${item.name}")
                if (hidden.exists()) {
                    database.addItem(item)
                }
            }
        } catch (e: Exception) {
            // ignore corrupt/missing backup
        }
    }
}
