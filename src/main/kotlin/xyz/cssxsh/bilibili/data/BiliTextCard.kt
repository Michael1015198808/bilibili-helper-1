package xyz.cssxsh.bilibili.data


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BiliTextCard(
    @SerialName("item")
    val item: Item,
    @SerialName("user")
    val user: User,
    @SerialName("activity_infos")
    val activityInfos: JsonElement? = null,
    @SerialName("extension")
    val extension: JsonElement? = null
) {

    companion object {
        const val TYPE = 4
    }

    @Serializable
    data class Item(
        @SerialName("at_uids")
        val atUids: List<Long> = emptyList(),
        @SerialName("content")
        val content: String,
        @SerialName("ctrl")
        val ctrl: String = "",
        @SerialName("orig_dy_id")
        val origDyId: Long,
        @SerialName("pre_dy_id")
        val preDyId: Long,
        @SerialName("reply")
        val reply: Int,
        @SerialName("rp_id")
        val rpId: Long,
        @SerialName("timestamp")
        val timestamp: Long,
        @SerialName("uid")
        val uid: Long
    )

    @Serializable
    data class User(
        @SerialName("face")
        val face: String,
        @SerialName("uid")
        val uid: Int,
        @SerialName("uname")
        val uname: String
    )
}