package com.krscripts.app.ui.param

import android.view.View
import com.krscripts.app.model.ActionParamInfo

interface ParamRenderer {
    var actionParamInfo: ActionParamInfo

    val paramName: String?
        get() = actionParamInfo.name

    fun getValue(): String?
    fun render(): View
}