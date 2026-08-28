package com.krscripts.core.util

fun isExitError(code: Any?): Boolean {
    return when (code) {
        null -> false
        is Int -> code != 0
        is String -> code.isNotEmpty()
        else -> false
    }
}