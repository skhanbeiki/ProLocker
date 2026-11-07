package com.carbon.prolocker.ad

import com.carbon.prolocker.core.ui.centeredMaxWidth
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.carbon.prolocker.core.language.findActivity
import com.carbon.prolocker.core.ui.Adaptive

// ─── Overload: pre-loaded ad view ──────────────────────────────────────────

@Composable
fun NativeAdContainer(
    preloadedAd: View,
    modifier: Modifier = Modifier,
    transparentNativeBackground: Boolean = false
) {
    AndroidView(
        factory = {
            (preloadedAd.parent as? android.view.ViewGroup)?.removeView(preloadedAd)
            if (transparentNativeBackground) {
                makeBackgroundsTransparent(preloadedAd)
            }
            preloadedAd
        },
        modifier = Modifier.centeredMaxWidth(Adaptive.NativeAdMaxWidth).then(modifier)
    )
}

// ─── Overload: load by adType ───────────────────────────────────────────────

@Composable
fun NativeAdContainer(
    adManager: AdManager,
    placement: String,
    adType: NativeAdType,
    modifier: Modifier = Modifier,
    transparentNativeBackground: Boolean = false,
    onShown: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val layoutRes = NativeAdLayoutResolver.getLayout(adManager, placement, adType)
    NativeAdContainerInternal(
        adManager = adManager,
        placement = placement,
        layoutRes = layoutRes,
        modifier = modifier,
        transparentNativeBackground = transparentNativeBackground,
        onShown = onShown,
        onError = onError
    )
}

// ─── Overload: load by explicit layoutRes ──────────────────────────────────

@Composable
fun NativeAdContainer(
    adManager: AdManager,
    placement: String,
    @LayoutRes layoutRes: Int,
    modifier: Modifier = Modifier,
    transparentNativeBackground: Boolean = false,
    onShown: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    NativeAdContainerInternal(
        adManager = adManager,
        placement = placement,
        layoutRes = layoutRes,
        modifier = modifier,
        transparentNativeBackground = transparentNativeBackground,
        onShown = onShown,
        onError = onError
    )
}

// ─── Internal implementation ───────────────────────────────────────────────

@Composable
private fun NativeAdContainerInternal(
    adManager: AdManager,
    placement: String,
    @LayoutRes layoutRes: Int,
    modifier: Modifier,
    transparentNativeBackground: Boolean = false,
    onShown: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val adContext = activity ?: context

    val container = remember {
        FrameLayout(adContext).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (transparentNativeBackground) {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    // isAdLoaded controls visibility: container stays alpha=0 until ad renders
    var isAdLoaded by remember { mutableStateOf(false) }

    // Reload only when something that decides the ad changes (ads switched on, or a different
    // provider). Keying on the whole config reloaded every slot twice on open (default config,
    // then the stored one) and again on any unrelated preference write.
    val adSourceKey by remember(placement) {
        adManager.configFlow
            .map { "${adManager.areAdsEnabled()}|${adManager.getNativeProviderName(placement)}" }
            .distinctUntilChanged()
    }.collectAsState(
        initial = remember(placement) { "${adManager.areAdsEnabled()}|${adManager.getNativeProviderName(placement)}" }
    )

    DisposableEffect(adSourceKey) {
        // Reset visibility whenever a new load starts
        isAdLoaded = false

        adManager.loadNativeAd(
            activity = adContext,
            placement = placement,
            container = container,
            layoutRes = layoutRes,
            onRendered = {
                if (transparentNativeBackground) {
                    makeBackgroundsTransparent(container)
                }
                isAdLoaded = true
                onShown()
            },
            onError = { error ->
                isAdLoaded = false
                onError(error)
            }
        )

        onDispose {
            container.removeAllViews()
        }
    }

    // Container is invisible (alpha=0) while loading, visible (alpha=1) once loaded.
    // alpha(0f) still participates in layout so the space is reserved — no layout jump.
    AndroidView(
        factory = { container },
        // Ad layouts stretch to their container, so on tablets they get a capped, centered width.
        modifier = Modifier.centeredMaxWidth(Adaptive.NativeAdMaxWidth)
            .then(if (isAdLoaded) modifier else modifier.alpha(0f))
    )
}

private fun makeBackgroundsTransparent(view: View) {
    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            if (child is android.view.ViewGroup) {
                makeBackgroundsTransparent(child)
            }
        }
    }
}
