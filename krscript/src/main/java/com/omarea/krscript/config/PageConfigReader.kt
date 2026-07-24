package com.omarea.krscript.config

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.util.Log
import android.util.Xml
import android.widget.Toast
import com.omarea.common.model.SelectItem
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.executor.ScriptEnvironment
import com.omarea.krscript.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Locale.getDefault

/**
 * Created by Hello on 2018/04/01.
 * Edited by buylan on 2026/07/16
 */
class PageConfigReader {
    private var context: Context
    private var pageConfig: String = ""
    private var pageConfigAbsPath: String = ""
    private var pageConfigStream: InputStream? = null
    private var parentDir: String = ""

    constructor(context: Context, pageConfig: String, parentDir: String?) {
        this.context = context
        this.pageConfig = pageConfig
        this.parentDir = parentDir ?: ""
    }

    constructor(context: Context, pageConfigStream: InputStream) {
        this.context = context
        this.pageConfigStream = pageConfigStream
    }

    fun readConfigXml(): ArrayList<NodeInfoBase>? {
        if (pageConfigStream != null) {
            return readConfigXml(pageConfigStream!!)
        }
        try {
            val pathAnalysis = PathAnalysis(context, parentDir)
            pathAnalysis.parsePath(pageConfig).run {
                val fileInputStream = this ?: return ArrayList()
                pageConfigAbsPath = pathAnalysis.getCurrentAbsPath()
                return readConfigXml(fileInputStream)
            }
        } catch (ex: Exception) {
            reportParseFailure(ex)
        }
        return null
    }

    private sealed class MainNode {
        data class Page(val node: PageNode) : MainNode()
        data class Action(val node: ActionNode) : MainNode()
        data class Switch(val node: SwitchNode) : MainNode()
        data class Picker(val node: PickerNode) : MainNode()
        data class Text(val node: TextNode) : MainNode()
    }

