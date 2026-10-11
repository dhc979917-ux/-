<div align="center">

# 万物皆可读

**让屏幕上的文字与画面，变成可以听见的讲解。**

read-anything · AI 屏幕理解与朗读工具

![Android](https://img.shields.io/badge/Android-7.0%2B-285DE5?style=flat-square)
![Kotlin](https://img.shields.io/badge/App-Kotlin-20252B?style=flat-square)
![Windows](https://img.shields.io/badge/Desktop-Python-3776AB?style=flat-square)
![Stage](https://img.shields.io/badge/Status-Prototype-626B78?style=flat-square)

[界面预览](#preview) · [安装使用](#quick-start) · [模型接入](#providers) · [开发指南](#development) · [验证记录](MyReaderApp/VALIDATION.md)

</div>

---

万物皆可读将屏幕文字与可选截图交给配置的 AI 模型，生成中文讲解，再通过系统语音引擎朗读。项目面向需要语音获取屏幕信息的用户，包含 **Android 应用**与 **Windows 桌面脚本**。

> **当前阶段：可安装的测试原型。** Android 已完成构建和部分模拟器验证；真实模型的理解效果、中文发声及手机后台稳定性仍需真机验收。

<a id="preview"></a>

## 界面预览

<p align="center">
  <img src="MyReaderApp/validation/redesign/home.png" alt="Android 首页：朗读入口、播放控制、讲解结果与权限状态" width="260" />
  &nbsp;&nbsp;
  <img src="MyReaderApp/validation/model-settings/models.png" alt="模型选择：获取服务模型列表并搜索选择，图中为模拟接口数据" width="260" />
</p>

<p align="center"><sub>Android 14 模拟器实拍 · 朗读首页与模型选择（模拟列表）</sub></p>

### 核心体验

| 功能 | 当前实现 |
| :--- | :--- |
| **获取屏幕内容** | 无障碍服务读取界面文字，按需授权截图 |
| **理解与讲解** | 将内容发送至配置的模型，在页面显示返回的讲解 |
| **语音与控制** | 使用系统 TTS 朗读，支持停止请求与重播 |
| **跨应用入口** | 通过可拖动的悬浮球触发读取 |
| **模型配置** | 实时获取模型列表、搜索选择、手动模型 ID、独立服务配置与连接测试 |

<a id="quick-start"></a>

## 安装使用

### Android：安装测试包

**[获取测试 APK](MyReaderApp/validation/MyReader-debug.apk)** · [完整运行说明](MyReaderApp/TESTING.md)

1. 将 APK 通过 USB 或文件传输工具发送到安卓手机。
2. 在手机文件管理中打开 APK，按系统提示允许安装并完成安装。
3. 打开「万物皆可读」，点击右上角的**模型设置**，选择服务并填写密钥，点击**获取模型**后搜索选择，测试连接后保存。
4. 在首页的**读取设置**中开启「屏幕文字」；需要图片输入时，再授权「图片识别」。
5. 开启「悬浮朗读」，确认内容发送授权后，切换到目标页面点击悬浮球。

手机需配置支持中文的 TTS 语音引擎。APK 仅适用于 Android，不适用于 iPhone；当前提供的是 debug 测试包。

<details>
<summary><strong>Windows：运行桌面版</strong></summary>

在项目根目录打开 PowerShell，创建虚拟环境并安装依赖：

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

编辑 `config.json`，填写 `base_url`、`api_key` 和支持图片输入的 `model`，然后双击 `启动.bat`。

| 默认快捷键 | 操作 |
| :--- | :--- |
| `Alt + R` | 截取整个屏幕并请求讲解 |
| `Alt + S` | 框选区域并请求讲解 |
| `Alt + Q` | 停止朗读 |

配置中的模型名称是预设值，不代表其当前可用性或价格。本轮测试未覆盖 Windows 版。

</details>

<a id="providers"></a>

## 模型接入

Android 内置 **4 类协议适配器、9 个服务预设**，模型不再局限于预设名称。

1. 选择服务商，填写 API 密钥与 API 根地址；Ollama 不需要密钥。
2. 点击 **获取模型**，从接口返回的列表搜索并选择模型，也可以直接填写模型 ID。
3. 根据模型的实际图片能力设置截图开关，点击 **测试连接**，成功后保存。

各服务商分别记住已保存的地址、密钥和模型。设置页内来回切换会保留未保存草稿；退出设置页后，未保存的修改不会生效。每个预设保存一组配置，「自定义」也只有一个配置位置。

「自定义」可选择 OpenAI 兼容、Claude、Gemini 或 Ollama 协议，适用于对应协议的代理或自建服务。地址应填写 API 根地址，例如带 `/v1` 的 OpenAI 地址，不要填写完整的 `/chat/completions` 路径。

| 适配器 | 配置入口 |
| :--- | :--- |
| **OpenAI 兼容** | OpenAI、通义千问、DeepSeek、智谱 GLM、Kimi、自定义 |
| **Claude** | Anthropic Claude、自定义 |
| **Gemini** | Google Gemini、自定义 |
| **Ollama** | Ollama 本地服务、自定义 |

模型列表来自当前地址与密钥对应的服务；不支持列表接口时仍可手动填写。OpenAI 兼容列表可能包含非聊天模型，出现在列表中不保证能用于本应用；当前 OpenAI 适配器使用 Chat Completions。Gemini 列表只保留声明支持 `generateContent` 的模型，Ollama 列出本地已安装模型。

模型名称、图片能力及服务地址以实际部署或账户配置为准。**文字连接测试成功，不代表图片识别也已验证**；使用纯文字模型时，请关闭「发送已授权的截图」。

<details>
<summary><strong>连接电脑上的 Ollama</strong></summary>

手机中的 `localhost` 指手机本身。电脑已运行 Ollama、手机已开启 USB 调试并连接电脑时，可运行：

```sh
adb reverse tcp:11434 tcp:11434
```

在应用中选择 Ollama，地址填写 `http://localhost:11434`，模型名填写电脑上已安装的模型。

Android 模拟器也可使用 `http://10.0.2.2:11434`。当前应用只对本机及模拟器主机地址放行 HTTP，其他地址需要 HTTPS。

</details>

<a id="development"></a>

## 开发指南

### 构建 Android 应用

环境要求：**JDK 17 · Android SDK 34 · Build Tools 34.0.0**。项目自带 Gradle 8.2 Wrapper，也可直接在 Android Studio 中打开 `MyReaderApp`。

通过 `ANDROID_HOME` 或本机 `local.properties` 的 `sdk.dir` 指定 SDK 路径，然后执行：

```sh
cd MyReaderApp
./gradlew :app:assembleDebug :app:lintDebug
```

Windows 使用 `gradlew.bat`。构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`，可通过 ADB 安装：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 工作流程

```mermaid
flowchart LR
    A[点击朗读或悬浮球] --> B[读取屏幕文字]
    A --> C[可选的授权截图]
    B --> D[AiProvider 适配器]
    C --> D
    D --> E[配置的 AI 服务]
    E --> F[显示中文讲解]
    F --> G[系统 TTS 朗读]
```

业务层通过 `AiProvider` 调用模型；不同协议由各自的适配器处理。Windows 版使用「截屏 → 视觉模型 → 本地 TTS」流程。

### 目录导航

| 路径 | 内容 |
| :--- | :--- |
| [`MyReaderApp/`](MyReaderApp/) | 当前 Android 应用，包含界面、悬浮球与朗读流程 |
| [`MyReaderApp/app/src/main/java/com/example/myreader/ai/`](MyReaderApp/app/src/main/java/com/example/myreader/ai/) | 模型配置与协议适配器 |
| [`MyReaderApp/TESTING.md`](MyReaderApp/TESTING.md) | 构建、运行与验收步骤 |
| [`MyReaderApp/VALIDATION.md`](MyReaderApp/VALIDATION.md) | 实际测试结果与尚未验证的范围 |
| [`MyReaderDemo/`](MyReaderDemo/) | 早期 Android 原型，保留作参考 |
| [`read_screen.py`](read_screen.py) | Windows 截屏与朗读入口 |
| [`config.json`](config.json) | Windows 模型与快捷键配置 |

## 测试状态

以下为 **2026-10-11 的本地验证记录**，完整证据见 [VALIDATION.md](MyReaderApp/VALIDATION.md)。

| 验证范围 | 结果 |
| :--- | :--- |
| APK 构建与 Android Lint | 通过；0 个错误、46 个警告 |
| Android 14 安装、界面与配置入口 | 已验证 |
| 模型获取、搜索、切换保存与四种协议解析 | 已使用本地模拟接口验证 |
| 小屏幕、150% 字体与表单滚动 | 已验证 |
| 模拟接口返回、请求取消与 HTTP 401 | 已验证 |
| 连续截图与跨应用悬浮入口 | 已验证 |
| 真实模型理解效果、中文发声与真机后台稳定性 | **待验证** |

接口测试使用本地模拟服务返回固定讲解，不能据此评价 AI 的理解质量。

## 数据与权限

- **内容去向**：点击朗读后，屏幕文字和可选截图会发送到配置的服务。Android 首次朗读需要确认发送授权。
- **截图控制**：图片识别单独授权，也可以在模型设置中关闭图片发送。
- **敏感内容**：密码节点不参与文字收集，但截图尚未自动脱敏，请避开敏感页面。
- **密钥存储**：Android 密钥保存在应用私有 SharedPreferences 中，尚未使用 Keystore 加密。
- **功能边界**：当前只提供朗读，不执行自动点击、填表或支付；悬浮球尚不保证长期后台驻留。

## 常见问题

<details>
<summary><strong>有讲解文字，但没有声音？</strong></summary>

检查手机是否已配置支持中文的 TTS 引擎与语音数据，并确认媒体音量。当前测试模拟器没有配置 TTS 引擎，因此实际中文发声仍需真机验证。

</details>

<details>
<summary><strong>为什么需要“屏幕录制”授权？</strong></summary>

首页的「图片识别」使用系统屏幕捕获能力，因此会出现录制或投屏授权窗口。不授权图片识别时，仍可通过无障碍服务获取文字。

</details>

<details>
<summary><strong>连接失败，或者模型不能识别图片？</strong></summary>

先核对模型名称、服务地址和密钥，再使用「测试连接」。文字连接测试不包含图片；如果模型不支持视觉输入，请关闭截图发送。错误信息会在界面显示。

</details>

<details>
<summary><strong>为什么悬浮球退出应用后消失？</strong></summary>

当前悬浮球由 Activity 管理，退出或系统销毁 Activity 后会被移除。重新打开应用并开启悬浮朗读；长期后台运行仍是后续改进项。

</details>

## 后续计划

- [ ] 完成真实模型、中文 TTS 与目标手机的端到端验收
- [ ] 完善 TalkBack 导航、横竖屏切换与后台稳定性
- [ ] 加入截图脱敏与更完善的密钥保护
- [ ] 优化长页面摘要与连续朗读体验
- [ ] 探索离线模型与可控的助手操作能力

<details>
<summary><strong>参考项目</strong></summary>

原项目整理的相关项目链接：

- [read-screen](https://github.com/wangz-code/read-screen)
- [screen-speak](https://github.com/Lekonidus/screen-speak)
- [ScreenGPT-OCR](https://github.com/Chiwai15/ScreenGPT-OCR)
- [UGTLive](https://github.com/SethRobinson/UGTLive)
- [overlay-translator](https://github.com/ciddwd/overlay-translator)

</details>

---

<div align="center">

**万物皆可读 · read-anything**

许可证尚未确定。

[界面预览](#preview) · [安装使用](#quick-start) · [验证记录](MyReaderApp/VALIDATION.md)

</div>
