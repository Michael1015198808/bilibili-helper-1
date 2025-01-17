package xyz.cssxsh.mirai.bilibili

import io.ktor.client.network.sockets.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.Cookie
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import net.mamoe.mirai.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.code.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import org.openqa.selenium.*
import xyz.cssxsh.bilibili.data.*
import xyz.cssxsh.bilibili.*
import xyz.cssxsh.bilibili.api.*
import xyz.cssxsh.mirai.bilibili.data.*
import xyz.cssxsh.mirai.selenium.*
import xyz.cssxsh.selenium.*
import java.io.File
import java.time.*
import java.time.format.*
import kotlin.properties.*
import kotlin.reflect.*

internal val logger by lazy {
    try {
        BiliHelperPlugin.logger
    } catch (_: UninitializedPropertyAccessException) {
        MiraiLogger.Factory.create(BiliClient::class)
    }
}

internal var cookies by object : ReadWriteProperty<Any?, List<Cookie>> {
    private val json by lazy {
        BiliHelperPlugin.dataFolder.resolve("cookies.json").apply {
            if (exists().not()) createNewFile()
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): List<Cookie> {
        return try {
            BiliClient.Json
                .decodeFromString<List<EditThisCookie>>(json.readText().ifBlank { "[]" })
                .map { it.toCookie() }
        } catch (cause: Exception) {
            logger.warning({ "cookies.json 解析错误" }, cause)
            emptyList()
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<Cookie>) {
        json.writeText(BiliClient.Json.encodeToString(value.mapIndexed { index, cookie ->
            cookie.toEditThisCookie(index)
        }))
    }
}

internal val client by lazy {
    object : BiliClient() {
        override val ignore: suspend (Throwable) -> Boolean = { cause ->
            when (cause) {
                is ConnectTimeoutException,
                is SocketTimeoutException -> {
                    logger.warning { "Ignore ${cause.message}" }
                    true
                }
                is java.net.NoRouteToHostException,
                is java.net.UnknownHostException -> false
                is java.io.IOException -> {
                    logger.warning { "Ignore $cause" }
                    true
                }
                else -> false
            }
        }

        override val mutex: BiliApiMutex = BiliApiMutex(interval = BiliHelperSettings.api * 1000)
    }
}

internal fun BiliClient.load() {
    storage.container.addAll(cookies)
}

internal fun BiliClient.save() {
    cookies = storage.container
}

internal val ImageCache by lazy { File(BiliHelperSettings.cache) }

internal val ImageLimit by lazy { BiliHelperSettings.limit }

internal val SetupSelenium: Boolean by lazy {
    try {
        logger.info { "正在初始化 mirai-selenium-plugin，请稍后" }
        MiraiSeleniumPlugin.setup()
    } catch (error: NoClassDefFoundError) {
        logger.warning { "相关类加载失败，请安装 https://github.com/cssxsh/mirai-selenium-plugin $error" }
        false
    }
}

@Serializable
enum class CacheType : Mutex by Mutex() {
    ARTICLE,
    MUSIC,
    SKETCH,
    DYNAMIC,
    VIDEO,
    LIVE,
    SEASON,
    EPISODE,
    USER,
    EMOJI;

    val directory get() = ImageCache.resolve(name)
}

private val Url.filename get() = encodedPath.substringAfterLast("/")

internal object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(OffsetDateTime::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime =
        OffsetDateTime.parse(decoder.decodeString(), formatter)

    override fun serialize(encoder: Encoder, value: OffsetDateTime) =
        encoder.encodeString(formatter.format(value))

}

internal object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(LocalTime::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }
}

/**
 * 通过正负号区分群和用户
 */
val Contact.delegate get() = if (this is Group) id * -1 else id

/**
 * 查找Contact
 */
fun findContact(delegate: Long): Contact? {
    for (bot in Bot.instances.shuffled()) {
        if (delegate < 0) {
            for (group in bot.groups) {
                if (group.id == delegate * -1) return group
            }
        } else {
            for (friend in bot.friends) {
                if (friend.id == delegate) return friend
            }
            for (stranger in bot.strangers) {
                if (stranger.id == delegate) return stranger
            }
            for (group in bot.groups) {
                for (member in group.members) {
                    if (member.id == delegate) return member
                }
            }
        }
    }
    return null
}

private suspend fun Url.cache(type: CacheType, path: String, contact: Contact) = type.withLock {
    type.directory.resolve(path).apply {
        if (exists().not()) {
            parentFile.mkdirs()
            writeBytes(client.useHttpClient { http, _ -> http.prepareGet(this@cache).body() })
        } else {
            setLastModified(System.currentTimeMillis())
        }
    }.uploadAsImage(contact)
}

private suspend fun Url.screenshot(type: CacheType, path: String, refresh: Boolean, contact: Contact) = type.withLock {
    check(SetupSelenium) { "截图模式未启用" }
    val file = type.directory.resolve(path)
    if (refresh || file.exists().not()) {
        file.parentFile.mkdirs()
        val bytes = when (type) {
            CacheType.ARTICLE -> useRemoteWebDriver(config = BiliSeleniumConfig.Agent) { driver ->
                driver.get(this@screenshot.toString())

                var wait = 10
                while (wait-- > 0) {
                    delay(1_000)
                    val elements = driver.findElements(By.cssSelector(".img-box"))
                    if (elements.isEmpty()) continue
                }

                try {
                    val more = driver.findElement(By.cssSelector(".read-more"))
                    driver.executeScript("arguments[0].click()", more)
                } catch (cause: RuntimeException) {
                    //
                }

                try {
                    driver.findElement(By.cssSelector(".cancel"))
                        .click()
                } catch (cause: RuntimeException) {
                    //
                }

                for (element in driver.findElements(By.cssSelector(".img-box"))) {
                    // driver.manage().window().position = element.location
                    driver.executeScript("window.scrollTo(${element.location.x}, ${element.location.y})")
                    var count = 3
                    while (count-- > 0) {
                        if ("loaded" in element.getAttribute("className")) break
                        delay(1_000)
                    }
                }
                for (element in driver.findElements(By.cssSelector("bili-open-app, .cover-video-box, .read-recommend-info, .read-comment-box"))) {
                    driver.executeScript("arguments[0].style = 'display:none'", element)
                }

                val article = driver.findElement(By.cssSelector(".read-app"))
                driver.manage().window().size = article.size
                driver.executeScript("window.scrollTo(${article.location.x}, ${article.location.y})")
                article.getScreenshotAs(OutputType.BYTES)
            }
            CacheType.DYNAMIC -> useRemoteWebDriver(config = BiliSeleniumConfig.Agent) { driver ->
                driver.get(this@screenshot.toString())

                var count = 3
                while (count-- > 0) {
                    delay(1_000)
                    val elements = driver.findElements(By.cssSelector(".open-app, .upper-works"))
                    if (elements.isEmpty()) continue
                }

                for (element in driver.findElements(By.cssSelector(".open-app, .upper-works"))) {
                    driver.executeScript("arguments[0].style = 'display:none'", element)
                }

                val card = driver.findElement(By.cssSelector(".card"))
                driver.manage().window().size = card.size
                driver.executeScript("window.scrollTo(${card.location.x}, ${card.location.y})")
                card.getScreenshotAs(OutputType.BYTES)
            }
            else -> useRemoteWebDriver(config = BiliSeleniumConfig.Agent) { driver ->
                driver.getScreenshot(url = this@screenshot.toString(), hide = BiliSeleniumConfig.hide)
            }
        }
        file.writeBytes(bytes)
    } else {
        file.setLastModified(System.currentTimeMillis())
    }
    file.uploadAsImage(contact)
}

private val FULLWIDTH_CHARS = mapOf(
    '\\' to '＼',
    '/' to '／',
    ':' to '：',
    '*' to '＊',
    '?' to '？',
    '"' to '＂',
    '<' to '＜',
    '>' to '＞',
    '|' to '｜'
)

internal fun String.fullwidth(): String = fold("") { acc, char -> acc + (FULLWIDTH_CHARS[char] ?: char) }

internal suspend fun loadCookie() {
    // home
    try {
        client.useHttpClient { http, _ -> http.get(INDEX_PAGE).bodyAsText() }
    } catch (_: Exception) {
        //
    }
    // space
    try {
        client.useHttpClient { http, _ -> http.get(SPACE).bodyAsText() }
    } catch (_: Exception) {
        //
    }
}

internal suspend fun loadEmoteData() {
    // dynamic
    val dynamic = try {
        client.getEmotePanel(business = EmoteBusiness.dynamic).all
    } catch (_: Exception) {
        emptyList()
    }
    for (item in dynamic) {
        BiliEmoteData.dynamic[item.text] = item
    }
    // reply
    val reply = try {
        client.getEmotePanel(business = EmoteBusiness.reply).all
    } catch (_: Exception) {
        emptyList()
    }
    for (item in reply) {
        BiliEmoteData.reply[item.text] = item
    }
}

internal suspend fun EmojiDetail.cache(contact: Contact): Image {
    return Url(url).cache(
        type = CacheType.EMOJI,
        path = "${packageId}/${text.fullwidth()}.${url.substringAfterLast('.')}",
        contact = contact
    )
}

internal suspend fun DynamicInfo.screenshot(contact: Contact, refresh: Boolean = false): CodableMessage {
    return try {
        Url(h5).screenshot(
            type = CacheType.DYNAMIC,
            path = "${datetime.toLocalDate()}/${detail.id}.png",
            refresh = refresh,
            contact = contact
        )
    } catch (cause: Exception) {
        logger.warning({ "获取动态${detail.id}快照失败" }, cause)
        "获取动态${detail.id}快照失败".toPlainText()
    }
}

internal suspend fun Article.screenshot(contact: Contact, refresh: Boolean = false): CodableMessage {
    return try {
        Url(link).screenshot(
            type = CacheType.ARTICLE,
            path = "${id}/cv${id}.png",
            refresh = refresh,
            contact = contact
        )
    } catch (cause: Exception) {
        logger.warning({ "获取专栏${id}快照失败" }, cause)
        "获取专栏${id}快照失败".toPlainText()
    }
}

internal suspend fun UserInfo.getFace(contact: Contact): CodableMessage {
    return Url(face).runCatching {
        cache(type = CacheType.USER, path = "${mid}/face-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${mid}]头像失败" }, it)
        "获取[${mid}]头像失败".toPlainText()
    }
}

