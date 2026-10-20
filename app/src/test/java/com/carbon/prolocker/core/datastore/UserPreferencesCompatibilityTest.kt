package com.carbon.prolocker.core.datastore

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier
import java.util.Random

/**
 * Copy of the current [UserPreferences] WITHOUT @ProtoNumber: ProtoBuf numbers its fields by
 * declaration index + 1, which is the layout the annotations in the real class must reproduce.
 * Every test value is non-default because a ProtoBuf writer may omit default values.
 */
@Serializable
internal data class PlainUserPreferences(
    val onboardingCompleted: Boolean = false,
    val isDarkMode: Boolean = true,
    val selectedTab: String = "tools",
    val language: String = "fa",
    val lockType: String = "NONE",
    val hashedCredential: String = "",
    val securitySalt: String = "",
    val vibrationEnabled: Boolean = true,
    val hidePatternPath: Boolean = false,
    val failedAttemptsThreshold: Int = 3,
    val autoStartEnabled: Boolean = true,
    val captureIntruderSelfie: Boolean = false,
    val triggerAlarm: Boolean = false,
    val securityQuestionHash: String = "",
    val securityAnswerHash: String = "",
    val shortExitDurationSeconds: Int = 2,
    val relockOnScreenOff: Boolean = true,
    val lockScreenRotation: String = "SYSTEM",
    val isStealthModeEnabled: Boolean = false,
    val deviceUuid: String = "",
    val selectedBackgroundUrl: String = "",
    val lastBackgroundId: Int = 0,
    val remoteConfigJson: String = "",
    val remoteConfigInterval: Long = 360,
    val lastRemoteConfigSync: Long = 0,
    val remoteConfigInstallDate: Long = 0,
    val themeInterstitialCounter: Int = 0,
    val lastRamCleanerRunTime: Long = 0,
    val lastRamCleanerNotificationTime: Long = 0,
    val ramCleanerNotificationEnabled: Boolean = true,
    val rateAppLaunchCount: Int = 0,
    val rateDialogStage: Int = 0,
    val userHasRated: Boolean = false,
    val isProtectionEnabled: Boolean = true,
    val recoveryOnboardingDismissed: Boolean = false,
    val lastInstalledVersionCode: Int = 0,
    val fingerprintUnlockEnabled: Boolean = false,
    val ramCleanerInterstitialCounter: Int = 0,
    val inAppInterstitialCounter: Int = 0,
    val suggestionButtonDisplayCounter: Int = 0,
    val suggestionButtonLastClickedDate: String = ""
)

