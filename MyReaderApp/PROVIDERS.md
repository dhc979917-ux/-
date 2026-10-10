# 如何添加新的 AI 提供商

## 情况一：对方提供 OpenAI 兼容接口（绝大多数厂商）

**不需要写任何新代码**，两步搞定：

1. 在 `ModelConfig.kt` 的 `默认配置.提供商列表` 里加一条：

```kotlin
Provider预设("新厂商名", "openai", "https://他们的接口地址/v1", "默认模型名", 是否支持图片),
```

2. 重新编译运行 → 设置页的提供商下拉里就会出现它，选它、填密钥、保存即可。

通义、DeepSeek、智谱、Kimi 以及各类中转站都属于这种情况。

## 情况二：协议不兼容 OpenAI（如 Claude / Gemini 原生协议）

需要写一个适配器，共四步：

### 1. 新建 `ai/XxxProvider.kt`

```kotlin
class XxxProvider(private val config: ModelConfig) : AiProvider {

    override val 名称 = "Xxx"

    override suspend fun 理解屏幕(屏幕文字: String, 截图Base64: String?): String =
        withContext(Dispatchers.IO) {
            // 组装他们家的请求体 → OkHttp 发出去 → 解析出文本返回
        }

    override suspend fun 测试连接(): Result<String> = withContext(Dispatchers.IO) {
        // 调一次 理解屏幕("测试：请回复'连接成功'", null)，成功返回 Result.success
    }
}
```

**必须遵守的约定**：

- 网络请求放在 `withContext(Dispatchers.IO)` 里
- 任何异常都不能抛出去，一律转成文字返回（`"AI 调用失败：${e.message}"`），由 TTS 读给用户
- 支持图片的模型要消费 `截图Base64` 参数（不支持就忽略，别报错）

### 2. 注册到工厂

`AiProviderFactory.创建()` 的 `when` 里加一个分支：

```kotlin
"xxx" -> XxxProvider(config)
```

### 3. 加预设（可选）

在 `默认配置.提供商列表` 加一条，类型填 `"xxx"`，让设置页能直接选到它。不加也行，用户可以选「自定义」后手填地址。

### 4. 验证

设置页 → 选中新提供商 → 填密钥 → 点「测试连接」，弹"✅ 连接成功"即接入完成。

## 架构铁律

- **业务层（MainActivity、朗读屏幕流程）只依赖 `AiProvider` 接口**，不知道任何具体厂商的存在
- 加一个模型 = 加一个文件；如果加模型需要改业务代码，说明架构退化了
- 密钥只存手机本地 SharedPreferences，绝不进代码、绝不进仓库
