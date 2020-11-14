package xyz.cssxsh.mirai.plugin

import com.google.auto.service.AutoService
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import xyz.cssxsh.mirai.plugin.command.BiliBiliSubscribeCommand
import xyz.cssxsh.mirai.plugin.data.*
import net.mamoe.mirai.utils.minutesToMillis
import org.openqa.selenium.chrome.ChromeDriverService
import xyz.cssxsh.bilibili.BilibiliClient
import xyz.cssxsh.mirai.plugin.data.BilibiliChromeDriverConfig.driverPath
import java.io.File

@AutoService(JvmPlugin::class)
object BilibiliHelperPlugin : KotlinPlugin(
    JvmPluginDescription("xyz.cssxsh.mirai.plugin.bilibili-helper", "0.1.0-dev-1") {
        name("bilibili-helper")
        author("cssxsh")
    }
)  {

    internal val bilibiliClient = BilibiliClient(emptyMap())

    private var service : ChromeDriverService? = null

    private fun serviceStart(): Boolean = runCatching {
        ChromeDriverService.Builder().apply {
            usingDriverExecutable(File(driverPath))
            withVerbose(false)
            withSilent(true)
            withWhitelistedIps("")
            usingPort(9515)
        }.build().apply { start() }
    }.onFailure { logger.warning("启动${driverPath}失败", it) }.isSuccess

    private fun serviceStop() { service?.stop() }

    @ConsoleExperimentalApi
    override val autoSaveIntervalMillis: LongRange
        get() = 3.minutesToMillis..10.minutesToMillis

    @ConsoleExperimentalApi
    override fun onEnable() {
        BilibiliTaskData.reload()
        BilibiliChromeDriverConfig.reload()
        BiliBiliCommand.register()
        BiliBiliCommand.onInit()
        service = driverPath.takeIf { it.isNotBlank() }?.runCatching {
            ChromeDriverService.Builder().apply {
                usingDriverExecutable(File(driverPath))
                withVerbose(false)
                withSilent(true)
                withWhitelistedIps("")
                usingPort(9515)
            }.build().apply { start() }
        }?.onFailure { logger.warning("启动${driverPath}失败", it) }?.getOrNull()
    }

    @ConsoleExperimentalApi
    override fun onDisable() {
        BiliBiliSubscribeCommand.unregister()
        serviceStop()
    }
}