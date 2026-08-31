package com.krscripts.app.ui.param

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.krscripts.app.R
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.ui.dialog.DialogHelper
import java.util.Locale

class ColorPickerRender(
    override var actionParamInfo: ActionParamInfo,
    private val context: Context
) : ParamRenderer {

    private var editText: EditText? = null

    override fun getValue(): String? {
        try {
            return editText?.text?.toString()
        } catch (_: Exception) {
            throw Exception(context.getString(R.string.kr_invalid_color))
        }
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_color, null)
        editText = layout.findViewById(R.id.kr_param_color_text)
        val invalidView = layout.findViewById<ImageView>(R.id.kr_param_color_invalid)
        val preview = layout.findViewById<View>(R.id.kr_param_color_preview)

        editText?.apply {
            tag = actionParamInfo.name
            doOnTextChanged { text, _, _, _ ->
                updateColorPreview(invalidView, preview, text.toString())
            }
            if (actionParamInfo.valueFromShell != null) {
                setText(actionParamInfo.valueFromShell!!)
            } else if (actionParamInfo.value != null) {
                setText(actionParamInfo.value!!)
            }

            updateColorPreview(invalidView, preview, this.text.toString())
            layout.findViewById<View>(R.id.kr_param_color_picker).setOnClickListener {
                openColorPicker(this, invalidView, preview)
            }
        }

        return layout
    }

    private fun updateColorPreview(
        invalidView: ImageView,
        preview: View,
        colorStr: String
    ): Boolean {
        try {
            val color = colorStr.toColorInt()
            invalidView.visibility = View.GONE
            preview.visibility = View.VISIBLE
            preview.background = color.toDrawable()
            return true
        } catch (_: Exception) {
            invalidView.visibility = View.VISIBLE
            preview.visibility = View.GONE
            return false
        }
    }

    private fun currentColor(colorStr: CharSequence?): Int {
        if (!colorStr.isNullOrEmpty()) {
            try {
                return colorStr.toString().toColorInt()
            } catch (_: Exception) {
            }
        }
        return (0xff000000).toInt()
    }

    private fun openColorPicker(textView: TextView, invalidView: ImageView, preview: View) {
        val view = LayoutInflater.from(context).inflate(R.layout.kr_color_picker, null)
        val defValue = currentColor(textView.text)

        val picker = view.findViewById<ColorPickerView>(R.id.color_picker_view)
        val alphaBar = view.findViewById<Slider>(R.id.color_alpha)
        val colorPreview = view.findViewById<View>(R.id.color_preview)
        val colorPreviewText = view.findViewById<TextView>(R.id.color_preview_text)

        picker.setColor(defValue)
        alphaBar.value = Color.alpha(defValue).toFloat()

        alphaBar.stepSize = 1f

        fun refreshPreview() {
            val color = picker.getColor(alphaBar.value.toInt())
            colorPreview.setBackgroundColor(color)
            colorPreviewText.text = parseHexStr(
                alphaBar.value.toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
        }

        picker.setOnColorChangedListener(object : ColorPickerView.OnColorChangedListener {
            override fun onColorChanged(hue: Float, saturation: Float, value: Float) {
                refreshPreview()
            }
        })

        alphaBar.addOnChangeListener { _, _, _ ->
            refreshPreview()
        }

        refreshPreview()

        DialogHelper.animDialog(
            context,
            MaterialAlertDialogBuilder(context)
                .setTitle(
                    actionParamInfo.label
                        ?: actionParamInfo.placeholder.ifEmpty { context.getString(R.string.kr_color_picker) })
                .setView(view)
                .setPositiveButton(context.getString(R.string.btn_confirm)) { _, _ ->
                    val alpha = alphaBar.value.toInt()
                    val color = picker.getColor(alpha)

                    textView.text = parseHexStr(
                        alpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )

                    invalidView.visibility = View.GONE
                    preview.visibility = View.VISIBLE
                    preview.background = color.toDrawable()
                }
                .setNegativeButton(context.getString(R.string.btn_cancel)) { _, _ -> }
        )
    }

    private fun parseHexStr(a: Int, r: Int, g: Int, b: Int): String {
        return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
    }
}