package com.example.myreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class VoiceCommandActivity : AppCompatActivity(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var submitted = false
    private var sessionId = ""
    private var listening = false
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognitionTimeout = Runnable { showError("语音识别超时，请重试") }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition() else showError("未获得麦克风权限，可以输入口令或在系统设置中开启权限")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReaderRuntime.initialize(this)
        sessionId = intent.getStringExtra("session").orEmpty()
        if (ReaderRuntime.voiceSession?.id != sessionId || sessionId.isEmpty()) { finish(); return }
        setContentView(R.layout.activity_voice_command)
        findViewById<View>(R.id.btnCloseVoice).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnListen).setOnClickListener { prepareRecognition() }
        findViewById<Button>(R.id.btnSendCommand).setOnClickListener {
            val text = findViewById<EditText>(R.id.etVoiceCommand).text.toString().trim()
            if (text.isBlank()) showError("请先说出或输入口令") else submit(text)
        }
        findViewById<View>(R.id.btnVoiceHelp).setOnClickListener {
            closeRecognizer()
            ReaderRuntime.say(VoiceCommands.HELP)
            MaterialAlertDialogBuilder(this).setTitle("语音口令")
                .setMessage(VoiceCommands.HELP).setPositiveButton("知道了", null).show()
        }
        if (savedInstanceState == null) prepareRecognition()
        else showStatus("语音已暂停", false)
    }

    private fun prepareRecognition() {
        if (listening) { recognizer?.stopListening(); return }
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("voice_input_consent", false)) {
            MaterialAlertDialogBuilder(this).setTitle("启用语音指令？")
                .setMessage("仅在你启动语音输入时使用麦克风。语音由手机配置的识别服务处理，可能传至该服务的服务器。朗读口令会连同已授权的屏幕内容发送给你配置的 AI。")
                .setNegativeButton("暂不开启") { _, _ -> showStatus("语音未开启", false) }
                .setPositiveButton("开启") { _, _ ->
                    prefs.edit().putBoolean("voice_input_consent", true).apply()
                    requestMicrophone()
                }.show()
        } else requestMicrophone()
    }

    private fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecognition()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecognition() {
        closeRecognizer()
        ReaderRuntime.stop(clearPending = false)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("手机没有可用的语音识别服务，请安装或启用支持中文的语音识别服务")
            return
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            showStatus("正在启动麦克风", true)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            handler.postDelayed(recognitionTimeout, 30_000)
        } catch (error: RuntimeException) { showError("语音识别无法启动，请检查麦克风权限和识别服务") }
    }

    private fun showStatus(message: String, active: Boolean) {
        listening = active
        findViewById<TextView>(R.id.tvVoiceStatus).text = message
        findViewById<Button>(R.id.btnListen).text = if (active) "结束录音" else "开始录音"
        findViewById<ProgressBar>(R.id.voiceLevel).visibility = if (active) View.VISIBLE else View.INVISIBLE
        ReaderRuntime.voiceState(active, message)
    }

    private fun showError(message: String) {
        closeRecognizer()
        showStatus(message, false)
        ReaderRuntime.say(message)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        showStatus("正在听，请说出口令", true)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 50) }.getOrNull()
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        ReaderService.instance?.feedback()
    }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {
        findViewById<ProgressBar>(R.id.voiceLevel).progress = ((rmsdB + 2) * 8).toInt().coerceIn(0, 100)
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { showStatus("正在识别口令", false) }
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        findViewById<EditText>(R.id.etVoiceCommand).setText(text)
    }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
        val command = VoiceCommands.parse(text)
        if (text.isBlank()) showError("没有听清，请再说一次")
        else if (confidence != null && confidence >= 0 && confidence < 0.5f &&
            command !is VoiceCommand.Read && command != VoiceCommand.Stop) {
            findViewById<EditText>(R.id.etVoiceCommand).setText(text)
            showError("没有听清操作口令，请重新说一次，或核对文字后执行")
        } else submit(text)
    }
    override fun onError(error: Int) = showError(when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "麦克风权限未开启"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络不可用，请重试"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务忙，请稍后重试"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "语音服务暂不支持中文，请检查语言包"
        else -> "语音识别失败，请重试或输入口令"
    })

    private fun submit(text: String) {
        if (submitted) return
        submitted = true
        closeRecognizer()
        finish()
        ReaderRuntime.submitVoice(sessionId, text.take(500))
    }

    private fun closeRecognizer() {
        handler.removeCallbacks(recognitionTimeout)
        listening = false
        recognizer?.setRecognitionListener(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tone?.release()
        tone = null
    }

    override fun onPause() {
        closeRecognizer()
        if (!isFinishing && findViewById<View?>(R.id.tvVoiceStatus) != null) showStatus("语音已暂停", false)
        super.onPause()
    }

    override fun onDestroy() {
        closeRecognizer()
        if (!submitted && !isChangingConfigurations) ReaderRuntime.cancelVoice(sessionId)
        super.onDestroy()
    }
}