internal suspend fun DynamicCard.getImages(contact: Contact) = images().mapIndexed { index, picture ->
    if (ImageLimit > 0 && index >= ImageLimit) return@mapIndexed "图片[${index + 1}]省略".toPlainText()
    Url(picture).runCatching {
        cache(
            type = CacheType.DYNAMIC,
            path = "${datetime.toLocalDate()}/${detail.id}-${index}-${filename}",
            contact = contact
        )
    }.getOrElse {
        logger.warning({ "获取动态${detail.id}图片[${index}]失败" }, it)
        "获取动态${detail.id}图片[${index}]失败".toPlainText()
    }
}

internal suspend fun Live.getCover(contact: Contact): CodableMessage {
    return Url(cover.ifBlank { face.orEmpty() }).runCatching {
        cache(type = CacheType.LIVE, path = "${roomId}/cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${roomId}]直播间封面失败" }, it)
        "获取[${roomId}]直播间封面失败".toPlainText()
    }
}

internal suspend fun Video.getCover(contact: Contact): CodableMessage {
    return Url(cover).runCatching {
        cache(type = CacheType.VIDEO, path = "${mid}/${id}-cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${title}](${id})视频封面失败" }, it)
        "获取[${title}](${id})视频封面失败".toPlainText()
    }
}

