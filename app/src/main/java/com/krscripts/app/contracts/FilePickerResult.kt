package com.krscripts.app.contracts

import android.net.Uri
import com.krscripts.app.model.FileType

data class FilePickerResult(
    val uri: Uri?
)

sealed class FilePickerRequest {
    abstract val source: Source
    abstract val fileType: FileType
    abstract val isMultiple: Boolean
    abstract val onSelected: (Uri) -> Unit

    data class SystemPicker(
        override val fileType: FileType,
        override val onSelected: (Uri) -> Unit,
        override val isMultiple: Boolean = false,
        val mime: String
    ) : FilePickerRequest() {
        override val source = Source.SYSTEM
    }

    data class InternalPicker(
        override val fileType: FileType,
        override val onSelected: (Uri) -> Unit,
        override val isMultiple: Boolean = false,
        val extension: String
    ) : FilePickerRequest() {
        override val source = Source.INTERNAL
    }

    enum class Source { SYSTEM, INTERNAL }
}