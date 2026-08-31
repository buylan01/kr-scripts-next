package com.krscripts.app.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Filterable
import android.widget.RelativeLayout
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.krscripts.app.R
import com.krscripts.app.ui.adapter.AdapterAppChooser

class DialogAppChooser(
    private var packages: ArrayList<AdapterAppChooser.AppInfo>,
    private val multiple: Boolean = false,
    private var onConfirm: ((List<AdapterAppChooser.AppInfo>) -> Unit)? = null
) : DialogFullScreen(R.layout.dialog_item_chooser) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.item_list)
        val filterResult = ArrayList<AdapterAppChooser.AppInfo>(packages)
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = AdapterAppChooser(recyclerView.context, filterResult, multiple)
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            this.onConfirm(recyclerView)
        }

        // 全选功能
        val selectAll = view.findViewById<CheckBox>(R.id.select_all)
        val selectAllGroup = view.findViewById<RelativeLayout>(R.id.select_all_block)
        selectAllGroup.isVisible = multiple
        selectAll?.let {
            if (multiple) {
                val adapter = (recyclerView.adapter as AdapterAppChooser?)
                selectAll.isChecked = packages.filter { it.selected }.size == packages.size
                selectAll.setOnClickListener {
                    adapter?.setSelectAllState((it as CheckBox).isChecked)
                }
                adapter?.run {
                    setSelectStateListener(object : AdapterAppChooser.SelectStateListener {
                        override fun onSelectChange(selected: List<AdapterAppChooser.AppInfo>) {
                            selectAll.isChecked = selected.size == packages.size
                        }
                    })
                }
            }
        }

        val searchView = view.findViewById<SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                (recyclerView.adapter as Filterable).filter.filter(newText ?: "")
                return true
            }
        })
    }

    private fun onConfirm(recyclerView: RecyclerView) {
        val apps = (recyclerView.adapter as AdapterAppChooser).getSelectedItems()

        onConfirm?.invoke(apps)

        this.dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}
