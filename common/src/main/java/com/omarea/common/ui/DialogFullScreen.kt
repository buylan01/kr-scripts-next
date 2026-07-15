package com.omarea.common.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.omarea.common.R

open class DialogFullScreen(private val layout: Int) : androidx.fragment.app.DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        currentView = inflater.inflate(layout, container)
        return currentView
    }
    private lateinit var currentView: View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireActivity(), R.style.dialog_full_screen)
    }
}