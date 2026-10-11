package com.example.myreader

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myreader.ai.AiProviderFactory
import com.example.myreader.ai.ModelCatalog
import com.example.myreader.ai.ModelConfig
import com.example.myreader.ai.ModelConfigStore
import com.example.myreader.ai.默认配置
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ModelSettingsDrafts : ViewModel() {
    var selected = ""
    val profiles = mutableMapOf<String, ModelConfig>()
}

class SettingsActivity : AppCompatActivity() {
    private lateinit var spProvider: Spinner
    private lateinit var spProtocol: Spinner
    private lateinit var etModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var drafts: ModelSettingsDrafts
    private val protocols = listOf("openai", "claude", "gemini", "ollama")
    private var binding = false
    private var catalogJob: Job? = null
    private var testJob: Job? = null
    private var revision = 0
    private var catalogRevision = 0
    private var models = emptyList<String>()
    private var modelDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        drafts = ViewModelProvider(this)[ModelSettingsDrafts::class.java]
        spProvider = findViewById(R.id.spProvider)
        spProtocol = findViewById(R.id.spProtocol)
        etModel = findViewById(R.id.etModel)
        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        // 草稿由 ViewModel 保存；密钥不写入系统保存的界面状态。
        listOf<View>(spProvider, spProtocol, etModel, etApiKey, etBaseUrl,
            findViewById(R.id.switchImages)).forEach { it.isSaveEnabled = false }

        spProvider.adapter = spinnerAdapter(默认配置.提供商列表.map { it.名称 })
        spProtocol.adapter = spinnerAdapter(listOf("OpenAI 兼容", "Anthropic Claude", "Google Gemini", "Ollama"))
        if (drafts.selected.isEmpty()) {
            val config = ModelConfigStore.读取(this)
            drafts.selected = ModelConfigStore.预设名称(config)
            drafts.profiles[drafts.selected] = config.copy(预设名 = drafts.selected)
        }
        showProfile(drafts.selected)
        spProvider.onItemSelectedListener = selectedListener { position ->
            val name = 默认配置.提供商列表[position].名称
            if (name != drafts.selected) {
                rememberDraft()
                invalidateRequests(true)
                showProfile(name)
            }
        }
        spProtocol.onItemSelectedListener = selectedListener { position ->
            if (!binding && drafts.selected == "自定义" &&
                drafts.profiles[drafts.selected]?.提供商 != protocols[position]) {
                invalidateRequests(true)
                rememberDraft()
                refreshKeyVisibility()
            }
        }
        etBaseUrl.doAfterTextChanged { if (!binding) invalidateRequests(true) }
        etApiKey.doAfterTextChanged { if (!binding) invalidateRequests(true) }
        etModel.doAfterTextChanged { if (!binding) invalidateRequests(false) }
        findViewById<SwitchMaterial>(R.id.switchImages).setOnCheckedChangeListener { _, _ ->
            if (!binding) invalidateRequests(false)
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnFetchModels).setOnClickListener { fetchModels() }
        findViewById<Button>(R.id.btnChooseModel).setOnClickListener { chooseModel() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun spinnerAdapter(names: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun selectedListener(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun showProfile(name: String) {
        binding = true
        drafts.selected = name
        val preset = 默认配置.提供商列表.first { it.名称 == name }
        val config = drafts.profiles[name] ?: ModelConfigStore.读取预设(this, name)
            ?: ModelConfig(preset.类型, preset.默认模型, "", preset.baseUrl, preset.支持图片, name)
        drafts.profiles[name] = config
        spProvider.setSelection(默认配置.提供商列表.indexOf(preset))
        spProtocol.setSelection(protocols.indexOf(config.提供商).coerceAtLeast(0))
        findViewById<View>(R.id.protocolContainer).visibility = if (name == "自定义") View.VISIBLE else View.GONE
        etBaseUrl.setText(config.baseUrl.ifBlank { preset.baseUrl })
        etApiKey.setText(config.apiKey)
        etModel.setText(config.模型名)
        findViewById<SwitchMaterial>(R.id.switchImages).isChecked = config.支持图片
        listOf(etBaseUrl, etApiKey, etModel).forEach { it.error = null }
        refreshKeyVisibility()
        binding = false
    }

    private fun config() = ModelConfig(
        提供商 = if (drafts.selected == "自定义") protocols[spProtocol.selectedItemPosition]
            else 默认配置.提供商列表.first { it.名称 == drafts.selected }.类型,
        模型名 = etModel.text.toString().trim(),
        apiKey = etApiKey.text.toString().trim(),
        baseUrl = etBaseUrl.text.toString().trim().trimEnd('/'),
        支持图片 = findViewById<SwitchMaterial>(R.id.switchImages).isChecked,
        预设名 = drafts.selected,
    )

    private fun rememberDraft() { drafts.profiles[drafts.selected] = config() }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberDraft()
        super.onSaveInstanceState(outState)
    }

    private fun refreshKeyVisibility() {
        val visible = if (config().提供商 == "ollama") View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvApiKeyLabel).visibility = visible
        findViewById<View>(R.id.apiKeyContainer).visibility = visible
    }

    private fun invalidateRequests(clearModels: Boolean) {
        revision++
        testJob?.cancel()
        findViewById<Button>(R.id.btnTest).isEnabled = true
        findViewById<TextView>(R.id.tvConnectionStatus).text = ""
        if (clearModels) {
            catalogRevision++
            catalogJob?.cancel()
            models = emptyList()
            modelDialog?.dismiss()
            findViewById<Button>(R.id.btnFetchModels).isEnabled = true
            findViewById<Button>(R.id.btnChooseModel).isEnabled = false
            findViewById<TextView>(R.id.tvModelStatus).text = ""
        }
    }

    private fun fetchModels() {
        val current = config()
        if (!validate(current, requireModel = false)) return
        invalidateRequests(true)
        val requestRevision = catalogRevision
        val button = findViewById<Button>(R.id.btnFetchModels)
        val status = findViewById<TextView>(R.id.tvModelStatus)
        button.isEnabled = false
        status.text = "正在获取模型列表…"
        catalogJob = lifecycleScope.launch {
            try {
                val fetched = ModelCatalog.获取(current)
                if (requestRevision != catalogRevision) return@launch
                models = fetched
                status.text = if (models.isEmpty()) "服务未返回可选模型，可手动填写模型 ID"
                    else "已获取 ${models.size} 个模型"
                findViewById<Button>(R.id.btnChooseModel).isEnabled = models.isNotEmpty()
                if (models.isNotEmpty()) chooseModel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestRevision == catalogRevision) {
                    status.text = when (error) {
                        is java.net.SocketTimeoutException -> "获取超时，可重试或手动填写模型 ID"
                        is org.json.JSONException -> "返回格式不正确，请检查服务地址和协议"
                        is java.io.IOException -> "获取失败：${error.message?.take(150)}。可手动填写模型 ID"
                        else -> "获取失败，请检查配置或手动填写模型 ID"
                    }
                }
            } finally {
                if (requestRevision == catalogRevision) button.isEnabled = true
            }
        }
    }

