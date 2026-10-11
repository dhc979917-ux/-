package com.example.myreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale

class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var generation = 0
    private var completion: (() -> Unit)? = null
    private var lastUtterance = ""
    private val handler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context, this)
    var rate = 1.0f
        private set

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finish(id) }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { finish(id) }
            override fun onError(id: String?, errorCode: Int) { finish(id) }
            private fun finish(id: String?) {
                handler.post {
                    if (id == lastUtterance) {
                        val callback = completion
                        completion = null
                        callback?.invoke()
                    }
                }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.CHINESE)
            tts.setSpeechRate(rate)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun 朗读(text: String, onDone: (() -> Unit)? = null): Boolean {
        停止()
        if (!ready) {
            Toast.makeText(context, "中文语音尚未就绪，请检查系统语音引擎；内容仍显示在页面上", Toast.LENGTH_LONG).show()
            onDone?.invoke()
            return false
        }
        val chunks = text.chunked(TextToSpeech.getMaxSpeechInputLength() - 1)
        if (chunks.isEmpty()) { onDone?.invoke(); return false }
        completion = onDone
        lastUtterance = "reader-${generation}-${chunks.lastIndex}"
        chunks.forEachIndexed { index, chunk ->
            val queue = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(chunk, queue, null, "reader-${generation}-$index") == TextToSpeech.ERROR) {
                val callback = completion
                停止()
                Toast.makeText(context, "语音播放失败，请检查系统语音设置", Toast.LENGTH_LONG).show()
                callback?.invoke()
                return false
            }
        }
        return true
    }

    fun 调整语速(delta: Float): Float {
        rate = (rate + delta).coerceIn(0.5f, 1.8f)
        if (ready) tts.setSpeechRate(rate)
        return rate
    }

    fun 停止() {
        generation++
        completion = null
        lastUtterance = ""
        if (ready) tts.stop()
    }

    fun 释放() { 停止(); tts.shutdown() }
}
