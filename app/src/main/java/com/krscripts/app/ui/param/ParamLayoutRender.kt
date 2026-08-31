package com.krscripts.app.ui.param

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.krscripts.app.R
import com.krscripts.app.databinding.KrParamRowBinding
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.model.SelectItem

class ParamLayoutRender(
    private var linearLayout: LinearLayout,
    private val context: FragmentActivity
) {
    private val renderers = mutableListOf<ParamRenderer>()

    companion object {

        // Label is hidden in these params
        private val HIDE_LABEL_TYPES = setOf("bool", "checkbox", "switch")

        // Single Select
        fun getInitialSelectedIndex(actionParamInfo: ActionParamInfo, options: List<SelectItem>): Int {
            actionParamInfo.valueFromShell?.let { value ->
                val index = options.indexOfFirst { it.value == value }
                if (index != -1) return index
            }
            actionParamInfo.value?.let { value ->
                val index = options.indexOfFirst { it.value == value }
                if (index != -1) return index
            }
            return -1
        }

        // Multi Select
        fun getCurrentValues(actionParamInfo: ActionParamInfo): List<String>? {
            val value = actionParamInfo.valueFromShell ?: actionParamInfo.value
            val values = value?.split(actionParamInfo.separator)
            return values
        }

        fun getSelectedFlags(actionParamInfo: ActionParamInfo, options: ArrayList<SelectItem>): BooleanArray {
            val valueSet = getCurrentValues(actionParamInfo)?.toHashSet()
                ?: return BooleanArray(options.size)
            return BooleanArray(options.size) { index ->
                valueSet.contains(options[index].value)
            }
        }

        fun applySelectedState(actionParamInfo: ActionParamInfo, options: MutableList<SelectItem>): List<SelectItem> {
            val valueSet = getCurrentValues(actionParamInfo)?.toHashSet()
            for (option in options) {
                option.selected = valueSet?.contains(option.value) == true
            }
            return options
        }
    }

    fun renderList(actionParamInfos: ArrayList<ActionParamInfo>, fileChooser: FileChooserRender.FileChooserInterface?) {
        for (actionParamInfo in actionParamInfos) {
            val options = actionParamInfo.optionsFromShell
            val render: ParamRenderer =
                if (options != null && actionParamInfo.type !in setOf("app", "packages")) {
                    // Picker
                    if (actionParamInfo.multiple) {
                        MultipleSelectRender(actionParamInfo, context)
                    } else {
                        SingleSelectRender(actionParamInfo, context)
                    }
                } else {
                    when (actionParamInfo.type) {
                        // CheckBox
                        "bool", "checkbox" -> CheckboxRender(actionParamInfo, context)
                        // Switch
                        "switch" -> SwitchRender(actionParamInfo, context)
                        // SeekBar
                        "seekbar" -> SliderRender(actionParamInfo, context)
                        // FileSelector
                        "file", "folder" -> FileChooserRender(actionParamInfo, context, fileChooser)
                        // AppsSelector
                        "app", "packages" -> AppChooserRender(actionParamInfo, context)
                        // ColorPicker
                        "color" -> ColorPickerRender(actionParamInfo, context)

                        else -> {
                            // EditText
                            EditTextRender(actionParamInfo, context)
                        }
                    }
                }

            addRender(actionParamInfo, render)
        }
    }

    private fun addRender(
        actionParamInfo: ActionParamInfo,
        render: ParamRenderer
    ) {
        val view = render.render()
        addToLayout(view, actionParamInfo)
        renderers.add(render)
    }

    private fun addToLayout(inputView: View, actionParamInfo: ActionParamInfo) {
        val binding = KrParamRowBinding.inflate(LayoutInflater.from(context))
        with(binding) {
            // title
            val title = actionParamInfo.title
            krParamTitle.isVisible = !title.isNullOrEmpty()
            krParamTitle.text = title.orEmpty()

            // label
            val label = actionParamInfo.label
            val showLabel = !label.isNullOrEmpty() && !HIDE_LABEL_TYPES.contains(actionParamInfo.type)
            krParamLabel.isVisible = showLabel
            krParamLabel.text = label.orEmpty()
            krParamLabelDivier.isVisible = showLabel

            // desc
            val desc = actionParamInfo.desc
            krParamDesc.isVisible = !desc.isNullOrEmpty()
            krParamDesc.text = desc.orEmpty()

            krParamInput.addView(inputView)
            linearLayout.addView(root)

            (inputView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun getFieldTips(actionParamInfo: ActionParamInfo): String {
        return buildString {
            if (!actionParamInfo.title.isNullOrEmpty()) {
                append(actionParamInfo.title)
                append(" ")
            }
            if (!actionParamInfo.label.isNullOrEmpty()) {
                append(actionParamInfo.label)
                append(" ")
            }
            append("(")
            append(actionParamInfo.name)
            append(") ")
        }
    }

    fun readParamsValue(): HashMap<String, String> {
        val params = HashMap<String, String>()
        for (renderer in renderers) {

            var value: String? = null
            try {
                value = renderer.getValue()
            } catch (e: Exception) {
                throw Exception(getFieldTips(renderer.actionParamInfo) + ": " + e.message)
            }

            val paramName = renderer.paramName

            if (value == null) continue

            if (value.isEmpty() && renderer.actionParamInfo.required) {
                throw Exception(getFieldTips(renderer.actionParamInfo) + context.getString(R.string.do_not_empty))
            } else {
                paramName?.let { name ->
                    params[name] = value
                    // It's out of responsibility of the function,
                    // but reserve that match the orign behavior
                    renderer.actionParamInfo.value = value
                }
            }
        }
        return params
    }
}