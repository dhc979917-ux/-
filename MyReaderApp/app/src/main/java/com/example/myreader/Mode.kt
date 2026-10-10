package com.example.myreader

enum class Mode(val 显示名: String) {
    READ("朗读模式"),
    ASSISTANT("助手模式"),
    FULL("全权模式")
}

enum class OperationLevel(val 显示名: String, val 级别: Int) {
    NONE("不操作", 0),
    CLICK("只点按钮", 1),
    FILL("可以填表", 2),
    PAY("可以下单支付", 3)
}
