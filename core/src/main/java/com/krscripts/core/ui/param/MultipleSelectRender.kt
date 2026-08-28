package com.krscripts.core.ui.param

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.krscripts.core.R
import com.krscripts.core.model.ActionParamInfo
import com.krscripts.core.model.SelectItem
import com.krscripts.core.ui.dialog.DialogItemChooser

class MultipleSelectRender(
    override var actionParamInfo: ActionParamInfo,
    private val context: FragmentActivity
): ParamRenderer {
    private var options: ArrayList<SelectItem>? = null
    private var status = booleanArrayOf()
    private var labels: Array<String?> = arrayOf()
    private var values: Array<String?> = arrayOf()
    private var inputTextView: TextView? = null
    private var value: String? = null

    override fun getValue(): String? {
        return value
    }

    override fun render(): View {
        options = actionParamInfo.optionsFromShell
        options?.run {
            labels = map { it.title }.toTypedArray()
            values = map { it.value }.toTypedArray()
            status = ParamLayoutRender.getSelectedFlags(actionParamInfo, this)
        }

        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_multiple_select, null)
        inputTextView = layout.findViewById(R.id.kr_param_label_text)
        val countView = layout.findViewById<TextView>(R.id.kr_param_count_text)

        inputTextView?.tag = actionParamInfo.name

        setView(countView)

        layout.setOnClickListener {
            openDialog(countView)
        }

        return layout
    }

    private fun setView(countView: TextView) {
        val resultValues = ArrayList<String?>()
        val resultLables = ArrayList<String?>()
        var count = 0
        for (index in status.indices) {
            if (status[index]) {
                values[index]?.run {
                    resultValues.add(this)
                }
                labels[index]?.run {
                    resultLables.add(this)
                }
                count++
            }
        }
        value = resultValues.joinToString(actionParamInfo.separator)
        inputTextView?.text = resultLables.joinToString("，")
        countView.text = count.toString()
    }

    private fun openDialog(countView: TextView) {
        options?.run {
            val items = ArrayList<SelectItem>()
            for (i in labels.indices) {
                val item = SelectItem(
                    title = "" + labels[i],
                    selected = status[i]
                )
                items.add(item)
            }

            DialogItemChooser(ArrayList(items), true, onConfirm = { _, status ->
                status.forEachIndexed { index, value ->
                    this@MultipleSelectRender.status[index] = value
                }
                setView(countView)
            }).show(context.supportFragmentManager, "params-multi-select")
        }
    }
}
