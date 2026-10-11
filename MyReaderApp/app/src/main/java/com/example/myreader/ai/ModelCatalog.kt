package com.example.myreader.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ModelCatalog {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun 获取(config: ModelConfig): List<String> = withContext(Dispatchers.IO) {
        val models = linkedSetOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor = ""
        repeat(50) {
            val path = if (config.提供商 == "ollama") "api/tags" else "models"
            val url = config.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
                .addPathSegments(path)
            when (config.提供商) {
                "claude" -> {
                    url.addQueryParameter("limit", "1000")
                    if (cursor.isNotEmpty()) url.addQueryParameter("after_id", cursor)
                }
                "gemini" -> {
                    url.addQueryParameter("pageSize", "1000")
                    if (cursor.isNotEmpty()) url.addQueryParameter("pageToken", cursor)
                }
            }
            val request = Request.Builder().url(url.build()).get()
            when (config.提供商) {
                "openai" -> request.header("Authorization", "Bearer ${config.apiKey}")
                "claude" -> request.header("x-api-key", config.apiKey)
                    .header("anthropic-version", "2023-06-01")
                "gemini" -> request.header("x-goog-api-key", config.apiKey)
                "ollama" -> Unit
                else -> throw IOException("不支持的接口协议")
            }
            val body = client.newCall(request.build()).awaitResponse().use { response ->
                if (!response.isSuccessful) throw IOException(when (response.code) {
                    401, 403 -> "密钥无效或没有模型列表权限"
                    404, 405 -> "此服务未开放模型列表，请手动填写模型 ID"
                    429 -> "请求过于频繁，请稍后重试"
                    in 300..399 -> "服务地址发生跳转，请填写最终 API 地址"
                    else -> "服务返回 HTTP ${response.code}"
                })
                JSONObject(response.body?.string() ?: throw IOException("服务返回空响应"))
            }
            val field = if (config.提供商 in setOf("ollama", "gemini")) "models" else "data"
            // Gemini 的空列表响应可以省略 models 字段。
            val items = body.optJSONArray(field)
                ?: if (config.提供商 == "gemini") org.json.JSONArray()
                else throw IOException("响应缺少模型列表，请检查服务地址和协议")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (config.提供商 == "gemini") {
                    val methods = item.optJSONArray("supportedGenerationMethods") ?: continue
                    if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) continue
                }
                val id = when (config.提供商) {
                    "gemini" -> item.optString("name").removePrefix("models/")
                    "ollama" -> item.optString("name").ifBlank { item.optString("model") }
                    else -> item.optString("id")
                }.trim()
                if (id.isNotEmpty()) models.add(id)
            }
            cursor = when (config.提供商) {
                "gemini" -> body.optString("nextPageToken")
                "claude" -> if (body.optBoolean("has_more")) {
                    body.optString("last_id").ifBlank { throw IOException("模型列表分页信息不完整") }
                } else ""
                else -> ""
            }
            if (cursor.isBlank()) return@withContext models.sorted()
            if (!cursors.add(cursor)) throw IOException("模型列表分页重复，请手动填写模型 ID")
        }
        throw IOException("模型列表过大，请手动填写模型 ID")
    }
}
