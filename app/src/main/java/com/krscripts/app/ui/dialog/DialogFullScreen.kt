package com.krscripts.app.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.google.android.material.color.MaterialColors
import com.krscripts.app.R

open class DialogFullScreen(private val layout: Int) : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        currentView = inflater.inflate(layout, container)
        return currentView
    }
    private lateinit var currentView: View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireActivity(), R.style.dialog_full_screen)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.setOnShowListener {
                dialog.window?.apply {
                    val surfaceColor = MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        Color.WHITE
                    )
                    setBackgroundDrawable(surfaceColor.toDrawable().apply { alpha = 128 })
                    setBackgroundBlurRadius(40)
                }
            }
        }
        return dialog
    }
}