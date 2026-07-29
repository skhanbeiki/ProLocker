package com.carbon.prolocker.feature.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayWindowManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentRoot: FrameLayout? = null

    private fun resolveOverlayType(): Int {
        if (Settings.canDrawOverlays(context)) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        throw SecurityException("No overlay permission granted")
    }

    @Suppress("DEPRECATION")
    private fun getDisplaySize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val (w, h) = getDisplaySize()

        return WindowManager.LayoutParams().apply {
            type = resolveOverlayType()
            format = PixelFormat.OPAQUE
            flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SECURE
            width = w
            height = h
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            windowAnimations = 0
            title = "ProLocker Lock Screen"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }
    }

    fun show(
        composeView: ComposeView,
        lifecycleOwner: LifecycleOwner,
        savedStateRegistryOwner: SavedStateRegistryOwner,
        onBackPressedDispatcher: OnBackPressedDispatcher? = null
    ) {
        dismiss()

        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onBackPressedDispatcher?.onBackPressed()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        currentRoot = root
        val layoutParams = createLayoutParams()
        try {
            windowManager.addView(root, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            currentRoot = null
        }
    }

    fun dismiss() {
        currentRoot?.let { root ->
            try {
                windowManager.removeViewImmediate(root)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay: ${e.message}")
            }
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child is ComposeView) {
                    try {
                        child.disposeComposition()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        currentRoot = null
    }

    fun isShowing(): Boolean = currentRoot != null
}
