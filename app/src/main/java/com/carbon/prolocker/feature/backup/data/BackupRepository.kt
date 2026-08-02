package com.carbon.prolocker.feature.backup.data

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.carbon.prolocker.feature.backup.model.AppBackupProgressItem
import com.carbon.prolocker.feature.backup.model.AppBackupStatus
import com.carbon.prolocker.feature.backup.model.BackupCategory
import com.carbon.prolocker.feature.backup.model.BackupFileInfo
import com.carbon.prolocker.feature.backup.model.CallLogBackupData
import com.carbon.prolocker.feature.backup.model.ContactBackupData
import com.carbon.prolocker.feature.backup.model.InstalledAppItem
import com.carbon.prolocker.feature.backup.model.SmsBackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRepository(
    private val context: Context,
    private val preferences: BackupPreferences
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val categorySubfolders = mapOf(
        BackupCategory.CONTACTS to "Contacts",
        BackupCategory.CALL_LOGS to "Call Logs",
        BackupCategory.SMS to "SMS",
        BackupCategory.APPLICATIONS to "Applications"
    )

    val backupDisplayPathFlow: Flow<String> = preferences.backupDisplayPathFlow

    suspend fun getCategoryItemCount(category: BackupCategory): Int = withContext(Dispatchers.IO) {
        try {
            when (category) {
                BackupCategory.CONTACTS -> getContactsCount()
                BackupCategory.CALL_LOGS -> getCallLogsCount()
                BackupCategory.SMS -> getSmsCount()
                BackupCategory.APPLICATIONS -> getInstalledApps().size
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getContactsCount(): Int {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null
        )
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    private fun getCallLogsCount(): Int {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            null,
            null,
            null
        )
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    private fun getSmsCount(): Int {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null
        )
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    suspend fun getInstalledApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledAppItem>()

        for (appInfo in apps) {
            // Filter out system apps without launcher intents or self
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemApp && pm.getLaunchIntentForPackage(appInfo.packageName) == null) {
                continue
            }
            if (appInfo.packageName == context.packageName) {
                continue
            }

            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
            val apkFile = File(appInfo.sourceDir)
            val size = if (apkFile.exists()) apkFile.length() else 0L

            val pkgInfo = try {
                pm.getPackageInfo(appInfo.packageName, 0)
            } catch (_: Exception) { null }
            val version = pkgInfo?.versionName ?: ""

            result.add(
                InstalledAppItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    iconDrawable = icon,
                    version = version,
                    apkSizeBytes = size,
                    isSelected = false
                )
            )
        }

        result.sortBy { it.appName.lowercase(Locale.getDefault()) }
        result
    }

    // --- Directory Helpers ---

    private suspend fun getCategoryTargetDirectory(category: BackupCategory): TargetDirectory = withContext(Dispatchers.IO) {
        val treeUriString = preferences.backupTreeUriFlow.first()
        val subfolderName = categorySubfolders[category] ?: category.name

        if (treeUriString != null) {
            try {
                val treeUri = Uri.parse(treeUriString)
                val docTree = DocumentFile.fromTreeUri(context, treeUri)
                if (docTree != null && docTree.canWrite()) {
                    var subDir = docTree.findFile(subfolderName)
                    if (subDir == null || !subDir.isDirectory) {
                        subDir = docTree.createDirectory(subfolderName)
                    }
                    if (subDir != null) {
                        return@withContext TargetDirectory.DocumentDir(subDir)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Fallback to local public Documents directory
        val baseDir = preferences.getDefaultBackupDir()
        val categoryDir = File(baseDir, subfolderName)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }
        TargetDirectory.FileDir(categoryDir)
    }

    private sealed class TargetDirectory {
        data class DocumentDir(val docFile: DocumentFile) : TargetDirectory()
        data class FileDir(val file: File) : TargetDirectory()
    }

    private fun generateFileName(prefix: String, extension: String): String {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return "${prefix}_$timeStamp.$extension"
    }

    // --- Contacts Backup & Restore ---

    suspend fun backupContacts(onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<ContactBackupData>()
        val cr = context.contentResolver

        val cursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            null
        )

        cursor?.use { c ->
            val total = c.count.coerceAtLeast(1)
            val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

            var processed = 0
            while (c.moveToNext()) {
                val id = c.getString(idIndex)
                val name = c.getString(nameIndex) ?: "Unknown"

                // Phones
                val phoneList = mutableListOf<String>()
                val phoneCursor = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(id),
                    null
                )
                phoneCursor?.use { pc ->
                    val numIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (pc.moveToNext()) {
                        pc.getString(numIndex)?.let { phoneList.add(it) }
                    }
                }

                // Emails
                val emailList = mutableListOf<String>()
                val emailCursor = cr.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                    arrayOf(id),
                    null
                )
                emailCursor?.use { ec ->
                    val addrIndex = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    while (ec.moveToNext()) {
                        ec.getString(addrIndex)?.let { emailList.add(it) }
                    }
                }

                contactsList.add(
                    ContactBackupData(
                        displayName = name,
                        phoneNumbers = phoneList,
                        emails = emailList
                    )
                )

                processed++
                onProgress(processed.toFloat() / total)
            }
        }

        val jsonContent = json.encodeToString(contactsList)
        val fileName = generateFileName("Contacts", "json")
        saveFileContent(BackupCategory.CONTACTS, fileName, jsonContent)
        fileName
    }

    suspend fun restoreContacts(fileInfo: BackupFileInfo): Int = withContext(Dispatchers.IO) {
        val jsonContent = readTextFromFile(fileInfo)
        val list: List<ContactBackupData> = json.decodeFromString(jsonContent)
        var restoredCount = 0

        for (contact in list) {
            val ops = ArrayList<ContentProviderOperation>()

            val rawContactInsertIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .build()
            )

            // Phones
            for (phone in contact.phoneNumbers) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }

            // Emails
            for (email in contact.emails) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                restoredCount++
            } catch (_: Exception) {
            }
        }
        restoredCount
    }

    // --- Call Logs Backup & Restore ---

    suspend fun backupCallLogs(onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val callLogsList = mutableListOf<CallLogBackupData>()
        val cr = context.contentResolver

        val cursor = cr.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null,
            null,
            null
        )

        cursor?.use { c ->
            val total = c.count.coerceAtLeast(1)
            val numIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = c.getColumnIndex(CallLog.Calls.DATE)
            val durIndex = c.getColumnIndex(CallLog.Calls.DURATION)

            var processed = 0
            while (c.moveToNext()) {
                val number = c.getString(numIndex) ?: ""
                val name = c.getString(nameIndex)
                val type = c.getInt(typeIndex)
                val dateMs = c.getLong(dateIndex)
                val duration = c.getLong(durIndex)

                callLogsList.add(
                    CallLogBackupData(
                        number = number,
                        name = name,
                        type = type,
                        dateMs = dateMs,
                        durationSec = duration
                    )
                )

                processed++
                onProgress(processed.toFloat() / total)
            }
        }

        val jsonContent = json.encodeToString(callLogsList)
        val fileName = generateFileName("CallLogs", "json")
        saveFileContent(BackupCategory.CALL_LOGS, fileName, jsonContent)
        fileName
    }

    suspend fun restoreCallLogs(fileInfo: BackupFileInfo): Int = withContext(Dispatchers.IO) {
        val jsonContent = readTextFromFile(fileInfo)
        val list: List<CallLogBackupData> = json.decodeFromString(jsonContent)
        var restoredCount = 0

        val cr = context.contentResolver
        for (item in list) {
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, item.number)
                put(CallLog.Calls.CACHED_NAME, item.name)
                put(CallLog.Calls.TYPE, item.type)
                put(CallLog.Calls.DATE, item.dateMs)
                put(CallLog.Calls.DURATION, item.durationSec)
                put(CallLog.Calls.NEW, 1)
            }

            try {
                cr.insert(CallLog.Calls.CONTENT_URI, values)
                restoredCount++
            } catch (_: Exception) {
            }
        }
        restoredCount
    }

    // --- SMS Backup & Restore ---

    suspend fun backupSms(onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val smsList = mutableListOf<SmsBackupData>()
        val cr = context.contentResolver

        val cursor = cr.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            null
        )

        cursor?.use { c ->
            val total = c.count.coerceAtLeast(1)
            val addrIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = c.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = c.getColumnIndex(Telephony.Sms.READ)

            var processed = 0
            while (c.moveToNext()) {
                val address = c.getString(addrIndex) ?: ""
                val body = c.getString(bodyIndex) ?: ""
                val dateMs = c.getLong(dateIndex)
                val type = c.getInt(typeIndex)
                val read = c.getInt(readIndex)

                smsList.add(
                    SmsBackupData(
                        address = address,
                        body = body,
                        dateMs = dateMs,
                        type = type,
                        read = read
                    )
                )

                processed++
                onProgress(processed.toFloat() / total)
            }
        }

        val jsonContent = json.encodeToString(smsList)
        val fileName = generateFileName("SMS", "json")
        saveFileContent(BackupCategory.SMS, fileName, jsonContent)
        fileName
    }

    suspend fun restoreSms(fileInfo: BackupFileInfo): Int = withContext(Dispatchers.IO) {
        val jsonContent = readTextFromFile(fileInfo)
        val list: List<SmsBackupData> = json.decodeFromString(jsonContent)
        var restoredCount = 0

        val cr = context.contentResolver
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        for (item in list) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, item.address)
                put(Telephony.Sms.BODY, item.body)
                put(Telephony.Sms.DATE, item.dateMs)
                put(Telephony.Sms.TYPE, item.type)
                put(Telephony.Sms.READ, item.read)
            }

            try {
                cr.insert(uri, values)
                restoredCount++
            } catch (_: Exception) {
            }
        }
        restoredCount
    }

    // --- Applications Backup & Restore ---

    suspend fun backupSingleApp(
        packageName: String,
        onProgress: (AppBackupStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(AppBackupStatus.BACKING_UP)
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val sourceApk = File(appInfo.sourceDir)
            if (!sourceApk.exists()) {
                onProgress(AppBackupStatus.FAILED)
                return@withContext false
            }

            val appLabel = pm.getApplicationLabel(appInfo).toString().replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "${appLabel}_${packageName}.apk"

            copyApkToTarget(sourceApk, BackupCategory.APPLICATIONS, fileName)
            onProgress(AppBackupStatus.COMPLETED)
            true
        } catch (e: Exception) {
            onProgress(AppBackupStatus.FAILED)
            false
        }
    }

    private fun copyApkToTarget(sourceApk: File, category: BackupCategory, fileName: String) {
        val targetDir = when (val dir = runCatching { preferences.getDefaultBackupDir() }.getOrNull()) {
            else -> null
        }

        // Determine destination stream
        val input: InputStream = FileInputStream(sourceApk)
        val output: OutputStream

        val targetFolder = runCatching { preferences.getDefaultBackupDir() }.getOrNull()
        val appFolder = File(targetFolder, categorySubfolders[category] ?: "Applications")
        if (!appFolder.exists()) {
            appFolder.mkdirs()
        }
        val destFile = File(appFolder, fileName)
        output = FileOutputStream(destFile)

        input.use { inStream ->
            output.use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    // --- File Storage Operations ---

    private fun saveFileContent(category: BackupCategory, fileName: String, content: String) {
        val folderName = categorySubfolders[category] ?: category.name
        val baseDir = preferences.getDefaultBackupDir()
        val categoryDir = File(baseDir, folderName)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }
        val file = File(categoryDir, fileName)
        file.writeText(content, Charsets.UTF_8)
    }

    private fun readTextFromFile(fileInfo: BackupFileInfo): String {
        return if (fileInfo.filePath.isNotEmpty()) {
            File(fileInfo.filePath).readText(Charsets.UTF_8)
        } else {
            val uri = Uri.parse(fileInfo.uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
        }
    }

    suspend fun getBackupFiles(category: BackupCategory): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<BackupFileInfo>()
        val folderName = categorySubfolders[category] ?: category.name

        // Check local default folder first
        val baseDir = preferences.getDefaultBackupDir()
        val categoryDir = File(baseDir, folderName)
        if (categoryDir.exists() && categoryDir.isDirectory) {
            categoryDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    result.add(
                        BackupFileInfo(
                            fileName = file.name,
                            category = category,
                            filePath = file.absolutePath,
                            uriString = Uri.fromFile(file).toString(),
                            sizeBytes = file.length(),
                            lastModifiedMs = file.lastModified()
                        )
                    )
                }
            }
        }

        // Also check SAF tree directory if set
        val treeUriString = preferences.backupTreeUriFlow.first()
        if (treeUriString != null) {
            try {
                val docTree = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
                val subDir = docTree?.findFile(folderName)
                if (subDir != null && subDir.isDirectory) {
                    subDir.listFiles().forEach { doc ->
                        if (doc.isFile && doc.name != null) {
                            val existing = result.any { it.fileName == doc.name }
                            if (!existing) {
                                result.add(
                                    BackupFileInfo(
                                        fileName = doc.name!!,
                                        category = category,
                                        filePath = "",
                                        uriString = doc.uri.toString(),
                                        sizeBytes = doc.length(),
                                        lastModifiedMs = doc.lastModified()
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        result.sortByDescending { it.lastModifiedMs }
        result
    }

    suspend fun deleteBackupFile(fileInfo: BackupFileInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            if (fileInfo.filePath.isNotEmpty()) {
                val file = File(fileInfo.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            if (fileInfo.uriString.isNotEmpty()) {
                val uri = Uri.parse(fileInfo.uriString)
                if (uri.scheme == "content") {
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    docFile?.delete()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareBackupFile(fileInfo: BackupFileInfo) {
        val fileUri: Uri = if (fileInfo.filePath.isNotEmpty()) {
            val file = File(fileInfo.filePath)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.parse(fileInfo.uriString)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (fileInfo.category == BackupCategory.APPLICATIONS) {
                "application/vnd.android.package-archive"
            } else {
                "application/json"
            }
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, context.getString(com.carbon.prolocker.R.string.backup_share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun installApk(fileInfo: BackupFileInfo) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri: Uri = if (fileInfo.filePath.isNotEmpty()) {
            val file = File(fileInfo.filePath)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            Uri.parse(fileInfo.uriString)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    // --- Date Formatting Helper (Jalali Persian) ---

    fun formatJalaliDate(timestampMs: Long): String {
        if (timestampMs <= 0) return "—"
        return try {
            val persianDate = PersianDate(timestampMs)
            PersianDateFormat("Y/m/d H:i").format(persianDate)
        } catch (e: Exception) {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}
