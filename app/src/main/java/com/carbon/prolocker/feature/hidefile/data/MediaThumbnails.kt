package com.carbon.prolocker.feature.hidefile.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaThumbnails {

    /**
     * Extracts a frame thumbnail from a video file on disk (works for hidden video files).
     */
    suspend fun videoFileThumbnail(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (e: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(file, Size(320, 320), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                }
            } catch (err: Throwable) {
                null
            }
        } finally {
            try {
                retriever?.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Loads a video frame thumbnail using Android's recommended
     * MediaStore.Video.Thumbnails API (MINI_KIND).
     */
    suspend fun videoThumbnail(context: Context, id: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (id <= 0L) return@withContext null
        try {
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Resolves the album artwork file path via MediaStore.Audio.Albums.
     */
    suspend fun albumArtPath(context: Context, albumId: Long): String? = withContext(Dispatchers.IO) {
        if (albumId <= 0L) return@withContext null
        try {
            val projection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Albums._ID}=?",
                arrayOf(albumId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Throwable) {
            null
        }
    }
}
