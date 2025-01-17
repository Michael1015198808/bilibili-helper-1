package xyz.cssxsh.bilibili.data

import kotlinx.serialization.*
import xyz.cssxsh.bilibili.*
import java.time.*

internal inline fun <reified T : Entry> DynamicCard.decode(): T {
    if (decode == null) {
        decode = when (val entry = BiliClient.Json.decodeFromString<T>(card)) {
            is DynamicReply -> entry.copy(detail = detail.origin ?: entry.describe(), display = display, emoji = display?.emoji)
            is DynamicText -> entry.copy(emoji = display?.emoji)
            is DynamicPicture -> entry.copy(emoji = display?.emoji)
            is DynamicSketch -> entry.copy(emoji = display?.emoji)
            is DynamicVideo -> entry.copy(id = detail.bvid ?: "av${entry.aid}")
            else -> entry
        }
    }
    return decode as T
}

sealed interface DynamicCard : Entry, WithDateTime {
    val card: String
    val detail: DynamicDescribe
    val display: DynamicDisplay?
    val profile: UserProfile

    override val datetime: OffsetDateTime

    var decode: Entry?

    fun images(): List<String> = when (detail.type) {
        DynamicType.PICTURE -> decode<DynamicPicture>().detail.pictures.map { it.source }
        else -> emptyList()
    }

    fun username(): String = when (detail.type) {
        DynamicType.EPISODE, DynamicType.BANGUMI, DynamicType.TV -> decode<DynamicEpisode>().season.title
        else -> profile.user.uname
    }

    fun uid() = when (detail.type) {
        DynamicType.EPISODE, DynamicType.BANGUMI, DynamicType.TV -> decode<DynamicEpisode>().season.seasonId
        else -> profile.user.uid
    }
}

sealed interface DynamicEmojiContent : Entry {
    val emoji: EmojiInfo?
    val content: String
}

@Serializable
data class BiliDynamicList(
    @SerialName("cards")
    val dynamics: List<DynamicInfo> = emptyList(),
    @SerialName("has_more")
    @Serializable(NumberToBooleanSerializer::class)
    val more: Boolean,
    @SerialName("next_offset")
    val next: Long
)

@Serializable
data class BiliDynamicInfo(
    @SerialName("card")
    val dynamic: DynamicInfo,
)

object DynamicType {
    const val NONE = 0
    const val REPLY = 1
    const val PICTURE = 2
    const val TEXT = 4
    const val VIDEO = 8
    const val ARTICLE = 64
    const val MUSIC = 256
    const val EPISODE = 512
    const val DELETE = 1024
    const val SKETCH = 2048
    const val BANGUMI = 4101
    const val TV = 4099
    const val LIVE = 4200
    const val LIVE_END = 4308
}

@Serializable
data class DynamicDescribe(
    @SerialName("bvid")
    val bvid: String? = null,
    @SerialName("comment")
    val comment: Int = 0,
    @SerialName("dynamic_id")
    val id: Long = 0,
    @SerialName("is_liked")
    @Serializable(NumberToBooleanSerializer::class)
    val isLiked: Boolean = false,
    @SerialName("like")
    val like: Long = 0,
    @SerialName("origin")
    val origin: DynamicDescribe? = null,
    @SerialName("orig_dy_id")
    val originDynamicId: Long? = null,
    @SerialName("orig_type")
    val originType: Int? = null,
    @SerialName("previous")
    val previous: DynamicDescribe? = null,
    @SerialName("pre_dy_id")
    val previousDynamicId: Long? = null,
    @SerialName("repost")
    val repost: Long = 0,
    @SerialName("timestamp")
    val timestamp: Long = 0,
    @SerialName("type")
    val type: Int = 0,
    @SerialName("uid")
    val uid: Long = 0,
    @SerialName("user_profile")
    val profile: UserProfile = UserProfile(),
    @SerialName("view")
    val view: Long = 0
) {
    companion object {
        val Empty = DynamicDescribe()
    }
}

