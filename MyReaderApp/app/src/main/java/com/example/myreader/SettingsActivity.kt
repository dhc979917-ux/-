package com.example.myreader

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myreader.ai.AiProviderFactory
import com.example.myreader.ai.ModelConfig
import com.example.myreader.ai.ModelConfigStore
import com.example.myreader.ai.默认配置
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgMode: RadioGroup
    private lateinit var rgLevel: RadioGroup
    private lateinit var spProvider: Spinner
    private lateinit var etModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var tvApiKeyLabel: TextView

    // 程序化创建的 RadioButton 的 id 集合，保存时用来反查选中的枚举序号
    private val 模式按钮 = mutableListOf<Int>()
    private val 权限按钮 = mutableListOf<Int>()

    private var 当前预设 = 默认配置.提供商列表.first()
    private var 由程序设置 = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "设置"

        rgMode = findViewById(R.id.rgMode)
        rgLevel = findViewById(R.id.rgLevel)
        spProvider = findViewById(R.id.spProvider)
        etModel = findViewById(R.id.etModel)
        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        tvApiKeyLabel = findViewById(R.id.tvApiKeyLabel)

        初始化模式与权限()
        初始化模型配置()

        findViewById<Button>(R.id.btnTest).setOnClickListener { 测试连接() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { 保存() }
    }

    private fun 初始化模式与权限() {
        // 单选项文案来自 arrays.xml，顺序与 Mode / OperationLevel 枚举一一对应
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val mode = prefs.getInt("mode", Mode.READ.ordinal)
        val level = prefs.getInt("level", OperationLevel.NONE.ordinal)

        resources.getStringArray(R.array.mode_names).forEachIndexed { i, name ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                isChecked = i == mode
            }
            模式按钮.add(rb.id)
            rgMode.addView(rb)
        }

        resources.getStringArray(R.array.operation_level_names).forEachIndexed { i, name ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                isChecked = i == level
            }
            权限按钮.add(rb.id)
            rgLevel.addView(rb)
        }
    }

    private fun 初始化模型配置() {
        val config = ModelConfigStore.读取(this)

        // 回填当前配置：只想换模型时，地址和密钥不用重填
        etModel.setText(config.模型名)
        etApiKey.setText(config.apiKey)
        etBaseUrl.setText(config.baseUrl)

        val names = 默认配置.提供商列表.map { it.名称 }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spProvider.adapter = adapter

        // 已保存配置反推选中项：类型和地址都对上才算命中；地址为空取同类型第一个预设
        var index = if (config.baseUrl.isBlank()) {
            默认配置.提供商列表.indexOfFirst { it.类型 == config.提供商 }
        } else {
            默认配置.提供商列表.indexOfFirst { it.类型 == config.提供商 && it.baseUrl == config.baseUrl }
        }
        if (index < 0) index = 默认配置.提供商列表.indexOfFirst { it.名称 == "自定义" }
        if (index < 0) index = 0
        spProvider.setSelection(index)
        当前预设 = 默认配置.提供商列表[index]
        刷新密钥可见性()

        spProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                当前预设 = 默认配置.提供商列表[position]
                if (当前预设.baseUrl.isNotEmpty() && !由程序设置) {
                    // 切换提供商 → 自动填默认地址和模型；密钥不覆盖（预设不知道你的密钥）
                    etBaseUrl.setText(当前预设.baseUrl)
                    etModel.setText(当前预设.默认模型)
                }
                刷新密钥可见性()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // setSelection 会触发一次监听回调，用标志位避免这次回调覆盖已回填的配置
        spProvider.post { 由程序设置 = false }
    }

    private fun 刷新密钥可见性() {
        // Ollama 跑在本地不需要密钥，直接隐藏密钥输入
        val 需要密钥 = 当前预设.类型 != "ollama"
        tvApiKeyLabel.visibility = if (需要密钥) View.VISIBLE else View.GONE
        etApiKey.visibility = if (需要密钥) View.VISIBLE else View.GONE
    }

    private fun 组装配置(): ModelConfig {
        return ModelConfig(
            提供商 = 当前预设.类型,
            模型名 = etModel.text.toString().trim(),
            apiKey = etApiKey.text.toString().trim(),
            baseUrl = etBaseUrl.text.toString().trim(),
            支持图片 = 当前预设.支持图片,
        )
    }

    private fun 测试连接() {
        val config = 组装配置()
        if (config.模型名.isEmpty() || config.baseUrl.isEmpty()) {
            Toast.makeText(this, "请先填好地址和模型名", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在测试...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            // 测试走完整 Provider 链路：能测通就说明朗读也能用
            val result = AiProviderFactory.创建(config).测试连接()
            val msg = result.fold(
                onSuccess = { "✅ 连接成功" },
                onFailure = { "❌ 测试失败：${it.message}" }
            )
            Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun 保存() {
        val config = 组装配置()
        if (config.模型名.isEmpty() || config.baseUrl.isEmpty()) {
            Toast.makeText(this, "地址和模型名必填", Toast.LENGTH_SHORT).show()
            return
        }
        ModelConfigStore.保存(this, config)

        // 模式与权限一并落盘（Demo 阶段实际只生效"朗读模式 + 不操作"，其余为预留）
        val editor = getSharedPreferences("app_settings", MODE_PRIVATE).edit()
        模式按钮.indexOf(rgMode.checkedRadioButtonId).takeIf { it >= 0 }?.let { editor.putInt("mode", it) }
        权限按钮.indexOf(rgLevel.checkedRadioButtonId).takeIf { it >= 0 }?.let { editor.putInt("level", it) }
        editor.apply()

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
