package com.krscripts.app.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.krscripts.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AdapterAppChooser(
        private val context: Context,
        private var apps: ArrayList<AppInfo>,
        private val multiple: Boolean
) : RecyclerView.Adapter<AdapterAppChooser.ViewHolder>(), Filterable {
    interface SelectStateListener {
        fun onSelectChange(selected: List<AppInfo>)
    }

    data class AppInfo (
        var appName: String = "",
        var packageName: String = "",
        var notFound: Boolean = false,
        var selected: Boolean = false
    )

    private var selectStateListener: SelectStateListener? = null
    private var filter: Filter? = null
    internal var filterApps: ArrayList<AppInfo> = apps

    override fun getFilter(): Filter {
        if (filter == null) {
            filter = object : Filter() {

                @SuppressLint("NotifyDataSetChanged")
                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filterApps = results?.values as ArrayList<AppInfo>
                    notifyDataSetChanged()
                }

                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val results = FilterResults()
                    val query = constraint?.toString()?.trim() ?: ""

                    val filteredList = if (query.isEmpty()) {
                        apps
                    } else {
                        val lowerQuery = query.lowercase(Locale.getDefault())
                        apps.filter { item ->
                            item.selected || item.appName.lowercase(Locale.getDefault())
                                .contains(lowerQuery) || item.packageName.lowercase(Locale.getDefault())
                                .contains(lowerQuery)
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

    private val iconCaches = LruCache<String, Drawable>(100)

    init {
        filterApps.sortBy { !it.selected }
    }

    override fun getItemCount(): Int {
        return filterApps.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private suspend fun loadIcon(app: AppInfo): Drawable? = withContext(Dispatchers.IO) {
        val packageName = app.packageName
        val icon: Drawable? = iconCaches.get(packageName)
        if (icon == null && !app.notFound) {
            try {
                val installInfo = context.packageManager.getPackageInfo(packageName, 0)
                iconCaches.put(
                    packageName,
                    installInfo.applicationInfo!!.loadIcon(context.packageManager)
                )
            } catch (_: Exception) {
                app.notFound = true
            } finally {
            }
            iconCaches.get(packageName)
        } else {
            icon
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_chooser_item, parent, false)
        val holder = ViewHolder(view)
        holder.checkBox?.isVisible = multiple
        holder.radioButton?.isVisible = !multiple
        holder.imgView?.isVisible = true

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filterApps[position]

        val packageName = item.packageName
        holder.packageName = packageName

        holder.itemView.setOnClickListener {
            if (multiple) {
                item.selected = !item.selected
                holder.checkBox?.isChecked = item.selected
            } else {
                if (item.selected) return@setOnClickListener
                val oldIndex = filterApps.indexOfFirst { it.selected }
                if (oldIndex != -1) {
                    filterApps[oldIndex].selected = false
                    notifyItemChanged(oldIndex)
                }

                item.selected = true
                holder.radioButton?.isChecked = item.selected
            }
            selectStateListener?.onSelectChange(getSelectedItems())
        }

        holder.run {
            itemTitle?.text = item.appName
            itemDesc?.text = item.packageName
            checkBox?.isChecked = item.selected
            radioButton?.isChecked = item.selected

            scope.launch(Dispatchers.Main) {
                val icon = loadIcon(item)
                icon?.let {
                    holder.imgView?.setImageDrawable(icon)
                }
            }
        }
    }

    fun setSelectAllState(allSelected: Boolean) {
        apps.forEach {
            it.selected = allSelected
        }
        notifyItemRangeChanged(0, apps.size)
    }

    fun setSelectStateListener(selectStateListener: SelectStateListener?) {
        this.selectStateListener = selectStateListener
    }

    fun getSelectedItems(): List<AppInfo> {
        return apps.filter { it.selected }
    }

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        internal val scope = MainScope()
        internal var packageName: String? = null
        internal var itemTitle: TextView? = view.findViewById(R.id.ItemTitle)
        internal var itemDesc: TextView? = view.findViewById(R.id.ItemDesc)
        internal var imgView: ImageView? = view.findViewById(R.id.ItemIcon)
        internal var checkBox: CheckBox? = view.findViewById(R.id.ItemCheckBox)
        internal var radioButton: RadioButton? = view.findViewById(R.id.ItemRadioButton)
    }
}