/** Copy of the layout written before commit c14a18f (no selectedTab yet), no annotations. */
@Serializable
internal data class PlainLegacyUserPreferences(
    val onboardingCompleted: Boolean = false,
    val isDarkMode: Boolean = true,
    val language: String = "fa",
    val lockType: String = "NONE",
    val hashedCredential: String = "",
    val securitySalt: String = "",
    val vibrationEnabled: Boolean = true,
    val hidePatternPath: Boolean = false,
    val failedAttemptsThreshold: Int = 3,
    val autoStartEnabled: Boolean = true,
    val captureIntruderSelfie: Boolean = false,
    val triggerAlarm: Boolean = false,
    val securityQuestionHash: String = "",
    val securityAnswerHash: String = "",
    val shortExitDurationSeconds: Int = 2,
    val relockOnScreenOff: Boolean = true,
    val lockScreenRotation: String = "SYSTEM",
    val isStealthModeEnabled: Boolean = false,
    val deviceUuid: String = "",
    val selectedBackgroundUrl: String = "",
    val lastBackgroundId: Int = 0,
    val remoteConfigJson: String = "",
    val remoteConfigInterval: Long = 360,
    val lastRemoteConfigSync: Long = 0,
    val remoteConfigInstallDate: Long = 0,
    val themeInterstitialCounter: Int = 0,
    val lastRamCleanerRunTime: Long = 0,
    val lastRamCleanerNotificationTime: Long = 0,
    val ramCleanerNotificationEnabled: Boolean = true,
    val rateAppLaunchCount: Int = 0,
    val rateDialogStage: Int = 0,
    val userHasRated: Boolean = false,
    val isProtectionEnabled: Boolean = true,
    val recoveryOnboardingDismissed: Boolean = false,
    val lastInstalledVersionCode: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
class UserPreferencesCompatibilityTest {

    private val knownLockTypes = setOf("NONE", "PIN", "PATTERN")

    // ---- (a) the on-disk format of the current class did not change

    @Test
    fun testValuesAreAllDifferentFromDefaults() {
        val full = fieldMap(fullPlain())
        val defaults = fieldMap(PlainUserPreferences())
        assertEquals(41, full.size)
        for ((name, value) in full) {
            assertNotEquals("field $name must use a non-default test value", defaults[name], value)
        }
    }

    @Test
    fun realClassCoversEveryPlainField() {
        assertEquals(fieldMap(fullPlain()).keys.toList(), fieldMap(fullReal()).keys.toList())
    }

    @Test
    fun encodedBytesAreIdenticalToTheUnannotatedLayout() {
        val plainBytes = ProtoBuf.encodeToByteArray(PlainUserPreferences.serializer(), fullPlain())
        val realBytes = ProtoBuf.encodeToByteArray(UserPreferences.serializer(), fullReal())
        assertTrue(plainBytes.isNotEmpty())
        assertArrayEquals(plainBytes, realBytes)
    }

    @Test
    fun realClassDecodesBytesWrittenByTheUnannotatedLayout() {
        val plain = fullPlain()
        val bytes = ProtoBuf.encodeToByteArray(PlainUserPreferences.serializer(), plain)
        val decoded = ProtoBuf.decodeFromByteArray(UserPreferences.serializer(), bytes)
        assertSameFields(plain, decoded)
    }

    @Test
    fun unannotatedLayoutDecodesBytesWrittenByTheRealClass() {
        val real = fullReal()
        val bytes = ProtoBuf.encodeToByteArray(UserPreferences.serializer(), real)
        val decoded = ProtoBuf.decodeFromByteArray(PlainUserPreferences.serializer(), bytes)
        assertSameFields(real, decoded)
    }

    @Test
    fun serializerRoundTripKeepsEveryField() {
        val real = fullReal()
        val out = ByteArrayOutputStream()
        runBlocking { UserPreferencesSerializer.writeTo(real, out) }
        val read = runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(out.toByteArray())) }
        assertEquals(real, read)
    }

    @Test
    fun currentFileWithAnUnknownLockTypeIsNotReplacedByTheLegacyLayout() {
        val real = fullReal().copy(lockType = "CUSTOM")
        val out = ByteArrayOutputStream()
        runBlocking { UserPreferencesSerializer.writeTo(real, out) }
        val read = runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(out.toByteArray())) }
        assertEquals(real, read)
    }

    // ---- (b) files still in the pre-c14a18f layout are recovered

    @Test
    fun legacyFileKeepsCredentialsLockTypeOnboardingAndIds() {
        val read = readLegacy(encodeDefaults = true)
        assertEquals("PIN", read.lockType)
        assertEquals("hash-credential-5", read.hashedCredential)
        assertEquals("salt-6", read.securitySalt)
        assertEquals(true, read.onboardingCompleted)
        assertEquals("{\"key\":\"value-22\"}", read.remoteConfigJson)
        assertEquals("uuid-19-abcdef", read.deviceUuid)
    }

    @Test
    fun legacyFileKeepsEveryOtherField() {
        for (encodeDefaults in listOf(true, false)) {
            val legacy = fullLegacy()
            val readFields = fieldMap(readLegacy(encodeDefaults))
            for ((name, value) in fieldMap(legacy)) {
                assertTrue("missing field $name", readFields.containsKey(name))
                assertEquals("field $name (encodeDefaults=$encodeDefaults)", value, readFields[name])
            }
        }
    }

    @Test
    fun legacyFileGivesCurrentDefaultsToFieldsThatDidNotExistYet() {
        val read = readLegacy(encodeDefaults = true)
        val defaults = UserPreferences()
        assertEquals(defaults.selectedTab, read.selectedTab)
        assertEquals(defaults.fingerprintUnlockEnabled, read.fingerprintUnlockEnabled)
        assertEquals(defaults.ramCleanerInterstitialCounter, read.ramCleanerInterstitialCounter)
        assertEquals(defaults.inAppInterstitialCounter, read.inAppInterstitialCounter)
        assertEquals(defaults.suggestionButtonDisplayCounter, read.suggestionButtonDisplayCounter)
        assertEquals(defaults.suggestionButtonLastClickedDate, read.suggestionButtonLastClickedDate)
    }

    // The insertion of selectedTab moved String fields onto String fields: a legacy file that only
    // carries Strings in the shifted range decodes as the current class without any exception.
    @Test
    fun legacyFileThatDecodesAsCurrentLayoutWithoutAnErrorIsStillRecovered() {
        val legacy = PlainLegacyUserPreferences(
            lockType = "PIN",
            hashedCredential = "hash-credential-5",
            securitySalt = "salt-6"
        )
        val bytes = ProtoBuf { encodeDefaults = false }
            .encodeToByteArray(PlainLegacyUserPreferences.serializer(), legacy)

        val misread = ProtoBuf.decodeFromByteArray(UserPreferences.serializer(), bytes)
        assertFalse("the shift must not raise an error and must give a bogus lock type", misread.lockType in knownLockTypes)

        val read = readFrom(bytes)
        assertEquals("PIN", read.lockType)
        assertEquals("hash-credential-5", read.hashedCredential)
        assertEquals("salt-6", read.securitySalt)
        assertEquals(legacy.vibrationEnabled, read.vibrationEnabled)
        assertEquals(legacy.isDarkMode, read.isDarkMode)
        assertEquals(UserPreferences().selectedTab, read.selectedTab)
    }

    @Test
    fun legacyPatternFileWithOnboardingCompletedIsRecoveredToo() {
        val legacy = PlainLegacyUserPreferences(
            onboardingCompleted = true,
            lockType = "PATTERN",
            hashedCredential = "pattern-hash",
            securitySalt = "pattern-salt"
        )
        val bytes = ProtoBuf { encodeDefaults = false }
            .encodeToByteArray(PlainLegacyUserPreferences.serializer(), legacy)
        val read = readFrom(bytes)
        assertEquals(true, read.onboardingCompleted)
        assertEquals("PATTERN", read.lockType)
        assertEquals("pattern-hash", read.hashedCredential)
        assertEquals("pattern-salt", read.securitySalt)
    }

    // ---- (c) garbage never crashes

    @Test
    fun garbageBytesReturnDefaults() {
        val garbage = ByteArray(32) { 0xFF.toByte() }
        val read = runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(garbage)) }
        assertEquals(UserPreferencesSerializer.defaultValue, read)
    }

    @Test
    fun randomBytesNeverThrow() {
        val random = Random(20260920)
        repeat(500) {
            val bytes = ByteArray(1 + random.nextInt(200)).also { random.nextBytes(it) }
            runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(bytes)) }
        }
    }

    @Test
    fun emptyFileReturnsDefaults() {
        val read = runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(ByteArray(0))) }
        assertEquals(UserPreferencesSerializer.defaultValue, read)
    }

    // ---- helpers

    private fun readFrom(bytes: ByteArray): UserPreferences =
        runBlocking { UserPreferencesSerializer.readFrom(ByteArrayInputStream(bytes)) }

    private fun readLegacy(encodeDefaults: Boolean): UserPreferences {
        val bytes = ProtoBuf { this.encodeDefaults = encodeDefaults }
            .encodeToByteArray(PlainLegacyUserPreferences.serializer(), fullLegacy())
        return readFrom(bytes)
    }

    private fun fieldMap(obj: Any): Map<String, Any?> =
        obj.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .associate { field ->
                field.isAccessible = true
                field.name to field.get(obj)
            }

    private fun assertSameFields(expected: Any, actual: Any) {
        val actualFields = fieldMap(actual)
        for ((name, value) in fieldMap(expected)) {
            assertTrue("missing field $name", actualFields.containsKey(name))
            assertEquals("field $name", value, actualFields[name])
        }
    }

    private fun fullPlain() = PlainUserPreferences(
        onboardingCompleted = true,
        isDarkMode = false,
        selectedTab = "settings",
        language = "de",
        lockType = "PIN",
        hashedCredential = "hash-credential-5",
        securitySalt = "salt-6",
        vibrationEnabled = false,
        hidePatternPath = true,
        failedAttemptsThreshold = 9,
        autoStartEnabled = false,
        captureIntruderSelfie = true,
        triggerAlarm = true,
        securityQuestionHash = "question-hash-13",
        securityAnswerHash = "answer-hash-14",
        shortExitDurationSeconds = 15,
        relockOnScreenOff = false,
        lockScreenRotation = "PORTRAIT",
        isStealthModeEnabled = true,
        deviceUuid = "uuid-19-abcdef",
        selectedBackgroundUrl = "https://example.test/bg-20.jpg",
        lastBackgroundId = 21,
        remoteConfigJson = "{\"key\":\"value-22\"}",
        remoteConfigInterval = 1234567890123L,
        lastRemoteConfigSync = 2234567890123L,
        remoteConfigInstallDate = 3234567890123L,
        themeInterstitialCounter = 26,
        lastRamCleanerRunTime = 4234567890123L,
        lastRamCleanerNotificationTime = 5234567890123L,
        ramCleanerNotificationEnabled = false,
        rateAppLaunchCount = 30,
        rateDialogStage = 31,
        userHasRated = true,
        isProtectionEnabled = false,
        recoveryOnboardingDismissed = true,
        lastInstalledVersionCode = 101,
        fingerprintUnlockEnabled = true,
        ramCleanerInterstitialCounter = 37,
        inAppInterstitialCounter = 38,
        suggestionButtonDisplayCounter = 39,
        suggestionButtonLastClickedDate = "2026-09-26"
    )

    private fun fullReal() = UserPreferences(
        onboardingCompleted = true,
        isDarkMode = false,
        selectedTab = "settings",
        language = "de",
        lockType = "PIN",
        hashedCredential = "hash-credential-5",
        securitySalt = "salt-6",
        vibrationEnabled = false,
        hidePatternPath = true,
        failedAttemptsThreshold = 9,
        autoStartEnabled = false,
        captureIntruderSelfie = true,
        triggerAlarm = true,
        securityQuestionHash = "question-hash-13",
        securityAnswerHash = "answer-hash-14",
        shortExitDurationSeconds = 15,
        relockOnScreenOff = false,
        lockScreenRotation = "PORTRAIT",
        isStealthModeEnabled = true,
        deviceUuid = "uuid-19-abcdef",
        selectedBackgroundUrl = "https://example.test/bg-20.jpg",
        lastBackgroundId = 21,
        remoteConfigJson = "{\"key\":\"value-22\"}",
        remoteConfigInterval = 1234567890123L,
        lastRemoteConfigSync = 2234567890123L,
        remoteConfigInstallDate = 3234567890123L,
        themeInterstitialCounter = 26,
        lastRamCleanerRunTime = 4234567890123L,
        lastRamCleanerNotificationTime = 5234567890123L,
        ramCleanerNotificationEnabled = false,
        rateAppLaunchCount = 30,
        rateDialogStage = 31,
        userHasRated = true,
        isProtectionEnabled = false,
        recoveryOnboardingDismissed = true,
        lastInstalledVersionCode = 101,
        fingerprintUnlockEnabled = true,
        ramCleanerInterstitialCounter = 37,
        inAppInterstitialCounter = 38,
        suggestionButtonDisplayCounter = 39,
        suggestionButtonLastClickedDate = "2026-09-26"
    )

    private fun fullLegacy() = PlainLegacyUserPreferences(
        onboardingCompleted = true,
        isDarkMode = false,
        language = "de",
        lockType = "PIN",
        hashedCredential = "hash-credential-5",
        securitySalt = "salt-6",
        vibrationEnabled = false,
        hidePatternPath = true,
        failedAttemptsThreshold = 9,
        autoStartEnabled = false,
        captureIntruderSelfie = true,
        triggerAlarm = true,
        securityQuestionHash = "question-hash-13",
        securityAnswerHash = "answer-hash-14",
        shortExitDurationSeconds = 15,
        relockOnScreenOff = false,
        lockScreenRotation = "PORTRAIT",
        isStealthModeEnabled = true,
        deviceUuid = "uuid-19-abcdef",
        selectedBackgroundUrl = "https://example.test/bg-20.jpg",
        lastBackgroundId = 21,
        remoteConfigJson = "{\"key\":\"value-22\"}",
        remoteConfigInterval = 1234567890123L,
        lastRemoteConfigSync = 2234567890123L,
        remoteConfigInstallDate = 3234567890123L,
        themeInterstitialCounter = 26,
        lastRamCleanerRunTime = 4234567890123L,
        lastRamCleanerNotificationTime = 5234567890123L,
        ramCleanerNotificationEnabled = false,
        rateAppLaunchCount = 30,
        rateDialogStage = 31,
        userHasRated = true,
        isProtectionEnabled = false,
        recoveryOnboardingDismissed = true,
        lastInstalledVersionCode = 101
    )
}
