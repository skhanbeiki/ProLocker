package com.carbon.prolocker.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The two in-memory "skip the entry lock once" tokens must be one-shot and expire. */
class TrustedLaunchTest {

    @Before
    fun reset() {
        TrustedInternalLaunchManager.clear()
        TrustedReturnManager.clear()
    }

    @Test
    fun `internal launch token is consumed exactly once`() {
        TrustedInternalLaunchManager.arm("memory")
        assertTrue(TrustedInternalLaunchManager.isArmed())
        assertEquals("memory", TrustedInternalLaunchManager.consume())
        assertNull(TrustedInternalLaunchManager.consume())
        assertFalse(TrustedInternalLaunchManager.isArmed())
    }

    @Test
    fun `expired internal launch token does not skip the lock`() {
        TrustedInternalLaunchManager.arm("memory", ttlMs = -1)
        assertFalse(TrustedInternalLaunchManager.isArmed())
        assertNull(TrustedInternalLaunchManager.consume())
    }

    @Test
    fun `cleared internal launch token does not skip the lock`() {
        TrustedInternalLaunchManager.arm("memory")
        TrustedInternalLaunchManager.clear()
        assertNull(TrustedInternalLaunchManager.consume())
    }

    @Test
    fun `no internal launch token means lock`() {
        assertNull(TrustedInternalLaunchManager.consume())
    }

    @Test
    fun `trusted return is consumed exactly once`() {
        TrustedReturnManager.startTrustedReturn()
        assertTrue(TrustedReturnManager.consumeTrustedReturn())
        assertFalse(TrustedReturnManager.consumeTrustedReturn())
    }

    @Test
    fun `expired trusted return does not skip the lock`() {
        TrustedReturnManager.startTrustedReturn(durationMillis = -1)
        assertFalse(TrustedReturnManager.consumeTrustedReturn())
    }

    @Test
    fun `cleared trusted return does not skip the lock`() {
        TrustedReturnManager.startTrustedReturn()
        TrustedReturnManager.clear()
        assertFalse(TrustedReturnManager.consumeTrustedReturn())
    }
}
