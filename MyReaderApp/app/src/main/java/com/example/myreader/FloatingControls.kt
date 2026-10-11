package com.example.myreader

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingControls(private val service: ReaderService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        val controls = LayoutInflater.from(service).inflate(R.layout.floating_ball, null)
        val dp = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams((68 * dp).toInt(), (136 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }
        val read = controls.findViewById<View>(R.id.floatingRead)
        read.setOnClickListener {
            if (ReaderRuntime.state.value.busy || ReaderRuntime.state.value.speaking) ReaderRuntime.stop(announce = true)
            else ReaderRuntime.read()
        }
        val voice = controls.findViewById<View>(R.id.floatingVoice)
        voice.setOnClickListener { ReaderRuntime.beginVoice(service) }
        makeDraggable(read, controls, params)
        makeDraggable(voice, controls, params)
        manager.addView(controls, params)
        view = controls
        ReaderRuntime.overlayVisible(true)
        render(ReaderRuntime.state.value)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(target: View, controls: View, params: WindowManager.LayoutParams) {
        var startX = 0f
        var startY = 0f
        var originalX = 0
        var originalY = 0
        var moved = false
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        target.setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    originalX = params.x; originalY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) + abs(event.rawY - startY) > slop) moved = true
                    if (moved) {
                        val metrics = service.resources.displayMetrics
                        params.x = (originalX + (event.rawX - startX).toInt()).coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
                        params.y = (originalY + (event.rawY - startY).toInt()).coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
                        manager.updateViewLayout(controls, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) button.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    fun render(state: ReaderState) {
        val controls = view ?: return
        controls.visibility = if (ReaderRuntime.voiceSession != null) View.GONE else View.VISIBLE
        controls.findViewById<View>(R.id.floatingProgress).visibility = if (state.busy) View.VISIBLE else View.GONE
        controls.findViewById<TextView>(R.id.floatingLabel).text = if (state.busy || state.speaking) "停" else "读"
        controls.findViewById<View>(R.id.floatingRead).contentDescription = when {
            state.busy -> "AI 正在理解屏幕，点击停止"
            state.speaking -> "正在朗读，点击停止"
            else -> "朗读当前屏幕"
        }
    }

    fun hide() {
        view?.let { manager.removeView(it) }
        view = null
        ReaderRuntime.overlayVisible(false)
    }
}
