package com.example.myreaderdemo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiClient {

    // 连接 30 秒 / 读取 60 秒：屏幕文字可能很长，模型响应慢时也要等得住
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun 理解屏幕(屏幕文字: String): String = withContext(Dispatchers.IO) {
        // 地址/模型/密钥改为运行时从 AiConfig 读取：换服务商、换模型都不用改代码重新编译
        val url = AiConfig.url.trim()
        val model = AiConfig.model.trim()
        val key = AiConfig.key.trim()

        if (url.isEmpty() || model.isEmpty()) {
            return@withContext "还没有配置 AI。请回到应用主界面，点 AI 设置，选择预设并填入密钥。"
        }

        // 用户可能填"基础地址"也可能直接贴完整接口地址，这里统一补全成完整路径
        val endpoint = when {
            url.endsWith("chat/completions") -> url
            url.endsWith("/") -> url + "chat/completions"
            else -> url + "/chat/completions"
        }

        // 网络请求必须在 IO 线程：suspend + withContext 保证调用方（主线程）不阻塞、不 ANR
        try {
            val prompt = """
                你是一个"屏幕理解助手"，服务对象是看不到屏幕的人。
                请理解下面的屏幕内容，用自然的口语讲解：
                1. 这是什么页面（购物/聊天/文章/设置等）
                2. 核心内容是什么（一两句话）
                3. 有哪些重要信息（价格/时间/按钮作用）
                4. 有什么值得注意的
                5. 如果需要操作，该怎么做

                要求：像朋友聊天，突出重点，跳过无关内容。

                屏幕内容：
                ${屏幕文字}
            """.trimIndent()

            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))

            val request = Request.Builder()
                .url(endpoint)
                .apply {
                    // 绝大多数 OpenAI 兼容服务用 Bearer 密钥；
                    // 本地模型（如电脑上的 Ollama）没有密钥，就不加这个请求头
                    if (key.isNotEmpty()) header("Authorization", "Bearer $key")
                }
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use "AI 调用失败：HTTP ${response.code}"
                }
                val text = response.body?.string() ?: return@use "AI 调用失败：响应为空"
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            // 网络异常/解析异常一律转成文字返回，由 TTS 读给用户，绝不让 App 崩溃
            "AI 调用失败：${e.message}"
        }
    }
}
