# Android Demo 运行与验收

本轮改动集中在 `MyReaderApp`。`MyReaderDemo` 与 Windows 版保留原样。

## 构建

需要 JDK 17、Android SDK Platform 34 和 Build Tools 34.0.0。项目自带 Gradle 8.2 Wrapper。

```sh
cd MyReaderApp
./gradlew :app:assembleDebug :app:lintDebug
```

将 SDK 目录写入本机 `local.properties` 的 `sdk.dir`，或设置 `ANDROID_HOME`。
Windows 使用 `gradlew.bat`。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 首次运行

1. 在 AI 设置中选择服务，填写地址和密钥，点击「获取模型」并搜索选择，或手动填写模型 ID，测试文字连接并保存。
2. 开启文字读取，并在系统无障碍设置中授权“万物皆可读”。
3. 图片识别单独授权；仅文字模型应关闭“发送已授权的截图”。
4. 显示悬浮球，首次朗读确认内容发送授权，再切换到目标页面点击悬浮球。
5. 返回应用查看讲解，可停止或再次朗读。
6. 首页或悬浮麦克风可启动一次语音输入；先确认语音数据说明并授权麦克风。没有语音识别服务时可输入口令。

模型预设来自原 Demo，不保证服务商仍提供这些模型。优先获取账户对应的模型列表；列表不可用时按实际模型 ID 填写。列表可能包含非聊天模型，需测试连接；文字连接测试不代表图片能力已通过验证。

各服务商独立保存一组配置，设置页内切换保留草稿，退出不保存则放弃草稿。「自定义」可选择四种协议之一，保存后重新进入应保持协议与地址。

## 本机 Ollama

手机中的 localhost 指手机本身。通过 USB 连接电脑后运行：

```sh
adb reverse tcp:11434 tcp:11434
```

然后选择 Ollama，将地址设为 `http://localhost:11434`，填写电脑上已安装的模型名。
模拟器也可以使用 `http://10.0.2.2:11434`。其他地址需要 HTTPS。

## 验收场景

| 场景 | 预期 |
| --- | --- |
| 未配置模型就朗读 | 引导进入 AI 设置，不发送请求 |
| 空密钥、空模型或非法地址 | 输入框显示错误，不能保存 |
| 获取模型与搜索 | 不要求提前填写模型 ID；去重并显示接口模型，搜索忽略大小写，选中后填入模型 ID |
| 列表接口不存在、无权限、空列表或错误格式 | 显示对应状态，恢复获取按钮，保留已填写模型，允许手动配置 |
| 获取过程中切换服务或修改地址、密钥 | 取消旧请求，清空旧列表，迟到响应不得覆盖新配置 |
| 来回切换服务商 | 草稿地址、密钥和模型不会相互覆盖；分别保存后退出重进仍可恢复 |
| 自定义服务保存后重新进入 | 保持所选协议，不自动变成 OpenAI |
| 服务不可达或返回 HTTP 401/500 | 展示失败，恢复按钮，不把错误当作讲解 |
| 连续点击朗读 | 只保留一个正在执行的请求 |
| 请求过程中停止 | 取消 HTTP 请求，迟到结果不得自动播放 |
| 正常返回 | 展示完整讲解，尝试中文 TTS，允许重播 |
| 缺少中文语音引擎 | 给出提示，仍保留文字结果 |
| 拒绝截图授权 | 仍可通过无障碍文字朗读 |
| Android 14 连续两次截图 | 复用同一虚拟显示会话，不重复创建 |
| 系统停止屏幕共享 | 授权失效，可重新申请 |
| 系统字体放大、横竖屏切换 | 页面可滚动，按钮可达；已返回的结果保留 |
| TalkBack | 悬浮球有朗读标签，支持无障碍点击 |
| 悬浮读取中的反馈 | 白色加载环持续旋转，标签变为“停”，触摸或 TalkBack 点击可取消请求 |
| 跨应用语音朗读 | 读上半部分、读下半部分、读当前焦点、只读价格、总结等口令传入对应区域与朗读要求 |
| 语音导航 | 上滑、下滑、返回、回桌面、最近应用、按完整应用名打开均执行单步操作 |
| 语音点击 | 按完整名称精确匹配；多个同名目标拒绝执行；确认后点击，30 秒超时或页面变化则取消 |
| 语音异常 | 拒绝麦克风、无识别服务、识别超时、网络失败、低置信度均提示并允许重试，不擅自操作 |
| 关闭语音或切到其他应用 | 立即取消识别、释放麦克风；不持续监听 |
| 关闭首页后使用悬浮入口 | 无障碍服务仍管理悬浮入口，可在其他应用继续发出口令 |

## 范围与限制

- 当前支持语音朗读与明确的单步导航、确认后点击；尚无自主多步任务、自动填表或支付流程。
- 悬浮入口由无障碍服务管理；关闭首页不再主动移除，但服务关闭、进程终止或厂商后台限制仍会影响使用。
- 截图会话中旋转屏幕可能保留原比例，需要重新授权；需在目标设备复测。
- 密码节点不参与文字收集，但截图没有自动脱敏，不应在敏感页面使用。
- API 密钥存于应用私有 SharedPreferences，尚未使用 Keystore 加密；已关闭常规备份。
- Android 7、厂商后台限制、真实云端模型与中文语音效果均需真机验收。

实际执行记录见 `VALIDATION.md`，未执行的项目不计为通过。

## 语音自动化验证

使用专用 Android 14 模拟器，不在个人手机上运行。测试会重连无障碍服务、临时配置模型与麦克风权限。测试语音服务和测试页面只存在于 `androidTest` APK，不会包含在 `MyReader-debug.apk` 中。

先在一个终端运行本地模拟接口：

```sh
node tools/voice-mock.cjs
```

另一个终端执行（以下假设测试模拟器编号为 `emulator-5554`）：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 reverse tcp:11434 tcp:11435
adb -s emulator-5554 shell settings get secure voice_recognition_service
adb -s emulator-5554 shell settings put secure voice_recognition_service com.example.myreader.test/com.example.myreader.MockRecognitionService
adb -s emulator-5554 shell am instrument -w com.example.myreader.test/com.example.myreader.VoiceSmokeInstrumentation
```

记录并恢复原来的 `voice_recognition_service`；原值为 `null` 时用 `settings delete secure voice_recognition_service` 恢复。退出模拟接口进程并移除测试 APK。只有输出各项 `PASS` 且没有 `FAIL` 才算通过，不能只看 ADB 退出码。

覆盖语音识别回调到朗读及操作的链路，采用模拟转录和 AI 响应；真实麦克风识别率、中文发声、TalkBack 协同和不同品牌手机仍需单独验收。
