package com.krscripts.common.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.common.R

open class ProgressBarDialog(private var context: Activity) {
    private var alert: AlertDialog? = null
    private var textView: TextView? = null

    private val handler = Handler(Looper.getMainLooper())

    private var pendingShow: Runnable? = null


    init {
        hideDialog()
    }

    private fun isActivityUsable(): Boolean {
        if (context.isFinishing) return false
        if (context.isDestroyed) return false
        return true
    }


    fun hideDialog() {

        pendingShow?.let { handler.removeCallbacks(it) }
        pendingShow = null

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
    fun showDialog(text: String = "加载中…", delayMillis: Long = 300L): ProgressBarDialog {

        if (alert != null && textView != null) {
            textView!!.text = text
            return this
        }

        if (pendingShow != null) {
            pendingText = text
            return this
        }

        if (delayMillis <= 0) {
            showDialog(text)
            return this
        }

        pendingText = text
        val runnable = Runnable {
            pendingShow = null
            showDialog(pendingText)
        }
        pendingShow = runnable
        handler.postDelayed(runnable, delayMillis)
        return this
    }

    private var pendingText: String = ""

    private fun showDialog(text: String): ProgressBarDialog {

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
