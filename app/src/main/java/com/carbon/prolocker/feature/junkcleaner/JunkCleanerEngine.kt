package com.carbon.prolocker.feature.junkcleaner

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.carbon.prolocker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class JunkCategory(val titleRes: Int) {
    APP_CACHE(R.string.category_app_cache),
    TEMP_FILES(R.string.category_temp_files),
    RESIDUAL_JUNK(R.string.category_residual_junk),
    SYSTEM_FILES(R.string.category_system_files),
    AD_JUNK(R.string.category_ad_junk),
    APK_FILES(R.string.category_apk_files)
}

data class JunkItem(
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val category: JunkCategory
)

data class JunkCategoryGroup(
    val category: JunkCategory,
    val titleRes: Int,
    val items: MutableList<JunkItem>,
    val totalSize: Long,
    var isSelected: Boolean = true,
    var isExpanded: Boolean = false
)

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usagePercentage: Int
)

data class ScanProgress(
    val currentPath: String,
    val currentCategory: JunkCategory,
    val completedCategories: Set<JunkCategory>,
    val totalFoundBytes: Long,
    val progress: Float
)

data class DeleteProgress(
    val currentPath: String,
    val currentCategory: JunkCategory,
    val completedCategories: Set<JunkCategory>,
    val freedBytes: Long,
    val remainingBytes: Long,
    val progress: Float
)

class JunkCleanerEngine {

