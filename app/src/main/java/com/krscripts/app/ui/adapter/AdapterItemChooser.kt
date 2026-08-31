package com.krscripts.app.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.krscripts.app.R
import com.krscripts.app.model.SelectItem
import java.util.Locale

class AdapterItemChooser(
    private val context: Context,
    private var items: List<SelectItem>,
    private val multiple: Boolean
) : RecyclerView.Adapter<AdapterItemChooser.ViewHolder>(), Filterable {
    interface SelectStateListener {
        fun onSelectChange(selected: List<SelectItem>)
    }

    private var selectStateListener: SelectStateListener? = null
    private var filter: Filter? = null
    internal var filterItems: List<SelectItem> = items

    override fun getFilter(): Filter {
        if (filter == null) {
            filter = object : Filter() {

                @SuppressLint("NotifyDataSetChanged")
                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filterItems = results?.values as ArrayList<SelectItem>
                    notifyDataSetChanged()
                }

                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val results = FilterResults()
                    val query = constraint?.toString()?.trim() ?: ""

                    val filteredList = if (query.isEmpty()) {
                        items
                    } else {
                        val lowerQuery = query.lowercase(Locale.getDefault())
                        items.filter { item ->
                            item.selected || item.title?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
                        }
                    }

                    results.values = filteredList
                    results.count = filteredList.size
                    return results
                }
            }
        }
        return filter!!
    }

    override fun getItemCount(): Int {
        return filterItems.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.layout_chooser_item, parent, false)
        val holder = ViewHolder(view)

        holder.checkBox?.isVisible = multiple
        holder.radioButton?.isVisible = !multiple

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filterItems[position]

        holder.itemView.setOnClickListener {
            if (multiple) {
                item.selected = !item.selected
                holder.checkBox?.isChecked = item.selected
            } else {
                if (item.selected) return@setOnClickListener
                val oldIndex = filterItems.indexOfFirst { it.selected }
                if (oldIndex != -1) {
                    filterItems[oldIndex].selected = false
                    notifyItemChanged(oldIndex)
                }

                item.selected = true
                holder.radioButton?.isChecked = item.selected
            }
            selectStateListener?.onSelectChange(getSelectedItems())
        }

        holder.itemTitle?.text = item.title
        holder.itemDesc?.run{
            if (item.title.isNullOrEmpty()) {
                text = item.title
            } else {
                visibility = View.GONE
            }
        }
        holder.checkBox?.isChecked = item.selected
        holder.radioButton?.isChecked = item.selected
        item.icon?.let {
            holder.imgView?.visibility = View.VISIBLE
        }
    }

    fun setSelectAllState(allSelected: Boolean) {
        items.forEach { it.selected = allSelected }
        notifyItemRangeChanged(0, items.size)
    }

    fun setSelectStateListener(selectStateListener: SelectStateListener?) {
        this.selectStateListener = selectStateListener
    }

    fun getSelectedItems(): List<SelectItem> {
        return items.filter { it.selected }
    }

    fun getSelectStatus(): BooleanArray {
        return items.map { it.selected }.toBooleanArray()
    }

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        internal var imgView: ShapeableImageView? = view.findViewById(R.id.ItemIcon)
        internal var itemTitle: TextView? = view.findViewById(R.id.ItemTitle)
        internal var itemDesc: TextView? = view.findViewById(R.id.ItemDesc)
        internal var checkBox: CheckBox? = view.findViewById(R.id.ItemCheckBox)
        internal var radioButton: RadioButton? = view.findViewById(R.id.ItemRadioButton)
    }
}
