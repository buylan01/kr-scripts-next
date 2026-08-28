package com.krscripts.core.model

open class RunnableNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var confirm: Boolean = false
    var warning: String = ""
    var interruptable: Boolean = true
    var reloadPage: Boolean = false
    var reloadBlock: Array<String>? = null
    var executionMode = ExecutionMode.NORMAL
    var afterExecution = ActionAfterExecution.NONE
}

enum class ExecutionMode(
    val label: String
) { BACKGROUND("bg-task"), NORMAL("default"), HIDDEN("hidden") }

enum class ActionAfterExecution { HIDE, FINISH_ACTIVITY, NONE }