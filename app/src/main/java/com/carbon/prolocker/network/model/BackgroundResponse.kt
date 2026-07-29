package com.carbon.prolocker.network.model

import kotlinx.serialization.Serializable

@Serializable
data class BackgroundResponse(
    val next: String? = null,
    val previous: String? = null,
    val results: List<BackgroundItem> = emptyList()
)
