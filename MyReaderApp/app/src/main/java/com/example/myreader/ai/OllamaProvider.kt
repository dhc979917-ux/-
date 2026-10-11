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

class OllamaProvider(private val config: ModelConfig) : AiProvider {

    override val 名称 = "Ollama 本地"

    // 本地推理可能很慢，读取超时给到 120 秒
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?): String =
        withContext(Dispatchers.IO) {
            val baseUrl = config.baseUrl.ifBlank { "http://localhost:11434" }
            val url = "${baseUrl.trimEnd('/')}/api/chat"

            val json = JSONObject().apply {
                put("model", config.模型名.ifBlank { "llava" })
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", 构建Prompt(屏幕文字))
                        if (!截图Base64.isNullOrBlank() && config.支持图片) {
                            put("images", JSONArray().put(截图Base64))
                        }
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).awaitResponse().use { response ->
                    check(response.isSuccessful) { "服务返回 HTTP ${response.code}，请检查模型和地址" }
                    val body = checkNotNull(response.body?.string()) { "AI 无响应" }
                    JSONObject(body)
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
            if (result.isBlank()) {
                Result.failure(Exception(result))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun 构建Prompt(屏幕文字: String) = """
你是一个"屏幕理解助手"。请理解下面的屏幕内容，用自然的口语讲解。

屏幕文字：
$屏幕文字
    """.trimIndent()
}
