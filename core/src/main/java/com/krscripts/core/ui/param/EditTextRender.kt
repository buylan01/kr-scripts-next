package com.krscripts.core.ui.param

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.krscripts.core.R
import com.krscripts.core.model.ActionParamInfo

class EditTextRender(
    override var actionParamInfo: ActionParamInfo,
    private var context: Context
): ParamRenderer {

    private var editText: TextInputEditText? = null
    private var inputLayout: TextInputLayout? = null

    override fun getValue(): String? {
        if (inputLayout?.isErrorEnabled == true) {
            throw Exception(inputLayout?.error.toString())
        }
        return editText?.text?.toString()
    }

    private fun validateNumber(value: String?): String? {
        value ?: return null
        value.ifEmpty { return null }
        return try {
            val value = value.toInt()
            if (value < actionParamInfo.min) {
                "值应大于等于 ${actionParamInfo.min}"
            } else if (value > actionParamInfo.max) {
                "值应小于等于 ${actionParamInfo.max}"
            } else {
                null
            }
        } catch (_: NumberFormatException) {
            "不是数字"
        }
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_edit_text, null)
        inputLayout = layout.findViewById(R.id.textInputLayout)
        editText = layout.findViewById(R.id.kr_param_text)

        val isNumber = (actionParamInfo.type == "int" || actionParamInfo.type == "number")
        val isLimitNumber = isNumber && (actionParamInfo.min != Int.MIN_VALUE || actionParamInfo.max != Int.MAX_VALUE)

        editText?.run {
            tag = actionParamInfo.name
            isEnabled = !actionParamInfo.readonly

            // initial text
            setText(actionParamInfo.valueFromShell ?: actionParamInfo.value)

            // hint
            hint = if (actionParamInfo.placeholder.isNotEmpty()) {
                actionParamInfo.placeholder
            } else if (isLimitNumber) {
                "${actionParamInfo.min} ~ ${actionParamInfo.max}"
            } else {
                context.getString(if (isNumber) R.string.kr_hint_number else R.string.kr_hint_text)
            }

            // fliter input
            if (isNumber) {
                inputType = when(actionParamInfo.type) {
                    "int" -> InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_CLASS_NUMBER
                    "number" -> InputType.TYPE_CLASS_NUMBER
                    else -> InputType.TYPE_CLASS_TEXT
                }
            }
            if (actionParamInfo.maxLength > -1) {
                filters += InputFilter.LengthFilter(actionParamInfo.maxLength)
            }

            // validate
            if (actionParamInfo.required || isLimitNumber) {
                fun validateRequired(text: CharSequence?) {
                    val text = text?.toString()
                    val isEmptyError = text.isNullOrEmpty() && actionParamInfo.required
                    val digitError = if (isLimitNumber) validateNumber(text) else null
                    inputLayout?.apply {
                        isErrorEnabled = isEmptyError || (digitError != null)
                        error = if (isEmptyError) "此项不能为空" else digitError
                    }
                }

                doOnTextChanged { text, _, _, _ ->
                    validateRequired(text)
                }

                validateRequired(text)
            }
        }

        return layout
    }
}
