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
import java.io.File
import java.io.FileInputStream
import java.util.Date

class HideFileStorage(val context: Context) {

    companion object {
        const val HIDE_FILE_DIR = ".hideFile"
        private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }

    val storageRoot: File
        get() = Environment.getExternalStorageDirectory()

    val hiddenDir: File
        get() = File(storageRoot, HIDE_FILE_DIR)

    fun hiddenFile(item: HideItem): File = File(hiddenDir, ".${item.name}")

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

        return HideItem(
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
        addToMediaStore(item.type, to, item.path)
        return true
    }

    fun deleteHiddenFile(item: HideItem): Boolean = hiddenFile(item).delete()

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
        val kind = mediaKind(type) ?: return
        val collection = collectionFor(kind)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(file.absolutePath))
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.MediaColumns.SIZE, file.length())
        }
        fillMediaMetadata(kind, file, values)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Try absolute-path insert first (works with legacy storage / all-files access);
            // fall back to a pending-copy insert for scoped storage.
            val inserted = try {
                values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
                resolver.insert(collection, values) != null
            } catch (e: Exception) {
                insertWithPendingCopy(kind, collection, values, file, relDir)
            }
            if (!inserted) return
        } else {
            values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
            resolver.insert(collection, values)
        }
        scanFile(file.absolutePath)
    }

    private fun insertWithPendingCopy(
        kind: MediaKind,
        collection: Uri,
        values: ContentValues,
        file: File,
        relDir: String
    ): Boolean {
        val resolver = context.contentResolver
        return try {
            val copyValues = ContentValues(values)
            copyValues.remove(MediaStore.MediaColumns.DATA)
            copyValues.remove(MediaStore.MediaColumns.SIZE)
            copyValues.put(MediaStore.MediaColumns.RELATIVE_PATH, sanitizeRelDir(kind, relDir))
            copyValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri = resolver.insert(collection, copyValues) ?: return false
            try {
                val out = resolver.openOutputStream(uri) ?: return false
                out.use { output ->
                    FileInputStream(file).use { input ->
                        val buf = ByteArray(64 * 1024)
                        var len: Int
                        while (input.read(buf).also { len = it } != -1) {
                            output.write(buf, 0, len)
                        }
                    }
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitizeRelDir(kind: MediaKind, relDir: String): String {
        val clean = relDir.trimStart('/')
        return when (kind) {
            MediaKind.IMAGE ->
                if (clean.isEmpty() || clean.startsWith("DCIM/") || clean.startsWith("Pictures/") ||
                    clean.startsWith("Movies/")
                ) clean.ifEmpty { "Pictures" } else "Pictures/$clean"

            MediaKind.VIDEO ->
                if (clean.isEmpty() || clean.startsWith("Movies/") || clean.startsWith("DCIM/") ||
                    clean.startsWith("Pictures/")
                ) clean.ifEmpty { "Movies" } else "Movies/$clean"

            MediaKind.AUDIO ->
                if (clean.isEmpty() || clean.startsWith("Music/") || clean.startsWith("Alarms/") ||
                    clean.startsWith("Ringtones/") || clean.startsWith("Notifications/")
                ) clean.ifEmpty { "Music" } else "Music/$clean"

            MediaKind.FILE ->
                if (clean.startsWith("Download/")) clean else "Download/${clean.ifEmpty { "Hidden" }}"
        }
    }

    private fun fillMediaMetadata(kind: MediaKind, file: File, values: ContentValues) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            when (kind) {
                MediaKind.IMAGE -> {
                    values.put(MediaStore.Images.Media.TITLE, file.nameWithoutExtension)
                    val dateTaken = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        ?.toLongOrNull()
                        ?: System.currentTimeMillis()
                    values.put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
                }

                MediaKind.VIDEO -> {
                    values.put(
                        MediaStore.Video.Media.DURATION,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    )
                }

                MediaKind.AUDIO -> {
                    values.put(MediaStore.Audio.Media.ALBUM, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                    values.put(MediaStore.Audio.Media.ARTIST, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    values.put(MediaStore.Audio.Media.TITLE, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                    values.put(MediaStore.Audio.Media.IS_MUSIC, true)
                    values.put(MediaStore.Audio.AudioColumns.DISPLAY_NAME, file.nameWithoutExtension)
                }

                MediaKind.FILE -> Unit
            }
        } catch (e: Exception) {
            // metadata is best-effort
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
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
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(path)))
            )
        } catch (e: Exception) {
            // ignore
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
