package com.krscripts.app.model

import android.content.Intent
import android.view.View

interface KrScriptActionHandler {
    fun onSubPageClick(pageNode: PageNode)
    fun onActionCompleted(runnableNode: RunnableNode)
    fun createShortcut(clickableNode: ClickableNode, createShortcutHandler: CreateShortcutHandler)
    fun openParamsPage(actionNode: ActionNode, view: View, onCompleted: Runnable): Boolean {
        return false
    }

    interface CreateShortcutHandler {
        fun onCreateShortcut(clickableNode: ClickableNode, intent: Intent?)
    }
}
