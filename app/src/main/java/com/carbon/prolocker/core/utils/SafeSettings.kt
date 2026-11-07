package com.carbon.prolocker.core.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher

/**
 * Opens system settings screens without crashing on devices that lack them
 * (ActivityNotFoundException for USAGE_ACCESS_SETTINGS, battery or accessibility pages on some ROMs).
 * Falls back to this app's details page, then to the main Settings screen.
 */
object SafeSettings {
    private const val TAG = "SafeSettings"

    private fun fallbacks(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
        Intent(Settings.ACTION_SETTINGS)
    )

    /** startActivity with fallbacks. Returns false if nothing could be opened. */
    fun open(context: Context, intent: Intent): Boolean {
        for (candidate in listOf(intent) + fallbacks(context)) {
            try {
                if (context !is Activity) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No activity for ${candidate.action}", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to open ${candidate.action}", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to open ${candidate.action}", e)
            }
        }
        return false
    }

    /** ActivityResultLauncher.launch with the same fallbacks (the result callback still runs on return). */
    fun launch(launcher: ActivityResultLauncher<Intent>, context: Context, intent: Intent): Boolean {
        for (candidate in listOf(intent) + fallbacks(context)) {
            try {
                launcher.launch(candidate)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No activity for ${candidate.action}", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to open ${candidate.action}", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to open ${candidate.action}", e)
            }
        }
        return false
    }
}
