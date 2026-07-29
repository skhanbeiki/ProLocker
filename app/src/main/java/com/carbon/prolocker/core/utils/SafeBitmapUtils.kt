package com.carbon.prolocker.core.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Safely converts a Drawable to a Bitmap with size constraints.
 * Prevents Canvas too large bitmap crashes and zero dimension crashes.
 */
fun Drawable?.toSafeBitmap(maxSize: Int = 128): ImageBitmap? {
    if (this == null) return null
    return try {
        val width = intrinsicWidth.coerceIn(1, maxSize)
        val height = intrinsicHeight.coerceIn(1, maxSize)
        toBitmap(width, height).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Safely converts a Drawable to a Bitmap with specified dimensions.
 * Prevents Canvas too large bitmap crashes and zero dimension crashes.
 */
fun Drawable?.toSafeBitmap(width: Int, height: Int): Bitmap? {
    if (this == null) return null
    val safeWidth = width.coerceIn(1, 512)
    val safeHeight = height.coerceIn(1, 512)
    return try {
        toBitmap(safeWidth, safeHeight)
    } catch (_: Exception) {
        null
    }
}
