package com.carbon.prolocker.feature.hidefile.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class PickedMediaItem(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    val uri: Uri?,
    val duration: Long = 0L,
    val artist: String? = null,
    val album: String? = null,
    val albumId: Long = 0L
) {
    companion object {
        val EMPTY = PickedMediaItem(0, "", "", 0L, null)
    }
}

object MediaStoreQueries {

    private const val ID = MediaStore.MediaColumns._ID
    private const val DATA = MediaStore.MediaColumns.DATA
    private const val NAME = MediaStore.MediaColumns.DISPLAY_NAME
    private const val SIZE = MediaStore.MediaColumns.SIZE
    private const val DATE_ADDED = MediaStore.MediaColumns.DATE_ADDED
    private const val DURATION = MediaStore.MediaColumns.DURATION
    private const val ARTIST = MediaStore.MediaColumns.ARTIST
    private const val ALBUM = MediaStore.MediaColumns.ALBUM
    private const val ALBUM_ID = MediaStore.Audio.Media.ALBUM_ID

    fun listImages(context: Context): List<PickedMediaItem> = query(
        context,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(ID, DATA, NAME, SIZE)
    )

    fun listVideos(context: Context): List<PickedMediaItem> = query(
        context,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(ID, DATA, NAME, SIZE, DURATION)
    )

    fun listAudio(context: Context): List<PickedMediaItem> = query(
        context,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(ID, DATA, NAME, SIZE, DURATION, ARTIST, ALBUM, ALBUM_ID)
    )

    private fun query(
        context: Context,
        collection: Uri,
        projection: Array<String>
    ): List<PickedMediaItem> {
        val result = mutableListOf<PickedMediaItem>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "$DATE_ADDED DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ID)
                val dataCol = cursor.getColumnIndex(DATA)
                val nameCol = cursor.getColumnIndexOrThrow(NAME)
                val sizeCol = cursor.getColumnIndex(SIZE)
                val durCol = cursor.getColumnIndex(DURATION)
                val artistCol = cursor.getColumnIndex(ARTIST)
                val albumCol = cursor.getColumnIndex(ALBUM)
                val albumIdCol = cursor.getColumnIndex(ALBUM_ID)
                while (cursor.moveToNext()) {
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    if (path.isEmpty()) continue
                    result.add(
                        PickedMediaItem(
                            id = cursor.getLong(idCol),
                            path = path,
                            name = cursor.getString(nameCol) ?: path.substringAfterLast('/'),
                            size = cursor.getLong(sizeCol),
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                            duration = if (durCol >= 0) cursor.getLong(durCol) else 0L,
                            artist = if (artistCol >= 0) cursor.getString(artistCol) else null,
                            album = if (albumCol >= 0) cursor.getString(albumCol) else null,
                            albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // no media / permission denied
        }
        return result
    }
}
