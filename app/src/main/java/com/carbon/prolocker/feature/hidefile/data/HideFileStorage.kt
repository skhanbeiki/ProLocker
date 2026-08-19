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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
        get() {
            val appExt = context.getExternalFilesDir(null)
            if (appExt != null) {
                val extDir = File(appExt, HIDE_FILE_DIR)
                if (extDir.exists() || extDir.mkdirs()) {
                    return extDir
                }
            }
            val internal = File(context.filesDir, HIDE_FILE_DIR)
            if (!internal.exists()) internal.mkdirs()
            return internal
        }

    fun hiddenFile(item: HideItem): File {
        val primary = File(hiddenDir, ".${item.name}")
        if (primary.exists()) return primary
        val legacy = File(File(storageRoot, HIDE_FILE_DIR), ".${item.name}")
        if (legacy.exists()) return legacy
        val appExtDir = context.getExternalFilesDir(null)?.let { File(it, HIDE_FILE_DIR) }
        if (appExtDir != null) {
            val appExtFile = File(appExtDir, ".${item.name}")
            if (appExtFile.exists()) return appExtFile
        }
        val internalDir = File(context.filesDir, HIDE_FILE_DIR)
        val internalFile = File(internalDir, ".${item.name}")
        if (internalFile.exists()) return internalFile
        return primary
    }

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
            val legacyMeta = File(File(storageRoot, HIDE_FILE_DIR), ".${item.name}.meta")
            if (legacyMeta.exists()) legacyMeta.delete()
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    // ---------------------------------------------------------------- permissions

    fun hasAllFilesAccess(): Boolean = false

    fun needsAllFilesAccess(): Boolean = false

    fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ---------------------------------------------------------------- hide

    /**
     * Moves [path] into the hidden folder as `.<name>` and returns
     * the metadata to persist. Falls back to stream copy + delete for Scoped Storage compatibility.
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
        var moved = from.renameTo(target)
        if (!moved) {
            moved = try {
                FileInputStream(from).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                from.delete()
                true
            } catch (_: Exception) {
                if (target.exists()) target.delete()
                false
            }
        }
        if (!moved) return null

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

    fun hideUri(uri: Uri, type: String): HideItem? {
        val resolver = context.contentResolver
        var fileName = "file_${System.currentTimeMillis()}"
        var size = 0L
        try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        val dir = hiddenDir
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, ".$fileName")

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
        } catch (_: Exception) {
            if (target.exists()) target.delete()
            return null
        }

        if (size == 0L && target.exists()) {
            size = target.length()
        }

        val item = HideItem(
            name = fileName,
            path = defaultRelPath(type),
            type = type,
            date = Date().toString(),
            size = try {
                Formatter.formatShortFileSize(context, size)
            } catch (e: Exception) {
                "${size}B"
            },
            imagePath = "",
            image = null
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

        // Attempt 1: Direct filesystem restoration
        try {
            val targetDir = File(storageRoot, item.path.ifEmpty { defaultRelPath(item.type) })
            if (!targetDir.exists()) targetDir.mkdirs()
            val to = File(targetDir, item.name)
            if (from.renameTo(to)) {
                deleteSidecarMeta(item)
                addToMediaStore(item.type, to)
                return true
            }
            // Stream copy fallback
            FileInputStream(from).use { input ->
                FileOutputStream(to).use { output ->
                    input.copyTo(output)
                }
            }
            from.delete()
            deleteSidecarMeta(item)
            addToMediaStore(item.type, to)
            return true
        } catch (_: Exception) {
            // Fall through to MediaStore insert for Scoped Storage
        }

        // Attempt 2: MediaStore insertion for Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val collection = mediaCollection(item.type)
                val relPath = when (item.type) {
                    HideItem.TYPE_IMAGE -> Environment.DIRECTORY_PICTURES + "/Restored"
                    HideItem.TYPE_VIDEO -> Environment.DIRECTORY_MOVIES + "/Restored"
                    HideItem.TYPE_AUDIO -> Environment.DIRECTORY_MUSIC + "/Restored"
                    else -> Environment.DIRECTORY_DOWNLOADS + "/Restored"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeForItem(item))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(from).use { inp ->
                            inp.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    from.delete()
                    deleteSidecarMeta(item)
                    return true
                }
            } catch (_: Exception) {
                // Ignore and return false
            }
        }

        return false
    }

    private fun defaultRelPath(type: String): String = when (type) {
        HideItem.TYPE_IMAGE -> "Pictures/Restored"
        HideItem.TYPE_VIDEO -> "Movies/Restored"
        HideItem.TYPE_AUDIO -> "Music/Restored"
        else -> "Download/Restored"
    }

    fun deleteHiddenFile(item: HideItem): Boolean {
        val file = hiddenFile(item)
        val deleted = file.delete()
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

    private fun addToMediaStore(type: String, file: File) {
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
        MediaKind.FILE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    private enum class MediaKind {
        IMAGE, VIDEO, AUDIO, FILE
    }
}