    private fun readConfigXml(fileInputStream: InputStream): ArrayList<NodeInfoBase>? {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(fileInputStream, "utf-8")
            var type = parser.eventType

            val mainList = ArrayList<NodeInfoBase>()
            var currentGroup: GroupNode? = null
            var current: MainNode? = null
            var isRootNode = true

            fun addFinishedNode(node: NodeInfoBase) {
                val group = currentGroup
                if (group != null) group.children.add(node) else mainList.add(node)
            }

            while (type != XmlPullParser.END_DOCUMENT) {
                when (type) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when {
                            name == "group" -> {
                                currentGroup?.let { if (it.supported) mainList.add(it) }
                                currentGroup = groupNode(parser)
                            }
                            currentGroup?.supported == false -> {
                                // 当前 group 不支持，跳过其内所有项
                            }
                            name == "page" -> {
                                if (!isRootNode) {
                                    current = (clickableNode(PageNode(pageConfigAbsPath), parser) as PageNode?)
                                        ?.let { MainNode.Page(pageNode(it, parser)) }
                                }
                            }
                            name == "action" -> {
                                current = (runnableNode(ActionNode(pageConfigAbsPath), parser) as ActionNode?)
                                    ?.let { MainNode.Action(it) }
                            }
                            name == "switch" -> {
                                current = (runnableNode(SwitchNode(pageConfigAbsPath), parser) as SwitchNode?)
                                    ?.let { MainNode.Switch(it) }
                            }
                            name == "picker" -> {
                                current = (runnableNode(PickerNode(pageConfigAbsPath), parser) as PickerNode?)?.let {
                                    pickerNode(it, parser)
                                    MainNode.Picker(it)
                                }
                            }
                            name == "text" -> {
                                current = (mainNode(TextNode(pageConfigAbsPath), parser) as TextNode?)
                                    ?.let { MainNode.Text(it) }
                            }
                            else -> when (val c = current) {
                                is MainNode.Page -> tagStartInPage(c.node, parser)
                                is MainNode.Action -> tagStartInAction(c.node, parser)
                                is MainNode.Switch -> tagStartInSwitch(c.node, parser)
                                is MainNode.Picker -> tagStartInPicker(c.node, parser)
                                is MainNode.Text -> tagStartInText(c.node, parser)
                                null -> if (name == "resource") resourceNode(parser)
                            }
                        }
                        isRootNode = false
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "group" -> {
                                currentGroup?.let { if (it.supported) mainList.add(it) }
                                currentGroup = null
                            }
                            "page" -> (current as? MainNode.Page)?.let {
                                tagEndInPage(it.node)
                                addFinishedNode(it.node)
                                current = null
                            }
                            "action" -> (current as? MainNode.Action)?.let {
                                tagEndInAction(it.node)
                                addFinishedNode(it.node)
                                current = null
                            }
                            "switch" -> (current as? MainNode.Switch)?.let {
                                tagEndInSwitch(it.node)
                                addFinishedNode(it.node)
                                current = null
                            }
                            "picker" -> (current as? MainNode.Picker)?.let {
                                tagEndInPicker(it.node)
                                addFinishedNode(it.node)
                                current = null
                            }
                            "text" -> (current as? MainNode.Text)?.let {
                                addFinishedNode(it.node)
                                current = null
                            }
                        }
                    }
                }
                type = parser.next()
            }

            return mainList
        } catch (ex: Exception) {
            reportParseFailure(ex)
        }
        return null
    }

    private fun reportParseFailure(ex: Exception) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "解析配置文件失败\n" + ex.message, Toast.LENGTH_LONG).show()
        }
        Log.e("KrConfig Fail！", "" + ex.message, ex)
    }

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == name) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlPullParser.attrAny(vararg names: String): String? {
        for (n in names) {
            attr(n)?.let { return it }
        }
        return null
    }

    private fun isTruthy(value: String?, vararg extraTokens: String): Boolean {
        if (value == null) return false
        return value == "1" || value == "true" || extraTokens.contains(value)
    }

    private fun lower(value: String) = value.lowercase(getDefault()).trim { it <= ' ' }

    private val scriptResultCache = HashMap<String, String>()

    private var virtualRootNode: NodeInfoBase? = null
    private fun executeResultRoot(scriptIn: String): String {
        if (virtualRootNode == null) {
            virtualRootNode = NodeInfoBase(pageConfigAbsPath)
        }
        return ScriptEnvironment.executeResultRoot(context, scriptIn, virtualRootNode)
    }

    /** Same as [executeResultRoot] but memoized by exact script text for this parse pass. */
    private fun executeResultRootCached(scriptIn: String): String =
        scriptResultCache.getOrPut(scriptIn) { executeResultRoot(scriptIn) }

    private var actionParamInfos: ArrayList<ActionParamInfo>? = null
    private var actionParamInfo: ActionParamInfo? = null

    private fun tagStartInAction(action: ActionNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> action.title = parser.nextText()
            "desc" -> descNode(action, parser)
            "summary" -> summaryNode(action, parser)
            "script", "set", "setstate" -> action.setState = parser.nextText().trim()
            "lock", "lock-state" -> action.lockShell = parser.nextText()
            "param" -> {
                if (actionParamInfos == null) actionParamInfos = ArrayList()
                val info = ActionParamInfo()
                actionParamInfo = info
                parseActionParamAttrs(info, parser)
                if (info.supported && !info.name.isNullOrEmpty()) {
                    actionParamInfos!!.add(info)
                }
            }
            "option" -> actionParamInfo?.let { info ->
                if (info.options == null) info.options = ArrayList()
                val option = SelectItem()
                parser.attrAny("val", "value")?.let { option.value = it }
                option.title = parser.nextText()
                if (option.value == null) option.value = option.title
                info.options!!.add(option)
            }
            "resource" -> resourceNode(parser)
        }
    }

    private fun parseActionParamAttrs(info: ActionParamInfo, parser: XmlPullParser) {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            when (attrName) {
                "name" -> info.name = attrValue
                "label" -> info.label = attrValue
                "placeholder" -> info.placeholder = attrValue
                "title" -> info.title = attrValue
                "desc" -> info.desc = attrValue
                "value" -> info.value = attrValue
                "type" -> info.type = lower(attrValue)
                "suffix" -> {
                    val suffix = lower(attrValue)
                    if (info.mime.isEmpty()) info.mime = Suffix2Mime().toMime(suffix)
                    info.suffix = suffix
                }
                "mime" -> info.mime = attrValue.lowercase(getDefault())
                "readonly" -> info.readonly = isTruthy(lower(attrValue), "readonly")
                "maxlength" -> info.maxLength = attrValue.toInt()
                "min" -> info.min = attrValue.toInt()
                "max" -> info.max = attrValue.toInt()
                "required" -> info.required = isTruthy(attrValue, "required")
                "value-sh", "value-su" -> info.valueShell = attrValue
                "options-sh", "option-sh", "options-su" -> {
                    if (info.options == null) info.options = ArrayList()
                    info.optionsSh = attrValue
                }
                "support", "visible" -> {
                    if (executeResultRootCached(attrValue) != "1") info.supported = false
                }
                "multiple" -> info.multiple = isTruthy(attrValue, "multiple")
                "editable" -> info.editable = isTruthy(attrValue, "editable")
                "separator" -> info.separator = attrValue
            }
        }
    }

    private fun tagEndInAction(action: ActionNode) {
        if (action.setState == null) action.setState = ""
        action.params = actionParamInfos
        actionParamInfos = null
        actionParamInfo = null
    }

    private fun tagStartInPage(node: PageNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> node.title = parser.nextText()
            "desc" -> descNode(node, parser)
            "summary" -> summaryNode(node, parser)
            "resource" -> resourceNode(parser)
            "html" -> node.onlineHtmlPage = parser.nextText()
            "config" -> node.pageConfigPath = parser.nextText()
            "handler-sh", "handler", "set", "getstate", "script" -> node.pageHandlerSh = parser.nextText()
            "lock", "lock-state" -> node.lockShell = parser.nextText()
            "option", "page-option", "menu", "menu-item" -> {
                val option = runnableNode(PageMenuOption(pageConfigAbsPath), parser) as PageMenuOption?
                if (option != null) {
                    parser.attr("type")?.let { option.type = it }
                    parser.attr("style")?.let { option.isFab = it == "fab" }
                    parser.attrAny("suffix")?.let {
                        val suffix = lower(it)
                        if (option.mime.isEmpty()) option.mime = Suffix2Mime().toMime(suffix)
                        option.suffix = suffix
                    }
                    parser.attr("mime")?.let { option.mime = it.lowercase(getDefault()) }

                    option.title = parser.nextText()
                    if (option.key.isEmpty()) option.key = option.title

                    if (node.pageMenuOptions == null) node.pageMenuOptions = ArrayList()
                    node.pageMenuOptions?.add(option)
                }
            }
        }
    }

    @Suppress("unused")
    private fun tagEndInPage(page: PageNode) {
        // no-op, kept for symmetry / future use
    }

    private fun pageNode(page: PageNode, parser: XmlPullParser): PageNode {
        parser.attr("config")?.let { page.pageConfigPath = it }
        parser.attr("html")?.let { page.onlineHtmlPage = it }
        parser.attrAny("before-load", "before-read")?.let { page.beforeRead = it }
        parser.attrAny("after-load", "after-read")?.let { page.afterRead = it }
        parser.attrAny("load-ok", "load-success")?.let { page.loadSuccess = it }
        parser.attrAny("load-fail", "load-error")?.let { page.loadFail = it }
        parser.attr("config-sh")?.let { page.pageConfigSh = it }
        parser.attrAny("link", "href")?.let { page.link = it }
        parser.attrAny("activity", "a", "intent")?.let { page.activity = it }
        parser.attrAny("option-sh", "option-su", "options-sh")?.let { page.pageMenuOptionsSh = it }
        parser.attrAny("handler-sh", "handler", "set", "getstate", "script")?.let { page.pageHandlerSh = it }
        return page
    }

    private fun tagStartInSwitch(switchNode: SwitchNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> switchNode.title = parser.nextText()
            "desc" -> descNode(switchNode, parser)
            "summary" -> summaryNode(switchNode, parser)
            "get", "getstate" -> switchNode.getState = parser.nextText()
            "set", "setstate" -> switchNode.setState = parser.nextText()
            "resource" -> resourceNode(parser)
            "lock", "lock-state" -> switchNode.lockShell = parser.nextText()
        }
    }

    private fun tagEndInSwitch(switchNode: SwitchNode) {
        val getState = switchNode.getState
        val shellResult = if (getState.isEmpty()) "" else executeResultRootCached(getState)
        switchNode.checked = shellResult != "error" &&
                (shellResult == "1" || shellResult.lowercase(getDefault()) == "true")
        if (switchNode.setState == null) switchNode.setState = ""
    }

    private fun tagStartInPicker(pickerNode: PickerNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> pickerNode.title = parser.nextText()
            "desc" -> descNode(pickerNode, parser)
            "summary" -> summaryNode(pickerNode, parser)
            "option" -> {
                if (pickerNode.options == null) pickerNode.options = ArrayList()
                val option = SelectItem()
                parser.attrAny("val", "value")?.let { option.value = it }
                option.title = parser.nextText()
                if (option.value == null) option.value = option.title
                pickerNode.options!!.add(option)
            }
            "getstate", "get" -> pickerNode.getState = parser.nextText()
            "setstate", "set" -> pickerNode.setState = parser.nextText()
            "resource" -> resourceNode(parser)
            "lock", "lock-state" -> pickerNode.lockShell = parser.nextText()
        }
    }

    private fun pickerNode(pickerNode: PickerNode, parser: XmlPullParser) {
        parser.attrAny("option-sh", "options-sh", "options-su")?.let {
            if (pickerNode.options == null) pickerNode.options = ArrayList()
            pickerNode.optionsSh = it
        }
        parser.attr("multiple")?.let { pickerNode.multiple = isTruthy(it, "multiple") }
        parser.attr("separator")?.let { pickerNode.separator = it }
    }

    private fun tagEndInPicker(pickerNode: PickerNode) {
        val getState = pickerNode.getState
        if (getState == null) {
            pickerNode.getState = ""
        } else {
            pickerNode.value = executeResultRootCached(getState)
        }
        if (pickerNode.setState == null) pickerNode.setState = ""
    }

    private fun tagStartInText(textNode: TextNode, parser: XmlPullParser) {
        when (parser.name) {
            "title" -> textNode.title = parser.nextText()
            "desc" -> descNode(textNode, parser)
            "summary" -> summaryNode(textNode, parser)
            "slice" -> rowNode(textNode, parser)
            "resource" -> resourceNode(parser)
        }
    }

    private fun rowNode(textNode: TextNode, parser: XmlPullParser) {
        val textRow = TextNode.TextRow()
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i).lowercase(getDefault())
            val attrValue = parser.getAttributeValue(i)
            try {
                when (attrName) {
                    "bold", "b" -> textRow.bold = isTruthy(attrValue, "bold")
                    "italic", "i" -> textRow.italic = isTruthy(attrValue, "italic")
                    "underline", "u" -> textRow.underline = isTruthy(attrValue, "underline")
                    "foreground", "color" -> textRow.color = Color.parseColor(attrValue)
                    "bg", "background", "bgcolor" -> textRow.bgColor = Color.parseColor(attrValue)
                    "size" -> textRow.size = attrValue.toInt()
                    "break" -> textRow.breakRow = isTruthy(attrValue, "break")
                    "link", "href" -> textRow.link = attrValue
                    "activity", "a", "intent" -> textRow.activity = attrValue
                    "script", "run" -> textRow.onClickScript = attrValue
                    "sh" -> textRow.dynamicTextSh = attrValue
                    "align" -> when (attrValue) {
                        "left" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            textRow.align = Layout.Alignment.ALIGN_NORMAL
                        }
                        "right" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            textRow.align = Layout.Alignment.ALIGN_OPPOSITE
                        }
                        "center" -> textRow.align = Layout.Alignment.ALIGN_CENTER
                        "normal" -> textRow.align = Layout.Alignment.ALIGN_NORMAL
                    }
                }
            } catch (ex: Exception) {
                Log.w("KrConfig", "解析 slice 属性 '$attrName=$attrValue' 失败: ${ex.message}")
            }
        }
        textRow.text = parser.nextText().trim()
        textNode.rows.add(textRow)
    }

    private fun groupNode(parser: XmlPullParser): GroupNode {
        val groupInfo = GroupNode(pageConfigAbsPath)
        parser.attrAny("key", "index", "id")?.let { groupInfo.key = it.trim() }
        parser.attr("title")?.let { groupInfo.title = it }
        parser.attrAny("support", "visible")?.let {
            groupInfo.supported = executeResultRootCached(it) == "1"
        }
        return groupInfo
    }

    private fun clickableNode(clickableNode: ClickableNode, parser: XmlPullParser): ClickableNode? {
        val base = mainNode(clickableNode, parser) as? ClickableNode? ?: return null

        parser.attrAny("lock", "lock-state", "locked")?.let {
            base.locked = isTruthy(it, "locked")
        }
        parser.attrAny("min-sdk", "sdk-min")?.let { base.minSdkVersion = it.trim().toInt() }
        parser.attrAny("max-sdk", "sdk-max")?.let { base.maxSdkVersion = it.trim().toInt() }
        parser.attrAny("target-sdk", "sdk-target")?.let { base.targetSdkVersion = it.trim().toInt() }
        parser.attrAny("icon", "icon-path")?.let { base.iconPath = it.trim() }
        parser.attrAny("logo", "logo-path")?.let { base.logoPath = it.trim() }
        parser.attr("allow-shortcut")?.let {
            base.allowShortcut = isTruthy(it, "allow", "allow-shortcut")
        }

        if (base.key.isNotEmpty() && base.key.startsWith("@") && base.allowShortcut == null) {
            base.allowShortcut = false
        }
        return base
    }

    private fun runnableNode(node: RunnableNode, parser: XmlPullParser): RunnableNode? {
        val base = clickableNode(node, parser) as? RunnableNode? ?: return null

        parser.attr("confirm")?.let { base.confirm = isTruthy(it) }
        parser.attrAny("warn", "warning")?.let { base.warning = it }
        parser.attrAny("auto-off", "auto-close")?.let { base.autoOff = isTruthy(it, "auto-close", "auto-off") }
        parser.attr("auto-finish")?.let { base.autoFinish = isTruthy(it, "auto-finish") }
        parser.attrAny("interruptible", "interruptable")?.let {
            base.interruptable = it.isEmpty() || isTruthy(it, "interruptable")
        }
        parser.attr("reload-page")?.let {
            if (isTruthy(it, "reload-page", "reload", "page")) base.reloadPage = true
        }
        parser.attr("reload")?.let {
            if (isTruthy(it, "reload-page", "reload", "page")) {
                base.reloadPage = true
            } else if (it.isNotEmpty()) {
                base.updateBlocks = it.split(",").map { s -> s.trim() }
                    .dropLastWhile { s -> s.isEmpty() }.toTypedArray()
            }
        }
        parser.attr("shell")?.let { base.shell = it }
        parser.attrAny("bg-task", "background-task", "async-task")?.let {
            if (isTruthy(it, "async-task", "async", "bg-task", "background", "background-task")) {
                base.shell = RunnableNode.shellModeBgTask
            }
        }
        return base
    }

    private fun mainNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser): NodeInfoBase? {
        parser.attrAny("key", "index", "id")?.let { nodeInfoBase.key = it.trim() }
        parser.attr("title")?.let { nodeInfoBase.title = it }
        parser.attr("desc")?.let { nodeInfoBase.desc = it }
        parser.attrAny("support", "visible")?.let {
            if (executeResultRootCached(it) != "1") return null
        }
        parser.attr("desc-sh")?.let {
            nodeInfoBase.descSh = it
            nodeInfoBase.desc = executeResultRootCached(it)
        }
        parser.attr("summary")?.let { nodeInfoBase.summary = it }
        parser.attr("summary-sh")?.let {
            nodeInfoBase.summarySh = it
            nodeInfoBase.summary = executeResultRootCached(it)
        }
        return nodeInfoBase
    }

    private fun descNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        parser.attrAny("su", "sh", "desc-sh")?.let {
            nodeInfoBase.descSh = it
            nodeInfoBase.desc = executeResultRootCached(it)
        }
        if (nodeInfoBase.desc.isEmpty()) nodeInfoBase.desc = parser.nextText()
    }

    private fun summaryNode(nodeInfoBase: NodeInfoBase, parser: XmlPullParser) {
        parser.attrAny("su", "sh", "summary-sh")?.let {
            nodeInfoBase.summarySh = it
            nodeInfoBase.summary = executeResultRootCached(it)
        }
        if (nodeInfoBase.summary.isEmpty()) nodeInfoBase.summary = parser.nextText()
    }

    private fun resourceNode(parser: XmlPullParser) {
        parser.attr("file")?.let { ExtractAssets(context).extractResource(it.trim()) }
        parser.attr("dir")?.let { ExtractAssets(context).extractResources(it.trim()) }
    }
}