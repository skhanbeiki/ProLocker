package com.carbon.prolocker.feature.hidefile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HideFileDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        db.execSQL(CREATE_TABLE)
    }

    fun addItem(item: HideItem) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_NAME, item.name)
            put(KEY_PATH, item.path)
            put(KEY_TYPE, item.type)
            put(KEY_DATE, item.date)
            put(KEY_SIZE, item.size)
            put(KEY_IMAGE_PATH, item.imagePath)
            put(KEY_IMAGE, item.image)
        }
        db.insert(TABLE, null, values)
        db.close()
    }

    fun getAllItems(filterKey: String? = null): List<HideItem> {
        val result = mutableListOf<HideItem>()
        try {
            val db = writableDatabase
            db.rawQuery("SELECT * FROM $TABLE", null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val item = cursorToItem(cursor)
                    if (filterKey == null || item.type.equals(filterKey, ignoreCase = true)) {
                        result.add(item)
                    }
                }
            }
            db.close()
        } catch (e: Exception) {
            // ignore: mirror old behavior of silently returning empty list
        }
        return result
    }

    fun updateItem(item: HideItem): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_NAME, item.name)
            put(KEY_PATH, item.path)
            put(KEY_TYPE, item.type)
            put(KEY_DATE, item.date)
            put(KEY_SIZE, item.size)
        }
        val rows = db.update(TABLE, values, "$KEY_ID = ?", arrayOf(item.id.toString()))
        db.close()
        return rows
    }

    fun deleteEntry(name: String) {
        val db = writableDatabase
        db.delete(TABLE, "$KEY_NAME = ?", arrayOf(name))
        db.close()
    }

    fun getItemCount(): Int {
        val db = readableDatabase
        val count = db.rawQuery("SELECT * FROM $TABLE", null)?.use { it.count } ?: 0
        db.close()
        return count
    }

    private fun cursorToItem(cursor: android.database.Cursor): HideItem {
        return HideItem(
            id = cursor.getLong(0),
            name = cursor.getString(1) ?: "",
            path = cursor.getString(2) ?: "",
            type = cursor.getString(3) ?: "",
            date = cursor.getString(4) ?: "",
            size = cursor.getString(5) ?: "",
            imagePath = cursor.getString(6) ?: "",
            image = cursor.getBlob(7)
        )
    }

    companion object {
        const val DATABASE_NAME = "hidefile"
        private const val DATABASE_VERSION = 3
        private const val TABLE = "hide_table"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_PATH = "path"
        private const val KEY_TYPE = "type"
        private const val KEY_DATE = "date"
        private const val KEY_SIZE = "size"
        private const val KEY_IMAGE_PATH = "image_path"
        private const val KEY_IMAGE = "image"

        private const val CREATE_TABLE =
            "CREATE TABLE $TABLE(" +
                "$KEY_ID INTEGER PRIMARY KEY, $KEY_NAME TEXT, $KEY_PATH TEXT, $KEY_TYPE TEXT, " +
                "$KEY_DATE TEXT, $KEY_SIZE TEXT, $KEY_IMAGE_PATH TEXT, $KEY_IMAGE BLOB)"
    }
}
