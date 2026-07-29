package com.carbon.prolocker.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView


tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun NativeAdContainer(
    preloadedAd: View,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { preloadedAd },
        modifier = modifier
    )
}

@Composable
fun NativeAdContainer(
    adManager: AdManager,
    placement: String,
    adType: NativeAdType,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val layoutRes = NativeAdLayoutResolver.getLayout(adManager, placement, adType)
    NativeAdContainerInternal(
        adManager = adManager,
        placement = placement,
        layoutRes = layoutRes,
        modifier = modifier,
        onShown = onShown,
        onError = onError
    )
}

@Composable
fun NativeAdContainer(
    adManager: AdManager,
    placement: String,
    @LayoutRes layoutRes: Int,
    modifier: Modifier = Modifier,
    onShown: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    NativeAdContainerInternal(
        adManager = adManager,
        placement = placement,
        layoutRes = layoutRes,
        modifier = modifier,
        onShown = onShown,
        onError = onError
    )
}

@Composable
private fun NativeAdContainerInternal(
    adManager: AdManager,
    placement: String,
    @LayoutRes layoutRes: Int,
    modifier: Modifier,
    onShown: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val adContext = activity ?: context
    val container = remember { FrameLayout(adContext) }
    val config by adManager.configFlow.collectAsState(initial = com.carbon.prolocker.network.model.RemoteConfigResponse.DEFAULT)
    DisposableEffect(config) {
        adManager.loadNativeAd(
            activity = adContext,
            placement = placement,
            container = container,
            layoutRes = layoutRes,
            onRendered = { renderedView ->
                onShown()
            },
            onError = { error ->
                onError(error)
            }
        )

        onDispose {
        }
    }
    AndroidView(
        factory = { container },
        modifier = modifier
    )
}
