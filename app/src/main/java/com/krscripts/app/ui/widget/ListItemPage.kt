package com.krscripts.app.ui.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.krscripts.app.R
import com.krscripts.app.model.PageNode

class ListItemPage(context: Context, config: PageNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageResource(R.drawable.baseline_arrow_forward_ios_24)
    }
}
