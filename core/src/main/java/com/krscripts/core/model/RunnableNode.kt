package com.krscripts.core.model

open class RunnableNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {

    // 是否在开始前显示操作确认提示
    var confirm: Boolean = false
    // 警示信息
    var warning: String = ""
    // 执行完成后是否自动关闭日志界面
    var autoOff: Boolean = false
    // 是否可中断执行
    var interruptable: Boolean = true
    // 是否在执行完以后重载整个界面
    var reloadPage: Boolean = false
    // 执行完之后要刷新的功能区域 (id)
    var updateBlocks: Array<String>? = null
    // 执行完成后是否自动关闭页面
    var autoFinish = false

    var executionMode = ExecutionMode.NORMAL

    // 脚本
    var setState: String? = null
}

enum class ExecutionMode(
    val label: String
) {
    BACKGROUND("bg-task"), NORMAL("default"), HIDDEN("hidden")
}