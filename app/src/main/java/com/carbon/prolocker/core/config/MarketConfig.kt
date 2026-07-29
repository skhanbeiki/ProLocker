package com.carbon.prolocker.core.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.carbon.prolocker.BuildConfig


object MarketConfig {
    val marketName: String
        get() = BuildConfig.MARKET_NAME

    val marketType: String
        get() = BuildConfig.MARKET_TYPE

    // Example use cases derived from flavors
    val isGooglePlay: Boolean
        get() = marketType == "googleplay"

    val isBazaar: Boolean
        get() = marketType == "bazaar"

    val isMyket: Boolean
        get() = marketType == "myket"

    val contactUsUrl: String = "mailto:carbon.apps@gmail.com"

    val aboutUsUrl: String = "https://example.com/about"

    fun shareUrl(context: Context): String {
        val packageName = context.packageName
        return when {
            isBazaar -> "https://cafebazaar.ir/app/$packageName"
            isMyket -> "https://myket.ir/app/$packageName"
            else -> "https://play.google.com/store/apps/details?id=$packageName"
        }
    }

    fun rateApp(context: Context) {
        val packageName = context.packageName

        val rateIntent = when {
            isBazaar -> {
                Intent(Intent.ACTION_EDIT).apply {
                    data = Uri.parse("bazaar://details?id=$packageName")
                    setPackage("com.farsitel.bazaar")
                }
            }

            isMyket -> {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("myket://comment?id=$packageName")
                }
            }

            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(shareUrl(context))
                }
            }
        }

        try {
            context.startActivity(rateIntent)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(shareUrl(context))
                    )
                )
            } catch (_: Exception) {
            }
        }
    }
}
