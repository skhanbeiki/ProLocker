package com.carbon.prolocker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.carbon.prolocker.network.model.BackgroundFile
import com.carbon.prolocker.network.model.BackgroundImage
import com.carbon.prolocker.network.model.BackgroundItem

@Entity(tableName = "downloaded_backgrounds")
data class DownloadedBackgroundEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val category: String? = null,
    val color: String? = null,
    val localPath: String,
    val photoThumb: String,
    val photoGallery: String,
    val photoThumb2x: String,
    val downloadCount: Int = 0,
    val tags: String = "",
    val created: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toBackgroundItem(): BackgroundItem {
        val parsedTags = if (tags.isNotEmpty()) tags.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        return BackgroundItem(
            id = id,
            name = name,
            category = category,
            color = color,
            image = BackgroundImage(
                file = BackgroundFile(
                    photoThumb = photoThumb,
                    photoThumb2x = photoThumb2x,
                    photoGallery = photoGallery
                ),
                width = width,
                height = height
            ),
            downloadCount = downloadCount,
            tags = parsedTags,
            created = created
        )
    }
}
