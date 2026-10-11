package com.example.myreader

enum class ReadRegion { ALL, TOP, BOTTOM, FOCUS }
enum class Navigation { NEXT, PREVIOUS, BACK, HOME, RECENTS }

sealed class VoiceCommand {
    data class Read(val instruction: String, val region: ReadRegion = ReadRegion.ALL) : VoiceCommand()
    data class Navigate(val direction: Navigation) : VoiceCommand()
    data class OpenApp(val label: String) : VoiceCommand()
    data class Click(val label: String) : VoiceCommand()
    data class Rate(val delta: Float) : VoiceCommand()
    object Stop : VoiceCommand()
    object Replay : VoiceCommand()
    object Confirm : VoiceCommand()
    object Help : VoiceCommand()
    object Unknown : VoiceCommand()
}

object VoiceCommands {
    fun normalize(text: String): String = text.trim().lowercase()
        .replace(Regex("[\\s，。！？、,.!?]"), "")

    fun parse(raw: String): VoiceCommand {
        val text = normalize(raw).removePrefix("请").removePrefix("帮我")
        return when (text) {
            "停止", "停止朗读", "停止播放", "取消", "取消操作", "别读了" -> VoiceCommand.Stop
            "重播", "再读一遍", "重复朗读" -> VoiceCommand.Replay
            "确认", "确认执行", "确认点击" -> VoiceCommand.Confirm
            "帮助", "语音帮助", "有哪些口令" -> VoiceCommand.Help
            "慢一点", "读慢一点", "说慢一点" -> VoiceCommand.Rate(-0.15f)
            "快一点", "读快一点", "说快一点" -> VoiceCommand.Rate(0.15f)
            "向上滑动", "上滑", "向上滑", "下一页", "向下滚动", "往下翻" -> VoiceCommand.Navigate(Navigation.NEXT)
            "向下滑动", "下滑", "向下滑", "上一页", "向上滚动", "往上翻" -> VoiceCommand.Navigate(Navigation.PREVIOUS)
            "返回", "返回上一页", "后退", "退出软件", "退出应用" -> VoiceCommand.Navigate(Navigation.BACK)
            "回到桌面", "返回桌面", "回桌面", "主页" -> VoiceCommand.Navigate(Navigation.HOME)
            "最近应用", "最近任务", "切换应用" -> VoiceCommand.Navigate(Navigation.RECENTS)
            else -> when {
                text.startsWith("打开") && text.length > 2 -> VoiceCommand.OpenApp(rawLabel(raw, "打开"))
                text.startsWith("点击") && text.length > 2 -> VoiceCommand.Click(rawLabel(raw, "点击"))
                text.startsWith("点按") && text.length > 2 -> VoiceCommand.Click(rawLabel(raw, "点按"))
                listOf("读", "朗读", "念", "总结", "概括", "解释", "描述", "讲解", "只读", "只念").any { text.startsWith(it) } -> {
                    val region = when {
                        listOf("上半", "顶部", "上面").any { text.contains(it) } -> ReadRegion.TOP
                        listOf("下半", "底部", "下面").any { text.contains(it) } -> ReadRegion.BOTTOM
                        listOf("焦点", "选中", "这里").any { text.contains(it) } -> ReadRegion.FOCUS
                        else -> ReadRegion.ALL
                    }
                    VoiceCommand.Read(raw.trim().take(500), region)
                }
                else -> VoiceCommand.Unknown
            }
        }
    }

    private fun rawLabel(text: String, verb: String): String = text.substringAfter(verb)
        .trim().trimEnd('。', '！', '？', '.', '!', '?')

    const val HELP = "可以说：读整个页面、读上半部分、读当前焦点、只读价格、总结一下、读慢一点、停止朗读。" +
        "也可以说：向上滑动、向下滑动、返回、回到桌面、最近应用、打开设置、点击按钮名称。点击前会请你确认。"
}
