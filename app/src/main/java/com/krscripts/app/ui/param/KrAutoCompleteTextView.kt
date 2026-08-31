package com.krscripts.app.ui.param

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class KrAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialAutoCompleteTextView(context, attrs) {

    var showMenuAsDialog: Boolean = false
    var selectedValue: String? = null

    override fun showDropDown() {
        if (!showMenuAsDialog) {
            super.showDropDown()
        }
    }

}