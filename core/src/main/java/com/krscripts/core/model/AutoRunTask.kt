package com.krscripts.core.model

interface AutoRunTask {
    fun onCompleted(result: Boolean?)
    val key: String?
}
