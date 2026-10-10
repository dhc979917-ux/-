package com.example.myreaderdemo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {

    private var ready = false
    private val tts = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 引擎初始化是异步的，成功后才置 ready，否则朗读会"无声失败"
            tts.language = Locale.CHINESE
            tts.setSpeechRate(1.0f)
            ready = true
        }
    }

    fun 朗读(text: String) {
        if (ready) {
            // QUEUE_FLUSH：新内容直接顶掉旧的，保证每次点击读的都是当前屏幕
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "myreader")
        }
    }

    fun 停止() {
        if (ready) tts.stop()
    }

    fun 释放() {
        tts.shutdown()
    }
}