@Serializable
data class EmojiInfo(
    @SerialName("emoji_details")
    val details: List<EmojiDetail> = emptyList()
)

@Serializable
data class CardInfo(
    @SerialName("add_on_card_show_type")
    val type: Int,
    @SerialName("vote_card")
    val vote: String = ""
)

@Serializable
data class DynamicDisplay(
    @SerialName("emoji_info")
    val emoji: EmojiInfo = EmojiInfo(),
    @SerialName("origin")
    val origin: DynamicDisplay? = null,
    // TODO: add_on_card_info
    @SerialName("add_on_card_info")
    val infos: List<CardInfo> = emptyList()
)

@Serializable
data class DynamicInfo(
    @SerialName("card")
    override val card: String,
    @SerialName("desc")
    override val detail: DynamicDescribe,
    @SerialName("display")
    override val display: DynamicDisplay? = null
) : DynamicCard, Entry {
    val link get() = "https://t.bilibili.com/${detail.id}"
    val h5 get() = "https://t.bilibili.com/h5/dynamic/detail/${detail.id}"

    @Transient
    override var decode: Entry? = null
    override val profile: UserProfile get() = detail.profile
    override val datetime: OffsetDateTime get() = timestamp(detail.timestamp)
}

@Serializable
data class DynamicArticle(
    @SerialName("act_id")
    val actId: Int,
    @SerialName("apply_time")
    val apply: String,
    @SerialName("author")
    val author: ArticleAuthor,
    @SerialName("banner_url")
    val banner: String,
    @SerialName("categories")
    val categories: List<ArticleCategory>? = null,
    @SerialName("category")
    val category: ArticleCategory,
    @SerialName("check_time")
    val check: String,
    @SerialName("cover_avid")
    val avid: Long = 0,
    @SerialName("ctime")
    val created: Long,
    @SerialName("id")
    override val id: Long,
    @SerialName("image_urls")
    override val images: List<String>,
    @SerialName("is_like")
    val isLike: Boolean,
    @SerialName("list")
    val list: ArticleList?,
    @SerialName("media")
    val media: ArticleMedia,
    @SerialName("origin_image_urls")
    val originImageUrls: List<String>,
    @SerialName("original")
    val original: Int,
    @SerialName("publish_time")
    override val published: Long,
    @SerialName("reprint")
    val reprint: Int,
    @SerialName("state")
    val state: Int,
    @SerialName("stats")
    override val status: ArticleStatus,
    @SerialName("summary")
    override val summary: String,
    @SerialName("template_id")
    val templateId: Int,
    @SerialName("title")
    override val title: String,
    @SerialName("words")
    val words: Int
) : Article

@Serializable
data class DynamicEpisode(
    @SerialName("apiSeasonInfo")
    val season: SeasonInfo,
    @SerialName("bullet_count")
    val bullet: Long,
    @SerialName("cover")
    override val cover: String,
    @SerialName("episode_id")
    override val episodeId: Long,
    @SerialName("index")
    override val index: String,
    @SerialName("index_title")
    override val title: String,
    @SerialName("new_desc")
    val description: String,
    @SerialName("online_finish")
    val onlineFinish: Int,
    @SerialName("play_count")
    val play: Long,
    @SerialName("reply_count")
    val reply: Long,
    @SerialName("url")
    override val share: String
) : Episode

@Serializable
data class SeasonInfo(
    @SerialName("cover")
    override val cover: String,
    @SerialName("is_finish")
    @Serializable(NumberToBooleanSerializer::class)
    val isFinish: Boolean,
    @SerialName("season_id")
    override val seasonId: Long,
    @SerialName("title")
    override val title: String,
    @SerialName("total_count")
    val total: Long,
    @SerialName("ts")
    val timestamp: Long,
    @SerialName("type_name")
    override val type: String
) : Season

