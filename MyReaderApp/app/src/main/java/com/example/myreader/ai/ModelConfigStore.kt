package com.example.myreader.ai

import android.content.Context
import org.json.JSONObject

object ModelConfigStore {
    private const val PREF = "model_config"
    private const val KEY_提供商 = "提供商"
    private const val KEY_模型名 = "模型名"
    private const val KEY_APIKEY = "apiKey"
    private const val KEY_BASEURL = "baseUrl"
    private const val KEY_支持图片 = "支持图片"

    // 配置只存在手机本地 SharedPreferences，密钥绝不进代码和仓库
    fun 保存(context: Context, config: ModelConfig) {
        val previous = 读取(context)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putString("profile:${预设名称(previous)}", 编码(previous))
            putString("profile:${预设名称(config)}", 编码(config))
            putString("preset", 预设名称(config))
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
            预设名 = sp.getString("preset", "") ?: "",
        )
    }

    fun 预设名称(config: ModelConfig): String =
        config.预设名.takeIf { name -> 默认配置.提供商列表.any { it.名称 == name } }
            ?: 默认配置.提供商列表.firstOrNull {
                it.类型 == config.提供商 &&
                    (config.baseUrl.isBlank() || it.baseUrl.trimEnd('/') == config.baseUrl.trimEnd('/'))
            }?.名称 ?: "自定义"

    fun 读取预设(context: Context, name: String): ModelConfig? {
        val active = 读取(context)
        if (预设名称(active) == name) return active.copy(预设名 = name)
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("profile:$name", null) ?: return null
        return runCatching {
            val value = JSONObject(json)
            ModelConfig(value.getString("protocol"), value.getString("model"),
                value.getString("key"), value.getString("url"), value.getBoolean("images"), name)
        }.getOrNull()
    }

    private fun 编码(config: ModelConfig): String = JSONObject()
        .put("protocol", config.提供商).put("model", config.模型名)
        .put("key", config.apiKey).put("url", config.baseUrl)
        .put("images", config.支持图片).toString()
}
