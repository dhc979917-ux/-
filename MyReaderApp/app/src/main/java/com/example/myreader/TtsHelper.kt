package com.example.myreader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class TtsHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var ready = false
    private val tts = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // TTS 引擎异步初始化，成功后才置 ready，否则朗读会"无声失败"
            val result = tts.setLanguage(Locale.CHINESE)
            tts.setSpeechRate(1.0f)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun 朗读(text: String) {
        if (ready) {
            // QUEUE_FLUSH：新内容直接顶掉旧的，保证每次读的都是当前屏幕
            text.chunked(TextToSpeech.getMaxSpeechInputLength() - 1).forEachIndexed { index, chunk ->
                val queue = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (tts.speak(chunk, queue, null, "myreader-$index") == TextToSpeech.ERROR) {
                    Toast.makeText(context, "语音播放失败，请检查系统语音设置", Toast.LENGTH_LONG).show()
                    return
                }
            }
        } else {
            Toast.makeText(context, "中文语音尚未就绪，请检查系统语音引擎；讲解已显示在页面上", Toast.LENGTH_LONG).show()
        }
    }

    fun 停止() {
        if (ready) tts.stop()
    }

    fun 释放() {
        tts.shutdown()
    }
}
