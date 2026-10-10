package com.example.myreaderdemo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// ⚠️ 隐私提醒：Demo 会把屏幕文字发给你在「AI 设置」里配置的服务商，正式产品需要加隐私保护（脱敏、本地模型或用户授权协议）
class MainActivity : AppCompatActivity() {

    private lateinit var tts: TtsHelper
    private lateinit var windowManager: WindowManager
    private var 悬浮球: View? = null

    // 记录手势起点，用于区分"点击"和"拖动"
    private var 按下X = 0f
    private var 按下Y = 0f
    private var 初始X = 0
    private var 初始Y = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TtsHelper(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // AI 配置存在 SharedPreferences：初始化一次全局上下文，之后随时换服务商不用重新编译
        AiConfig.init(applicationContext)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            // 无障碍服务不允许代码直接开启（防恶意软件），只能引导用户去系统设置
            Toast.makeText(this, "请找到'万物皆可读 Demo'并开启", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnFloating).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                显示悬浮球()
            } else {
                // 悬浮窗权限同样只能跳设置页让用户手动授权
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        findViewById<Button>(R.id.btnRead).setOnClickListener { 朗读屏幕() }

        findViewById<Button>(R.id.btnSettings).setOnClickListener { AiSettingsDialog.显示(this) }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页返回时在这里刷新状态：instance 非空即服务已存活
        findViewById<TextView>(R.id.tvStatus).text =
            if (ReaderService.instance != null) "✅ 无障碍服务已开启" else "❌ 请先开启无障碍服务"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun 显示悬浮球() {
        if (悬浮球 != null) return

        val ball = layoutInflater.inflate(R.layout.floating_ball, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_APPLICATION_OVERLAY：Android 8.0+ 的标准悬浮窗类型
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢焦点：悬浮球不能挡住用户对底下 App 的操作
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        ball.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    按下X = event.rawX
                    按下Y = event.rawY
                    初始X = params.x
                    初始Y = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 拖动 = 手指位移加到窗口坐标上，实时刷新位置
                    params.x = 初始X + (event.rawX - 按下X).toInt()
                    params.y = 初始Y + (event.rawY - 按下Y).toInt()
                    windowManager.updateViewLayout(ball, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 位移很小视为点击（拖动时手指难免微动，给 20px 容差）
                    val moved = abs(event.rawX - 按下X) + abs(event.rawY - 按下Y)
                    if (moved < 20) 朗读屏幕()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(ball, params)
        悬浮球 = ball
    }

    private fun 朗读屏幕() {
        val service = ReaderService.instance
        if (service == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        val 屏幕文字 = service.读取当前屏幕()
        if (屏幕文字 == "无法读取屏幕") {
            Toast.makeText(this, "没读到内容", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在理解...", Toast.LENGTH_SHORT).show()
        // lifecycleScope.launch 在主线程启动；网络部分由 AiClient 内部切到 IO 线程
        lifecycleScope.launch {
            val 讲解 = AiClient.理解屏幕(屏幕文字)
            tts.朗读(讲解)
        }
    }

    override fun onDestroy() {
        // 防窗口泄漏：退出前必须移除悬浮球并释放 TTS
        悬浮球?.let { windowManager.removeView(it) }
        悬浮球 = null
        tts.释放()
        super.onDestroy()
    }
}
