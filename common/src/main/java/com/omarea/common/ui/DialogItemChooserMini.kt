package com.omarea.common.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.EditText
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import com.omarea.common.R
import com.omarea.common.model.SelectItem

class DialogItemChooserMini(
        private val context: Context,
        private var items: ArrayList<SelectItem>,
        private var selectedItems: ArrayList<SelectItem>,
        private val multiple: Boolean = false) {

    companion object {
        public fun singleChooser(context: Context, items: Array<String>, checkedItem: Int): DialogItemChooserMini {
            val options = ArrayList(items.map {
                SelectItem().apply {
                    title = it
                    value = title
                }
            })
            val selectItems = if (checkedItem > -1) {
                ArrayList<SelectItem>().apply {
                    add(options[checkedItem])
                }
            } else {
                ArrayList()
            }

            return DialogItemChooserMini(context, options, selectItems, false)
        }
    }

    private val layout = R.layout.dialog_item_chooser_small
    private var view: View? = null
    private var dialog:DialogHelper.DialogWrap? = null

    public fun show(): DialogHelper.DialogWrap {
        if (dialog?.isShowing != true) {
            onViewCreated(createView())
            this.dialog = DialogHelper.customDialog(context, this.view!!)
        }
        return dialog!!
    }

    private fun dismiss () {
        dialog?.dismiss()
    }

    private fun createView (): View {
        if (view != null) {
            return view!!
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(layout, null)
        this.view = view

        return view
    }

    private fun onViewCreated(view: View) {
        val absListView = view.findViewById<AbsListView>(R.id.item_list)
        setup(absListView)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            this.onConfirm(absListView)
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
    private var callback: Callback? = null

    private fun updateTitle() {
        view?.run {
                findViewById<TextView?>(R.id.dialog_title)!!.run {
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
            findViewById<TextView?>(R.id.dialog_desc)!!.run {
                text = message
                visibility = if (message.isNotEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    public fun setTitle(resId: Int): DialogItemChooserMini {
        return setTitle(context.getString(resId))
    }

    public fun setTitle(title: String): DialogItemChooserMini {
        this.title = title
        updateTitle()

        return this
    }

    public fun setMessage(message: String): DialogItemChooserMini {
        this.message = message
        updateMessage()

        return this
    }

    public fun setMessage(resId: Int): DialogItemChooserMini {
        return setMessage(context.getString(resId))
    }

    public fun setCallback(callback: Callback?): DialogItemChooserMini {
        this.callback = callback

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
}
