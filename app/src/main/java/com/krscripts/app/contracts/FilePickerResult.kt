package com.krscripts.app.contracts

import android.net.Uri
import com.krscripts.app.model.FileType

data class FilePickerResult(
    val uri: Uri?
)

sealed class FilePickerRequest {
    abstract val source: Source
    abstract val fileType: FileType
    abstract val onSelected: (Uri) -> Unit

    data class SystemPicker(
        override val fileType: FileType,
        override val onSelected: (Uri) -> Unit,
        val mime: String
    ) : FilePickerRequest() {
        override val source = Source.SYSTEM
    }

    data class InternalPicker(
        override val fileType: FileType,
        override val onSelected: (Uri) -> Unit,
        val extension: String
    ) : FilePickerRequest() {
        override val source = Source.INTERNAL
    }

    enum class Source { SYSTEM, INTERNAL }
}