internal suspend fun Season.getCover(contact: Contact): CodableMessage {
    return Url(cover).runCatching {
        cache(type = CacheType.SEASON, path = "${seasonId}/cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${title}](${seasonId})}剧集封面失败" }, it)
        "获取[${title}](${seasonId})}剧集封面失败".toPlainText()
    }
}

internal suspend fun Episode.getCover(contact: Contact): CodableMessage {
    return Url(cover).runCatching {
        cache(type = CacheType.EPISODE, path = "${episodeId}/cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${title}](${episodeId})剧集封面失败" }, it)
        "获取[${title}](${episodeId})剧集封面失败".toPlainText()
    }
}

internal suspend fun Article.getImages(contact: Contact) = images.mapIndexed { index, picture ->
    if (ImageLimit > 0 && index >= ImageLimit) return@mapIndexed "图片[${index + 1}]省略".toPlainText()
    Url(picture).runCatching {
        cache(type = CacheType.ARTICLE, path = "${id}/${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取专栏[${title}](${id})图片失败" }, it)
        "获取专栏[${title}](${id})图片失败".toPlainText()
    }
}

internal suspend fun DynamicMusic.getCover(contact: Contact): CodableMessage {
    return Url(cover).runCatching {
        cache(type = CacheType.MUSIC, path = "${id}/cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${title}](${id})音乐封面失败" }, it)
        "获取[${title}](${id})音乐封面失败".toPlainText()
    }
}

internal suspend fun DynamicSketch.getCover(contact: Contact): CodableMessage {
    return Url(detail.cover).runCatching {
        cache(type = CacheType.SKETCH, path = "${detail.sketchId}/cover-${filename}", contact = contact)
    }.getOrElse {
        logger.warning({ "获取[${detail.title}](${detail.sketchId})简述封面失败" }, it)
        "获取[${detail.title}](${detail.sketchId})简述封面失败".toPlainText()
    }
}