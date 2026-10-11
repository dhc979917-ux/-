package com.example.myreader

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.example.myreader.ai.ModelConfig
import com.example.myreader.ai.ModelConfigStore
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class VoiceSmokeInstrumentation : Instrumentation() {
    private val report = StringBuilder()
    private var commandScreen: ScreenSnapshot? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun checkThat(name: String, condition: Boolean) {
        check(condition) { name }
        report.appendLine("PASS $name")
        sendStatus(0, Bundle().apply { putString("stream", "PASS $name\n") })
    }
    private fun until(timeout: Long = 7000, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!check()) {
            check(SystemClock.elapsedRealtime() < deadline) {
                "Timed out: ${ReaderRuntime.state.value.status}; original=$commandScreen; current=${snapshot()}"
            }
            Thread.sleep(100)
        }
    }
    private fun snapshot(): ScreenSnapshot? {
        var result: ScreenSnapshot? = null
        runOnMainSync { result = ReaderService.instance?.screen?.snapshot() }
        return result
    }
    private fun fixture() {
        context.startActivity(Intent().setComponent(ComponentName(context.packageName, VoiceFixtureActivity::class.java.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        until { snapshot()?.text?.contains("action-count=0") == true }
    }
    private fun command() {
        var previous: String? = null
        var stableSince = SystemClock.elapsedRealtime()
        until {
            val key = snapshot()?.key
            if (key != previous) stableSince = SystemClock.elapsedRealtime()
            val stable = key != null && SystemClock.elapsedRealtime() - stableSince >= 700
            previous = key
            stable
        }
        runOnMainSync {
            commandScreen = ReaderService.instance?.screen?.snapshot()
            ReaderRuntime.beginVoice(targetContext)
        }
        until { ReaderRuntime.voiceSession == null }
    }
    private fun spoken(text: String, expected: () -> Boolean) {
        (URL("http://127.0.0.1:11434/voice").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            outputStream.use { it.write(JSONObject().put("command", text).toString().toByteArray()) }
            inputStream.close()
            disconnect()
        }
        command()
        until { expected() }
    }
    private fun mode(value: String) {
        (URL("http://127.0.0.1:11434/control/$value").openConnection() as HttpURLConnection).apply {
            inputStream.close(); disconnect()
        }
    }

    private fun screenshot(name: String) {
        val bitmap = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()
        checkNotNull(bitmap)
        java.io.File(targetContext.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun rejectedSpeech(confidence: Double, error: Int, expected: String) {
        (URL("http://127.0.0.1:11434/voice").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(JSONObject().put("command", "回到桌面").put("confidence", confidence).put("error", error).toString().toByteArray()) }
            inputStream.close(); disconnect()
        }
        val monitor = addMonitor(VoiceCommandActivity::class.java.name, null, false)
        runOnMainSync { ReaderRuntime.beginVoice(targetContext) }
        val activity = waitForMonitorWithTimeout(monitor, 5000) ?: error("Voice activity missing")
        until { ReaderRuntime.state.value.status.contains(expected) }
        screenshot("voice-error.png")
        runOnMainSync { activity.finish() }
        until { ReaderRuntime.voiceSession == null }
        removeMonitor(monitor)
        until { snapshot()?.packageName == context.packageName }
        checkThat("speech rejected without navigating: $expected", true)
    }

    override fun onStart() {
        try {
            check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Use a dedicated Android emulator" }
            val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            fun shell(command: String) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
            }
            // Instrumentation 会重启目标进程；在专用模拟器中重新连接服务。
            shell("settings delete secure enabled_accessibility_services")
            shell("settings put secure enabled_accessibility_services com.example.myreader/com.example.myreader.ReaderService")
            shell("settings put secure accessibility_enabled 1")
            shell("pm grant com.example.myreader android.permission.RECORD_AUDIO")
            shell("pm grant com.example.myreader.test android.permission.RECORD_AUDIO")
            until(15000) { ReaderService.instance != null }
            runOnMainSync { ReaderRuntime.initialize(targetContext) }
            targetContext.getSharedPreferences("app_settings", 0).edit()
                .putBoolean("screen_upload_consent", true).putBoolean("voice_input_consent", true).commit()
            ModelConfigStore.保存(targetContext, ModelConfig("ollama", "mock-voice", "", "http://localhost:11434", false, "Ollama 本地"))
            checkThat("negated operation is not executed", VoiceCommands.parse("不要返回") == VoiceCommand.Unknown)
            checkThat("compound operation is not executed", VoiceCommands.parse("返回然后打开设置") == VoiceCommand.Unknown)
            checkThat("reading scope", (VoiceCommands.parse("读上半部分，只读价格") as VoiceCommand.Read).region == ReadRegion.TOP)
            fixture()
            checkThat("password text excluded", snapshot()?.text?.contains("PRIVATE-PASSWORD-SENTINEL") == false)
            runOnMainSync { if (!ReaderRuntime.state.value.overlay) ReaderService.instance!!.toggleFloating() }
            mode("slow")
            runOnMainSync { ReaderRuntime.read() }
            until { ReaderRuntime.state.value.busy }
            checkThat("request enters loading state", ReaderRuntime.state.value.busy)
            Thread.sleep(250)
            screenshot("loading-overlay.png")
            runOnMainSync { ReaderRuntime.stop() }
            Thread.sleep(8500)
            checkThat("cancel clears loading and ignores delayed result", !ReaderRuntime.state.value.busy && ReaderRuntime.state.value.result.isBlank())
            mode("success")
            spoken("读上半部分，只读价格") { ReaderRuntime.state.value.result.contains("模拟讲解") }
            checkThat("speech result reaches reading request", ReaderRuntime.state.value.command == "读上半部分，只读价格")
            spoken("向上滑动") { snapshot()?.text?.contains("商品价格") == false && ReaderRuntime.state.value.status == "已滚动页面" }
            checkThat("voice scroll advances external page", snapshot()?.text?.contains("商品价格") == false)
            spoken("向下滑动") { snapshot()?.text?.contains("商品价格") == true }
            spoken("点击Test action") { ReaderRuntime.state.value.status.contains("准备点击") }
            checkThat("click requires confirmation", snapshot()?.text?.contains("action-count=0") == true)
            spoken("确认执行") { snapshot()?.text?.contains("action-count=1") == true }
            checkThat("confirmed exact label click", true)
            spoken("点击Test action") { ReaderRuntime.state.value.status.contains("准备点击") }
            fixture()
            spoken("确认执行") { ReaderRuntime.state.value.status.contains("页面") && ReaderRuntime.state.value.status.contains("变化") }
            checkThat("stale click confirmation rejected", snapshot()?.text?.contains("action-count=0") == true)
            rejectedSpeech(0.1, 0, "没有听清操作口令")
            rejectedSpeech(0.95, android.speech.SpeechRecognizer.ERROR_NETWORK, "语音识别网络不可用")
            spoken("回到桌面") { snapshot()?.packageName?.contains("launcher") == true }
            checkThat("home command", true)
            spoken("打开Reader test page") { snapshot()?.packageName == context.packageName }
            checkThat("open app by installed label", true)
            val beforeBack = snapshot()?.key
            spoken("返回") { snapshot()?.key != beforeBack && ReaderRuntime.state.value.status == "已返回" }
            checkThat("back command", true)
            fixture()
            mode("error")
            runOnMainSync { ReaderRuntime.read() }
            until { ReaderRuntime.state.value.status.contains("HTTP 401") }
            checkThat("failure clears loading", !ReaderRuntime.state.value.busy)
            mode("success")
            report.appendLine("NOTE Recognition service and AI responses were mocked; microphone accuracy and audible Chinese TTS need real-device testing.")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString() + "FAIL " + error.stackTraceToString()) })
        }
    }
}
