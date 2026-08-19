package com.carbon.prolocker.core.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.carbon.prolocker.BuildConfig
import com.google.android.play.core.review.ReviewManagerFactory

object MarketConfig {
    val marketName: String
        get() = BuildConfig.MARKET_NAME

    val marketType: String
        get() = BuildConfig.MARKET_TYPE

    val isGooglePlay: Boolean
        get() = marketType == "googleplay"

    val isBazaar: Boolean
        get() = marketType == "bazaar"

    val isMyket: Boolean
        get() = marketType == "myket"

    val contactUsUrl: String = "mailto:carbon.prolocker@gmail.com"
    val aboutUsUrl: String = "https://carbonprolocker.com/about"
    val privacyPolicyUrl: String = "https://carbonprolocker.com/privacy-policy"

    fun shareUrl(context: Context): String {
        val packageName = context.packageName
        return when {
            isBazaar -> "https://cafebazaar.ir/app/$packageName"
            isMyket -> "https://myket.ir/app/$packageName"
            else -> "https://play.google.com/store/apps/details?id=$packageName"
        }
    }

    fun rateApp(context: Context) {
        if (isGooglePlay && context is Activity) {
            try {
                val manager = ReviewManagerFactory.create(context)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        manager.launchReviewFlow(context, reviewInfo)
                    } else {
                        fallbackRateApp(context)
                    }
                }
                return
            } catch (_: Exception) {
                fallbackRateApp(context)
                return
            }
        }
        fallbackRateApp(context)
    }

    private fun fallbackRateApp(context: Context) {
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
                    Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl(context)))
                )
            } catch (_: Exception) {
            }
        }
    }
}
