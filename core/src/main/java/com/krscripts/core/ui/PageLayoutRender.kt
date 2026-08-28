package com.krscripts.core.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.krscripts.core.R
import com.krscripts.core.model.ActionNode
import com.krscripts.core.model.ClickableNode
import com.krscripts.core.model.GroupNode
import com.krscripts.core.model.ImageNode
import com.krscripts.core.model.NodeInfoBase
import com.krscripts.core.model.PageNode
import com.krscripts.core.model.PickerNode
import com.krscripts.core.model.RunnableNode
import com.krscripts.core.model.SwitchNode
import com.krscripts.core.model.TextNode
import com.krscripts.core.ui.widget.ListItemAction
import com.krscripts.core.ui.widget.ListItemClickable
import com.krscripts.core.ui.widget.ListItemGroup
import com.krscripts.core.ui.widget.ListItemImage
import com.krscripts.core.ui.widget.ListItemPage
import com.krscripts.core.ui.widget.ListItemPicker
import com.krscripts.core.ui.widget.ListItemSwitch
import com.krscripts.core.ui.widget.ListItemText
import com.krscripts.core.ui.widget.ListItemView

class PageLayoutRender(
    private val mContext: Context,
    private val itemConfigList: ArrayList<NodeInfoBase>,
    private val clickListener: OnItemClickListener,
    private val rootGroup: ListItemGroup
) {

    interface OnItemClickListener {
        fun onPageClick(item: PageNode, onCompleted: Runnable)
        fun onActionClick(item: ActionNode, onCompleted: Runnable)
        fun onSwitchClick(item: SwitchNode, onCompleted: Runnable)
        fun onPickerClick(item: PickerNode, onCompleted: Runnable)
        fun onItemLongClick(clickableNode: ClickableNode)
    }


    private fun findItemByDynamicIndex(key: String, actionInfos: ArrayList<NodeInfoBase>): NodeInfoBase? {
        for (item in actionInfos) {
            if (item.index == key) {
                return item
            } else if (item is GroupNode && item.children.isNotEmpty()) {
                val result = findItemByDynamicIndex(key, item.children)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    private fun getCommonOnExitRunnable(item: NodeInfoBase, node: ListItemView): Runnable {
        val handler = Handler(Looper.getMainLooper())
        return Runnable {
            handler.post {
                node.updateViewByShell()

                if (item is RunnableNode && item.reloadBlock != null) {
                    rootGroup.triggerUpdateByKey(item.reloadBlock!!)
                }
            }
        }
    }

    private fun onItemClick(item: NodeInfoBase, listItemView: ListItemClickable) {
        when (item) {
            is PageNode -> clickListener.onPageClick(item, getCommonOnExitRunnable(item, listItemView))
            is ActionNode -> clickListener.onActionClick(item, getCommonOnExitRunnable(item, listItemView))
            is PickerNode -> clickListener.onPickerClick(item, getCommonOnExitRunnable(item, listItemView))
            is SwitchNode -> clickListener.onSwitchClick(item, getCommonOnExitRunnable(item, listItemView))
        }
    }

    private val onItemClickListener: ListItemClickable.OnClickListener = object : ListItemClickable.OnClickListener {
        override fun onClick(listItemView: ListItemClickable) {
            val key = listItemView.index
            try {
                val item = findItemByDynamicIndex(key, itemConfigList)
                if (item == null) {
                    Log.e("onItemClick", "找不到指定ID的项 index: $key")
                    return
                } else {
                    onItemClick(item, listItemView)
                }
            } catch (_: Exception) {
            }
        }
    }

    private val onItemCheckedListener: ListItemSwitch.OnCheckedChangeListener = object : ListItemSwitch.OnCheckedChangeListener {
        override fun onCheckedChanged(item: ListItemSwitch, isChecked: Boolean) {
            val key = item.index
            try {
                val itemNode = findItemByDynamicIndex(key, itemConfigList)
                if (itemNode == null) {
                    Log.e("onItemChecked", "找不到指定ID的项 index: $key")
                    return
                } else {
                    val switchNode = itemNode as SwitchNode

                    item.setEnabled(false)
                    val onComplete = {
                        item.setEnabled(true)
                        getCommonOnExitRunnable(switchNode, item).run()
                    }
                    clickListener.onSwitchClick(switchNode, onComplete)
                }
            } catch (_: Exception) {
            }
        }
    }

    private val onItemLongClickListener = object : ListItemClickable.OnLongClickListener {
        override fun onLongClick(listItemView: ListItemClickable) {
            val item = findItemByDynamicIndex(listItemView.index, itemConfigList)
            if (item is ClickableNode) {
                clickListener.onItemLongClick(item)
            }
        }
    }

    private fun mapConfigList(parent: ListItemGroup, actionInfos: ArrayList<NodeInfoBase>) {
        for (index in actionInfos.indices) {
            val actionInfo = actionInfos[index]
            try {
                var uiRender: ListItemView? = null

                when(actionInfo) {
                    is PageNode -> { uiRender = createPageItem(actionInfo) }
                    is SwitchNode -> { uiRender = createSwitchItem(actionInfo) }
                    is ActionNode -> { uiRender = createActionItem(actionInfo) }
                    is PickerNode -> { uiRender = createListItem(actionInfo) }
                    is ImageNode -> { uiRender = createImageItem(actionInfo) }
                    is TextNode -> {
                        uiRender = if (parent.isRootGroup) createTextItem(actionInfo) else createTextItemWhite(actionInfo)
                    }
                    is GroupNode -> {
                        val subGroup = createItemGroup(actionInfo)
                        if (actionInfo.children.isNotEmpty()) {
                            parent.addView(subGroup)
                            mapConfigList(subGroup, actionInfo.children)
                        }
                    }
                }

                if (uiRender != null) {
                    if (uiRender is ListItemClickable) {
                        uiRender.setOnClickListener(this.onItemClickListener)
                        uiRender.setOnLongClickListener(this.onItemLongClickListener)
                    }
                    if (uiRender is ListItemSwitch) {
                        uiRender.setOnCheckedChangeListener(this.onItemCheckedListener)
                    }
                    parent.addView(uiRender)
                }
            } catch (ex: Exception) {
                Toast.makeText(mContext, actionInfo.title + "界面渲染异常" + ex.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createTextItem(node: TextNode): ListItemView {
        return ListItemText(mContext, R.layout.kr_text_list_item, node)
    }

    private fun createImageItem(node: ImageNode): ListItemView {
        return ListItemImage(mContext, R.layout.kr_list_item_image, node)
    }

    private fun createTextItemWhite(node: TextNode): ListItemView {
        return ListItemText(mContext, R.layout.kr_text_list_item_white, node)
    }

    private fun createListItem(node: PickerNode): ListItemView {
        return ListItemPicker(mContext, node)
    }

    private fun createPageItem(node: PageNode): ListItemView {
        return ListItemPage(mContext, node)
    }

    private fun createSwitchItem(node: SwitchNode): ListItemSwitch {
        return ListItemSwitch(mContext, node)
    }

    private fun createActionItem(node: ActionNode): ListItemView {
        return ListItemAction(mContext, node)
    }

    private fun createItemGroup(node: GroupNode): ListItemGroup {
        return ListItemGroup(mContext, false, node)
    }

    init {
        mapConfigList(rootGroup, itemConfigList)
    }
}
