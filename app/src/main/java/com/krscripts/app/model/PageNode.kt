package com.krscripts.app.model

class PageNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {

    // Target config path
    var configPath: String = ""

    // Target config by script
    var configShell: String = ""

    // Link will be opened inside
    var htmlPage: String = ""

    // Link will be opened outside
    var link: String = ""

    // Activity will be start after click
    var activity: String = ""

    // Script run before config read
    var beforeRead = ""

    // Script run after config readed
    var afterRead = ""

    // Page menu
    var pageMenuOptions: ArrayList<PageMenuOption>? = null

    // Page menu by script
    var pageMenuOptionsSh: String = ""

    // Handler of page menu
    var pageHandlerSh:  String = ""

    // Script run after load success
    var loadSuccess = ""

    // Script run after load failed
    var loadFail = ""
}
