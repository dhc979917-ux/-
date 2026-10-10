package com.example.myreader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReaderService : AccessibilityService() {

    companion object {
        // 静态单例暴露"活着的服务实例"：悬浮球/主界面随时可调（无障碍服务无法普通 bind 拿到）
        var instance: ReaderService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        // 服务被关闭时清空引用：防泄漏，同时让界面能判断服务是否存活
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Demo 采用"拉模式"：由悬浮球点击时主动读取，不监听事件流（助手模式才需要事件流）
    }

    override fun onInterrupt() {}

    fun 读取当前屏幕(): String {
        val root = rootInActiveWindow ?: return "无法读取屏幕"
        val builder = StringBuilder()
        收集节点文字(root, builder)
        val text = builder.toString().trim()
        return if (text.isEmpty()) "无法读取屏幕" else text
    }

    // ---- 以下两个方法为后续"操作能力"预留，0 级权限（只读）下不会被调用 ----

    fun 查找节点(条件: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return 递归查找(root, 条件)
    }

    fun 点击节点(节点: AccessibilityNodeInfo): Boolean {
        return 节点.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun 递归查找(node: AccessibilityNodeInfo, 条件: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (条件(node)) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                递归查找(child, 条件)?.let { return it }
            }
        }
        return null
    }

    // text 是控件文字，contentDescription 是图片/按钮的语义描述（读屏场景往往更重要）
    private fun 收集节点文字(node: AccessibilityNodeInfo, builder: StringBuilder) {
        node.text?.let { builder.append(it).append('\n') }
        node.contentDescription?.let { builder.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { 收集节点文字(it, builder) }
        }
    }
}
