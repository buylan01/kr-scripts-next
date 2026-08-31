package com.krscripts.app.ui.widget

import android.content.Context
import android.view.View
import com.google.android.material.imageview.ShapeableImageView
import com.krscripts.app.R
import com.krscripts.app.model.ClickableNode

open class ListItemClickable(
    context: Context,
    layoutId: Int,
    config: ClickableNode
) : ListItemView(context, layoutId, config) {
    protected var mOnClickListener: OnClickListener? = null
    protected var mOnLongClickListener: OnLongClickListener? = null
    protected var shortcutIconView: View? = layout.findViewById(R.id.kr_shortcut_icon)
    protected var iconView: ShapeableImageView? = layout.findViewById(R.id.kr_icon)

    fun setOnClickListener(onClickListener: OnClickListener): ListItemClickable {
        this.mOnClickListener = onClickListener

        return this
    }

    fun setOnLongClickListener(onLongClickListener: OnLongClickListener): ListItemClickable {
        this.mOnLongClickListener = onLongClickListener

        return this
    }

    fun triggerAction() {
        this.mOnClickListener?.onClick(this)
    }

    init {
        this.layout.setOnClickListener {
            this.mOnClickListener?.onClick(this)
        }
        if (this.key.isNotEmpty() && config.allowShortcut != false) {
            this.layout.setOnLongClickListener {
                this.mOnLongClickListener?.onLongClick(this)
                true
            }
            shortcutIconView?.visibility = View.VISIBLE
        } else {
            shortcutIconView?.visibility = View.GONE
        }
        iconView?.apply {
            IconHelper.applyIcon(
                context = context,
                view = this,
                iconPath = config.iconPath,
                configPath = config.pageConfigDir,
                clip = config.iconClip,
            )
        }
    }

    interface OnClickListener {
        fun onClick(listItemView: ListItemClickable)
    }

    interface OnLongClickListener {
        fun onLongClick(listItemView: ListItemClickable)
    }
}
