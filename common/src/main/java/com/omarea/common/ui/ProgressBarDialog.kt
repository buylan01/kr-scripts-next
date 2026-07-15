package com.omarea.common.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omarea.common.R

open class ProgressBarDialog(private var context: Activity) {
    private var alert: AlertDialog? = null
    private var textView: TextView? = null

    init {
        hideDialog()
    }

    private fun isActivityUsable(): Boolean {
        if (context.isFinishing) return false
        if (context.isDestroyed) return false
        return true
    }


    fun hideDialog() {
        try {
            if (alert != null) {
                alert!!.dismiss()
                alert!!.hide()
                alert = null
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("InflateParams")
    fun showDialog(text: String = "加载中…"): ProgressBarDialog {

        if (!isActivityUsable()) {
            return this
        }

        if (textView != null && alert != null) {
            textView!!.text = text
        } else {
            hideDialog()
            val layoutInflater = LayoutInflater.from(context)
            val dialog = layoutInflater.inflate(R.layout.dialog_loading, null)
            textView = (dialog.findViewById(R.id.dialog_text)!!)
            textView!!.text = text
            alert = MaterialAlertDialogBuilder(context).setView(dialog).setCancelable(false).show()
        }

        return this
    }
}
