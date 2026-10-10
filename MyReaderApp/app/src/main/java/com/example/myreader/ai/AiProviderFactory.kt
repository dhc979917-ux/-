package com.example.myreader.ai

object AiProviderFactory {
    fun 创建(config: ModelConfig): AiProvider {
        // 业务层只认 AiProvider 接口；加新模型 = 这里加一个分支 + 一个 Provider 文件
        return when (config.提供商.lowercase()) {
            "openai" -> OpenAiProvider(config)
            "claude" -> ClaudeProvider(config)
            "gemini" -> GeminiProvider(config)
            "ollama" -> OllamaProvider(config)
            else -> OpenAiProvider(config)
        }
    }
}
