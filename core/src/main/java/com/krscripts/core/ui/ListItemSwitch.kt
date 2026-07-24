package com.krscripts.core.ui

import android.content.Context
import com.google.android.material.materialswitch.MaterialSwitch
import com.krscripts.core.R
import com.krscripts.core.executor.ScriptEnvironment
import com.krscripts.core.model.SwitchNode
import java.util.Locale.getDefault

class ListItemSwitch(
    private val context: Context,
    private val config: SwitchNode
) : ListItemClickable(context, R.layout.kr_switch_list_item, config) {
    private var switchView = layout.findViewById<MaterialSwitch?>(R.id.kr_switch)

    var checked: Boolean
        get() {
            return if (switchView != null) switchView!!.isChecked else false
        }
        set(value) {
            switchView?.isChecked = value
        }

    override fun updateViewByShell() {
        super.updateViewByShell()

        if (config.getState.isNotEmpty()) {
            val shellResult = ScriptEnvironment.executeResultRoot(context, config.getState, config)
            config.checked = shellResult == "1" || shellResult.lowercase(getDefault()) == "true"
        }
        checked = config.checked
    }

    init {
        checked = config.checked
    }
}
