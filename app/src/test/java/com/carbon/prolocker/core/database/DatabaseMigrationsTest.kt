package com.carbon.prolocker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carbon.prolocker.feature.callblocker.data.BlockedCallLogEntity
import com.carbon.prolocker.feature.callblocker.data.BlockedNumberEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class DatabaseMigrationsTest {

    private fun run(migration: Migration): List<String> {
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") executed.add(args!![0] as String)
            null
        } as SupportSQLiteDatabase
        migration.migrate(db)
        return executed
    }

    private fun columnsOf(sql: String): List<String> =
        Regex("`(\\w+)` (?:INTEGER|TEXT)").findAll(sql).map { it.groupValues[1] }.toList()

    private fun fieldsOf(entity: Class<*>): List<String> =
        entity.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }

    @Test
    fun migrationsCoverVersion4To6AndAreRegistered() {
        assertEquals(4, DatabaseMigrations.MIGRATION_4_5.startVersion)
        assertEquals(5, DatabaseMigrations.MIGRATION_4_5.endVersion)
        assertEquals(5, DatabaseMigrations.MIGRATION_5_6.startVersion)
        assertEquals(6, DatabaseMigrations.MIGRATION_5_6.endVersion)
        assertTrue(DatabaseMigrations.ALL.contains(DatabaseMigrations.MIGRATION_4_5))
        assertTrue(DatabaseMigrations.ALL.contains(DatabaseMigrations.MIGRATION_5_6))
    }

    @Test
    fun migration4To5CreatesTheCallBlockerTables() {
        val executed = run(DatabaseMigrations.MIGRATION_4_5)
        assertEquals(
            listOf(
                DatabaseMigrations.CREATE_BLOCKED_NUMBERS,
                DatabaseMigrations.CREATE_BLOCKED_CALL_LOGS
            ),
            executed
        )
        assertTrue(executed[0].startsWith("CREATE TABLE IF NOT EXISTS `blocked_numbers` ("))
        assertTrue(executed[1].startsWith("CREATE TABLE IF NOT EXISTS `blocked_call_logs` ("))
        assertTrue(executed.all { it.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL") })
    }

    @Test
    fun migration5To6CreatesDownloadedBackgroundsTable() {
        val executed = run(DatabaseMigrations.MIGRATION_5_6)
        assertEquals(1, executed.size)
        val sql = executed.single()
        assertEquals(DatabaseMigrations.CREATE_DOWNLOADED_BACKGROUNDS, sql)
        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS `downloaded_backgrounds` ("))
        assertTrue(sql.endsWith("PRIMARY KEY(`id`))"))
    }

    @Test
    fun migrationColumnsMatchTheEntityFields() {
        val numbers = columnsOf(DatabaseMigrations.CREATE_BLOCKED_NUMBERS)
        assertEquals(fieldsOf(BlockedNumberEntity::class.java).toSet(), numbers.toSet())
        assertEquals(fieldsOf(BlockedNumberEntity::class.java).size, numbers.size)

        val logs = columnsOf(DatabaseMigrations.CREATE_BLOCKED_CALL_LOGS)
        assertEquals(fieldsOf(BlockedCallLogEntity::class.java).toSet(), logs.toSet())
        assertEquals(fieldsOf(BlockedCallLogEntity::class.java).size, logs.size)

        val backgrounds = columnsOf(DatabaseMigrations.CREATE_DOWNLOADED_BACKGROUNDS)
        assertEquals(fieldsOf(DownloadedBackgroundEntity::class.java).toSet(), backgrounds.toSet())
        assertEquals(fieldsOf(DownloadedBackgroundEntity::class.java).size, backgrounds.size)
    }

    // Compares against the schema JSON Room exports during a build (app/schemas); skipped until it exists.
    @Test
    fun migrationSqlMatchesExportedRoomSchema() {
        val schema = listOf(
            File("schemas/com.carbon.prolocker.core.database.AppDatabase/6.json"),
            File("app/schemas/com.carbon.prolocker.core.database.AppDatabase/6.json")
        ).firstOrNull { it.isFile }
        assumeTrue("Room schema 6.json has not been exported yet", schema != null)

        val entities = Json.parseToJsonElement(schema!!.readText())
            .jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
            .map { it.jsonObject }

        fun expectedSql(table: String): String {
            val entity = entities.first { it.getValue("tableName").jsonPrimitive.content == table }
            assertNoIndices(entity)
            return entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
        }

        assertEquals(expectedSql("blocked_numbers"), DatabaseMigrations.CREATE_BLOCKED_NUMBERS)
        assertEquals(expectedSql("blocked_call_logs"), DatabaseMigrations.CREATE_BLOCKED_CALL_LOGS)
        assertEquals(expectedSql("downloaded_backgrounds"), DatabaseMigrations.CREATE_DOWNLOADED_BACKGROUNDS)
    }

    private fun assertNoIndices(entity: JsonObject) {
        val indices = entity["indices"] as? JsonArray
        assertTrue("entity declares no indices, migration creates none", indices == null || indices.isEmpty())
    }
}
