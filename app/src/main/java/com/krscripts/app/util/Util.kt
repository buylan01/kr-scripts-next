package com.krscripts.app.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.krscripts.app.R

fun Context.startActivityLink(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
    }
}