package com.krscripts.app.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.krscripts.app.ActivityFileSelector

class FilePickerContract : ActivityResultContract<FilePickerRequest, FilePickerResult>() {

    override fun createIntent(context: Context, input: FilePickerRequest): Intent {
        val intent = when (input) {
            is FilePickerRequest.SystemPicker -> {
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, input.isMultiple)
                    type = input.mime
                }
            }
            is FilePickerRequest.InternalPicker -> {
                Intent(context, ActivityFileSelector::class.java).apply {
                    putExtra("extension", input.extension)
                    putExtra("mode", input.fileType.ordinal)
                    putExtra("multiple", input.isMultiple)
                }
            }
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): FilePickerResult {

        val uri = if (resultCode == Activity.RESULT_OK) intent?.data else null

        return FilePickerResult(uri)
    }
}