package com.krscripts.app.ui.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.krscripts.app.R
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.SwitchNode
import java.util.Locale.getDefault

class ListItemSwitch(
    private val context: Context,
    private val config: SwitchNode
): ListItemView(context, R.layout.kr_action_list_item, config) {

    private var switchView: MaterialSwitch? = layout.findViewById(R.id.kr_switch)
    private var onCheckedChangeListener: OnCheckedChangeListener? = null
    private var iconView: ShapeableImageView? = layout.findViewById(R.id.kr_icon)
    var isAdjusting: Boolean = false

    fun setEnabled(enabled: Boolean) {
        switchView?.post { switchView?.isEnabled = enabled }
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener): ListItemSwitch {
        this.onCheckedChangeListener = listener
        return this
    }

    override fun updateViewByShell() {
        super.updateViewByShell()

        if (!config.getScript.isNullOrEmpty()) {
            val shellResult = ScriptEnvironment.execute(context, config.getScript, config)
            config.checked = shellResult == "1" || shellResult.lowercase(getDefault()) == "true"
        }
        isAdjusting = true
        switchView?.isChecked = config.checked
        isAdjusting = false
    }

    init {
        switchView?.isChecked = config.checked

        switchView?.setOnCheckedChangeListener { _, isChecked ->
            if (!isAdjusting) {
                onCheckedChangeListener?.onCheckedChanged(this, isChecked)
            }
        }

        switchView?.visibility = View.VISIBLE
        layout.findViewById<ImageView>(R.id.kr_widget).visibility = View.GONE

        iconView?.apply {
            IconHelper.applyIcon(
                context = context,
                view = this,
                iconPath = config.iconPath,
                configPath = config.pageConfigPath,
                clip = config.iconClip,
            )
        }
    }

    interface OnCheckedChangeListener {
        fun onCheckedChanged(item: ListItemSwitch, isChecked: Boolean)
    }
}