    fun getStorageInfo(): StorageInfo {
        return try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.totalBytes
            val freeBytes = statFs.availableBytes
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            val percentage = if (totalBytes > 0) ((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt() else 0
            StorageInfo(totalBytes, usedBytes, freeBytes, percentage)
        } catch (_e: Exception) {
            StorageInfo(0L, 0L, 0L, 0)
        }
    }

    suspend fun scanJunk(
        context: Context,
        onProgress: (ScanProgress) -> Unit
    ): List<JunkCategoryGroup> = withContext(Dispatchers.IO) {
        val appCacheItems = mutableListOf<JunkItem>()
        val tempItems = mutableListOf<JunkItem>()
        val residualItems = mutableListOf<JunkItem>()
        val systemItems = mutableListOf<JunkItem>()
        val adJunkItems = mutableListOf<JunkItem>()
        val apkItems = mutableListOf<JunkItem>()

        val completed = mutableSetOf<JunkCategory>()
        var runningBytes = 0L

        fun report(path: String, category: JunkCategory, baseProg: Float, subProg: Float) {
            val progress = (baseProg + subProg * (1f / 6f)).coerceIn(0f, 0.99f)
            onProgress(
                ScanProgress(
                    currentPath = path,
                    currentCategory = category,
                    completedCategories = completed.toSet(),
                    totalFoundBytes = runningBytes,
                    progress = progress
                )
            )
        }

        // 1. Scan App Cache
        val catCache = JunkCategory.APP_CACHE
        val appCaches = listOfNotNull(
            context.cacheDir,
            context.codeCacheDir,
            context.externalCacheDir
        )
        for ((idx, cacheDir) in appCaches.withIndex()) {
            scanDirectoryRecursively(
                dir = cacheDir,
                onProgress = { p -> report(p, catCache, 0f / 6f, idx.toFloat() / appCaches.size.coerceAtLeast(1)) },
                onFileFound = { file ->
                    val size = file.length()
                    if (size > 0) {
                        runningBytes += size
                        appCacheItems.add(JunkItem(file, file.name, file.absolutePath, size, catCache))
                    }
                }
            )
        }

        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            val androidData = File(externalStorage, "Android/data")
            if (androidData.exists() && androidData.canRead()) {
                androidData.listFiles()?.take(25)?.forEach { appDir ->
                    val cDir = File(appDir, "cache")
                    if (cDir.exists() && cDir.isDirectory) {
                        scanDirectoryRecursively(
                            dir = cDir,
                            onProgress = { p -> report(p, catCache, 0f / 6f, 0.5f) },
                            onFileFound = { file ->
                                val size = file.length()
                                if (size > 0) {
                                    runningBytes += size
                                    appCacheItems.add(JunkItem(file, file.name, file.absolutePath, size, catCache))
                                }
                            }
                        )
                    }
                }
            }
        }
        completed.add(catCache)
        report("", catCache, 1f / 6f, 0f)

        // 2. Scan Temporary Files
        val catTemp = JunkCategory.TEMP_FILES
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            scanForSpecificType(
                rootDir = externalStorage,
                targetCategory = catTemp,
                onProgress = { p -> report(p, catTemp, 1f / 6f, 0.5f) },
                matcher = { file ->
                    val name = file.name.lowercase()
                    name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".bak")
                },
                onItemFound = { item ->
                    runningBytes += item.size
                    tempItems.add(item)
                }
            )
        }
        completed.add(catTemp)
        report("", catTemp, 2f / 6f, 0f)

        // 3. Scan Residual Junk
        val catResidual = JunkCategory.RESIDUAL_JUNK
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            scanForSpecificType(
                rootDir = externalStorage,
                targetCategory = catResidual,
                onProgress = { p -> report(p, catResidual, 2f / 6f, 0.5f) },
                matcher = { file ->
                    val name = file.name.lowercase()
                    name.endsWith(".log") || (name.startsWith("crash_") && name.endsWith(".txt"))
                },
                onItemFound = { item ->
                    runningBytes += item.size
                    residualItems.add(item)
                }
            )
        }
        completed.add(catResidual)
        report("", catResidual, 3f / 6f, 0f)

        // 4. Scan System Files (Thumbnails & empty dirs)
        val catSystem = JunkCategory.SYSTEM_FILES
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            val dcim = File(externalStorage, "DCIM")
            val pictures = File(externalStorage, "Pictures")
            listOf(dcim, pictures).forEach { mediaFolder ->
                if (mediaFolder.exists() && mediaFolder.isDirectory) {
                    val thumb = File(mediaFolder, ".thumbnails")
                    if (thumb.exists() && thumb.isDirectory) {
                        scanDirectoryRecursively(
                            dir = thumb,
                            onProgress = { p -> report(p, catSystem, 3f / 6f, 0.5f) },
                            onFileFound = { file ->
                                val size = file.length()
                                if (size > 0) {
                                    runningBytes += size
                                    systemItems.add(JunkItem(file, file.name, file.absolutePath, size, catSystem))
                                }
                            }
                        )
                    }
                }
            }
            scanForEmptyDirectories(
                rootDir = externalStorage,
                onProgress = { p -> report(p, catSystem, 3f / 6f, 0.8f) },
                onFound = { dir ->
                    systemItems.add(JunkItem(dir, dir.name, dir.absolutePath, 0L, catSystem))
                }
            )
        }
        completed.add(catSystem)
        report("", catSystem, 4f / 6f, 0f)

        // 5. Scan AD Junk
        val catAd = JunkCategory.AD_JUNK
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            scanForSpecificType(
                rootDir = externalStorage,
                targetCategory = catAd,
                onProgress = { p -> report(p, catAd, 4f / 6f, 0.5f) },
                matcher = { file ->
                    val pLower = file.absolutePath.lowercase()
                    pLower.contains("/.ad_") || pLower.contains("/adcache") ||
                        pLower.contains("/tapsell") || pLower.contains("/unityads") ||
                        pLower.contains("/vungle") || pLower.contains("/mraid")
                },
                onItemFound = { item ->
                    runningBytes += item.size
                    adJunkItems.add(item)
                }
            )
        }
        completed.add(catAd)
        report("", catAd, 5f / 6f, 0f)

        // 6. Scan APK Files
        val catApk = JunkCategory.APK_FILES
        if (externalStorage != null && externalStorage.exists() && externalStorage.canRead()) {
            scanForSpecificType(
                rootDir = externalStorage,
                targetCategory = catApk,
                onProgress = { p -> report(p, catApk, 5f / 6f, 0.5f) },
                matcher = { file -> file.name.lowercase().endsWith(".apk") },
                onItemFound = { item ->
                    runningBytes += item.size
                    apkItems.add(item)
                }
            )
        }
        completed.add(catApk)
        report("", catApk, 1f, 0f)

        listOf(
            JunkCategoryGroup(
                category = JunkCategory.APP_CACHE,
                titleRes = JunkCategory.APP_CACHE.titleRes,
                items = appCacheItems,
                totalSize = appCacheItems.sumOf { it.size }
            ),
            JunkCategoryGroup(
                category = JunkCategory.TEMP_FILES,
                titleRes = JunkCategory.TEMP_FILES.titleRes,
                items = tempItems,
                totalSize = tempItems.sumOf { it.size }
            ),
            JunkCategoryGroup(
                category = JunkCategory.RESIDUAL_JUNK,
                titleRes = JunkCategory.RESIDUAL_JUNK.titleRes,
                items = residualItems,
                totalSize = residualItems.sumOf { it.size }
            ),
            JunkCategoryGroup(
                category = JunkCategory.SYSTEM_FILES,
                titleRes = JunkCategory.SYSTEM_FILES.titleRes,
                items = systemItems,
                totalSize = systemItems.sumOf { it.size }
            ),
            JunkCategoryGroup(
                category = JunkCategory.AD_JUNK,
                titleRes = JunkCategory.AD_JUNK.titleRes,
                items = adJunkItems,
                totalSize = adJunkItems.sumOf { it.size }
            ),
            JunkCategoryGroup(
                category = JunkCategory.APK_FILES,
                titleRes = JunkCategory.APK_FILES.titleRes,
                items = apkItems,
                totalSize = apkItems.sumOf { it.size }
            )
        )
    }

    private fun scanDirectoryRecursively(
        dir: File,
        onProgress: (String) -> Unit,
        onFileFound: (File) -> Unit,
        currentDepth: Int = 0,
        maxDepth: Int = 5
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return
        onProgress(dir.absolutePath)

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursively(file, onProgress, onFileFound, currentDepth + 1, maxDepth)
            } else {
                onFileFound(file)
            }
        }
    }

    private fun scanForSpecificType(
        rootDir: File,
        targetCategory: JunkCategory,
        onProgress: (String) -> Unit,
        matcher: (File) -> Boolean,
        onItemFound: (JunkItem) -> Unit,
        currentDepth: Int = 0,
        maxDepth: Int = 4
    ) {
        if (currentDepth > maxDepth || !rootDir.exists() || !rootDir.isDirectory) return
        onProgress(rootDir.absolutePath)

        val files = rootDir.listFiles() ?: return
        val protectedDirs = setOf("DCIM", "Pictures", "Music", "Movies", "Documents", "Android")
        for (file in files) {
            if (file.isDirectory) {
                if (currentDepth == 0 && file.name in protectedDirs) {
                    continue
                }
                scanForSpecificType(file, targetCategory, onProgress, matcher, onItemFound, currentDepth + 1, maxDepth)
            } else {
                if (matcher(file)) {
                    val size = file.length()
                    if (size > 0 || targetCategory == JunkCategory.APK_FILES) {
                        onItemFound(JunkItem(file, file.name, file.absolutePath, size, targetCategory))
                    }
                }
            }
        }
    }

    private fun scanForEmptyDirectories(
        rootDir: File,
        onProgress: (String) -> Unit,
        onFound: (File) -> Unit,
        currentDepth: Int = 0,
        maxDepth: Int = 3
    ) {
        if (currentDepth > maxDepth || !rootDir.exists() || !rootDir.isDirectory) return
        val protectedDirs = setOf("DCIM", "Pictures", "Music", "Movies", "Documents", "Android")
        if (currentDepth == 0 && rootDir.name in protectedDirs) return

        val files = rootDir.listFiles() ?: return
        if (files.isEmpty() && currentDepth > 0) {
            onFound(rootDir)
        } else {
            for (file in files) {
                if (file.isDirectory) {
                    onProgress(file.absolutePath)
                    scanForEmptyDirectories(file, onProgress, onFound, currentDepth + 1, maxDepth)
                }
            }
        }
    }

    suspend fun deleteJunkItems(
        items: List<JunkItem>,
        onProgress: (DeleteProgress) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val totalBytesToDelete = items.sumOf { it.size }
        val itemsByCategory = items.groupBy { it.category }
        val completedCategories = mutableSetOf<JunkCategory>()

        var itemsProcessed = 0
        val totalItems = items.size.coerceAtLeast(1)

        for ((cat, catItems) in itemsByCategory) {
            for (item in catItems) {
                try {
                    val size = item.size
                    val deleted = if (item.file.isDirectory) {
                        item.file.deleteRecursively()
                    } else {
                        item.file.delete()
                    }
                    if (deleted) {
                        freedBytes += size
                    }
                } catch (_e: Exception) {
                }
                itemsProcessed++
                val prog = (itemsProcessed.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
                val remaining = (totalBytesToDelete - freedBytes).coerceAtLeast(0L)
                onProgress(
                    DeleteProgress(
                        currentPath = item.path,
                        currentCategory = cat,
                        completedCategories = completedCategories.toSet(),
                        freedBytes = freedBytes,
                        remainingBytes = remaining,
                        progress = prog
                    )
                )
            }
            completedCategories.add(cat)
        }

        onProgress(
            DeleteProgress(
                currentPath = "",
                currentCategory = JunkCategory.APK_FILES,
                completedCategories = completedCategories.toSet(),
                freedBytes = freedBytes,
                remainingBytes = 0L,
                progress = 1f
            )
        )

        freedBytes
    }
}
