package com.carbon.prolocker.core.security

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.carbon.prolocker.core.utils.SecurityUtils
import com.carbon.prolocker.feature.entrylock.AppEntryLockActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LockSecurityRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --- PIN / pattern hashing -------------------------------------------------------------

    @Test
    fun `same credential and salt always give the same hash`() {
        val salt = SecurityUtils.generateSalt()
        assertEquals(SecurityUtils.hashCredential("1234", salt), SecurityUtils.hashCredential("1234", salt))
    }

    @Test
    fun `wrong pin or pattern does not match`() {
        val salt = SecurityUtils.generateSalt()
        val stored = SecurityUtils.hashCredential("0,1,2,5,8", salt)
        assertNotEquals(stored, SecurityUtils.hashCredential("0,1,2,5,7", salt))
        assertNotEquals(SecurityUtils.hashCredential("1234", salt), SecurityUtils.hashCredential("1235", salt))
    }

    @Test
    fun `salt changes the hash and every salt is new`() {
        val a = SecurityUtils.generateSalt()
        val b = SecurityUtils.generateSalt()
        assertNotEquals(a, b)
        assertEquals(16, Base64.decode(a, Base64.NO_WRAP).size)
        assertNotEquals(SecurityUtils.hashCredential("1234", a), SecurityUtils.hashCredential("1234", b))
    }

    @Test
    fun `stored hash never contains the plain credential`() {
        val hash = SecurityUtils.hashCredential("1234", SecurityUtils.generateSalt())
        assertFalse(hash.contains("1234"))
    }

    // --- Notification launch token (lets a tap on our own notification skip the entry lock) ---

    @Test
    fun `notification token works once`() {
        val token = NotificationLaunchToken.issue(context)
        assertTrue(NotificationLaunchToken.consume(context, token))
        assertFalse(NotificationLaunchToken.consume(context, token))
    }

    @Test
    fun `forged or missing notification token is rejected`() {
        NotificationLaunchToken.issue(context)
        assertFalse(NotificationLaunchToken.consume(context, "00112233445566778899aabbccddeeff"))
        assertFalse(NotificationLaunchToken.consume(context, null))
        assertFalse(NotificationLaunchToken.consume(context, ""))
    }

    @Test
    fun `a newer notification token replaces the older one`() {
        val old = NotificationLaunchToken.issue(context)
        val fresh = NotificationLaunchToken.issue(context)
        assertFalse(NotificationLaunchToken.consume(context, old))
        assertTrue(NotificationLaunchToken.consume(context, fresh))
    }

    // --- Entry lock state --------------------------------------------------------------------

    @Test
    fun `entry lock is required again after it is re-armed`() {
        AppEntryLockActivity.markAuthenticated()
        assertFalse(AppEntryLockActivity.requiresAuthentication())
        AppEntryLockActivity.markNeedsAuthentication()
        assertTrue(AppEntryLockActivity.requiresAuthentication())
    }
}
