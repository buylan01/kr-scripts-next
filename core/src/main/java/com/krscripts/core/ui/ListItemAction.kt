package com.krscripts.core.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.krscripts.core.R
import com.krscripts.core.model.ActionNode

class ListItemAction(context: Context, config: ActionNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)

    init {
        widgetView?.visibility = View.VISIBLE
        if (config.params != null && config.params!!.size > 0) {
            widgetView?.setImageDrawable(context.getDrawable(R.drawable.baseline_checklist_24))
        } else {
            widgetView?.setImageDrawable(context.getDrawable(R.drawable.baseline_build_24))
        }
    }
}
