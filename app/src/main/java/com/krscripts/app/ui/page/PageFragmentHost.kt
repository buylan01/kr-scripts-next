package com.krscripts.app.ui.page

import android.content.Intent
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.RunnableNode

interface PageFragmentHost {

    fun onPageConfigLoaded(pageNode: PageNode, config: ConfigNode, pageId: Int)
    fun openSubPage(pageNode: PageNode)
    fun createShortcut(clickableNode: ClickableNode, intent: Intent)
    fun onRunnableNodeCompleted(runnableNode: RunnableNode)
}