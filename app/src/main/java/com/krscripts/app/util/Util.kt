package com.krscripts.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.krscripts.app.ActivityFileSelector
import com.krscripts.app.R
import com.krscripts.app.shared.FilePathResolver
import com.krscripts.app.ui.param.FileChooserRender

fun Context.startActivityLink(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
    }
}

private fun Activity.startFileSelector(extension: String? = null, mode: Int = ActivityFileSelector.MODE_FILE) {
    try {
        val intent = Intent(this, ActivityFileSelector::class.java)
        extension?.let { intent.putExtra("extension", it) }
        intent.putExtra("mode", mode)
        this.startActivityForResult(intent, ActivityFileSelector.ACTION_FILE_PATH_CHOOSER_INNER)
    } catch (_: Exception) {
        Toast.makeText(this, "启动内置文件选择器失败！", Toast.LENGTH_SHORT).show()
    }
}

fun Activity.chooseFilePath(
    fileSelectedInterface: FileChooserRender.FileSelectedInterface
): Boolean {
    return try {
        if (fileSelectedInterface.type() == FileChooserRender.FileSelectedInterface.TYPE_FOLDER) {
            this.startFileSelector(mode = ActivityFileSelector.MODE_FOLDER)
        } else {
            val suffix = fileSelectedInterface.suffix()
            if (!suffix.isNullOrEmpty()) {
                this.startFileSelector(suffix)
            } else {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                val mimeType = fileSelectedInterface.mimeType()
                if (mimeType != null) {
                    intent.type = mimeType
                } else {
                    intent.type = "*/*"
                }
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                startActivityForResult(intent, ActivityFileSelector.ACTION_FILE_PATH_CHOOSER)
            }
        }
        true
    } catch (_: java.lang.Exception) {
        false
    }
}

fun handleFileSelectorResult(
    context: Context,
    resultCode: Int,
    requestCode: Int,
    data: Intent?,
    fileSelectorInterface: FileChooserRender.FileSelectedInterface?,
    useUri: Boolean = false
) {
    if (resultCode != Activity.RESULT_OK) return

    if (requestCode == ActivityFileSelector.ACTION_FILE_PATH_CHOOSER) {
        if (useUri) {
            val path = getPath(context, data?.data)
            fileSelectorInterface?.onFileSelected(path)
            fileSelectorInterface?.onFileSelected(data?.data)
        } else {
            val path = getPath(context, data?.data)
            fileSelectorInterface?.onFileSelected(path)
        }
    } else if (requestCode == ActivityFileSelector.ACTION_FILE_PATH_CHOOSER_INNER) {
        val result = data?.getStringExtra("file")
        fileSelectorInterface?.onFileSelected(result)
    }
}

private fun getPath(context: Context, uri: Uri?): String? {
    return try {
        uri?.let { FilePathResolver().getPath(context, uri) }
    } catch (_: java.lang.Exception) {
        null
    }
}