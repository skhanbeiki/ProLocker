package com.carbon.prolocker.core.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CrashReportRequest(
    val uuid: String,
    val brand: String,
    val model: String,
    val market: String,
    val language: String,
    @SerialName("android_ver") val androidVer: String,
    @SerialName("app_ver_code") val appVerCode: String,
    @SerialName("app_ver_name") val appVerName: String,
    val event: String,
    val issue: String,
    val body: String
)
