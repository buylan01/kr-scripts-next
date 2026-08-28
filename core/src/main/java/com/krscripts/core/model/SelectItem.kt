package com.krscripts.core.model

class SelectItem(
    var icon: String? = null,
    var iconClip: String? = null,
    var title: String? = null,
    var desc: String? = null,
    var value: String? = null,
    var selected: Boolean = false
) {
    override fun toString(): String {
        return when {
            !title.isNullOrEmpty() -> title!!
            !value.isNullOrEmpty() -> value!!
            else -> ""
        }
    }
}