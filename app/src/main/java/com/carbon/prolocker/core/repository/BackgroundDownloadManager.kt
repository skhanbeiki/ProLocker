package com.carbon.prolocker.core.repository

import android.content.Context
import android.util.Log
import com.carbon.prolocker.core.database.DownloadedBackgroundDao
import com.carbon.prolocker.core.database.DownloadedBackgroundEntity
import com.carbon.prolocker.core.domain.ReportBackgroundDownloadUseCase
import com.carbon.prolocker.network.model.BackgroundItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BackgroundDownloadManager(
    private val context: Context,
    private val httpClient: HttpClient,
    private val downloadedBackgroundDao: DownloadedBackgroundDao,
    private val backgroundRepository: BackgroundRepository,
    private val reportBackgroundDownloadUseCase: ReportBackgroundDownloadUseCase
) {
    val downloadedBackgroundsFlow: Flow<List<DownloadedBackgroundEntity>> =
        downloadedBackgroundDao.getAllFlow()

    val downloadedCountFlow: Flow<Int> =
        downloadedBackgroundDao.getCountFlow()

    fun isDownloadedFlow(id: Int): Flow<Boolean> =
        downloadedBackgroundDao.isDownloadedFlow(id)

    suspend fun isDownloaded(id: Int): Boolean =
        downloadedBackgroundDao.isDownloaded(id)

    suspend fun getDownloadedById(id: Int): DownloadedBackgroundEntity? =
        downloadedBackgroundDao.getById(id)

    suspend fun downloadBackground(
        item: BackgroundItem,
        packageName: String
    ): Result<DownloadedBackgroundEntity> = withContext(Dispatchers.IO) {
        try {
            val cached = backgroundRepository.getCachedBackground(item.id)
            val effectiveItem = if ((item.downloadCount == 0 || item.name.isEmpty()) && cached != null) {
                cached
            } else {
                item
            }

            // Priority: photo_thumb_2x -> photo_gallery -> photo_thumb
            val downloadUrl = effectiveItem.photoThumb2x.ifEmpty {
                effectiveItem.photoGallery.ifEmpty { effectiveItem.photoThumb }
            }

            if (downloadUrl.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No valid image URL found"))
            }

            val dir = File(context.filesDir, "backgrounds")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir, "bg_${effectiveItem.id}.jpg")
            val response = httpClient.get(downloadUrl)
            val channel: ByteReadChannel = response.bodyAsChannel()

            FileOutputStream(file).use { output ->
                val input = channel.toInputStream()
                input.copyTo(output)
            }

            val countToSave = if (effectiveItem.downloadCount > 0) {
                effectiveItem.downloadCount
            } else {
                cached?.downloadCount ?: 0
            }

            val nameToSave = effectiveItem.name.ifEmpty { cached?.name ?: "" }
            val categoryToSave = effectiveItem.category ?: cached?.category
            val tagsToSave = if (effectiveItem.tags.isNotEmpty()) effectiveItem.tags else (cached?.tags ?: emptyList())
            val widthToSave = effectiveItem.width ?: cached?.width
            val heightToSave = effectiveItem.height ?: cached?.height
            val createdToSave = effectiveItem.created ?: cached?.created

            val entity = DownloadedBackgroundEntity(
                id = effectiveItem.id,
                name = nameToSave,
                category = categoryToSave,
                color = effectiveItem.color ?: cached?.color,
                localPath = file.absolutePath,
                photoThumb = effectiveItem.photoThumb,
                photoGallery = effectiveItem.photoGallery,
                photoThumb2x = effectiveItem.photoThumb2x,
                downloadCount = countToSave,
                tags = tagsToSave.joinToString(","),
                created = createdToSave,
                width = widthToSave,
                height = heightToSave,
                downloadedAt = System.currentTimeMillis()
            )

            downloadedBackgroundDao.insert(entity)

            try {
                reportBackgroundDownloadUseCase(effectiveItem.id, packageName)
            } catch (e: Exception) {
                Log.w("BackgroundDownload", "Failed to report download to backend", e)
            }

            Result.success(entity)
        } catch (e: Exception) {
            Log.e("BackgroundDownload", "Failed to download background ${item.id}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDownloadedBackground(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val entity = downloadedBackgroundDao.getById(id)
            if (entity != null) {
                val file = File(entity.localPath)
                if (file.exists()) {
                    file.delete()
                }
                downloadedBackgroundDao.deleteById(id)
            }
            true
        } catch (e: Exception) {
            Log.e("BackgroundDownload", "Failed to delete downloaded background $id", e)
            false
        }
    }
}
