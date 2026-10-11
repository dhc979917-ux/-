package com.example.myreader.ai

data class ModelConfig(
    val 提供商: String,        // "openai" / "claude" / "gemini" / "ollama"
    val 模型名: String,        // "gpt-4o-mini" / "claude-3-5-sonnet-20241022" / ...
    val apiKey: String,
    val baseUrl: String,       // 自定义时用
    val 支持图片: Boolean = true,
    val 预设名: String = "",
)

data class Provider预设(
    val 名称: String,
    val 类型: String,
    val baseUrl: String,
    val 默认模型: String,
    val 支持图片: Boolean,
)

// 各提供商的默认配置：类型决定走哪个适配器
// "openai" 类型覆盖所有 OpenAI 兼容接口（通义/DeepSeek/智谱/Kimi/自定义...）
object 默认配置 {
    val 提供商列表 = listOf(
        Provider预设("OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o-mini", true),
        Provider预设("通义千问", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-vl-plus", true),
        Provider预设("DeepSeek", "openai", "https://api.deepseek.com/v1", "deepseek-chat", false),
        Provider预设("智谱 GLM", "openai", "https://open.bigmodel.cn/api/paas/v4", "glm-4v", true),
        Provider预设("月之暗面 Kimi", "openai", "https://api.moonshot.cn/v1", "moonshot-v1-8k-vision-preview", true),
        Provider预设("Anthropic Claude", "claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022", true),
        Provider预设("Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-1.5-pro", true),
        Provider预设("Ollama 本地", "ollama", "http://localhost:11434", "llava", true),
        Provider预设("自定义", "openai", "", "", true),
    )
}
