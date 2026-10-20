package com.carbon.prolocker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    // The statements below are identical to what Room generates for the entities (see the exported
    // schema JSON). None of these entities declares an index.
    internal const val CREATE_BLOCKED_NUMBERS =
        "CREATE TABLE IF NOT EXISTS `blocked_numbers` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`numberOrPattern` TEXT NOT NULL, " +
            "`displayName` TEXT NOT NULL, " +
            "`ruleType` TEXT NOT NULL, " +
            "`sourceCategory` TEXT NOT NULL, " +
            "`isEnabled` INTEGER NOT NULL, " +
            "`createdAtMs` INTEGER NOT NULL)"

    internal const val CREATE_BLOCKED_CALL_LOGS =
        "CREATE TABLE IF NOT EXISTS `blocked_call_logs` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`phoneNumber` TEXT NOT NULL, " +
            "`callerName` TEXT, " +
            "`matchedRule` TEXT NOT NULL, " +
            "`timestampMs` INTEGER NOT NULL)"

    internal const val CREATE_DOWNLOADED_BACKGROUNDS =
        "CREATE TABLE IF NOT EXISTS `downloaded_backgrounds` (" +
            "`id` INTEGER NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`category` TEXT, " +
            "`color` TEXT, " +
            "`localPath` TEXT NOT NULL, " +
            "`photoThumb` TEXT NOT NULL, " +
            "`photoGallery` TEXT NOT NULL, " +
            "`photoThumb2x` TEXT NOT NULL, " +
            "`downloadCount` INTEGER NOT NULL, " +
            "`tags` TEXT NOT NULL, " +
            "`created` TEXT, " +
            "`width` INTEGER, " +
            "`height` INTEGER, " +
            "`downloadedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))"

    // Version 5 only added the call blocker tables; every other table is unchanged.
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_BLOCKED_NUMBERS)
            db.execSQL(CREATE_BLOCKED_CALL_LOGS)
        }
    }

    // Version 6 only added the downloaded_backgrounds table; every other table is unchanged.
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_DOWNLOADED_BACKGROUNDS)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6)
}
