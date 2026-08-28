package com.krscripts.core.ui.param

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.TextInputLayout
import com.krscripts.core.R
import com.krscripts.core.model.ActionParamInfo
import com.krscripts.core.model.SelectItem
import com.krscripts.core.ui.dialog.DialogItemChooser
import com.krscripts.core.ui.param.ParamLayoutRender.Companion.getInitialSelectedIndex

class SingleSelectRender(
    override var actionParamInfo: ActionParamInfo,
    private var context: FragmentActivity
): ParamRenderer {

    val options = actionParamInfo.optionsFromShell!!
    var autoCompleteTextView: KrAutoCompleteTextView? = null

    override fun getValue(): String? {
        return autoCompleteTextView?.selectedValue
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_spinner, null)
        val inputLayout = layout.findViewById<TextInputLayout>(R.id.textInputLayout)
        autoCompleteTextView = layout.findViewById(R.id.kr_param_autoCompleteTextView)

        autoCompleteTextView?.run {

            // User input by keyboard wouldn't be saved.

            tag = actionParamInfo.name
            isEnabled = !actionParamInfo.readonly
            actionParamInfo.placeholder.takeIf { it.isNotEmpty() }?.apply {
                hint = this
            }

            val initialIndex = getInitialSelectedIndex(actionParamInfo, options)
            val initialOption = options.getOrNull(initialIndex)

            initialOption?.let {
                setText(initialOption.title, false)
                selectedValue = initialOption.value
            }

            showMenuAsDialog = options.size >= 5
            if (showMenuAsDialog) {
                setOnClickListener {
                    openSingleSelectDialog { index ->
                        val selectedItem = options[index]
                        setText(selectedItem.title, false)
                        selectedValue = selectedItem.value
                    }
                }
                inputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                inputLayout.setEndIconDrawable(R.drawable.baseline_chevron_right_24)
            } else {
                setOnItemClickListener { _, _, position, _ ->
                    selectedValue = options[position].value
                }

                val adapter = ArrayAdapter(
                    context,
                    R.layout.kr_spinner_dropdown,
                    options
                )

                setAdapter(adapter)
            }
        }

        return layout
    }

    private fun openSingleSelectDialog(
        onConfirm: (Int) -> Unit
    ) {
        DialogItemChooser(ArrayList(options.mapIndexed { _, item ->
            SelectItem(
                title = item.title,
                selected = item.value == autoCompleteTextView?.selectedValue
            )
        }), false, onConfirm = { _, status ->
            onConfirm(status.indexOf(true))
        }).show(context.supportFragmentManager, "params-single-select")
    }
}
