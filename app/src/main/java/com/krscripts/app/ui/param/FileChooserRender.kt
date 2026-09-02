package com.krscripts.app.ui.param

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.krscripts.app.R
import com.krscripts.app.contracts.FilePickerRequest
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.model.FileType

class FileChooserRender(
    override var actionParamInfo: ActionParamInfo,
    private var context: Context,
    private var startFilePicker: (FilePickerRequest) -> Unit
): ParamRenderer {
    private var editText: TextInputEditText? = null

    fun setEditTextReadOnly(view: TextInputEditText) {
        view.setCursorVisible(false)
        view.setFocusable(false)
        view.setFocusableInTouchMode(false)
    }

    override fun getValue(): String? {
        return editText?.text?.toString()
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_edit_text, null)
        val inputLayout = layout.findViewById<TextInputLayout>(R.id.textInputLayout)
        editText = layout.findViewById(R.id.kr_param_text)

        editText?.run {
            if (!actionParamInfo.editable) {
                setEditTextReadOnly(this)
            }

            hint = if (actionParamInfo.type == "folder") {
                context.getString(R.string.kr_please_choose_folder)
            } else {
                context.getString(R.string.kr_please_choose_file)
            }

            inputLayout.apply {
                endIconMode = TextInputLayout.END_ICON_CUSTOM
                endIconDrawable =
                    AppCompatResources.getDrawable(context, R.drawable.baseline_folder_24)
                setEndIconOnClickListener {
                    val type = when (actionParamInfo.type) {
                        "folder" -> FileType.FOLDER
                        else -> FileType.FILE
                    }

                    fun onSelected(uri: Uri) {
                        val filePath: String? = uri.path
                        setText(filePath)
                    }

                    val data = if (actionParamInfo.suffix.isNotEmpty() || actionParamInfo.type == "folder") {
                        FilePickerRequest.InternalPicker(
                            fileType = type,
                            extension = actionParamInfo.suffix,
                            isMultiple = actionParamInfo.multiple,
                            onSelected = { onSelected(it) }
                        )
                    } else {
                        FilePickerRequest.SystemPicker(
                            fileType = type,
                            mime = actionParamInfo.mime,
                            isMultiple = actionParamInfo.multiple,
                            onSelected = { onSelected(it) }
                        )
                    }
                    startFilePicker(data)
                }
            }

            if (actionParamInfo.valueFromShell != null) {
                setText(actionParamInfo.valueFromShell)
            } else if (!actionParamInfo.value.isNullOrEmpty()) {
                setText(actionParamInfo.value)
            }

            tag = actionParamInfo.name
        }

        return layout
    }
}
