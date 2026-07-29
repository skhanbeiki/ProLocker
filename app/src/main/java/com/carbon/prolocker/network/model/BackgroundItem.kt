package com.carbon.prolocker.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundItem(
    val id: Int = 0,
    val name: String = "",
    val category: String? = null,
    val color: String? = null,
    val image: BackgroundImage? = null,
    @SerialName("download_count") val downloadCount: Int = 0
) {
    val photoThumb: String
        get() = image?.file?.photoThumb ?: ""
    val photoGallery: String
        get() = image?.file?.photoGallery ?: ""
}

@Serializable
data class BackgroundImage(
    val file: BackgroundFile? = null
)

@Serializable
data class BackgroundFile(
    @SerialName("photo_thumb") val photoThumb: String? = null,
    @SerialName("photo_thumb_2x") val photoThumb2x: String? = null,
    @SerialName("photo_gallery") val photoGallery: String? = null
)
