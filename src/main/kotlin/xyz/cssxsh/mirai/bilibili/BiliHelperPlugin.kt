package xyz.cssxsh.mirai.bilibili

import kotlinx.coroutines.*
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.*
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.console.plugin.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.event.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.bilibili.api.*
import xyz.cssxsh.mirai.bilibili.data.*

object BiliHelperPlugin : KotlinPlugin(
    JvmPluginDescription(id = "xyz.cssxsh.mirai.plugin.bilibili-helper", version = "1.6.7") {
        name("bilibili-helper")
        author("cssxsh")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-selenium-plugin", ">= 2.1.0", true)
    }
) {

    override fun PluginComponentStorage.onLoad() {
        // run after auto login
        runAfterStartup {
            if (BiliHelperSettings.refresh) BiliTasker.refresh()
            for (task in BiliTasker) task.start()
            BiliCleaner.start()

            BiliTemplate.selenium() && SetupSelenium
        }
    }

    @Suppress("INVISIBLE_MEMBER")
    private inline fun <reified T : Any> services(): Lazy<List<T>> = lazy {
        with(net.mamoe.mirai.console.internal.util.PluginServiceHelper) {
            jvmPluginClasspath.pluginClassLoader
                .findServices<T>()
                .loadAllServices()
        }
    }

    private val commands: List<Command> by services()
    private val data: List<PluginData> by services()
    private val config: List<PluginConfig> by services()
    private val listeners: List<ListenerHost> by services()

    override fun onEnable() {
        // XXX: mirai console version check
        check(SemVersion.parseRangeRequirement(">= 2.12.0-RC").test(MiraiConsole.version)) {
            "$name $version 需要 Mirai-Console 版本 >= 2.12.0，目前版本是 ${MiraiConsole.version}"
        }

        for (config in config) config.reload()
        for (data in data) data.reload()
        BiliTemplate.reload(configFolder.resolve("Template"))

        System.setProperty(BiliTemplate.DATE_TIME_PATTERN, BiliTaskerConfig.pattern)
        System.setProperty(EXCEPTION_JSON_CACHE, dataFolder.absolutePath)

        client.load()

        for (command in commands) command.register()

        logger.info { "如果要B站动态的截图内容，请修改 DynamicInfo.template, 添加 #screenshot" }
        logger.info { "如果要B站专栏的截图内容，请修改 Article.template, 添加 #screenshot" }

        for (listener in listeners) (listener as SimpleListenerHost).registerTo(globalEventChannel())

        launch {
            loadCookie()
            loadEmoteData()
        }
    }

    override fun onDisable() {
        for (command in commands) command.unregister()

        for (listener in listeners) (listener as SimpleListenerHost).cancel()

        for (task in BiliTasker) task.stop()
        BiliCleaner.stop()
        client.save()
    }
}