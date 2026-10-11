package com.example.myreader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.myreader.ai.AiProviderFactory
import com.example.myreader.ai.ModelConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ⚠️ 隐私提醒：Demo 会把屏幕文字和截图发给你配置的 AI 服务，正式产品需要加隐私保护（脱敏、本地模型或用户授权协议）
class MainActivity : AppCompatActivity() {

    private lateinit var tts: TtsHelper
    private lateinit var windowManager: WindowManager
    private lateinit var screenshotHelper: ScreenshotHelper
    private var 悬浮球: View? = null
    private var readingJob: Job? = null
    private var lastResult = ""

    // 记录手势起点，用于区分"点击"和"拖动"
    private var 按下X = 0f
    private var 按下Y = 0f
    private var 初始X = 0
    private var 初始Y = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        tts = TtsHelper(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenshotHelper = ScreenshotHelper(this)

        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            // 无障碍服务不允许代码直接开启（防恶意软件），只能引导用户去系统设置
            Toast.makeText(this, "请找到'万物皆可读'并开启", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.btnFloating).setOnClickListener {
            if (悬浮球 != null) {
                windowManager.removeView(悬浮球)
                悬浮球 = null
                findViewById<TextView>(R.id.tvFloatingState).text = "未显示"
                return@setOnClickListener
            }
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
        findViewById<View>(R.id.btnCapture).setOnClickListener {
            if (screenshotHelper.是否已授权()) {
                screenshotHelper.释放()
                it.postDelayed({ 刷新状态() }, 300)
            } else {
                screenshotHelper.请求授权(this)
            }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            readingJob?.cancel()
            tts.停止()
            findViewById<TextView>(R.id.tvStatus).text = "已停止"
        }
        findViewById<Button>(R.id.btnReplay).setOnClickListener { tts.朗读(lastResult) }
        lastResult = savedInstanceState?.getString("lastResult").orEmpty()
        if (lastResult.isNotBlank()) {
            findViewById<TextView>(R.id.tvResult).text = lastResult
            findViewById<Button>(R.id.btnReplay).isEnabled = true
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        刷新状态()
    }

    private fun 刷新状态() {
        if (readingJob?.isActive != true) {
            findViewById<TextView>(R.id.tvStatus).text =
                if (ReaderService.instance != null) "文字读取已就绪" else "尚未开启文字读取"
        }
        findViewById<TextView>(R.id.tvAccessibilityState).text =
            if (ReaderService.instance != null) "已开启" else "未开启 · 前往授权"
        findViewById<TextView>(R.id.tvCaptureState).text =
            if (screenshotHelper.是否已授权()) "已授权 · 点击关闭" else "可选 · 尚未授权"
        findViewById<TextView>(R.id.tvFloatingState).text =
            if (悬浮球 != null) "已显示 · 点击隐藏" else "未显示"
        val config = ModelConfigStore.读取(this)
        findViewById<TextView>(R.id.tvModel).text =
            if (config.apiKey.isNotBlank() || config.提供商 == "ollama") config.模型名 else "模型未配置"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 屏幕录制授权结果：转给 ScreenshotHelper 启动前台服务并缓存投影
        if (requestCode == ScreenshotHelper.授权请求码) {
            if (resultCode == RESULT_OK && data != null) {
                screenshotHelper.处理授权结果(resultCode, data)
                Toast.makeText(this, "正在启动屏幕识别", Toast.LENGTH_SHORT).show()
                findViewById<TextView>(R.id.tvStatus).postDelayed({ 刷新状态() }, 600)
            } else {
                Toast.makeText(this, "未授权屏幕录制，将只朗读文字", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun 显示悬浮球() {
        if (悬浮球 != null) return

        val ball = layoutInflater.inflate(R.layout.floating_ball, null)
        val ballSize = (60 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            // TYPE_APPLICATION_OVERLAY：Android 8.0+ 的标准悬浮窗类型
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            // 不抢焦点：悬浮球不能挡住用户对底下 App 的操作
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        ball.setOnClickListener { 朗读屏幕() }
        ball.setOnTouchListener { view, event ->
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
                    params.x = (初始X + (event.rawX - 按下X).toInt())
                        .coerceIn(0, (resources.displayMetrics.widthPixels - ballSize).coerceAtLeast(0))
                    params.y = (初始Y + (event.rawY - 按下Y).toInt())
                        .coerceIn(0, (resources.displayMetrics.heightPixels - ballSize).coerceAtLeast(0))
                    windowManager.updateViewLayout(ball, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 位移很小视为点击（拖动时手指难免微动，给 20px 容差）
                    val moved = abs(event.rawX - 按下X) + abs(event.rawY - 按下Y)
                    if (moved < 20) view.performClick()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(ball, params)
        悬浮球 = ball
        findViewById<TextView>(R.id.tvFloatingState).text = "已显示 · 点击隐藏"
    }

    private fun 朗读屏幕() {
        if (readingJob?.isCompleted == false) return
        刷新状态()
        val config = ModelConfigStore.读取(this)
        if (config.模型名.isBlank() || config.baseUrl.isBlank() ||
            (config.提供商 != "ollama" && config.apiKey.isBlank())) {
            Toast.makeText(this, "请先配置 AI 服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("screen_upload_consent", false)) {
            AlertDialog.Builder(this)
                .setTitle("允许发送屏幕内容？")
                .setMessage("点击朗读后，当前屏幕文字及已授权的截图将发送至你配置的 AI 服务：${Uri.parse(config.baseUrl).host}。请避开密码、支付等敏感页面。")
                .setNegativeButton("暂不允许", null)
                .setPositiveButton("允许") { _, _ ->
                    prefs.edit().putBoolean("screen_upload_consent", true).apply()
                    Toast.makeText(this, "已允许，请在目标页面再次点击朗读", Toast.LENGTH_LONG).show()
                }.show()
            return
        }
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
        tts.停止()
        findViewById<TextView>(R.id.tvStatus).text = "正在理解屏幕…"
        findViewById<Button>(R.id.btnRead).isEnabled = false
        findViewById<Button>(R.id.btnReplay).isEnabled = false
        findViewById<View>(R.id.progressReading).visibility = View.VISIBLE
        readingJob = lifecycleScope.launch {
          try {
            // 截图放 IO 线程：里面有 Thread.sleep，主线程做会卡界面
            val 截图Base64 = if (截图可用 && config.支持图片) {
                withContext(Dispatchers.IO) {
                    screenshotHelper.截图()?.let {
                        try { screenshotHelper.转Base64(it) } finally { it.recycle() }
                    }
                }
            } else {
                null
            }

            val 文字 = if (屏幕文字 == "无法读取屏幕") "" else 屏幕文字
            check(文字.isNotBlank() || 截图Base64 != null) { "未获取到屏幕内容，请重新授权图片识别或切换页面" }
            val provider = AiProviderFactory.创建(config)
            val 讲解 = provider.理解屏幕(文字, 截图Base64)
            ensureActive()
            require(讲解.isNotBlank()) { "AI 返回了空内容，请重试" }
            lastResult = 讲解
            findViewById<TextView>(R.id.tvResult).text = 讲解
            findViewById<TextView>(R.id.tvStatus).text =
                if (截图Base64 == null) "讲解已返回 · 文字识别" else "讲解已返回 · 文字与图片"
            tts.朗读(讲解)
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            findViewById<TextView>(R.id.tvStatus).text = "本次朗读失败，请重试"
            findViewById<TextView>(R.id.tvResult).text = error.message ?: "无法完成朗读"
          } finally {
            findViewById<Button>(R.id.btnRead).isEnabled = true
            findViewById<Button>(R.id.btnReplay).isEnabled = lastResult.isNotBlank()
            findViewById<View>(R.id.progressReading).visibility = View.INVISIBLE
          }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("lastResult", lastResult)
        super.onSaveInstanceState(outState)
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
