# 验证记录

日期：2026-10-11。范围：MyReaderApp Android 原型。

当前 APK 已更新为 **0.3 语音交互版**，新增验证记录见下节。后文模型选择、界面改版、连续截图与朗读记录保留为历史验证；本轮没有调用真实 AI 或测试真实麦克风识别、中文发声。

## 0.3 语音与页面操作验证

- 编译、测试 APK 构建与 Android Lint 通过：0 errors、61 warnings。生产 APK 版本为 0.3 / versionCode 3，覆盖安装到 Android 14 模拟器。
- 悬浮入口改为朗读与麦克风两个按钮。慢请求中出现白色旋转加载环和“停”字；取消后恢复，等待超过模拟慢响应的 8 秒仍没有迟到结果。
- 语音识别通过独立测试 APK 中的 `MockRecognitionService` 提供转录，经过实际 `SpeechRecognizer`、语音窗口、口令处理与无障碍服务流程。目标页面是独立测试 APK 的可滚动页面。
- 自动化 17 项检查全部通过：否定与复合指令不执行、朗读区域解析、密码文字排除、进入加载、取消与迟到响应、语音朗读请求、外部页面滚动、点击待确认、确认后真实点击、页面变化后取消旧确认、低置信度不导航、识别网络失败不导航、回桌面、按安装名称打开应用、返回上一层窗口、HTTP 401 清除加载。
- 滚动测试实际执行了两个方向；点击后的测试计数从 0 变成 1。服务端请求确认包含“只读价格”的朗读要求，不包含测试密码。
- 语音窗口在普通尺寸及 720 × 1280、320 dpi、150% 字体下已检查，文本没有裁切、主要操作按钮可见；实际观察到 30 秒识别超时后的重试状态。新版首页的语音入口在大字体下可用。
- 检查最终 APK 的 manifest，没有测试语音服务或测试页面；`VoiceCommandActivity` 不对外导出。测试语音服务设置已恢复，模拟接口和测试 APK 在验收后清理。

证据：`validation/voice/instrumentation.txt` 包含各项 PASS 和最终汇总（汇总会再次列出 PASS），没有 FAIL；截图为 `loading-overlay.png`、`voice-error.png`、`small-home.png`、`small-voice.png`、`home.png`。测试源码保存在 `app/src/androidTest/`，模拟接口在 `tools/voice-mock.cjs`，复现步骤见 `TESTING.md`。

这轮验证证明的是模拟识别结果到页面操作的链路，不能代表真实语音识别率、中文音质或真实模型理解能力。暂未逐项验证焦点朗读、图片区域裁剪、手势回退、最近应用、语速调整的真机表现，以及 TalkBack 与录音的协同。当前每次需点击麦克风说一个口令，不包含后台唤醒和自主多步任务。

## 模型选择验证

- 编译与 Android Lint 通过：0 errors、46 warnings。最终 APK 已覆盖安装到 Android 14 模拟器。
- 临时 JVM 检查程序直接编译项目中的 `ModelCatalog`、`ModelConfig` 与 `HttpCall`，使用本机 HTTP 模拟接口，不使用真实密钥。
- 四种协议检查通过：OpenAI 路径与 Bearer 头、排序和去重；Claude 认证头与多页合并；Gemini 认证头、分页、去除 `models/` 前缀及生成能力过滤；Ollama 已安装模型名称解析。
- 故障检查通过：空列表、401/403/404/429/500、非 JSON、缺失列表、重复分页、拒绝重定向以及协程取消。
- 模拟器操作通过：获取列表、忽略大小写搜索、选中 `qwen2.5:7b`、模拟文字连接成功、保存并重新进入。模型名称与响应均为模拟数据。
- 独立配置验证通过：切换服务保留草稿；分别保存后可恢复模型和虚构测试密钥；自定义 Ollama 保存并重进后仍使用 Ollama 协议。未向云端发送测试密钥。
- 模拟器故障注入通过：401、404、空列表、错误 JSON 均保留当前模型并恢复获取按钮；慢请求期间切换服务，不显示旧列表或覆盖新配置，切回后可重新获取。
- 布局检查通过：1080 × 2340，以及 720 × 1280、320 dpi、150% 字体；表单可滚动，模型列表与底部操作可用；显示配置变化后模型草稿保持。
- `validation/model-settings/runtime-errors.txt` 为空，未观察到本轮运行期崩溃。截图见同目录 `settings.png`、`models.png`、`small-large-font.png`、`small-models.png`。

