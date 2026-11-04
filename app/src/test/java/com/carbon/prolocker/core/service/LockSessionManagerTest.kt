package com.carbon.prolocker.core.service

import androidx.datastore.core.DataStore
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore so the lock logic can be tested without disk or Android. */
internal class FakePreferencesStore(initial: UserPreferences) : DataStore<UserPreferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<UserPreferences> = state
    override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences): UserPreferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class LockSessionManagerTest {

    private val app = "com.example.chat"
    private val other = "com.example.bank"

    private fun manager(graceSeconds: Int = 2) = LockSessionManager(
        PreferencesRepository(FakePreferencesStore(UserPreferences(shortExitDurationSeconds = graceSeconds)))
    )

    @Test
    fun `apps are locked until they are unlocked`() {
        assertFalse(manager().isAppUnlocked(app))
    }

    @Test
    fun `unlocked app stays unlocked while in the foreground`() {
        val m = manager()
        m.unlockApp(app)
        assertTrue(m.isAppUnlocked(app))
        assertTrue(m.isAppUnlocked(app))
    }

    @Test
    fun `unlocking one app does not unlock another`() {
        val m = manager()
        m.unlockApp(app)
        assertFalse(m.isAppUnlocked(other))
    }

    @Test
    fun `returning within the grace period keeps the app unlocked`() {
        val m = manager(graceSeconds = 2)
        m.unlockApp(app)
        m.markAppBackgrounded(app, System.currentTimeMillis() - 500)
        assertTrue(m.isAppUnlocked(app))
        // Back in the foreground: the grace window is reset, not consumed again.
        assertTrue(m.isAppUnlocked(app))
    }

    @Test
    fun `returning after the grace period locks the app again`() {
        val m = manager(graceSeconds = 2)
        m.unlockApp(app)
        m.markAppBackgrounded(app, System.currentTimeMillis() - 5_000)
        assertFalse(m.isAppUnlocked(app))
        assertFalse(m.isAppUnlocked(app))
    }

    @Test
    fun `zero grace period locks immediately on leaving`() {
        val m = manager(graceSeconds = 0)
        m.unlockApp(app)
        m.markAppBackgrounded(app, System.currentTimeMillis() - 1)
        assertFalse(m.isAppUnlocked(app))
    }

    @Test
    fun `backgrounding a locked app never unlocks it`() {
        val m = manager()
        m.markAppBackgrounded(app)
        assertFalse(m.isAppUnlocked(app))
    }

    @Test
    fun `lockAll locks every app`() {
        val m = manager()
        m.unlockApp(app)
        m.unlockApp(other)
        m.lockAll()
        assertFalse(m.isAppUnlocked(app))
        assertFalse(m.isAppUnlocked(other))
    }

    @Test
    fun `disarm and remove lock the app`() {
        val m = manager()
        m.unlockApp(app)
        m.disarmUnlock(app)
        assertFalse(m.isAppUnlocked(app))
        m.unlockApp(other)
        m.removeUnlockedApp(other)
        assertFalse(m.isAppUnlocked(other))
    }
}

class FailedAttemptManagerTest {

    @Test
    fun `failed attempts are counted and reset`() {
        val m = FailedAttemptManager()
        assertEquals(0, m.state.value.count)
        m.recordFailedAttempt()
        m.recordFailedAttempt()
        assertEquals(2, m.state.value.count)
        assertTrue(m.state.value.lastAttemptTime > 0)
        m.reset()
        assertEquals(0, m.state.value.count)
    }
}
