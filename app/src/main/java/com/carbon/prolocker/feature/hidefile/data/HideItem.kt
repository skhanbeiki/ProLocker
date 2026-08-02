package com.carbon.prolocker.feature.hidefile.data

import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ByteArray", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encodeToString(value, Base64.NO_WRAP))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return try {
            Base64.decode(decoder.decodeString(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}

@Serializable
data class HideItem(
    val id: Long = 0,
    @SerialName("name") val name: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("type") val type: String = "file",
    @SerialName("date") val date: String = "",
    @SerialName("size") val size: String = "",
    @SerialName("imagePath") val imagePath: String = "",
    @Serializable(with = Base64ByteArraySerializer::class)
    @SerialName("image")
    val image: ByteArray? = null,
    @SerialName("ads") val ads: Boolean = false
) {
    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
        const val TYPE_FILE = "file"
    }
}
