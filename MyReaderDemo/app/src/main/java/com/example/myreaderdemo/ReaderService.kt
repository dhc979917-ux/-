package com.example.myreaderdemo

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReaderService : AccessibilityService() {

    companion object {
        // 用静态单例暴露"活着的服务实例"，让悬浮球/主界面能随时调用读屏
        // （无障碍服务由系统绑定生命周期，普通 bindService 拿不到它）
        var instance: ReaderService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        // 服务被关闭时清空引用：一是防内存泄漏，二是让界面能判断服务是否存活
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Demo 阶段采用"拉模式"：不监听事件流，由悬浮球点击时主动读取当前屏幕
    }

    override fun onInterrupt() {
        // 系统回调要求必须实现，Demo 中无需处理
    }

    fun 读取当前屏幕(): String {
        val root = rootInActiveWindow ?: return "无法读取屏幕"
        val builder = StringBuilder()
        收集节点文字(root, builder)
        val text = builder.toString().trim()
        return if (text.isEmpty()) "无法读取屏幕" else text
    }

    // 递归遍历节点树：text 是控件文字，contentDescription 是图片/按钮的语义描述
    // （对读屏来说后者往往更重要，图标本身没有 text）
    private fun 收集节点文字(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let { builder.append(it).append('\n') }
        node.contentDescription?.let { builder.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { 收集节点文字(it, builder) }
        }
    }
}
