package com.projectkr.shell

import android.content.Context
import com.omarea.krscript.executor.ScriptEnvironment
import com.omarea.krscript.model.PageNode
import java.nio.charset.Charset
import kotlin.math.max


class KrScriptConfig {
    fun init(context: Context): KrScriptConfig {
        if (configInfo == null) {

            configInfo = hashMapOf(
                EXECUTOR_CORE to EXECUTOR_CORE_DEFAULT,
                PAGE_LIST_CONFIG to PAGE_LIST_CONFIG_DEFAULT,
                TOOLKIT_DIR to TOOLKIT_DIR_DEFAULT,
                BEFORE_START_SH to BEFORE_START_SH_DEFAULT
            )

            try {
                var fileName = context.getString(R.string.kr_script_config)
                if (fileName.startsWith(ASSETS_FILE_PREFIX)) {
                    fileName = fileName.substring(ASSETS_FILE_PREFIX.length)
                }
                val inputStream = context.assets.open(fileName)
                val bytes = ByteArray(inputStream.available())
                inputStream.read(bytes)
                val rows = String(bytes, Charset.defaultCharset()).split("\n")
                for (row in rows) {
                    val rowText = row.trim()
                    if (!rowText.startsWith("#") && rowText.contains("=")) {
                        val separator = rowText.indexOf("=")
                        val key = rowText.substring(0, separator).trim()

                        val rightSide = rowText.substring(separator + 1).trimStart()
                        val value = if (rightSide.startsWith("\"")) {
                            val endQuote = rightSide.indexOf('"', 1)
                            if (endQuote != -1) {
                                rightSide.substring(1, endQuote).trim()
                            } else {
                                rightSide.substring(1).substringBefore('#').trim()
                            }
                        } else {
                            rightSide.substringBefore('#').trim()
                        }

                        configInfo?.apply {
                            remove(key)
                            put(key, value)
                        }
                    }
                }
            } catch (_: Exception) {

            }

            ScriptEnvironment.init(context, this.executorCore!!, this.toolkitDir)
        }
        return this
    }

    private val executorCore: String?
        get() {
            if (configInfo != null && configInfo!!.containsKey(EXECUTOR_CORE)) {
                return configInfo!![EXECUTOR_CORE]
            }
            return EXECUTOR_CORE_DEFAULT
        }

    private val toolkitDir: String?
        get() {
            if (configInfo != null && configInfo!!.containsKey(TOOLKIT_DIR)) {
                return configInfo!![TOOLKIT_DIR]
            }
            return TOOLKIT_DIR_DEFAULT
        }

    val beforeStartSh: String?
        get() {
            if (configInfo != null && configInfo!!.containsKey(BEFORE_START_SH)) {
                return configInfo!![BEFORE_START_SH]
            }
            return BEFORE_START_SH_DEFAULT
        }

    val pageListConfig: MutableList<PageNode?>
        get() {
            val pageNodes: MutableList<PageNode?> = ArrayList()
            if (configInfo != null) {
                val shConfig = configInfo!![PAGE_LIST_CONFIG_SH]
                val pathConfig = configInfo!![PAGE_LIST_CONFIG]
                if (shConfig != null || pathConfig != null) {
                    val shList = shConfig?.split(", ") ?: emptyList()
                    val pathList = pathConfig?.split(", ") ?: emptyList()
                    val maxLen = max(shList.size, pathList.size)

                    val newNodes = List(maxLen) { i ->
                        PageNode("").apply {
                            if (i < shList.size) pageConfigSh = shList[i]
                            if (i < pathList.size) pageConfigPath = pathList[i]
                        }
                    }
                    pageNodes.addAll(newNodes)
                }
            }
            return pageNodes
        }

    val variables: HashMap<String, String>
        get() = configInfo ?: hashMapOf()

    companion object {
        private const val ASSETS_FILE_PREFIX = "file:///android_asset/"
        private const val TOOLKIT_DIR = "toolkit_dir"
        private const val TOOLKIT_DIR_DEFAULT = ASSETS_FILE_PREFIX + "kr-script/toolkit"
        private const val EXECUTOR_CORE = "executor_core"
        private const val EXECUTOR_CORE_DEFAULT = ASSETS_FILE_PREFIX + "kr-script/executor.sh"
        private const val BEFORE_START_SH = "before_start_sh"
        private const val BEFORE_START_SH_DEFAULT = "" // ASSETS_FILE_PREFIX + "kr-script/before_start.sh"
        private const val PAGE_LIST_CONFIG = "page_list_config"
        private const val PAGE_LIST_CONFIG_SH = "page_list_config_sh"
        private const val PAGE_LIST_CONFIG_DEFAULT = ASSETS_FILE_PREFIX + "kr-script/pages/home.xml, " + ASSETS_FILE_PREFIX + "kr-script/pages/more.xml"
        var configInfo: HashMap<String, String>? = null
    }
}
