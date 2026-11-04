package com.carbon.prolocker.feature.notificationmanager.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NotificationHiderRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: NotificationHiderRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repo = NotificationHiderRepository(context)
    }

    private fun note(
        id: String,
        pkg: String = "com.chat",
        title: String = "Ali",
        text: String = "msg $id",
        at: Long = 1_000L,
        key: String = "0|$pkg|1",
        read: Boolean = false
    ) = HiddenNotification(id, pkg, title, text, at, key, read)

    // --- summarize (home list of apps) -------------------------------------------------------

    @Test
    fun `summary groups per app, newest app first, and counts unread`() {
        val list = listOf(
            note("1", pkg = "a", at = 100, title = "old"),
            note("2", pkg = "a", at = 300, title = "new", read = true),
            note("3", pkg = "b", at = 200)
        )
        val summary = NotificationHiderRepository.summarize(list)
        assertEquals(listOf("a", "b"), summary.map { it.packageName })
        val a = summary.first()
        assertEquals(2, a.count)
        assertEquals(300L, a.lastTime)
        assertEquals("new", a.lastTitle)
        assertEquals(1, a.unread)
        assertEquals(1, summary[1].unread)
    }

    @Test
    fun `app with the newest message moves to the top`() {
        val list = listOf(note("1", pkg = "a", at = 100), note("2", pkg = "b", at = 200), note("3", pkg = "a", at = 300))
        assertEquals(listOf("a", "b"), NotificationHiderRepository.summarize(list).map { it.packageName })
    }

    @Test
    fun `summary falls back to the text when the title is blank`() {
        val summary = NotificationHiderRepository.summarize(listOf(note("1", title = " ", text = "hello")))
        assertEquals("hello", summary.single().lastTitle)
    }

    @Test
    fun `summary of nothing is empty`() {
        assertTrue(NotificationHiderRepository.summarize(emptyList()).isEmpty())
    }

    // --- add / duplicates / unseen -----------------------------------------------------------

    @Test
    fun `identical repost is not kept twice`() {
        assertTrue(repo.add(note("1"), null))
        assertFalse(repo.add(note("2", text = "msg 1"), null))
        assertEquals(1, repo.notifications.value.size)
    }

    @Test
    fun `a new message in the same conversation is kept, newest first`() {
        repo.add(note("1", text = "first"), null)
        assertTrue(repo.add(note("2", text = "second"), null))
        assertEquals(listOf("2", "1"), repo.notifications.value.map { it.id })
    }

    @Test
    fun `unseen counter grows with new messages and resets when seen`() {
        repo.add(note("1"), null)
        repo.add(note("2"), null)
        assertEquals(2, repo.unseenCount.value)
        repo.markSeen()
        assertEquals(0, repo.unseenCount.value)
    }

    // --- read state ---------------------------------------------------------------------------

    @Test
    fun `opening an app marks only that app as read`() {
        repo.add(note("1", pkg = "a"), null)
        repo.add(note("2", pkg = "b"), null)
        repo.markAppRead("a")
        val byId = repo.notifications.value.associateBy { it.id }
        assertTrue(byId.getValue("1").read)
        assertFalse(byId.getValue("2").read)
    }

    // --- deleting -----------------------------------------------------------------------------

    @Test
    fun `delete and clearApp remove only what was asked`() {
        repo.add(note("1", pkg = "a"), null)
        repo.add(note("2", pkg = "a", text = "other"), null)
        repo.add(note("3", pkg = "b"), null)
        repo.delete("1")
        assertEquals(listOf("3", "2"), repo.notifications.value.map { it.id })
        repo.clearApp("a")
        assertEquals(listOf("3"), repo.notifications.value.map { it.id })
        repo.clearAll()
        assertTrue(repo.notifications.value.isEmpty())
    }

    // --- hidden apps and the one-time intro ---------------------------------------------------

    @Test
    fun `hidden apps are remembered`() {
        repo.hideApp("com.chat")
        assertTrue(repo.isHidden("com.chat"))
        assertTrue(NotificationHiderRepository(context).isHidden("com.chat"))
        repo.showApp("com.chat")
        assertFalse(repo.isHidden("com.chat"))
    }

    @Test
    fun `intro is not shown again after every app is un-hidden`() {
        assertFalse(repo.wasSetUp.value)
        repo.setHiddenApps(setOf("com.chat"))
        assertTrue(repo.wasSetUp.value)
        repo.showApp("com.chat")
        assertTrue(repo.hiddenApps.value.isEmpty())
        assertTrue(repo.wasSetUp.value)
        assertTrue(NotificationHiderRepository(context).wasSetUp.value)
    }

    @Test
    fun `enabled switch is remembered`() {
        assertFalse(repo.enabled.value)
        repo.setEnabled(true)
        assertTrue(NotificationHiderRepository(context).enabled.value)
    }
}
