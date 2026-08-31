package com.krscripts.app.model

import java.io.Serializable

class ConfigNode : Serializable {
    var title: String? = null
    var pageMenuOptions = ArrayList<PageMenuOption>()
    var pageMenuOptionsSh: String = ""
    var pageHandlerSh: String? = null
    val content = ArrayList<NodeInfoBase>()
}