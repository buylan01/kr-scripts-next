package com.krscripts.core.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.Filterable
import android.widget.RelativeLayout
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.krscripts.core.R
import com.krscripts.core.model.SelectItem
import com.krscripts.core.ui.adapter.AdapterItemChooser

class DialogItemChooser(
    private var items: List<SelectItem>,
    private val multiple: Boolean = false,
    private var onConfirm: ((selected: List<SelectItem>, status: BooleanArray) -> Unit)? = null,
    showAsSmall: Boolean? = null
) : DialogFullScreen(
    (if (items.size > 7 && showAsSmall != true) {
        R.layout.dialog_item_chooser
    } else {
        R.layout.dialog_item_chooser_small
    })
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.item_list)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = AdapterItemChooser(recyclerView.context, items, multiple)
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            this.onConfirm(recyclerView)
        }

        // 全选功能
        val selectAll = view.findViewById<CompoundButton?>(R.id.select_all)
        val selectAllGroup = view.findViewById<RelativeLayout>(R.id.select_all_block)
        selectAll?.let {
            if (multiple) {
                val adapter = (recyclerView.adapter as AdapterItemChooser?)
                selectAllGroup.visibility = View.VISIBLE
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
                selectAllGroup.visibility = View.GONE
            }
        }

        // 长列表才有搜索

        val searchView = view.findViewById<SearchView>(R.id.search_view)
        if (items.size > 5) {
            searchView.isVisible = true
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    (recyclerView.adapter as Filterable).filter.filter(newText ?: "")
                    return true
                }
            })
        } else {
            searchView.isVisible = false
        }
    }

    private fun onConfirm(recyclerView: RecyclerView) {
        val adapter = (recyclerView.adapter as AdapterItemChooser)
        val items = adapter.getSelectedItems()
        val status = adapter.getSelectStatus()

        onConfirm?.invoke(items, status)

        this.dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}
