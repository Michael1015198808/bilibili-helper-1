package xyz.cssxsh.mirai.bilibili.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.bilibili.*
import xyz.cssxsh.mirai.bilibili.data.*
import java.time.*

object BiliSeasonCommand : CompositeCommand(
    owner = BiliHelperPlugin,
    "bili-season", "B剧集", "B番剧",
    description = "B站剧集指令"
), BiliHelperCommand, BiliTasker by BiliSeasonWaiter {

    @SubCommand("add", "添加")
    suspend fun CommandSender.add(sid: Long, contact: Contact = subject()) = sendMessage(
        message = task(sid, contact)
    )

    @SubCommand("stop", "停止")
    suspend fun CommandSender.stop(sid: Long, contact: Contact = subject()) = sendMessage(
        message = remove(sid, contact)
    )

    @SubCommand("list", "列表")
    suspend fun CommandSender.detail(contact: Contact = subject()) = sendMessage(
        message = list(contact)
    )

    @SubCommand("sleep", "睡眠")
    suspend fun CommandSender.sleep(target: PermitteeId, start: LocalTime, end: LocalTime) {
        try {
            target as AbstractPermitteeId
        } catch (cause: Throwable) {
            sendMessage("出现错误, ${cause.message}")
            return
        }
        val interval = BiliInterval(start, end)
        if (interval.isEmpty()) {
            BiliTaskerConfig.seasonSleep.remove(target)
            sendMessage("睡眠时间取消成功")
        } else {
            BiliTaskerConfig.seasonSleep[target] = BiliInterval(start, end)
            sendMessage("睡眠时间添加成功")
        }
    }

    @SubCommand("at", "艾特")
    suspend fun CommandSender.at(target: PermitteeId, start: LocalTime, end: LocalTime) {
        try {
            target as AbstractPermitteeId
        } catch (cause: Throwable) {
            sendMessage("出现错误, ${cause.message}")
            return
        }
        val interval = BiliInterval(start, end)
        if (interval.isEmpty()) {
            BiliTaskerConfig.seasonAt.remove(target)
            sendMessage("艾特时间取消成功")
        } else {
            BiliTaskerConfig.seasonAt[target] = BiliInterval(start, end)
            sendMessage("艾特时间添加成功")
        }
    }
}