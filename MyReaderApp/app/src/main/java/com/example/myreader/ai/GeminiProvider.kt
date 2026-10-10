package com.example.myreader.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider(private val config: ModelConfig) : AiProvider {

    override val 名称 = "Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?): String =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) return@withContext "请先在设置里配置 API Key"

            val model = config.模型名.ifBlank { "gemini-1.5-pro" }
            val base = config.baseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
            val url = "${base.trimEnd('/')}/models/$model:generateContent?key=${config.apiKey}"

            val parts = JSONArray().apply {
                put(JSONObject().apply { put("text", 构建Prompt(屏幕文字)) })
                if (!截图Base64.isNullOrBlank() && config.支持图片) {
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/png")
                            put("data", 截图Base64)
                        })
                    })
                }
            }

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply { put("parts", parts) })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext "AI 无响应"
                    if (!response.isSuccessful) return@withContext "AI 报错：$body"
                    JSONObject(body)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            } catch (e: Exception) {
                "AI 调用失败：${e.message}"
            }
        }

    override suspend fun 测试连接(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = 理解屏幕("测试：请回复'连接成功'", null)
            if (result.contains("失败") || result.contains("报错") || result.contains("请先")) {
                Result.failure(Exception(result))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun 构建Prompt(屏幕文字: String) = """
你是一个"屏幕理解助手"，服务对象是看不到屏幕的人。
请理解下面的屏幕内容，用自然的口语讲解。
像朋友聊天，突出重点，跳过无关内容。

屏幕文字：
$屏幕文字
    """.trimIndent()
}
