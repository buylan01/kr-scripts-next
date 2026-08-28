package com.krscripts.core.model

class SwitchNode(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    var setScript: String? = null
    var getScript: String? = null
    var checked = false
}