package com.krscripts.app

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.krscripts.app.model.PageNode

class OpenPageHelper(private var activity: Activity) {
    fun openPage(pageNode: PageNode) {
        try {
            val intent = when {
                pageNode.htmlPage.isNotEmpty() -> {
                    Intent(activity, ActionPageOnline::class.java)
                        .putExtra("config", pageNode.htmlPage)
                }

                pageNode.configShell.isNotEmpty() -> {
                    Intent(activity, ActionPage::class.java)
                }

                pageNode.configPath.isNotEmpty() -> {
                    Intent(activity, ActionPage::class.java)
                }

                else -> null
            }

            intent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("page", pageNode)
            }

            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}
