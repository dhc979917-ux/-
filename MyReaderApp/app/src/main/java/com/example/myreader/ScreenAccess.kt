package com.example.myreader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ScreenSnapshot(
    val key: String,
    val packageName: String,
    val text: String,
    val top: String,
    val bottom: String,
    val focus: String,
) {
    fun textFor(region: ReadRegion) = when (region) {
        ReadRegion.ALL -> text
        ReadRegion.TOP -> top
        ReadRegion.BOTTOM -> bottom
        ReadRegion.FOCUS -> focus
    }
}

class ScreenAccess(private val service: ReaderService) {
    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit, budget: IntArray = intArrayOf(1000)) {
        if (--budget[0] < 0 || node.isPassword || !node.isVisibleToUser) return
        visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { walk(child, visit, budget) } finally { child.recycle() }
        }
    }

    fun snapshot(): ScreenSnapshot? {
        val root = service.rootInActiveWindow ?: return null
        try {
            val bounds = Rect().also(root::getBoundsInScreen)
            val all = StringBuilder()
            val top = StringBuilder()
            val bottom = StringBuilder()
            val identity = StringBuilder()
            walk(root, { node ->
                val label = listOfNotNull(node.text, node.contentDescription).map { it.toString().trim() }
                    .filter { it.isNotBlank() }.distinct().joinToString(" ")
                if (label.isNotBlank() && all.length < 16000) {
                    val rect = Rect().also(node::getBoundsInScreen)
                    all.appendLine(label)
                    (if (rect.centerY() < bounds.centerY()) top else bottom).appendLine(label)
                    identity.append(label).append(rect.toShortString())
                }
            })
            val focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val focused = StringBuilder()
            if (focusNode != null) {
                try {
                    if (!hasPasswordAncestor(focusNode)) walk(focusNode, { node ->
                        if (focused.length < 16000) {
                            node.text?.let { focused.appendLine(it) }
                            node.contentDescription?.let { focused.appendLine(it) }
                        }
                    })
                } finally { focusNode.recycle() }
            }
            val packageName = root.packageName?.toString().orEmpty()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(identity.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            return ScreenSnapshot("$packageName:${root.windowId}:$digest", packageName,
                all.toString().trim(), top.toString().trim(), bottom.toString().trim(), focused.toString().trim())
        } finally { root.recycle() }
    }

    private fun hasPasswordAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        repeat(40) {
            val item = current ?: return false
            if (item.isPassword) { item.recycle(); return true }
            current = item.parent
            item.recycle()
        }
        current?.recycle()
        return true
    }

    fun click(label: String, execute: Boolean): String {
        val root = service.rootInActiveWindow ?: return "无法访问当前页面"
        val matches = linkedMapOf<String, AccessibilityNodeInfo>()
        try {
            walk(root, { node ->
                if (listOf(node.text, node.contentDescription).any {
                        it != null && VoiceCommands.normalize(it.toString()) == VoiceCommands.normalize(label)
                    }) {
                    var target: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                    var depth = 0
                    while (target != null && !target.isClickable && depth++ < 12) {
                        val parent = target.parent
                        target.recycle()
                        target = parent
                    }
                    if (target != null && target.isClickable && target.isEnabled && !hasPasswordAncestor(target)) {
                        val rect = Rect().also(target::getBoundsInScreen)
                        val key = "${target.windowId}:$rect"
                        if (matches.containsKey(key)) target.recycle() else matches[key] = target
                    } else target?.recycle()
                }
            })
            return when {
                matches.isEmpty() -> "没有找到可点击的“$label”"
                matches.size > 1 -> "页面中有多个“$label”，请先滑动到只有一个目标的位置"
                !execute -> ""
                matches.values.single().performAction(AccessibilityNodeInfo.ACTION_CLICK) -> "已点击“$label”"
                else -> "这个按钮不支持无障碍点击"
            }
        } finally {
            matches.values.forEach { it.recycle() }
            root.recycle()
        }
    }

    suspend fun navigate(direction: Navigation): String {
        val global = when (direction) {
            Navigation.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            Navigation.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            Navigation.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> null
        }
        if (global != null) return if (service.performGlobalAction(global)) when (direction) {
            Navigation.BACK -> "已返回"
            Navigation.HOME -> "已回到桌面"
            else -> "已打开最近应用"
        } else "系统未能完成这次操作"

        val root = service.rootInActiveWindow ?: return "无法访问当前页面"
        var scrollable: AccessibilityNodeInfo? = null
        var largest = 0L
        val bounds = Rect().also(root::getBoundsInScreen)
        try {
            walk(root, { node ->
                val rect = Rect().also(node::getBoundsInScreen)
                val area = rect.width().toLong() * rect.height()
                if (node.isScrollable && node.isEnabled && area > largest) {
                    scrollable?.recycle()
                    scrollable = AccessibilityNodeInfo.obtain(node)
                    largest = area
                }
            })
            val action = if (direction == Navigation.NEXT) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (scrollable?.performAction(action) == true) return "已滚动页面"
            // 存在标准滚动容器但拒绝滚动通常表示已到边界，不再对其他控件注入手势。
            if (scrollable != null) return "页面已到边界，或当前区域不能继续滚动"
        } finally { scrollable?.recycle(); root.recycle() }
        if (bounds.isEmpty) return "没有可滑动的页面"
        val x = bounds.exactCenterX()
        val low = bounds.top + bounds.height() * 0.78f
        val high = bounds.top + bounds.height() * 0.25f
        val path = Path().apply {
            moveTo(x, if (direction == Navigation.NEXT) low else high)
            lineTo(x, if (direction == Navigation.NEXT) high else low)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450)).build()
        val completed = suspendCancellableCoroutine<Boolean> { continuation ->
            val sent = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
            if (!sent && continuation.isActive) continuation.resume(false)
        }
        return if (completed) "已完成滑动手势" else "滑动已取消"
    }

    fun openApp(label: String): String {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = service.packageManager
        val matches = pm.queryIntentActivities(query, 0).filter {
            VoiceCommands.normalize(it.loadLabel(pm).toString()) == VoiceCommands.normalize(label)
        }.distinctBy { it.activityInfo.packageName }
        if (matches.isEmpty()) return "没有找到名为“$label”的应用，请说完整应用名称"
        if (matches.size > 1) return "找到多个同名应用，请从桌面选择"
        val intent = pm.getLaunchIntentForPackage(matches.single().activityInfo.packageName)
            ?: return "这个应用暂时不能打开"
        return try {
            service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "已请求打开“$label”"
        } catch (error: RuntimeException) { "系统没有允许打开这个应用" }
    }
}
