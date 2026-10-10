package com.example.myreaderdemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object AiSettingsDialog {

    // 预设服务商：全部走 OpenAI 兼容协议，一套请求代码通吃
    // （名称, 接口基础地址, 默认模型名）
    private val 预设列表 = listOf(
        Triple("智谱 GLM（国内，glm-4-flash 免费）", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        Triple("通义千问（阿里）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        Triple("DeepSeek（国内）", "https://api.deepseek.com", "deepseek-chat"),
        Triple("Kimi 月之暗面（国内）", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        Triple("OpenAI 官方", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("本地电脑 Ollama（无需密钥）", "http://192.168.1.100:11434/v1", "qwen2.5:7b"),
        Triple("自定义（手动填写）", "", "")
    )

    fun 显示(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null)
        val spPreset = view.findViewById<Spinner>(R.id.spPreset)
        val etUrl = view.findViewById<EditText>(R.id.etUrl)
        val etModel = view.findViewById<EditText>(R.id.etModel)
        val etKey = view.findViewById<EditText>(R.id.etKey)

        // 打开时回填当前配置：只想换模型时，地址和密钥不用重填
        etUrl.setText(AiConfig.url)
        etModel.setText(AiConfig.model)
        etKey.setText(AiConfig.key)

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            预设列表.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPreset.adapter = adapter

        spPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val (_, url, model) = 预设列表[position]
                if (url.isNotEmpty()) {
                    // 选预设 = 自动填地址和模型；密钥不覆盖（预设不知道你的密钥）
                    etUrl.setText(url)
                    etModel.setText(model)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("AI 设置")
            .setView(view)
            // 正按钮先传 null，show 之后接管点击：校验失败时不关弹窗，方便改完直接保存
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = etUrl.text.toString().trim()
            val model = etModel.text.toString().trim()
            val key = etKey.text.toString().trim()
            if (url.isEmpty() || model.isEmpty()) {
                Toast.makeText(context, "接口地址和模型名必填", Toast.LENGTH_SHORT).show()
            } else {
                AiConfig.保存(url, model, key)
                Toast.makeText(context, "已保存，下次朗读即生效", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }
}
