package com.omarea.common.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SearchView
import com.omarea.common.R
import com.omarea.common.model.SelectItem

class DialogItemChooser(
        // 选择项以及选中状态
        private var items: ArrayList<SelectItem>,
        // 是否可多选
        private val multiple: Boolean = false,
        // 回调
        private var callback: Callback? = null,
        // 是否永远显示为小窗口（而不是全屏）
        private val alwaysSmallDialog: Boolean? = null
) : DialogFullScreen(
        (if (items.size > 7 && alwaysSmallDialog != true) {
            R.layout.dialog_item_chooser
        } else {
            R.layout.dialog_item_chooser_small
        })
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val absListView = view.findViewById<AbsListView>(R.id.item_list)
        setup(absListView)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            this.onConfirm(absListView)
        }

        // 全选功能
        val selectAll = view.findViewById<CompoundButton?>(R.id.select_all)
        if (selectAll != null) {
            if (multiple) {
                val adapter = (absListView.adapter as AdapterItemChooser?)
                selectAll.visibility = View.VISIBLE
                selectAll.isChecked = items.filter { it.selected }.size == items.size
                selectAll.setOnClickListener {
                    adapter?.setSelectAllState((it as CompoundButton).isChecked)
                }
                adapter?.run {
                    setSelectStateListener(object : AdapterItemChooser.SelectStateListener {
                        override fun onSelectChange(selected: List<SelectItem>) {
                            selectAll.isChecked = selected.size == items.size
                        }
                    })
                }
            } else {
                selectAll.visibility = View.GONE
            }
        }

        // 长列表才有搜索
        if (items.size > 5) {
            val searchView = view.findViewById<SearchView>(R.id.search_view)
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    (absListView.adapter as Filterable).filter.filter(newText ?: "")
                    return true
                }
            })
        }

        updateTitle()
        updateMessage()
    }

    private var title: String = ""
    private var message: String = ""

    private fun updateTitle() {
        view?.run {
                findViewById<TextView?>(R.id.dialog_title)?.run {
                    text = title
                    visibility = if (title.isNotEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
        }
    }

    private fun updateMessage() {
        view?.run {
            findViewById<TextView?>(R.id.dialog_desc)?.run {
                text = message
                visibility = if (message.isNotEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    public fun setTitle(title: String): DialogItemChooser {
        this.title = title
        updateTitle()

        return this
    }

    public fun setMessage(message: String): DialogItemChooser {
        this.message = message
        updateMessage()

        return this
    }

    private fun setup(gridView: AbsListView) {
        gridView.adapter = AdapterItemChooser(gridView.context, items, multiple)
    }

    interface Callback {
        fun onConfirm(selected: List<SelectItem>, status: BooleanArray)
    }

    private fun onConfirm(gridView: AbsListView) {
        val adapter = (gridView.adapter as AdapterItemChooser)
        val items = adapter.getSelectedItems()
        val status = adapter.getSelectStatus()

        callback?.onConfirm(items, status)

        this.dismiss()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}
