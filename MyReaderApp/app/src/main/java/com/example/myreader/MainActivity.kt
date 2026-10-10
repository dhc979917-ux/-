package com.example.myreader

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
import com.example.myreader.ai.AiProviderFactory
import com.example.myreader.ai.ModelConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ⚠️ 隐私提醒：Demo 会把屏幕文字和截图发给你配置的 AI 服务，正式产品需要加隐私保护（脱敏、本地模型或用户授权协议）
class MainActivity : AppCompatActivity() {

    private lateinit var tts: TtsHelper
    private lateinit var windowManager: WindowManager
    private lateinit var screenshotHelper: ScreenshotHelper
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
        screenshotHelper = ScreenshotHelper(this)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            // 无障碍服务不允许代码直接开启（防恶意软件），只能引导用户去系统设置
            Toast.makeText(this, "请找到'万物皆可读'并开启", Toast.LENGTH_LONG).show()
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

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页返回时刷新状态：instance 非空即服务已存活
        findViewById<TextView>(R.id.tvStatus).text =
            if (ReaderService.instance != null) "✅ 无障碍服务已开启" else "❌ 请先开启无障碍服务"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 屏幕录制授权结果：转给 ScreenshotHelper 启动前台服务并缓存投影
        if (requestCode == ScreenshotHelper.授权请求码) {
            if (resultCode == RESULT_OK && data != null) {
                screenshotHelper.处理授权结果(resultCode, data)
                Toast.makeText(this, "屏幕识别已就绪", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未授权屏幕录制，将只朗读文字", Toast.LENGTH_SHORT).show()
            }
        }
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
        val 截图可用 = screenshotHelper.是否已授权()
        if (屏幕文字 == "无法读取屏幕" && !截图可用) {
            Toast.makeText(this, "没读到内容", Toast.LENGTH_SHORT).show()
            return
        }
        if (!截图可用) {
            // 首次使用引导授权屏幕录制；本次先用纯文字朗读，不影响体验，下次点球就带截图
            screenshotHelper.请求授权(this)
        }

        // 每次朗读都从本地读取配置 → 通过工厂拿到当前选中的 Provider，业务层不认识任何具体厂商
        val config = ModelConfigStore.读取(this)
        Toast.makeText(this, "正在理解...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            // 截图放 IO 线程：里面有 Thread.sleep，主线程做会卡界面
            val 截图Base64 = if (截图可用) {
                withContext(Dispatchers.IO) {
                    screenshotHelper.截图()?.let { screenshotHelper.转Base64(it) }
                }
            } else {
                null
            }

            val 文字 = if (屏幕文字 == "无法读取屏幕") "" else 屏幕文字
            val provider = AiProviderFactory.创建(config)
            val 讲解 = provider.理解屏幕(文字, 截图Base64)
            tts.朗读(讲解)
        }
    }

    override fun onDestroy() {
        // 防窗口泄漏：退出前移除悬浮球、释放 TTS 和屏幕投影
        悬浮球?.let { windowManager.removeView(it) }
        悬浮球 = null
        tts.释放()
        screenshotHelper.释放()
        super.onDestroy()
    }
}
