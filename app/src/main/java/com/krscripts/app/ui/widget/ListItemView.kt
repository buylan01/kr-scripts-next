package com.krscripts.app.ui.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.krscripts.app.R
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.NodeInfoBase

open class ListItemView(
    private val context: Context,
    layoutId: Int,
    private val config: NodeInfoBase
) {
    protected var layout: View = LayoutInflater.from(context).inflate(layoutId, null)
    protected var descView: TextView? = layout.findViewById(R.id.kr_desc)
    protected var summaryView: TextView? = layout.findViewById(R.id.kr_summary)
    protected var titleView: TextView? = layout.findViewById(R.id.kr_title)


    val key: String
        get() = config.key

    val index: String
        get() = config.index

    var title: String
        get() = titleView?.text.toString()
        set(value) = titleView.update(value)

    var desc: String
        get() = descView?.text.toString()
        set(value) = descView.update(value)

    var summary: String
        get() = summaryView?.text.toString()
        set(value) = summaryView.update(value)

    private fun TextView?.update(value: String) {
        this?.apply {
            if (value.isEmpty()) {
                visibility = View.GONE
            } else {
                text = value
                visibility = View.VISIBLE
            }
        }
    }

    open fun updateViewByShell() {
        if (config.descSh.isNotEmpty()) {
            config.desc = ScriptEnvironment.execute(context, config.descSh, config)
            desc = config.desc
        }

        if (config.summarySh.isNotEmpty()) {
            config.summary = ScriptEnvironment.execute(context, config.summarySh, config)
            summary = config.summary
        }
    }

    fun getView(): View {
        return layout
    }

    init {
        title = config.title
        desc = config.desc
        summary = config.summary
    }
}
