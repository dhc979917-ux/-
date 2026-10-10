package com.example.myreader.ai

import android.content.Context

object ModelConfigStore {
    private const val PREF = "model_config"
    private const val KEY_提供商 = "提供商"
    private const val KEY_模型名 = "模型名"
    private const val KEY_APIKEY = "apiKey"
    private const val KEY_BASEURL = "baseUrl"
    private const val KEY_支持图片 = "支持图片"

    // 配置只存在手机本地 SharedPreferences，密钥绝不进代码和仓库
    fun 保存(context: Context, config: ModelConfig) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_提供商, config.提供商)
            putString(KEY_模型名, config.模型名)
            putString(KEY_APIKEY, config.apiKey)
            putString(KEY_BASEURL, config.baseUrl)
            putBoolean(KEY_支持图片, config.支持图片)
            apply()
        }
    }

    fun 读取(context: Context): ModelConfig {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return ModelConfig(
            提供商 = sp.getString(KEY_提供商, "openai") ?: "openai",
            模型名 = sp.getString(KEY_模型名, "gpt-4o-mini") ?: "gpt-4o-mini",
            apiKey = sp.getString(KEY_APIKEY, "") ?: "",
            baseUrl = sp.getString(KEY_BASEURL, "") ?: "",
            支持图片 = sp.getBoolean(KEY_支持图片, true),
        )
    }
}
