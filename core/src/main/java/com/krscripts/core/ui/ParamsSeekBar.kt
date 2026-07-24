package com.krscripts.core.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.slider.RangeSlider
import com.krscripts.core.R
import com.krscripts.core.model.ActionParamInfo

class ParamsSeekBar(private var actionParamInfo: ActionParamInfo, private var context: Context) {
    fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_seekbar, null)
        val rangeSlider = layout.findViewById<RangeSlider>(R.id.kr_param_seekbar)

        val minValue = actionParamInfo.min.toFloat()
        val maxValue = actionParamInfo.max.toFloat()
        rangeSlider.valueFrom = minValue
        rangeSlider.valueTo = maxValue
        rangeSlider.stepSize = 1.0f

        val initialValue = getInitialValue(minValue, maxValue)
        rangeSlider.values = listOf(initialValue)
        rangeSlider.tag = actionParamInfo.name

        val minusBtn = layout.findViewById<ImageButton>(R.id.kr_param_seekbar_minus)
        val plusBtn = layout.findViewById<ImageButton>(R.id.kr_param_seekbar_plus)
        val textView = layout.findViewById<TextView>(R.id.kr_param_seekbar_value)
        textView.text = formatValue(initialValue)

        rangeSlider.addOnChangeListener { _, value, _ ->
            textView.text = formatValue(value)
        }

        minusBtn.setOnClickListener {
            val current = rangeSlider.values[0]
            if (current > minValue) {
                rangeSlider.values = listOf(current - 1)
            }
        }
        plusBtn.setOnClickListener {
            val current = rangeSlider.values[0]
            if (current < maxValue) {
                rangeSlider.values = listOf(current + 1)
            }
        }

        return layout
    }

    private fun getInitialValue(min: Float, max: Float): Float {
        val raw = actionParamInfo.valueFromShell ?: actionParamInfo.value
        val intValue = raw?.toIntOrNull() ?: return min
        return intValue.toFloat().coerceIn(min, max) - actionParamInfo.min
    }

    private fun formatValue(value: Float): String {
        return value.toInt().toString()
    }
}
