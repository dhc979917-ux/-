package com.example.myreader.ai

interface AiProvider {
    val 名称: String
    suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?, 朗读要求: String = "讲解当前页面，突出核心内容与重要信息"): String
    suspend fun 测试连接(): Result<String>
}
