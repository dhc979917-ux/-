package com.example.myreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myreader.ai.ModelConfigStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var consentDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReaderRuntime.initialize(this)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            Toast.makeText(this, "请找到万物皆可读并开启无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.btnFloating).setOnClickListener {
            val service = ReaderService.instance
            if (service == null) {
                ReaderRuntime.say("请先开启无障碍服务")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else if (!ReaderRuntime.state.value.overlay && !hasConsent()) {
                requestConsent { service.toggleFloating() }
            } else service.toggleFloating()
        }
        findViewById<Button>(R.id.btnRead).setOnClickListener {
            val config = ModelConfigStore.读取(this)
            if (config.模型名.isBlank() || config.baseUrl.isBlank() ||
                (config.提供商 != "ollama" && config.apiKey.isBlank())) {
                ReaderRuntime.say("请先配置 AI 模型")
                startActivity(Intent(this, SettingsActivity::class.java))
            } else if (!hasConsent()) requestConsent()
            else ReaderRuntime.read()
        }
        findViewById<View>(R.id.btnVoice).setOnClickListener { ReaderRuntime.beginVoice(this) }
        findViewById<View>(R.id.btnCapture).setOnClickListener {
            if (ReaderRuntime.screenshots.是否已授权()) {
                ReaderRuntime.screenshots.释放()
                it.postDelayed({ refreshPermissions() }, 300)
            } else ReaderRuntime.screenshots.请求授权(this)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener { ReaderRuntime.stop(announce = true) }
        findViewById<Button>(R.id.btnReplay).setOnClickListener { ReaderRuntime.replay() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ReaderRuntime.state.collect { state ->
                    findViewById<TextView>(R.id.tvStatus).text = state.status
                    findViewById<TextView>(R.id.tvResult).text = state.result.ifBlank { "还没有朗读内容" }
                    findViewById<TextView>(R.id.tvVoiceCommand).text = state.command.takeIf { it.isNotBlank() }?.let { "上次口令：$it" }.orEmpty()
                    findViewById<Button>(R.id.btnRead).isEnabled = !state.busy
                    findViewById<Button>(R.id.btnReplay).isEnabled = !state.busy && state.result.isNotBlank()
                    findViewById<View>(R.id.progressReading).visibility = if (state.busy) View.VISIBLE else View.INVISIBLE
                    refreshPermissions()
                }
            }
        }
        handleConsentIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleConsentIntent()
    }

    private fun handleConsentIntent() {
        if (intent.getBooleanExtra("request_screen_consent", false)) {
            intent.removeExtra("request_screen_consent")
            findViewById<View>(R.id.btnRead).post { if (!hasConsent()) requestConsent() }
        }
    }

    private fun hasConsent() = getSharedPreferences("app_settings", MODE_PRIVATE)
        .getBoolean("screen_upload_consent", false)

    private fun requestConsent(onGranted: (() -> Unit)? = null) {
        if (consentDialog?.isShowing == true) return
        val config = ModelConfigStore.读取(this)
        consentDialog = AlertDialog.Builder(this)
            .setTitle("允许发送屏幕内容？")
            .setMessage("点击朗读或发出朗读口令后，选定区域的文字及已授权截图将发送至你配置的 AI 服务：${Uri.parse(config.baseUrl).host ?: "尚未配置"}。语音导航在本机执行。")
            .setNegativeButton("暂不允许", null)
            .setPositiveButton("允许") { _, _ ->
                getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                    .putBoolean("screen_upload_consent", true).apply()
                ReaderRuntime.say("已允许，请在目标页面再次发起朗读")
                onGranted?.invoke()
            }.show()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        findViewById<TextView>(R.id.tvAccessibilityState).text =
            if (ReaderService.instance != null) "已开启" else "未开启 · 前往授权"
        findViewById<TextView>(R.id.tvCaptureState).text =
            if (ReaderRuntime.screenshots.是否已授权()) "已授权 · 点击关闭" else "可选 · 尚未授权"
        findViewById<TextView>(R.id.tvFloatingState).text =
            if (ReaderRuntime.state.value.overlay) "已显示 · 点击隐藏" else "未显示"
        val config = ModelConfigStore.读取(this)
        findViewById<TextView>(R.id.tvModel).text =
            if (config.apiKey.isNotBlank() || config.提供商 == "ollama") config.模型名 else "模型未配置"
    }

    @Deprecated("MediaProjection authorization callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ScreenshotHelper.授权请求码) {
            if (resultCode == RESULT_OK && data != null) {
                ReaderRuntime.screenshots.处理授权结果(resultCode, data)
                findViewById<View>(R.id.tvStatus).postDelayed({ refreshPermissions() }, 600)
            } else ReaderRuntime.say("未授权图片识别，将只朗读文字")
        }
    }

    override fun onDestroy() {
        consentDialog?.dismiss()
        // 悬浮控件、播放与截图由无障碍服务管理，首页退出不会中断跨应用使用。
        super.onDestroy()
    }
}
