package com.krscripts.app.ui.page

import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.textview.MaterialTextView
import com.krscripts.app.R

open class LoadingHelper(
    private val loadingContainer: View
) {
    private var loadingIndicator: LoadingIndicator? = null
    private var loadingText: MaterialTextView? = null

    private val handler = Handler(Looper.getMainLooper())

    private var pendingShow: Runnable? = null


    init {
        hideDialog()
    }

    fun hideDialog() {

        pendingShow?.let { handler.removeCallbacks(it) }
        pendingShow = null

        loadingContainer.visibility = View.GONE
        loadingIndicator = null
    }

    fun showDialog(text: String = "加载中…", delayMillis: Long = 300L) {

        if (loadingIndicator != null && loadingText != null) {
            loadingText!!.text = text
            return
        }

        if (pendingShow != null) {
            pendingText = text
            return
        }

        if (delayMillis <= 0) {
            showDialog(text)
            return
        }

        pendingText = text
        val runnable = Runnable {
            pendingShow = null
            showDialog(pendingText)
        }
        pendingShow = runnable
        handler.postDelayed(runnable, delayMillis)
        return
    }

    private var pendingText: String = ""

    private fun showDialog(text: String) {

        if (loadingText != null && loadingIndicator != null) {
            loadingText!!.text = text
        } else {
            hideDialog()

            loadingText = loadingContainer.findViewById(R.id.loading_text)!!
            loadingText!!.text = text
            loadingIndicator = loadingContainer.findViewById(R.id.loading_indicator)
            loadingContainer.visibility = View.VISIBLE
        }

        return
    }
}