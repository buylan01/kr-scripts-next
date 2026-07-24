package com.omarea.common.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AbsListView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Filterable
import androidx.appcompat.widget.SearchView
import com.omarea.common.R

class DialogAppChooser(
    private var packages: ArrayList<AdapterAppChooser.AppInfo>,
    private val multiple: Boolean = false,
    private var callback: Callback? = null
) : DialogFullScreen(R.layout.dialog_item_chooser) {

    private var allowAllSelect = true
    private var excludeApps: Array<String> = arrayOf()

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
                val adapter = (absListView.adapter as AdapterAppChooser?)
                selectAll.visibility = View.VISIBLE
                selectAll.isChecked = packages.filter { it.selected }.size == packages.size
                selectAll.setOnClickListener {
                    adapter?.setSelectAllState((it as CompoundButton).isChecked)
                }
                adapter?.run {
                    setSelectStateListener(object : AdapterAppChooser.SelectStateListener {
                        override fun onSelectChange(selected: List<AdapterAppChooser.AppInfo>) {
                            selectAll.isChecked = selected.size == packages.size
                        }
                    })
                }
                if (!allowAllSelect) {
                    selectAll.visibility = View.GONE
                }
            } else {
                selectAll.visibility = View.GONE
            }
        }

        val searchView = view.findViewById<SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                (absListView.adapter as Filterable).filter.filter(newText ?: "")
                return true
            }
        })
    }

    private fun setup(gridView: AbsListView) {
        val filterResult = ArrayList<AdapterAppChooser.AppInfo>(packages.filter { !excludeApps.contains(it.packageName) })
        gridView.adapter = AdapterAppChooser(gridView.context, filterResult, multiple)
    }

    interface Callback {
        fun onConfirm(apps: List<AdapterAppChooser.AppInfo>)
    }

    public fun setExcludeApps(apps: Array<String>): DialogAppChooser {
        this.excludeApps = apps
        if (this.view != null) {
            Log.e("@DialogAppChooser", "Unable to set the exclusion list, The list has been loaded")
        }

        return this
    }

    public fun setAllowAllSelect(allow: Boolean): DialogAppChooser {
        this.allowAllSelect = allow
        view?.findViewById<CompoundButton?>(R.id.select_all)?.visibility = if (allow) View.VISIBLE else View.GONE

        return this
    }

    private fun onConfirm(gridView: AbsListView) {
        val apps = (gridView.adapter as AdapterAppChooser).getSelectedItems()

        callback?.onConfirm(apps)

        this.dismiss()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}
