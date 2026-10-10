package com.example.myreaderdemo

import android.content.Context

object AiConfig {

    private const val PREFS = "ai_config"

    // 全局持有 applicationContext：AiClient 在任意位置都能读写配置
    // （用 application 而不是 activity，避免界面销毁后 context 失效）
    private lateinit var context: Context

    fun init(appContext: Context) {
        context = appContext.applicationContext
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val url: String
        get() = prefs().getString("url", "") ?: ""

    val model: String
        get() = prefs().getString("model", "") ?: ""

    val key: String
        get() = prefs().getString("key", "") ?: ""

    fun 保存(url: String, model: String, key: String) {
        prefs().edit()
            .putString("url", url)
            .putString("model", model)
            .putString("key", key)
            .apply()
    }
}
