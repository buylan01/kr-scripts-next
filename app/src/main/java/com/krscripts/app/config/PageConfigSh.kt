package com.krscripts.app.config

import android.app.Activity
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.PageNode
import java.io.ByteArrayInputStream

class PageConfigSh(
    private var activity: Activity,
    private var configShell: String,
    private var configParent: PageNode?
) {

    fun getConfig(): ConfigNode? {
        val result = ScriptEnvironment.execute(activity, configShell, configParent).trim()

        return when {
            result.endsWith(".xml") -> {
                PageConfigReader(activity, result, configParent?.pageConfigPath).readConfigXml()
            }
            result.startsWith("<?xml") && result.endsWith(">") -> {
                val inputStream = ByteArrayInputStream(result.toByteArray())
                PageConfigReader(activity, inputStream).readConfigXml()
            }
            else -> null
        }
    }
}