模型列表接口参考：[OpenAI](https://developers.openai.com/api/reference/resources/models/methods/list)、[Claude](https://platform.claude.com/docs/en/api/models/list)、[Gemini](https://ai.google.dev/api/models)、[Ollama](https://docs.ollama.com/api/tags)。真实服务的权限、模型可用性与图片能力仍需实际账户验证。

## 界面改版验证

- 首页改为朗读主操作、播放控制、结果区和权限列表；设置入口移至右上角。
- 设置页改为表单与固定底部操作栏，移除无实际选择意义的模式单选框，保留只读行为。
- 增加原生图标、声波图形、密钥显隐、服务下拉指示和权限状态文案。
- 最终 APK 在 Android 14 模拟器覆盖安装并启动成功；编译与 Lint 通过，0 errors、43 warnings。
- 检查 1080 × 2340 常规尺寸，以及 720 × 1280、320 dpi、150% 字体的小屏布局；首页与表单可滚动，底部保存按钮保持可见。
- 实际切换 Ollama 与 OpenAI 表单；使用虚构的 demo-key 验证显隐按钮，界面结构确认 password 从 true 变为 false。未保存该密钥，未调用云端接口。
- 检查空密钥保存校验、设置返回和图片识别授权入口；本轮 AndroidRuntime:E 日志为空。
- 新版截图在 `validation/redesign/`，首页预览为 `preview.png`。

## 构建结果

- JDK 17、Gradle 8.2、Android SDK 34。
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 执行成功。
- Lint：0 errors，61 warnings。警告主要涉及硬编码文案、依赖版本提示、旧系统属性兼容和样式建议；并非零警告。
- APK：`validation/MyReader-debug.apk`，约 6 MB，debug 签名，仅用于测试安装。
- 当前 APK SHA-256：`af546f8224333b75a9126f574dca5bc1264d44ff17055835f85dacff4db9a793`。

## 实际运行

设备：Android 14/API 34、ARM64、Pixel 5 模拟器，1080 × 2340。
接口：本机临时 HTTP 服务模拟 Ollama `/api/chat`；返回固定中文讲解，不是实际 AI 推理。
没有使用用户的真实 API 密钥或个人屏幕内容。

| 验证项目 | 结果与证据 |
| --- | --- |
| 安装与启动 | `adb install -r` 成功，首页正常显示 |
| 未配置时朗读 | 实际跳转到模型设置 |
| 配置校验 | 空地址触发输入错误，未发送请求 |
| 文字连接测试 | 显示“文字连接成功 · llava” |
| 正常文字读取 | 服务收到请求，页面显示完整固定讲解，重播按钮启用 |
| Android 14 图片读取 | 单次授权后连续两次请求均携带图片，服务端记录 `image: true` |
| 请求去重与停止 | 连续两次点击只生成一个慢请求；停止后服务器记录 `completed: false`，连接已取消 |
| HTTP 401 | 页面显示失败信息，朗读按钮恢复可用 |
| 最终 APK 回归 | 重新安装后文字请求与结果显示正常 |
| 150% 字体 | 首页正常换行，可向下滚动查看讲解 |
| 跨应用悬浮球 | 模拟器桌面显示圆形悬浮球，点击后产生新的读取请求 |
| 运行期崩溃 | 本轮 `AndroidRuntime:E` 日志为空 |

截图：`validation/final-home.png`、`settings.png`、`image-result.png`、`error.png`、`stopped.png`、`large-font.png`、`large-font-scrolled.png`、`overlay.png`。
部分场景截图来自最终微调之前，最终首页截图以 `final-home.png` 为准。

## 尚未验证

- 模拟器没有配置 TTS 引擎，`tts_default_synth` 为 null。已观察到中文语音未就绪的提示；没有验证实际中文发声、音质、长文本播放或音频停止效果。
- 未调用真实 OpenAI、Claude、Gemini 或 Ollama 模型；未评价屏幕理解准确性和多模态回答质量。
- 未进行真机测试、TalkBack 完整导航、Android 7 兼容、厂商后台保活、截图过程中旋转屏幕等测试。
- 未建立持续集成；语音模拟器测试已纳入项目，历史协议验证使用临时检查程序。以上是实际构建、Lint、界面操作、截图与本地故障注入记录。

下一轮应使用目标手机、实际模型配置与中文 TTS 引擎，按 `TESTING.md` 逐项验收。
