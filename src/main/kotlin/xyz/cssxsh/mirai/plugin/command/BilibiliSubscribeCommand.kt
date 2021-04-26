package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.*
import xyz.cssxsh.bilibili.api.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.BilibiliHelperPlugin.client
import xyz.cssxsh.mirai.plugin.BilibiliHelperPlugin.logger
import xyz.cssxsh.mirai.plugin.data.BilibiliTaskData.tasks
import xyz.cssxsh.mirai.plugin.data.*
import kotlin.coroutines.CoroutineContext

object BilibiliSubscribeCommand : CompositeCommand(
    owner = BilibiliHelperPlugin,
    "bili-subscribe", "B订阅",
    description = "B站订阅指令"
), CoroutineScope {

    @ExperimentalCommandDescriptors
    @ConsoleExperimentalApi
    override val prefixOptional: Boolean = true

    override val coroutineContext: CoroutineContext = CoroutineName("Bilibili-Listener")

    private val taskJobs = mutableMapOf<Long, Job>()

    private val liveState = mutableMapOf<Long, Boolean>()

    private fun taskContactInfos(uid: Long) = tasks[uid]?.contacts.orEmpty()

    internal fun start(): Unit = synchronized(taskJobs) {
        tasks.forEach { (uid, _) ->
            taskJobs[uid] = addListener(uid)
        }
    }

    internal fun stop(): Unit = synchronized(taskJobs) {
        taskJobs.forEach { (_, job) ->
            job.cancel()
        }
        taskJobs.clear()
    }

    private suspend fun sendMessageToTaskContacts(
        uid: Long,
        block: suspend MessageChainBuilder.(contact: Contact) -> Unit
    ) = taskContactInfos(uid).forEach { info ->
        runCatching {
            info.getContact().let { contact ->
                contact.sendMessage(buildMessageChain {
                    block(contact)
                })
            }
        }.onFailure {
            logger.warning({ "对[${info}]构建消息失败" }, it)
        }
    }

    private suspend fun sendVideoMessage(uid: Long) = runCatching {
        client.searchVideo(uid).list.videoList.apply {
            logger.verbose { "(${uid})共加载${size}条视频" }
        }.sortedBy { it.created }.onEach { video ->
            if (video.created > tasks.getValue(uid).videoLast) {
                logger.verbose { "(${uid})当前处理视频[${video.bvid}]" }
                sendMessageToTaskContacts(uid = uid) { contact ->
                    appendLine("标题: ${video.title}")
                    appendLine("作者: ${video.author}")
                    appendLine("时间: ${video.time}")
                    appendLine("时长: ${video.length}")
                    appendLine("链接: ${video.url}")

                    runCatching {
                        add(video.getCover().uploadAsImage(contact))
                    }.onFailure {
                        logger.warning({ "获取[${video.bvid}]封面封面失败" }, it)
                        appendLine("获取[${video.bvid}]封面封面失败")
                    }
                }
            }
        }.maxByOrNull { it.created }?.let { video ->
            logger.info {
                "(${uid})[${video.author}]最新视频为(${video.bvid})[${video.title}]<${video.time}>"
            }
            tasks.compute(uid) { _, info ->
                info?.copy(videoLast = video.created)
            }
        }
    }.onFailure { logger.warning({ "($uid)获取视频失败" }, it) }.isSuccess

    private suspend fun sendLiveMessage(uid: Long) = runCatching {
        client.getUserInfo(uid).let { user ->
            logger.verbose { "(${uid})[${user.name}][${user.liveRoom.title}]最新开播状态为${user.liveRoom.liveStatus}" }
            liveState.put(uid, user.liveRoom.liveStatus).let { old ->
                if (old != true && user.liveRoom.liveStatus) {
                    sendMessageToTaskContacts(uid = uid) { contact ->
                        appendLine("主播: ${user.name}")
                        appendLine("标题: ${user.liveRoom.title}")
                        appendLine("人气: ${user.liveRoom.online}")
                        appendLine("链接: ${user.liveRoom.url}")

                        runCatching {
                            add(user.liveRoom.getCover().uploadAsImage(contact))
                        }.onFailure {
                            logger.warning({ "获取[${user.liveRoom.roomId}]直播间封面封面失败" }, it)
                            appendLine("获取[${user.liveRoom.roomId}]直播间封面失败")
                        }
                    }
                }
            }
        }
    }.onFailure { logger.warning({ "($uid)获取直播失败" }, it) }.isSuccess

    private suspend fun sendDynamicMessage(uid: Long) = runCatching {
        client.getSpaceHistory(uid).cards.apply {
            logger.verbose { "(${uid})共加载${size}条动态" }
        }.sortedBy { it.describe.timestamp }.onEach { dynamic ->
            if (dynamic.isText() && dynamic.describe.timestamp > tasks.getValue(uid).dynamicLast) {
                logger.verbose { "(${uid})当前处理动态[${dynamic.describe.dynamicId}]" }
                sendMessageToTaskContacts(uid = uid) { contact ->
                    appendLine("@${dynamic.username} 有新动态")
                    appendLine("时间: ${dynamic.datetime}")
                    appendLine("链接: ${dynamic.url}")

                    runCatching {
                        add(dynamic.getScreenShot(refresh = false).uploadAsImage(contact))
                    }.onFailure {
                        logger.warning({ "获取动态[${dynamic.describe.dynamicId}]快照失败" }, it)
                        add(dynamic.content)
                    }

                    dynamic.getImageFiles().forEachIndexed { index, result ->
                        runCatching {
                            add(result.getOrThrow().uploadAsImage(contact))
                        }.onFailure {
                            logger.warning({ "获取动态[${dynamic.describe.dynamicId}]图片[${index}]失败" }, it)
                            appendLine("获取动态[${dynamic.describe.dynamicId}]图片[${index}]失败")
                        }
                    }
                }
            }
        }.maxByOrNull { it.describe.timestamp }?.let { dynamic ->
            logger.info {
                "(${uid})[${dynamic.username}]最新动态时间为<${dynamic.datetime}>"
            }
            tasks.compute(uid) { _, info ->
                info?.copy(dynamicLast = dynamic.describe.timestamp)
            }
        }
    }.onFailure { logger.warning({ "($uid)获取动态失败" }, it) }.isSuccess

    private fun addListener(uid: Long): Job = launch {
        logger.info { "监听任务User(${tasks.getValue(uid).name}#${uid})开始" }
        delay(tasks.getValue(uid).interval.random())
        while (isActive && taskContactInfos(uid).isNotEmpty()) {
            runCatching {
                sendDynamicMessage(uid)
                sendVideoMessage(uid)
                sendLiveMessage(uid)
            }.onSuccess {
                delay(tasks.getValue(uid).interval.random().also {
                    logger.info { "(${uid}): ${tasks[uid]}监听任务完成一次, 即将进入延时delay(${it}ms)。" }
                })
            }.onFailure {
                logger.warning({ "(${uid})监听任务执行失败" }, it)
                delay(tasks.getValue(uid).interval.last)
            }
        }
    }

    private fun addUser(uid: Long, name: String, subject: Contact): Unit = synchronized(taskJobs) {
        tasks.compute(uid) { _, info ->
            (info ?: BilibiliTaskInfo(name = name)).run {
                copy(contacts = contacts + subject.toContactInfo())
            }
        }
        taskJobs.compute(uid) { _, job ->
            job?.takeIf { it.isActive } ?: addListener(uid)
        }
    }

    private fun removeUser(uid: Long, subject: Contact): Unit = synchronized(taskJobs) {
        tasks.compute(uid) { _, info ->
            info?.run {
                copy(contacts = contacts - subject.toContactInfo())
            }
        }
        taskJobs[uid]?.takeIf {
            taskContactInfos(uid).isEmpty()
        }?.cancel()
    }

    @SubCommand("add", "添加")
    suspend fun CommandSenderOnMessage<MessageEvent>.add(uid: Long) = runCatching {
        client.getUserInfo(mid = uid).apply { addUser(uid = uid, name = name, subject = fromEvent.subject) }
    }.onSuccess { info ->
        sendMessage(fromEvent.message.quote() + "对@${info.name}#${info.mid}的监听任务, 添加完成")
    }.onFailure {
        sendMessage(fromEvent.message.quote() + it.toString())
    }.isSuccess

    @SubCommand("stop", "停止")
    suspend fun CommandSenderOnMessage<MessageEvent>.stop(uid: Long) = runCatching {
        tasks[uid].apply {
            removeUser(uid, fromEvent.subject)
        }
    }.onSuccess { info ->
        sendMessage(fromEvent.message.quote() + "对@${info?.name}#${uid}的监听任务, 取消完成")
    }.onFailure {
        sendMessage(fromEvent.message.quote() + it.toString())
    }.isSuccess

    @SubCommand("list", "列表")
    suspend fun CommandSenderOnMessage<MessageEvent>.list() = runCatching {
        buildMessageChain {
            appendLine("监听状态:")
            tasks.forEach { (uid, info) ->
                if (info.contacts.isNotEmpty()) {
                    appendLine("@${info.name}#$uid -> ${taskJobs[uid]}")
                }
            }
        }
    }.onSuccess { message ->
        sendMessage(fromEvent.message.quote() + message)
    }.onFailure {
        sendMessage(fromEvent.message.quote() + it.toString())
    }.isSuccess
}