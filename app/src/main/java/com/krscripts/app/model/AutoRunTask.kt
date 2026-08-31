package com.krscripts.app.model

interface AutoRunTask {
    fun onCompleted(result: Boolean?)
    val key: String?
}
