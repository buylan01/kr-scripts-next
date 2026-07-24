package com.omarea.common.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.omarea.common.R
import com.omarea.common.model.SelectItem

class DialogItemChooser2(
        private val darkMode: Boolean,
        private var items: ArrayList<SelectItem>,
        private var selectedItems: ArrayList<SelectItem>,
        private val multiple: Boolean = false,
        private var callback: Callback? = null) : DialogFullScreen(
        (if (items.size > 7) {
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

        // 全选功能（因为这种类型的选择列表，需要关注选择顺序，将全选功能禁用）
        view.findViewById<CompoundButton?>(R.id.select_all)?.visibility = View.GONE

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

    public fun setTitle(title: String): DialogItemChooser2 {
        this.title = title
        updateTitle()

        return this
    }

    public fun setMessage(message: String): DialogItemChooser2 {
        this.message = message
        updateMessage()

        return this
    }

    private fun setup(gridView: AbsListView) {
        gridView.adapter = AdapterItemChooser2(gridView.context, items, selectedItems, multiple)
    }

    interface Callback {
        fun onConfirm(selected: List<SelectItem>, status: BooleanArray)
    }

    private fun onConfirm(gridView: AbsListView) {
        val adapter = (gridView.adapter as AdapterItemChooser2)
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
