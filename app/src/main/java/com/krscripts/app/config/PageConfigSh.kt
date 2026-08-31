package com.krscripts.app.config

import android.app.Activity
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.PageNode
import java.io.ByteArrayInputStream

class PageConfigSh(
    private var activity: Activity,
    private var pageConfigSh: String,
    private var parentConfig: PageNode?
) {

    fun getConfig(): ConfigNode? {
        val result = ScriptEnvironment.execute(activity, pageConfigSh, parentConfig).trim()

        return when {
            result.endsWith(".xml") -> {
                PageConfigReader(activity, result, parentConfig?.pageConfigDir).readConfigXml()
            }
            result.startsWith("<?xml") && result.endsWith(">") -> {
                val inputStream = ByteArrayInputStream(result.toByteArray())
                PageConfigReader(activity, inputStream).readConfigXml()
            }
            else -> null
        }
    }
}
