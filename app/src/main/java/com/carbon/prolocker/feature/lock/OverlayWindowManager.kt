package com.carbon.prolocker.feature.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner

class OverlayWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayWindowManager"

        // Frame callbacks stop while the display is off; never leave the root without content.
        private const val CONTENT_ATTACH_FALLBACK_MS = 150L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentRoot: FrameLayout? = null
    private var currentComposeView: ComposeView? = null
    private var isOverlayAttached: Boolean = false
    private var pendingFrameCallback: Choreographer.FrameCallback? = null
    private var pendingAttach: Runnable? = null

    private fun resolveOverlayType(): Int {
        if (Settings.canDrawOverlays(context)) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        throw SecurityException("No overlay permission granted")
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = resolveOverlayType()
            format = PixelFormat.OPAQUE
            flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        (if (!com.carbon.prolocker.BuildConfig.DEBUG) WindowManager.LayoutParams.FLAG_SECURE else 0)
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            windowAnimations = 0
            title = "ProLocker Lock Screen"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }
    }

    /**
     * Adds a plain opaque root window right away (it covers the locked app in the first frame)
     * and attaches the Compose content one frame later.
     *
     * @return true only if the window was added.
     */
    fun show(
        composeViewFactory: () -> ComposeView,
        lifecycleOwner: LifecycleOwner,
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        onBackPressedDispatcher: OnBackPressedDispatcher? = null,
        placeholderColor: Int = Color.BLACK,
        onContentFailed: (() -> Unit)? = null,
        onRootFirstDraw: (() -> Unit)? = null,
        onContentFirstDraw: (() -> Unit)? = null
    ): Boolean {
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
            setBackgroundColor(placeholderColor)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        }
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (onRootFirstDraw != null) {
            doOnFirstDraw(root, onRootFirstDraw)
        }

        currentRoot = root
        try {
            windowManager.addView(root, createLayoutParams())
            isOverlayAttached = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            currentRoot = null
            isOverlayAttached = false
            return false
        }

        scheduleContentAttach(root, composeViewFactory, onContentFailed, onContentFirstDraw)
        return true
    }

    private fun scheduleContentAttach(
        root: FrameLayout,
        composeViewFactory: () -> ComposeView,
        onContentFailed: (() -> Unit)?,
        onContentFirstDraw: (() -> Unit)?
    ) {
        var done = false
        val attach = Runnable {
            if (done) return@Runnable
            done = true
            if (currentRoot !== root || !isOverlayAttached) return@Runnable
            cancelPendingAttach()
            try {
                val composeView = composeViewFactory()
                currentComposeView = composeView
                root.addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                if (onContentFirstDraw != null) {
                    doOnFirstDraw(composeView, onContentFirstDraw)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach lock content", e)
                onContentFailed?.invoke()
            }
        }
        val frameCallback = Choreographer.FrameCallback { handler.post(attach) }
        pendingAttach = attach
        pendingFrameCallback = frameCallback
        Choreographer.getInstance().postFrameCallback(frameCallback)
        handler.postDelayed(attach, CONTENT_ATTACH_FALLBACK_MS)
    }

    private fun cancelPendingAttach() {
        pendingFrameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        pendingAttach?.let { handler.removeCallbacks(it) }
        pendingFrameCallback = null
        pendingAttach = null
    }

    private fun doOnFirstDraw(view: View, action: () -> Unit) {
        val listener = object : ViewTreeObserver.OnDrawListener {
            private var fired = false
            override fun onDraw() {
                if (fired) return
                fired = true
                action()
                // Listeners can't be removed from inside onDraw.
                handler.post {
                    val observer = view.viewTreeObserver
                    if (observer.isAlive) observer.removeOnDrawListener(this)
                }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
    }

    fun dismiss() {
        cancelPendingAttach()
        val root = currentRoot
        val composeView = currentComposeView
        currentRoot = null
        currentComposeView = null
        if (root != null && isOverlayAttached) {
            try {
                windowManager.removeViewImmediate(root)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay: ${e.message}")
                try {
                    windowManager.removeView(root)
                } catch (_: Exception) {
                }
            }
        }
        isOverlayAttached = false
        // The content may never have been attached (dismissed within the first frame).
        try {
            composeView?.disposeComposition()
        } catch (_: Exception) {
        }
    }

    fun isShowing(): Boolean = currentRoot != null
}
