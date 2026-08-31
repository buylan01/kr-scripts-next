package com.krscripts.app.ui.param

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.materialswitch.MaterialSwitch
import com.krscripts.app.R
import com.krscripts.app.model.ActionParamInfo

class SwitchRender(
    override var actionParamInfo: ActionParamInfo,
    private var context: Context
): ParamRenderer {
    private var switch: MaterialSwitch? = null

    override fun getValue(): String? {
        return if (switch?.isChecked == true) "1" else "0"
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_switch, null)
        switch = layout.findViewById(R.id.kr_param_switch)

        switch?.run {
            tag = actionParamInfo.name
            isChecked = getCheckState(actionParamInfo)
            if (!actionParamInfo.label.isNullOrEmpty()) {
                text = actionParamInfo.label
            }
        }

        return layout
    }

    private fun getCheckState(
        actionParamInfo: ActionParamInfo,
        defaultValue: Boolean = false
    ): Boolean {
        val value = actionParamInfo.valueFromShell ?: actionParamInfo.value ?: return defaultValue
        return value == "1" || value.lowercase() == "true"
    }
}
