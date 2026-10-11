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

// 一个适配器覆盖所有 OpenAI 兼容接口：OpenAI / 通义 / DeepSeek / 智谱 / Kimi / 自定义中转
class OpenAiProvider(private val config: ModelConfig) : AiProvider {

    override val 名称 = "OpenAI 兼容"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?): String =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) return@withContext "请先在设置里配置 API Key"

            val baseUrl = config.baseUrl.ifBlank { "https://api.openai.com/v1" }
            val url = "${baseUrl.trimEnd('/')}/chat/completions"

            val 内容 = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", 构建Prompt(屏幕文字))
                })
                if (!截图Base64.isNullOrBlank() && config.支持图片) {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/png;base64,$截图Base64")
                        })
                    })
                }
            }

            val json = JSONObject().apply {
                put("model", config.模型名)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", 内容)
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).awaitResponse().use { response ->
                    check(response.isSuccessful) { "服务返回 HTTP ${response.code}，请检查密钥、模型和地址" }
                    val body = checkNotNull(response.body?.string()) { "AI 无响应" }
                    JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } catch (e: Exception) {
                throw e
            }
        }

    override suspend fun 测试连接(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = 理解屏幕("测试：请回复'连接成功'", null)
            if (result.isBlank() || config.apiKey.isBlank()) {
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
请理解下面的屏幕内容，用自然的口语讲解：
1. 这是什么页面（购物/聊天/文章/设置等）
2. 核心内容是什么（一两句话）
3. 有哪些重要信息（价格/时间/按钮作用）
4. 图片里是什么（描述图片内容）
5. 有什么值得注意的
6. 如果需要操作，该怎么做

要求：像朋友聊天，突出重点，跳过无关内容。

屏幕文字：
$屏幕文字
    """.trimIndent()
}
