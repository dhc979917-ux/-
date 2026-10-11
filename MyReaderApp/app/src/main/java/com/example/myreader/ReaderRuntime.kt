package com.example.myreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import com.example.myreader.ai.AiProviderFactory
import com.example.myreader.ai.ModelConfigStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ReaderState(
    val busy: Boolean = false,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val overlay: Boolean = false,
    val status: String = "准备就绪",
    val result: String = "",
    val command: String = "",
)

data class VoiceSession(val id: String, val screen: ScreenSnapshot?, val createdAt: Long)

object ReaderRuntime {
    private val mutableState = MutableStateFlow(ReaderState())
    val state = mutableState.asStateFlow()
    private lateinit var context: Context
    private lateinit var tts: TtsHelper
    lateinit var screenshots: ScreenshotHelper
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var version = 0
    var voiceSession: VoiceSession? = null
        private set
    private data class PendingClick(val label: String, val screenKey: String, val expiresAt: Long)
    private var pendingClick: PendingClick? = null

    fun initialize(source: Context) {
        if (::context.isInitialized) return
        context = source.applicationContext
        tts = TtsHelper(context)
        screenshots = ScreenshotHelper(context)
    }

    fun overlayVisible(visible: Boolean) { mutableState.value = state.value.copy(overlay = visible) }

    fun stop(announce: Boolean = false, clearPending: Boolean = true) {
        version++
        work?.cancel()
        work = null
        tts.停止()
        if (clearPending) pendingClick = null
        mutableState.value = state.value.copy(busy = false, speaking = false, listening = false, status = "已停止")
        if (announce) say("已停止")
    }

    fun say(message: String) {
        mutableState.value = state.value.copy(status = message, speaking = true)
        tts.朗读(message) { mutableState.value = state.value.copy(speaking = false) }
    }

    fun replay() {
        stop()
        val text = state.value.result
        if (text.isBlank()) say("还没有可以重播的讲解") else {
            mutableState.value = state.value.copy(status = "正在朗读", speaking = true)
            tts.朗读(text) { mutableState.value = state.value.copy(speaking = false, status = "朗读结束") }
        }
    }