@Serializable
data class DynamicLive(
    @SerialName("background")
    val background: String,
    @SerialName("cover")
    override val cover: String,
    @SerialName("face")
    override val face: String,
    @SerialName("link")
    override val link: String,
    @SerialName("live_status")
    @Serializable(NumberToBooleanSerializer::class)
    override val liveStatus: Boolean,
    @SerialName("lock_status")
    val lockStatus: String,
    @SerialName("on_flag")
    val onFlag: Int,
    @SerialName("online")
    override val online: Long = 0,
    @SerialName("roomid")
    override val roomId: Long,
    @SerialName("round_status")
    @Serializable(NumberToBooleanSerializer::class)
    val roundStatus: Boolean,
    @SerialName("short_id")
    val shortId: Int,
    @SerialName("tags")
    val tags: String,
    @SerialName("title")
    override val title: String,
    @SerialName("uid")
    override val uid: Long,
    @SerialName("uname")
    override val uname: String,
    @SerialName("user_cover")
    val avatar: String,
    @SerialName("verify")
    val verify: String,
) : Live {
    override val start: OffsetDateTime? get() = null
}

@Serializable
data class DynamicMusic(
    @SerialName("author")
    val author: String,
    @SerialName("cover")
    val cover: String,
    @SerialName("ctime")
    val created: Long,
    @SerialName("id")
    val id: Long,
    @SerialName("intro")
    val intro: String,
    @SerialName("playCnt")
    val play: Long,
    @SerialName("replyCnt")
    val reply: Long,
    @SerialName("schema")
    val schema: String,
    @SerialName("title")
    val title: String,
    @SerialName("typeInfo")
    val type: String,
    @SerialName("upId")
    val upId: Long,
    @SerialName("upper")
    val upper: String,
    @SerialName("upperAvatar")
    val avatar: String
) : Entry {
    val link get() = "https://www.bilibili.com/audio/au$id"
}

@Serializable
data class DynamicPicture(
    @SerialName("item")
    val detail: DynamicPictureDetail,
    @SerialName("user")
    val user: UserSimple,
    @Transient
    override val emoji: EmojiInfo? = null
) : DynamicEmojiContent {
    override val content: String get() = detail.description
}

@Serializable
data class DynamicPictureDetail(
    @SerialName("category")
    val category: String,
    @SerialName("description")
    val description: String,
    @SerialName("id")
    val id: Long,
    @SerialName("is_fav")
    @Serializable(NumberToBooleanSerializer::class)
    val isFavourite: Boolean,
    @SerialName("pictures")
    val pictures: List<DynamicPictureInfo>,
    @SerialName("reply")
    val reply: Long,
    @SerialName("title")
    val title: String,
    @SerialName("upload_time")
    val uploaded: Long
)

@Serializable
data class DynamicPictureInfo(
    @SerialName("img_height")
    val height: Int,
    @SerialName("img_size")
    val size: Double? = null,
    @SerialName("img_src")
    val source: String,
    @SerialName("img_width")
    val width: Int
)

@Serializable
data class DynamicReply(
    @SerialName("item")
    val item: DynamicReplyDetail,
    @SerialName("origin")
    override val card: String,
    @SerialName("origin_user")
    val originUser: UserProfile,
    @SerialName("user")
    val user: UserSimple,
    @Transient
    override val emoji: EmojiInfo? = null,
    @Transient
    override val detail: DynamicDescribe = DynamicDescribe.Empty,
    @Transient
    override val display: DynamicDisplay? = null
) : DynamicCard, DynamicEmojiContent {
    @Transient
    override var decode: Entry? = null

    override val content get() = item.content
    override val profile: UserProfile get() = originUser
    override val datetime: OffsetDateTime get() = timestamp(detail.timestamp)

    internal fun describe() = DynamicDescribe.Empty.copy(
        id = item.id,
        type = item.type,
        uid = item.uid,
        timestamp = if (item.timestamp != 0L) item.timestamp else dynamictime(id = item.id),
        profile = originUser
    )
}

