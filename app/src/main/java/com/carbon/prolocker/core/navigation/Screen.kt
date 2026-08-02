package com.carbon.prolocker.core.navigation

import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute
@Serializable data object LockTypeSelectionRoute
@Serializable data object PatternSetupRoute
@Serializable data object PinSetupRoute
@Serializable data class PermissionsRoute(val pendingPackage: String = "")
@Serializable data object SuccessRoute
@Serializable data class HomeRoute(val tab: String? = null)
@Serializable data object LockedAppsRoute
@Serializable data object SecurityAuditRoute
@Serializable data object AppSettingsRoute
@Serializable data object AboutUsRoute
@Serializable data object MemoryOptimizerRoute
@Serializable data object BackgroundGalleryRoute
@Serializable data class BackgroundPreviewRoute(val url: String, val id: Int)
@Serializable data object SecurityRoute
@Serializable data class IntruderPhotoDetailRoute(val eventId: Long)
@Serializable data object HideFilesRoute
@Serializable data class HiddenItemsRoute(val category: String)
@Serializable data class MediaPickerRoute(val category: String)