    fun beginVoice(source: Context) {
        initialize(source)
        if (voiceSession != null) return
        stop(clearPending = false)
        val session = VoiceSession(UUID.randomUUID().toString(), ReaderService.instance?.screen?.snapshot(), SystemClock.elapsedRealtime())
        voiceSession = session
        voiceState(false, "准备语音输入")
        try {
            source.startActivity(Intent(source, VoiceCommandActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("session", session.id))
        } catch (error: RuntimeException) {
            cancelVoice(session.id)
            say("语音窗口未能打开，请返回首页重试")
        }
    }

    fun voiceState(listening: Boolean, message: String) {
        mutableState.value = state.value.copy(listening = listening, status = message)
    }

    fun cancelVoice(id: String) {
        if (voiceSession?.id == id) {
            voiceSession = null
            mutableState.value = state.value.copy(listening = false, status = "语音已关闭")
        }
    }

    fun submitVoice(id: String, text: String) {
        val session = voiceSession?.takeIf { it.id == id } ?: return
        voiceSession = null
        stop(clearPending = false)
        mutableState.value = state.value.copy(command = text, status = "正在处理口令")
        val currentVersion = version
        work = scope.launch {
          try {
            // 等语音窗口退出后再校验目标，避免对语音窗口或后来切换的页面执行指令。
            delay(350)
            val command = VoiceCommands.parse(text)
            if (command == VoiceCommand.Stop) { stop(announce = true); return@launch }
            if (command == VoiceCommand.Help) { say(VoiceCommands.HELP); return@launch }
            if (command == VoiceCommand.Replay) { replay(); return@launch }
            if (command is VoiceCommand.Rate) {
                val rate = tts.调整语速(command.delta)
                say("语速已调整为${"%.2f".format(rate)}倍，重播时使用新语速")
                return@launch
            }
            if (command == VoiceCommand.Unknown) {
                pendingClick = null
                say("没有识别到可执行的口令。请一次说一个指令，或说语音帮助")
                return@launch
            }
            val service = ReaderService.instance
            if (service == null) { say("请先开启万物皆可读的无障碍服务"); return@launch }
            val original = session.screen
            if (original == null || SystemClock.elapsedRealtime() - session.createdAt > 120_000) {
                say("原页面已失效，请在目标页面重新发出口令")
                return@launch
            }
            fun matches(current: ScreenSnapshot?): Boolean = current?.key == original.key ||
                (original.packageName == context.packageName && current?.packageName == context.packageName &&
                    current?.key?.substringBeforeLast(':') == original.key.substringBeforeLast(':'))
            var screen = service.screen.snapshot()
            repeat(6) {
                if (!matches(screen)) { delay(150); screen = service.screen.snapshot() }
            }
            if (!matches(screen)) {
                pendingClick = null
                say("页面已经变化，请重新发出口令")
                return@launch
            }
            if (command != VoiceCommand.Confirm) pendingClick = null
            when (command) {
                is VoiceCommand.Read -> {
                    // 当前命令协程转交给朗读任务，保留取消与状态的一致性。
                    work = null
                    read(command, original)
                }
                is VoiceCommand.Navigate -> say(service.screen.navigate(command.direction))
                is VoiceCommand.OpenApp -> say(service.screen.openApp(command.label))
                is VoiceCommand.Click -> {
                    val error = service.screen.click(command.label, execute = false)
                    if (error.isNotEmpty()) say(error) else {
                        pendingClick = PendingClick(command.label, original.key, SystemClock.elapsedRealtime() + 30_000)
                        say("准备点击“${command.label}”。请说确认执行，或说取消")
                    }
                }
                VoiceCommand.Confirm -> {
                    val pending = pendingClick
                    pendingClick = null
                    if (pending == null || pending.expiresAt < SystemClock.elapsedRealtime()) say("没有待确认的操作，或确认已超时")
                    else if (pending.screenKey != original.key) say("页面已变化，已取消点击")
                    else say(service.screen.click(pending.label, execute = true))
                }
                else -> Unit
            }
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (error: Exception) { pendingClick = null; say("未能完成口令，请在目标页面重试") }
        }.also { job ->
            job.invokeOnCompletion { if (version == currentVersion && work === job) work = null }
        }
    }

    fun read(command: VoiceCommand.Read = VoiceCommand.Read("讲解当前页面，突出核心内容与重要信息"), supplied: ScreenSnapshot? = null) {
        if (state.value.busy) return
        val config = ModelConfigStore.读取(context)
        if (config.模型名.isBlank() || config.baseUrl.isBlank() || (config.提供商 != "ollama" && config.apiKey.isBlank())) {
            say("请先在首页配置 AI 模型")
            openHome()
            return
        }
        if (!context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("screen_upload_consent", false)) {
            say("请先在首页确认屏幕内容发送授权")
            openHome(requestConsent = true)
            return
        }
        val service = ReaderService.instance
        if (service == null) { say("请先开启无障碍服务"); return }
        val snapshot = supplied ?: service.screen.snapshot()
        if (snapshot == null) { say("暂时无法读取当前页面"); return }
        stop()
        val requestVersion = version
        val screenText = snapshot.textFor(command.region)
        mutableState.value = state.value.copy(busy = true, status = "正在理解屏幕")
        service.feedback()
        work = scope.launch {
            try {
                val image = if (config.支持图片 && screenshots.是否已授权() && command.region != ReadRegion.FOCUS) {
                    withContext(Dispatchers.IO) {
                        screenshots.截图()?.let { full ->
                            val selected = when (command.region) {
                                ReadRegion.TOP -> Bitmap.createBitmap(full, 0, 0, full.width, full.height / 2)
                                ReadRegion.BOTTOM -> Bitmap.createBitmap(full, 0, full.height / 2, full.width, full.height - full.height / 2)
                                else -> full
                            }
                            try { screenshots.转Base64(selected) } finally {
                                if (selected !== full) selected.recycle()
                                full.recycle()
                            }
                        }
                    }
                } else null
                ensureActive()
                if (service.screen.snapshot()?.key != snapshot.key && snapshot.packageName != context.packageName) error("页面已变化，请重新朗读")
                check(screenText.isNotBlank() || image != null) {
                    if (command.region == ReadRegion.FOCUS) "没有找到当前焦点，请先用读屏选择目标或指定上半部分、下半部分"
                    else "指定区域没有可读取的内容"
                }
                val result = AiProviderFactory.创建(config).理解屏幕(screenText, image, command.instruction)
                ensureActive()
                check(result.isNotBlank()) { "模型没有返回讲解，请重试" }
                mutableState.value = state.value.copy(busy = false, result = result, status = "正在朗读", speaking = true)
                tts.朗读(result) { mutableState.value = state.value.copy(speaking = false, status = "讲解已返回") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = state.value.copy(busy = false)
                say(error.message ?: "本次朗读失败，请重试")
            } finally {
                if (requestVersion == version) mutableState.value = state.value.copy(busy = false)
            }
        }
    }

    private fun openHome(requestConsent: Boolean = false) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra("request_screen_consent", requestConsent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    fun serviceDisconnected() {
        if (!::context.isInitialized) return
        stop()
        voiceSession = null
        screenshots.释放()
        mutableState.value = state.value.copy(overlay = false, status = "无障碍服务已关闭")
    }
}