@Serializable
data class DynamicReplyDetail(
    @SerialName("at_uids")
    val atUsers: List<Long> = emptyList(),
    @SerialName("content")
    val content: String,
    @SerialName("orig_dy_id")
    val id: Long,
    @SerialName("orig_type")
    val type: Int,
    @SerialName("reply")
    val reply: Long,
    @SerialName("timestamp")
    val timestamp: Long = 0,
    @SerialName("uid")
    val uid: Long
)

@Serializable
data class DynamicSketch(
    @SerialName("rid")
    val rid: Long,
    @SerialName("sketch")
    val detail: DynamicSketchDetail,
    @SerialName("user")
    val user: UserSimple,
    @SerialName("vest")
    val vest: DynamicSketchVest,
    @Transient
    override val emoji: EmojiInfo? = null
) : Entry, DynamicEmojiContent {
    val title get() = detail.title
    val link get() = detail.target
    val cover get() = detail.cover
    override val content get() = vest.content
}

@Serializable
data class DynamicSketchDetail(
    @SerialName("cover_url")
    val cover: String,
    @SerialName("desc_text")
    val description: String,
    @SerialName("sketch_id")
    val sketchId: Long,
    @SerialName("target_url")
    val target: String,
    @SerialName("title")
    val title: String
)

@Serializable
data class DynamicSketchVest(
    @SerialName("content")
    val content: String,
    @SerialName("uid")
    val uid: Long
)

@Serializable
data class DynamicText(
    @SerialName("item")
    val detail: DynamicTextDetail,
    @SerialName("user")
    val user: UserSimple,
    @Transient
    override val emoji: EmojiInfo? = null
) : DynamicEmojiContent {
    override val content: String get() = detail.content
}

@Serializable
data class DynamicTextDetail(
    @SerialName("at_uids")
    val atUsers: List<Long> = emptyList(),
    @SerialName("content")
    val content: String,
    @SerialName("reply")
    val reply: Long,
    @SerialName("uid")
    val uid: Long
)

@Serializable
data class DynamicVideo(
    @SerialName("aid")
    val aid: Long,
    @SerialName("bvid")
    override val id: String = "",
    @SerialName("cid")
    val cid: Int,
    @SerialName("copyright")
    val copyright: Int,
    @SerialName("ctime")
    override val created: Long,
    @SerialName("desc")
    override val description: String,
    @SerialName("dimension")
    val dimension: VideoDimension,
    @SerialName("duration")
    val duration: Long,
    @SerialName("dynamic")
    val dynamic: String = "",
    @SerialName("jump_url")
    val jumpUrl: String,
    @SerialName("owner")
    val owner: VideoOwner,
    @SerialName("pic")
    override val cover: String,
    @SerialName("pubdate")
    val pubdate: Long,
    @SerialName("stat")
    override val status: VideoStatus,
    @SerialName("tid")
    override val tid: Int,
    @SerialName("title")
    override val title: String,
    @SerialName("tname")
    override val type: String,
    @SerialName("videos")
    val videos: Int,
    @SerialName("season_id")
    override val seasonId: Long? = null,
    @SerialName("rights")
    val rights: VideoRights
) : Video {
    override val uid: Long get() = owner.mid
    override val uname: String get() = owner.name
    override val author: String get() = owner.name
    override val mid: Long get() = owner.mid
    override val length: String by lazy {
        with(Duration.ofSeconds(duration)) { "%02d:%02d".format(toMinutes(), toSecondsPart()) }
    }

    override val isPay: Boolean get() = rights.pay || rights.ugcPay
    override val isUnionVideo: Boolean get() = rights.isCooperation
    override val isSteinsGate: Boolean get() = rights.isSteinGate
    override val isLivePlayback: Boolean get() = false
}