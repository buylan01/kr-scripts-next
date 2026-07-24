package com.krscripts.core.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.krscripts.core.R
import com.krscripts.core.model.PageNode

class ListItemPage(context: Context, config: PageNode) : ListItemClickable(context, R.layout.kr_action_list_item, config) {
    private val widgetView = layout.findViewById<ImageView?>(R.id.kr_widget)

    init {
        widgetView?.visibility = View.VISIBLE
        widgetView?.setImageDrawable(context.getDrawable(R.drawable.baseline_arrow_forward_ios_24))
    }
}
