package com.krscripts.app.ui.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.krscripts.app.R
import com.krscripts.app.model.ActionNode

class ListItemAction(context: Context, config: ActionNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)

    init {
        widgetView?.visibility = View.VISIBLE
        if (!config.params.isNullOrEmpty()) {
            widgetView?.setImageResource(R.drawable.baseline_checklist_24)
        } else {
            widgetView?.setImageResource(R.drawable.baseline_build_24)
        }
    }
}
