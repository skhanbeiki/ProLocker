package com.carbon.prolocker.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.carbon.prolocker.feature.gallery.BackgroundGalleryScreen
import com.carbon.prolocker.feature.gallery.BackgroundPreviewScreen
import com.carbon.prolocker.feature.home.MemoryOptimizerScreen
import java.net.URLDecoder
import java.net.URLEncoder

import com.carbon.prolocker.core.language.findActivity

@Composable
fun RestrictedNavigation(
    destination: String
) {
    val navController = rememberNavController()

    BackHandler(enabled = true) {
        navController.context.findActivity()?.finishAffinity()
    }

    NavHost(
        navController = navController,
        startDestination = when (destination) {
            "memory" -> MemoryOptimizerRoute
            else -> BackgroundGalleryRoute
        }
    ) {
        composable<MemoryOptimizerRoute> {
            MemoryOptimizerScreen(
                onBack = {
                    navController.context.findActivity()?.finishAffinity()
                }
            )
        }
        composable<BackgroundGalleryRoute> {
            BackgroundGalleryScreen(
                onBack = {
                    navController.context.findActivity()?.finishAffinity()
                },
                onBackgroundClick = { url, id ->
                    val encodedUrl = URLEncoder.encode(url, "UTF-8")
                    navController.navigate(BackgroundPreviewRoute(encodedUrl, id))
                }
            )
        }
        composable<BackgroundPreviewRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BackgroundPreviewRoute>()
            val decodedUrl = URLDecoder.decode(route.url, "UTF-8")
            BackgroundPreviewScreen(
                url = decodedUrl,
                id = route.id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
