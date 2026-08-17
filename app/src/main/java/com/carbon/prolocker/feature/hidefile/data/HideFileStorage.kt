package com.carbon.prolocker.feature.hidefile.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.Date

class HideFileStorage(val context: Context) {

    companion object {
        const val HIDE_FILE_DIR = ".hideFile"
        private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val storageRoot: File
        get() = Environment.getExternalStorageDirectory()

    val hiddenDir: File
        get() = File(storageRoot, HIDE_FILE_DIR)

    fun hiddenFile(item: HideItem): File = File(hiddenDir, ".${item.name}")

    // ---------------------------------------------------------------- sidecar metadata helpers

    fun writeSidecarMeta(item: HideItem) {
        try {
            val dir = hiddenDir
            if (!dir.exists()) dir.mkdirs()
            val metaFile = File(dir, ".${item.name}.meta")
            val jsonStr = json.encodeToString(HideItem.serializer(), item)
            metaFile.writeText(jsonStr)
        } catch (_: Exception) {
            // best-effort sidecar write
        }
    }

    fun readSidecarMeta(hiddenFile: File): HideItem? {
        return try {
            val metaFile = File(hiddenFile.parentFile, "${hiddenFile.name}.meta")
            if (!metaFile.exists()) return null
            val text = metaFile.readText()
            if (text.isBlank()) return null
            json.decodeFromString<HideItem>(text)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteSidecarMeta(item: HideItem) {
        try {
            val metaFile = File(hiddenDir, ".${item.name}.meta")
            if (metaFile.exists()) metaFile.delete()
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    // ---------------------------------------------------------------- permissions

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun needsAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    fun hasStorageAccess(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            else -> context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------------------------------------------------------- hide

    /**
     * Moves [path] into the hidden folder as `.<name>` (exact legacy format) and returns
     * the metadata to persist. Returns null when the rename fails.
     */
    fun hideFile(path: String, type: String, artBytes: ByteArray? = null): HideItem? {
        val from = File(path)
        if (!from.exists() || !from.isFile) return null
        val name = from.name
        if (name.isEmpty()) return null

        val size = from.length()
        val lastModified = from.lastModified()

        val dir = hiddenDir
        if (!dir.exists() && !dir.mkdirs()) return null

        val target = File(dir, ".$name")
        if (!from.renameTo(target)) return null

        val item = HideItem(
            name = name,
            path = computeRelDir(path),
            type = type,
            date = Date(lastModified).toString(),
            size = try {
                Formatter.formatShortFileSize(context, size)
            } catch (e: Exception) {
                "${size}B"
            },
            imagePath = if (type == HideItem.TYPE_AUDIO) "" else "",
            image = if (type == HideItem.TYPE_AUDIO) artBytes else null
        )
        writeSidecarMeta(item)
        return item
    }

    private fun computeRelDir(path: String): String {
        val storage = storageRoot.path
        val rel = path.removePrefix(storage)
        val idx = rel.lastIndexOf('/')
        return if (idx <= 0) "" else rel.substring(0, idx)
    }

    // ---------------------------------------------------------------- restore / unhide

    fun restore(item: HideItem): Boolean {
        val from = hiddenFile(item)
        if (!from.exists()) return false
        val dir = File(storageRoot, item.path)
        if (!dir.exists()) dir.mkdirs()
        val to = File(dir, item.name)
        if (!from.renameTo(to)) return false
        deleteSidecarMeta(item)
        addToMediaStore(item.type, to, item.path)
        return true
    }

    fun deleteHiddenFile(item: HideItem): Boolean {
        val deleted = hiddenFile(item).delete()
        deleteSidecarMeta(item)
        return deleted
    }

    // ---------------------------------------------------------------- media store helpers

    fun removeFromMediaStore(type: String, originalPath: String, originalSize: Long) {
        val collection = mediaCollection(type)
        val resolver = context.contentResolver
        var deleted = 0
        try {
            deleted = resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(originalPath)
            )
        } catch (e: Exception) {
            deleted = 0
        }
        if (deleted <= 0) {
            val fileName = File(originalPath).name
            try {
                val ids = mutableListOf<Long>()
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?",
                    arrayOf(fileName, originalSize.toString()),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        ids.add(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
                    }
                }
                for (id in ids) {
                    resolver.delete(collection, "${MediaStore.MediaColumns._ID}=?", arrayOf(id.toString()))
                }
            } catch (e: Exception) {
                // best-effort cleanup
            }
        }
        scanFile(originalPath)
    }

    private fun addToMediaStore(type: String, file: File, relDir: String) {
        scanFile(file.absolutePath)
    }

    fun extractAudioArt(path: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun scanFile(path: String) {
        if (path.isEmpty()) return
        try {
            val file = File(path)
            val mime = mimeType(path).ifEmpty { null }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                if (mime != null) arrayOf(mime) else null,
                null
            )
        } catch (e: Exception) {
            try {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(path)))
                )
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // ---------------------------------------------------------------- open / share

    fun open(item: HideItem) {
        val file = hiddenFile(item)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeForItem(item))
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // no handler available
        }
    }

    fun share(item: HideItem) {
        val file = hiddenFile(item)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeForItem(item)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // no handler available
        }
    }

    private fun mimeForItem(item: HideItem): String {
        if (item.type == HideItem.TYPE_FILE) return "*/*"
        val full = hiddenFile(item).absolutePath
        return mimeType(full).ifEmpty { "${item.type}/*" }
    }

    private fun mimeType(path: String): String {
        return try {
            val ext = path.substringAfterLast('.', "").lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ---------------------------------------------------------------- media collections

    private fun mediaCollection(type: String): Uri = collectionFor(mediaKind(type) ?: MediaKind.FILE)

    private fun mediaKind(type: String): MediaKind? = when (type) {
        HideItem.TYPE_IMAGE -> MediaKind.IMAGE
        HideItem.TYPE_VIDEO -> MediaKind.VIDEO
        HideItem.TYPE_AUDIO -> MediaKind.AUDIO
        HideItem.TYPE_FILE -> MediaKind.FILE
        else -> null
    }

    private fun collectionFor(kind: MediaKind): Uri = when (kind) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.FILE -> MediaStore.Files.getContentUri("external")
    }

    private enum class MediaKind {
        IMAGE, VIDEO, AUDIO, FILE
    }
}
