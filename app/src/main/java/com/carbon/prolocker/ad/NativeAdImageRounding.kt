package com.carbon.prolocker.ad

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.carbon.prolocker.R

/**
 * Rounds the images inside native ad views so they match the rounded ad card.
 * It clips each view to a rounded outline instead of swapping view classes, so the SDKs keep binding
 * to the same view types and ids (ImageView for icons, the SDK's own media view for banners).
 */
object NativeAdImageRounding {
    /** Main media / banner image: close to the 16dp card radius, minus the card's inner padding. */
    private const val MEDIA_RADIUS_DP = 12f

    /** Small icon / logo. */
    private const val ICON_RADIUS_DP = 10f

    private val mediaIds = intArrayOf(R.id.adivery_image, R.id.tapsell_native_ad_media)
    private val iconIds = intArrayOf(R.id.adivery_icon, R.id.tapsell_native_ad_logo)

    fun apply(root: View) {
        val density = root.resources.displayMetrics.density
        mediaIds.forEach { id -> root.findViewById<View?>(id)?.let { round(it, MEDIA_RADIUS_DP * density) } }
        iconIds.forEach { id ->
            root.findViewById<View?>(id)?.let { icon ->
                round(icon, ICON_RADIUS_DP * density)
                // A fit-center icon would leave its own corners square inside the rounded clip.
                (icon as? ImageView)?.takeIf { it.scaleType == ImageView.ScaleType.FIT_CENTER }
                    ?.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    private fun round(view: View, radiusPx: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }
}
