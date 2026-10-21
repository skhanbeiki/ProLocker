package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.carbon.prolocker.feature.lock.LockService
import org.koin.android.ext.android.inject

class InterstitialAdActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "InterstitialAdActivity"
        const val EXTRA_PLACEMENT = "extra_placement"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"

        private var onClosedCallback: (() -> Unit)? = null
        private var onErrorCallback: ((String) -> Unit)? = null

        fun start(
            context: Context,
            placement: String,
            providerName: String? = null,
            onClosed: () -> Unit = {},
            onError: (String) -> Unit = {}
        ) {
            LockService.prepareForAd()
            onClosedCallback = onClosed
            onErrorCallback = onError
            val intent = Intent(context, InterstitialAdActivity::class.java).apply {
                putExtra(EXTRA_PLACEMENT, placement)
                if (!providerName.isNullOrBlank()) {
                    putExtra(EXTRA_PROVIDER_NAME, providerName)
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
        }
    }

    private val adManager: AdManager by inject()
    private val handler = Handler(Looper.getMainLooper())
    private var isAdFinished = false
    private var hasStartedAd = false
    private var wasCoveredByAd = false
    private var placement: String = AdPlacement.INTERSTITIAL_BACKGROUND
    private var providerName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Set opaque solid background to completely cover the screen (never expose the locked app)
        val backgroundColor = Color.parseColor("#101014")
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
            val progressBar = ProgressBar(this@InterstitialAdActivity).apply {
                isIndeterminate = true
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            addView(progressBar, lp)
        }
        setContentView(rootLayout)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleClose()
            }
        })

        placement = intent.getStringExtra(EXTRA_PLACEMENT) ?: AdPlacement.INTERSTITIAL_BACKGROUND
        providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME)
    }

    override fun onResume() {
        super.onResume()
        if (isAdFinished) return
        if (!hasStartedAd) {
            hasStartedAd = true
            Log.i(TAG, "Activity resumed and attached to window. Now hiding overlay and requesting ad.")

            // Dismiss LockScreen overlay cleanly now that this opaque Activity covers the screen
            LockService.hideOverlayForAd()

            // Safety timeout of 6s to prevent user getting stuck if ad fails or hangs
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && !isAdFinished) {
                    Log.w(TAG, "Interstitial ad timed out after 6s")
                    handleError("Timeout")
                }
            }, 6000)

            adManager.showInterstitialAdDirect(
                activity = this,
                placement = placement,
                providerNameOverride = providerName,
                onClosed = {
                    handleClose()
                },
                onError = { error ->
                    handleError(error)
                }
            )
        } else if (wasCoveredByAd) {
            // The ad's own activity is gone but it never called back.
            Log.i(TAG, "Resumed again after the ad activity closed without a callback")
            handleClose()
        }
    }

    override fun onPause() {
        super.onPause()
        if (hasStartedAd && !isAdFinished && !isFinishing) {
            // An ad activity opened on top of us: the safety timeout must not restore the lock screen over a playing ad.
            wasCoveredByAd = true
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun handleClose() {
        if (isAdFinished) return
        isAdFinished = true
        handler.removeCallbacksAndMessages(null)
        adManager.resetInterstitialShowingState()
        val cb = onClosedCallback
        onClosedCallback = null
        onErrorCallback = null
        LockService.restoreLockScreen(this)
        cb?.invoke()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun handleError(error: String) {
        if (isAdFinished) return
        isAdFinished = true
        Log.e(TAG, "InterstitialAdActivity handleError: $error")
        handler.removeCallbacksAndMessages(null)
        adManager.resetInterstitialShowingState()
        val cb = onErrorCallback
        onClosedCallback = null
        onErrorCallback = null
        LockService.restoreLockScreen(this)
        cb?.invoke(error)
        finish()
        overridePendingTransition(0, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleClose()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        adManager.resetInterstitialShowingState()
        if (!isAdFinished) {
            isAdFinished = true
            LockService.restoreLockScreen(this)
        }
        onClosedCallback = null
        onErrorCallback = null
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