    private fun chooseModel() {
        if (models.isEmpty()) return
        modelDialog?.dismiss()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val search = EditText(this).apply {
            hint = "搜索模型"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            isSaveEnabled = false
        }
        val empty = TextView(this).apply {
            text = "没有匹配的模型"
            setPadding(0, padding, 0, padding)
            visibility = View.GONE
        }
        val list = ListView(this)
        var filtered = models
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered.toMutableList())
        list.adapter = adapter
        panel.addView(search)
        panel.addView(empty)
        panel.addView(list, LinearLayout.LayoutParams(-1,
            (resources.displayMetrics.heightPixels * 0.35f).toInt()))
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择模型").setView(panel).setNegativeButton("取消", null).create()
        modelDialog = dialog
        search.doAfterTextChanged {
            val query = it.toString().trim()
            filtered = models.filter { name -> name.contains(query, ignoreCase = true) }
            adapter.clear()
            adapter.addAll(filtered)
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        list.setOnItemClickListener { _, _, position, _ ->
            etModel.setText(filtered[position])
            etModel.error = null
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun testConnection() {
        val current = config()
        if (!validate(current)) return
        testJob?.cancel()
        val requestRevision = revision
        val button = findViewById<Button>(R.id.btnTest)
        val status = findViewById<TextView>(R.id.tvConnectionStatus)
        button.isEnabled = false
        status.text = "正在测试当前配置…"
        testJob = lifecycleScope.launch {
            try {
                val result = AiProviderFactory.创建(current).测试连接()
                if (requestRevision == revision) status.text = result.fold(
                    onSuccess = { "文字连接成功 · ${current.模型名}" },
                    onFailure = { "连接失败：${it.message}" }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestRevision == revision) status.text = "连接失败：${error.message}"
            } finally {
                if (requestRevision == revision) button.isEnabled = true
            }
        }
    }

    private fun validate(current: ModelConfig, requireModel: Boolean = true): Boolean {
        if (requireModel && current.模型名.isBlank()) {
            etModel.error = "请选择或输入模型 ID"
            return false
        }
        val url = current.baseUrl.toHttpUrlOrNull()
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null) {
            etBaseUrl.error = "请输入 API 根地址，不要包含账号密码、查询参数或片段"
            return false
        }
        if (current.提供商 != "ollama" && url.scheme != "https") {
            etBaseUrl.error = "云端服务请使用 HTTPS 地址"
            return false
        }
        if (url.scheme == "http" && url.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) {
            etBaseUrl.error = "此地址需使用 HTTPS；本机 Ollama 可通过 adb reverse 连接 localhost"
            return false
        }
        if (current.提供商 != "ollama" && current.apiKey.isBlank()) {
            etApiKey.error = "请输入 API 密钥"
            return false
        }
        return true
    }

    private fun save() {
        val current = config()
        if (!validate(current)) return
        if (ModelConfigStore.读取(this).baseUrl != current.baseUrl) {
            getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                .putBoolean("screen_upload_consent", false).apply()
        }
        ModelConfigStore.保存(this, current)
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
            .putInt("mode", Mode.READ.ordinal).putInt("level", OperationLevel.NONE.ordinal).apply()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        modelDialog?.dismiss()
        super.onDestroy()
    }
}
