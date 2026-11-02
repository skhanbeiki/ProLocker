package com.carbon.prolocker.feature.privatebrowser

import android.content.Context
import androidx.compose.ui.graphics.Color

val PrivateBrowserGradient = listOf(Color(0xFF7C3AED), Color(0xFFA855F7))

/** Opens the private browser from inside the app (the user is already past the entry lock). */
fun openPrivateBrowser(context: Context) {
    PrivateBrowserActivity.start(context)
}
