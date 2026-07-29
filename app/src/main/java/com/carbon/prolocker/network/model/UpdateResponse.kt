package com.carbon.prolocker.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateResponse(
    @SerialName("display_type") val displayType: String,
    val priority: String,
    val title: String,
    val description: String,
    @SerialName("new_ver_code") val newVerCode: Int,
    @SerialName("new_ver_name") val newVerName: String
)
