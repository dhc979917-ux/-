package com.example.myreader

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class ReaderService : AccessibilityService() {
    companion object {
        var instance: ReaderService? = null
            private set
    }

    lateinit var screen: ScreenAccess
        private set
    private var floating: FloatingControls? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        screen = ScreenAccess(this)
        ReaderRuntime.initialize(this)
        floating = FloatingControls(this)
        scope.launch { ReaderRuntime.state.collect { floating?.render(it) } }
        if (getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("floating_enabled", false)) {
            floating?.show()
        }
    }

    fun toggleFloating() {
        if (ReaderRuntime.state.value.overlay) floating?.hide() else floating?.show()
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
            .putBoolean("floating_enabled", ReaderRuntime.state.value.overlay).apply()
    }

    fun feedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(50)
    }

    fun 读取当前屏幕(): String = screen.snapshot()?.text?.ifBlank { "无法读取屏幕" } ?: "无法读取屏幕"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { ReaderRuntime.stop() }

    override fun onDestroy() {
        floating?.hide()
        floating = null
        scope.cancel()
        instance = null
        ReaderRuntime.serviceDisconnected()
        super.onDestroy()
    }
}
