package com.example.myreader.ai

interface AiProvider {
    val 名称: String
    suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?): String
    suspend fun 测试连接(): Result<String>
}
