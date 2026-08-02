package com.carbon.prolocker.feature.hidefile.data

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaThumbnails {

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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            null
        }
    }
}
