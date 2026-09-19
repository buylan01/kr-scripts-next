package com.krscripts.app.ui.page

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.krscripts.app.ActionPage
import com.krscripts.app.R
import com.krscripts.app.TryOpenActivity
import com.krscripts.app.config.PageConfigReader
import com.krscripts.app.config.PageConfigSh
import com.krscripts.app.contracts.FilePickerContract
import com.krscripts.app.contracts.FilePickerRequest
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ActionNode
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.model.AutoRunTask
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.ExecutionMode
import com.krscripts.app.model.GroupNode
import com.krscripts.app.model.NodeInfoBase
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.PickerNode
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.model.SwitchNode
import com.krscripts.app.shared.FilePathResolver
import com.krscripts.app.shell.ShellBackground
import com.krscripts.app.shell.ShellHiddenTask
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.dialog.DialogItemChooser
import com.krscripts.app.ui.dialog.DialogLogFragment
import com.krscripts.app.ui.param.ParamLayoutRender
import com.krscripts.app.ui.param.ParamUtil
import com.krscripts.app.ui.param.ParamsDialogFragment
import com.krscripts.app.ui.param.ParamsDialogFragment.Companion.BUNDLE_KEY_PARAMS
import com.krscripts.app.ui.param.ParamsDialogFragment.Companion.BUNDLE_KEY_STATUS
import com.krscripts.app.ui.param.ParamsDialogFragment.Companion.REQUEST_KEY_PARAMS
import com.krscripts.app.ui.param.ParamsDialogFragment.Companion.STATUS_CANCEL
import com.krscripts.app.ui.param.ParamsDialogFragment.Companion.STATUS_CONFIRM
import com.krscripts.app.ui.param.ParamsResult
import com.krscripts.app.ui.widget.ListItemGroup
import com.krscripts.app.util.startActivityLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageFragment: Fragment(), PageLayoutRender.OnItemClickListener {

    private var pageConfig: PageNode? = null
    private var host: PageFragmentHost? = null
    private var autoRunItemId: String? = null
    private var pageId: Int = 0

    companion object {
        fun newInstance(
            pageConfig: PageNode,
            autoRunItemId: String? = null,
            pageId: Int = 0
        ): PageFragment {
            val fragment = PageFragment()
            val args = Bundle()
            args.putSerializable("pageConfig", pageConfig)
            args.putString("autoRunItemId", autoRunItemId)
            args.putInt("pageId", pageId)
            fragment.arguments = args
            return fragment
        }
    }

    private var pendingFileRequest: FilePickerRequest? = null
    private val launcher = registerForActivityResult(FilePickerContract()) { result ->
        val request = pendingFileRequest
        pendingFileRequest = null
        val uri = result.uri?.let { FilePathResolver().getPath(requireContext(), it)?.toUri() }
        if (uri != null && request != null) {
            request.onSelected(uri)
        }
    }

    private var actionInfos: ArrayList<NodeInfoBase>? = null
    private lateinit var loadingHelper: LoadingHelper
    private var autoRunTask: AutoRunTask? = null
    private var actionsLoaded = false
    private lateinit var rootGroup: ListItemGroup

    fun update() {
        loadContent()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? PageFragmentHost
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            pageConfig = it.getSerializable("pageConfig") as? PageNode
            autoRunItemId = it.getString("autoRunItemId")
            pageId = it.getInt("pageId", 0)
        }
        if (pageConfig == null) {
            throw IllegalStateException("PageFragment requires a PageNode argument")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingHelper = LoadingHelper(view.findViewById<ViewGroup>(R.id.loading_container))

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.page_content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        loadContent()
    }

    private fun loadContent() {
        val contentView = view?.findViewById<NestedScrollView?>(R.id.page_content)

        contentView?.alpha = 0f
        rootGroup = ListItemGroup(this.requireContext(), true, GroupNode(""))

        lifecycleScope.launch {
            loadPageConfig(pageConfig!!)
            if (actionInfos != null) {
                PageLayoutRender(
                    this@PageFragment.requireContext(),
                    actionInfos!!,
                    this@PageFragment,
                    rootGroup
                )
                val layout = rootGroup.getView()

                contentView?.removeAllViews()
                contentView?.addView(layout)
                triggerAction(autoRunTask)
            }

            contentView?.animate()?.alpha(1f)?.setDuration(220)?.start()
        }
    }

    private suspend fun showDialog(msg: String) = withContext(Dispatchers.Main) {
        loadingHelper.showDialog(msg)
    }

    private suspend fun hideDialog() = withContext(Dispatchers.Main) {
        loadingHelper.hideDialog()
    }

    private suspend fun loadPageConfig(
        pageConfig: PageNode
    ) = withContext(Dispatchers.IO){
        val activity = this@PageFragment.requireActivity()
            pageConfig.run {
                if (beforeRead.isNotEmpty()) {
                    showDialog(getString(R.string.kr_page_before_load))
                    ScriptEnvironment.execute(activity, beforeRead, this)
                }

                showDialog(getString(R.string.kr_page_loading))

                val config = getConfig(activity, this)

                if (afterRead.isNotEmpty()) {
                    showDialog(getString(R.string.kr_page_after_load))
                    ScriptEnvironment.execute(activity, afterRead, this)
                }

                config?.let { config ->
                    if (loadSuccess.isNotEmpty()) {
                        showDialog(getString(R.string.kr_page_load_success))
                        ScriptEnvironment.execute(activity, loadSuccess, this)
                    }

                    withContext(Dispatchers.Main) {
                        val autoRunTask = if (actionsLoaded) null else object : AutoRunTask {
                            override val key = autoRunItemId
                            override fun onCompleted(result: Boolean?) {
                                if (result != true) {
                                    Toast.makeText(
                                        activity,
                                        getString(R.string.kr_auto_run_item_losted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        host?.onPageConfigLoaded(pageConfig, config, pageId)

                        this@PageFragment.autoRunTask = autoRunTask
                        this@PageFragment.actionInfos = config.content
                        hideDialog()
                        actionsLoaded = true
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        loadingHelper.postFailed(getString(R.string.kr_page_load_fail))
                    }
                    if (loadFail.isNotEmpty()) {
                        ScriptEnvironment.execute(activity, loadFail, this)
                    }
                }
            }
    }

    private suspend fun PageNode.getConfig(context: Activity, parent: PageNode? = null): ConfigNode? {
        return withContext(Dispatchers.IO) {
            when {
                configShell.isNotEmpty() -> {
                    PageConfigSh(context, configShell, parent).getConfig()
                }

                configPath.isNotEmpty() -> {
                    PageConfigReader(context, configPath, pageConfigPath).readConfigXml()
                }

                else -> null
            }
        }
    }

    // ----------------------
    //    ActionPageContent
    // ----------------------

    private fun triggerAction(autoRunTask: AutoRunTask?) {
        autoRunTask?.run {
            if (!key.isNullOrEmpty()) {
                onCompleted(rootGroup.triggerActionByKey(key!!))
            }
        }
    }

    private fun checkNodeLocked(clickableNode: ClickableNode): Boolean {

        // Check for system sdk
        val currentSDK = Build.VERSION.SDK_INT
        val requiredSdk = clickableNode.targetSdkVersion
        val requiredMinSdk = clickableNode.minSdkVersion
        val requiredMaxSdk = clickableNode.maxSdkVersion
        val lockedMessage = when {
            requiredSdk != null && currentSDK != requiredSdk -> {
                getString(R.string.kr_sdk_discrepancy) to getString(R.string.kr_sdk_discrepancy_message).format(requiredSdk)
            }
            requiredMaxSdk != null && currentSDK > requiredMaxSdk -> {
                getString(R.string.kr_sdk_overtop) to getString(R.string.kr_sdk_message).format(requiredMinSdk, requiredMaxSdk)
            }
            requiredMinSdk != null && currentSDK < requiredMinSdk -> {
                getString(R.string.kr_sdk_too_low) to getString(R.string.kr_sdk_message).format(requiredMinSdk, requiredMaxSdk)
            }
            else -> null
        }
        lockedMessage?.let {
            DialogHelper.openInfoAlert(
                context = context ?: return true,
                title = it.first,
                message = it.second
            )
            return true
        }

        // Check with script
        var message = ""
        val locked = if (clickableNode.lockShell.isNotEmpty()) {
            message = ScriptEnvironment.execute(requireContext(), clickableNode.lockShell, clickableNode)
            message !in setOf("unlock", "unlocked", "false", "0")
        } else {
            clickableNode.locked
        }

        if (locked) {
            Toast.makeText(context, message.ifEmpty { getString(R.string.kr_lock_message) }, Toast.LENGTH_SHORT).show()
        }
        return locked
    }

    override fun onPageClick(item: PageNode, onCompleted: () -> Unit) {
        val context = context ?: return
        val locked = checkNodeLocked(item)
        if (locked) return

        when {
            item.link.isNotEmpty() -> {
                context.startActivityLink(item.link)
            }
            item.activity.isNotEmpty() -> {
                TryOpenActivity(context, item.activity).tryOpen()
            }
            else -> {
                host?.openSubPage(item)
            }
        }
    }

    // 长按 添加收藏
    override fun onItemLongClick(clickableNode: ClickableNode) {
        val context = context ?: return

        if (clickableNode.key.isEmpty()) {
            DialogHelper.openConfirmAlert(
                context,
                getString(R.string.kr_shortcut_create_fail),
                getString(R.string.kr_ushortcut_nsupported)
            )
        } else {
            var page = clickableNode as? PageNode
            if (page == null) {
                if (clickableNode is RunnableNode) {
                    page = pageConfig
                } else {
                    return
                }
            }

            val intent = Intent()

            intent.component = ComponentName(this@PageFragment.requireContext().applicationContext, ActionPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            if (clickableNode is RunnableNode) {
                intent.putExtra("autoRunItemId", clickableNode.key)
            }

            intent.putExtra("page", page)
            host?.createShortcut(clickableNode, intent)
        }
    }

    // Switch

    override fun onSwitchClick(item: SwitchNode, onCompleted: () -> Unit) {
        val toValue = !item.checked
        onRunnableItemClick(item) { switchExecute(item, toValue, onCompleted) }
    }

    private fun switchExecute(switchNode: SwitchNode, toValue: Boolean, onFinish: () -> Unit) {
        val script = switchNode.setScript ?: ""
        actionExecute(switchNode, script, hashMapOf("state" to if (toValue) "1" else "0"), onFinish)
    }

    // Picker

    override fun onPickerClick(item: PickerNode, onCompleted: () -> Unit) {
        onRunnableItemClick(item) { pickerExecute(item, onCompleted) }
    }

    private fun pickerExecute(item: PickerNode, onCompleted: () -> Unit) {
        val paramInfo = ActionParamInfo()
        paramInfo.options = item.options
        paramInfo.optionsSh = item.optionsSh
        paramInfo.separator = item.separator

        loadingHelper.showDialog(getString(R.string.kr_param_options_load))

        lifecycleScope.launch(Dispatchers.IO) {
            // 获取当前值
            item.getScript?.let {
                paramInfo.valueFromShell = executeScriptGetResult(it, item)
            }

            // 获取可选项（合并options-sh和静态options的结果）
            val options = ParamUtil.getParamOptions(requireContext(), paramInfo, item)
            val optionsSorted = options?.let {
                ParamLayoutRender.applySelectedState(paramInfo, options)
            }

            withContext(Dispatchers.IO) {
                loadingHelper.hideDialog()

                if (optionsSorted != null) {
                    DialogItemChooser(optionsSorted, item.multiple, onConfirm = { items, _ ->
                        if (item.multiple) {
                            pickerOnConfirm(
                                item,
                                (items.map { it.value }).joinToString(item.separator),
                                onCompleted
                            )
                        } else {
                            if (items.isNotEmpty()) {
                                pickerOnConfirm(
                                    item,
                                    items[0].value.toString(),
                                    onCompleted
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    getString(R.string.picker_select_none),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }).show(requireActivity().supportFragmentManager, "picker-item-chooser")
                } else {
                    Toast.makeText(context, getString(R.string.picker_not_item), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }

    private fun pickerOnConfirm(pickerNode: PickerNode, toValue: String, onFinish: () -> Unit) {
        val script = pickerNode.setScript ?: ""
        actionExecute(pickerNode, script, hashMapOf("state" to toValue), onFinish)
    }

    // Action

    override fun onActionClick(item: ActionNode, onCompleted: () -> Unit) {
        val ignoreWarning = !item.params.isNullOrEmpty()
        onRunnableItemClick(item, ignoreWarning) { actionExecute(item, onCompleted) }
    }

    private fun actionExecute(action: ActionNode, onFinish: () -> Unit) {
        val script = action.script ?: ""

        action.params?.let {
            if (it.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val isLongList = it.size > 4

                    val dialog = ParamsDialogFragment.newInstance(
                        paramNodes = it,
                        parentNode = action,
                        isDialog = !isLongList
                    )

                    parentFragmentManager.setFragmentResultListener(
                        REQUEST_KEY_PARAMS,
                        viewLifecycleOwner
                    ) { _, bundle ->
                        if (
                            bundle.getInt(
                                BUNDLE_KEY_STATUS,
                                STATUS_CANCEL
                            ) != STATUS_CONFIRM
                        ) {
                            return@setFragmentResultListener
                        } else {
                            try {
                                val params = BundleCompat.getParcelable(
                                    bundle,
                                    BUNDLE_KEY_PARAMS,
                                    ParamsResult::class.java
                                )
                                actionExecute(action, script, params?.values, onFinish)
                            } catch (ex: Exception) {
                                Toast.makeText(
                                    context,
                                    ex.message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    dialog.show(parentFragmentManager, "params")
                }

                return
            }
        }

        actionExecute(action, script, null, onFinish)
    }

    // Common on runnable click

    private fun onRunnableItemClick(
        item: RunnableNode,
        ignoreWarning: Boolean = false,
        onExecute: () -> Unit
    ) {
        val isLocked = checkNodeLocked(item)
        if (isLocked) return

        when {
            item.confirm -> {
                DialogHelper.openConfirmAlert(
                    context = requireActivity(),
                    title = item.title,
                    message = item.desc,
                    onConfirm = { onExecute() }
                )
            }

            item.warning.isNotEmpty() && !ignoreWarning -> {
                // May change in days, keep
                DialogHelper.openConfirmAlert(
                    context = requireActivity(),
                    title = item.title,
                    message = item.warning,
                    onConfirm = { onExecute() }
                )
            }

            else -> {
                onExecute()
            }
        }
    }

    private fun executeScriptGetResult(shellScript: String, nodeInfoBase: NodeInfoBase): String {
        return ScriptEnvironment.execute(this.requireContext(), shellScript, nodeInfoBase)
    }

    private fun onRunnableNodeComplete(nodeInfo: RunnableNode) {
        if (nodeInfo.reloadPage) {
            update()
        }
        host?.onRunnableNodeCompleted(nodeInfo)
    }

    private var runningTasks = mutableListOf<String>()
    private fun actionExecute(
        nodeInfo: RunnableNode,
        script: String,
        params: HashMap<String, String>?,
        onFinish: () -> Unit
    ) {
        val context = context ?: return

        when (nodeInfo.executionMode) {
            ExecutionMode.NORMAL -> {
                val dialog = DialogLogFragment.create(
                    nodeInfo = nodeInfo,
                    script = script,
                    params = params,
                    onFinish = onFinish,
                    onDismiss = { onRunnableNodeComplete(nodeInfo) }
                )
                dialog.isCancelable = false
                dialog.show(parentFragmentManager, null)
            }

            ExecutionMode.BACKGROUND -> {
                ShellBackground.startTask(context, script, params, nodeInfo) {
                    onFinish()
                    onRunnableNodeComplete(nodeInfo)
                }
            }

            ExecutionMode.HIDDEN -> {
                val index = nodeInfo.index
                if (index in runningTasks) {
                    Toast.makeText(context, getString(R.string.kr_hidden_task_running), Toast.LENGTH_SHORT).show()
                } else {
                    runningTasks.add(index)
                    ShellHiddenTask.startTask(context, script, params, nodeInfo) {
                        onFinish()
                        runningTasks.remove(index)
                        onRunnableNodeComplete(nodeInfo)
                    }
                }
            }
        }
    }
}