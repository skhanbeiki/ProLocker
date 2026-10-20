package com.carbon.prolocker.core.datastore

import androidx.datastore.core.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences()

    private val KNOWN_LOCK_TYPES = setOf("NONE", "PIN", "PATTERN")

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): UserPreferences {
        val bytes = input.readBytes()
        // Malformed bytes surface as SerializationException / IllegalArgumentException, but a corrupt
        // settings file must never crash the app, so every failure falls through to the next layout.
        val current = try {
            ProtoBuf.decodeFromByteArray(UserPreferences.serializer(), bytes)
        } catch (exception: Exception) {
            return decodeLegacy(bytes) ?: defaultValue
        }
        // Inserting selectedTab (commit c14a18f) moved String fields onto String fields, so a file in
        // the old layout can decode as the current class without any error. An unknown lock type
        // gives it away; the old layout is then used if it yields a valid one.
        if (current.lockType !in KNOWN_LOCK_TYPES) {
            val legacy = decodeLegacy(bytes)
            if (legacy != null && legacy.lockType in KNOWN_LOCK_TYPES) return legacy
        }
        return current
    }

    // Files written before commit c14a18f use a different field numbering.
    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeLegacy(bytes: ByteArray): UserPreferences? {
        return try {
            ProtoBuf.decodeFromByteArray(LegacyUserPreferences.serializer(), bytes).toCurrent()
        } catch (exception: Exception) {
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(UserPreferences.serializer(), t))
    }
}
