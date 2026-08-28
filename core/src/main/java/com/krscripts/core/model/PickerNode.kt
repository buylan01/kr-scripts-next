package com.krscripts.core.model

class PickerNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var options: ArrayList<SelectItem>? = null
    var optionsSh = ""
    var value: String? = null

    // 参数名
    var name: String = ""
    // 是否允许多选
    var multiple: Boolean = false
    // 多个值的分隔符
    var separator: String = "\n"

    var setScript: String? = null
    var getScript: String? = null
}
