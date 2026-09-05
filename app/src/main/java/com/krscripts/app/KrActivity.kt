package com.krscripts.app

import android.app.Activity
import android.net.Uri
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.krscripts.app.config.PageConfigReader
import com.krscripts.app.config.PageConfigSh
import com.krscripts.app.config.PathResolver
import com.krscripts.app.contracts.FilePickerContract
import com.krscripts.app.contracts.FilePickerRequest
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.ExecutionMode
import com.krscripts.app.model.FileType
import com.krscripts.app.model.PageMenuOption
import com.krscripts.app.model.PageNode
import com.krscripts.app.shared.FilePathResolver
import com.krscripts.app.shell.ShellHiddenTask
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.dialog.DialogLogFragment
import com.krscripts.app.ui.dialog.ProgressBarDialog
import com.krscripts.app.util.PathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class KrActivity: AppCompatActivity() {

    protected val progressBarDialog = ProgressBarDialog(this)
    protected var menuExtra: MutableMap<Int, PageMenuOption> = mutableMapOf()
    protected var menuHandler: String? = null
    private var pendingFileRequest: FilePickerRequest? = null

    private val launcher = registerForActivityResult(FilePickerContract()) { result ->
        val request = pendingFileRequest
        pendingFileRequest = null
        val uri = result.uri?.let { FilePathResolver().getPath(this, it)?.toUri() }
        if (uri != null && request != null) {
            request.onSelected(uri)
        }
    }

    protected suspend fun PageNode.getConfig(context: Activity, parent: PageNode? = null): ConfigNode? {
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

    protected fun onMenuItemClick(
        menuOption: PageMenuOption
    ): Boolean {
        when(menuOption.type) {
            "refresh", "reload" -> {
                onReload()
            }
            "exit", "finish", "close" -> {
                finish()
            }
            "file" -> {
                menuItemChooseFile(menuOption)
            }
            else -> {
                menuItemExecute(menuOption, HashMap<String, String>().apply{
                    put("state", menuOption.key)
                    put("menu_id", menuOption.key)
                })
            }
        }
        return true
    }

    protected open fun onReload() = recreate()

    protected open fun menuItemExecute(menuOption: PageMenuOption, params: HashMap<String, String>) {
        val onFinish = {
            if (menuOption.afterExecution == ActionAfterExecution.FINISH_ACTIVITY) {
                finish()
            } else if (menuOption.reloadPage) {
                onReload()
            } else if (menuOption.reloadBlock != null) {
                // TODO rootGroup.triggerUpdateByKey(item.updateBlocks!!)
            }
        }

        val scripts = menuHandler ?: "echo Handler not found"

        fun runScripts() {
            if (menuOption.executionMode == ExecutionMode.HIDDEN) {
                ShellHiddenTask.startTask(this, scripts, params, menuOption, onFinish)
            } else {
                val dialog = DialogLogFragment.create(
                    menuOption,
                    scripts,
                    params,
                    onFinish = onFinish,
                    onDismiss = {}
                )
                dialog.show(supportFragmentManager, "")
                dialog.isCancelable = false
            }
        }

        if (menuOption.confirm) {
            DialogHelper.openConfirmAlert(this, menuOption.title, menuOption.desc.ifEmpty { "真的要这么做么" }) {
                runScripts()
            }
        } else {
            runScripts()
        }
    }

    protected fun menuItemChooseFile(menuOption: PageMenuOption) {
        val type = when (menuOption.type) {
            "folder" -> FileType.FOLDER
            else -> FileType.FILE
        }

        fun onSelected(uri: Uri) {
            val path = uri.path
            if (path != null) {
                lifecycleScope.launch(Dispatchers.Main) {
                    menuItemExecute(menuOption, HashMap<String, String>().apply{
                        put("state", menuOption.key)
                        put("menu_id", menuOption.key)
                        put("file", path)
                        put("folder", path)
                    })
                }
            }
        }

        val data = if (menuOption.suffix.isNotEmpty() || menuOption.type == "folder") {
            FilePickerRequest.InternalPicker(
                fileType = type,
                extension = menuOption.suffix,
                onSelected = { onSelected(it) }
            )
        } else {
            FilePickerRequest.SystemPicker(
                fileType = type,
                mime = menuOption.mime,
                onSelected = { onSelected(it) }
            )
        }
        pendingFileRequest = data
        launcher.launch(data)
    }

    protected fun createOptionsMenu(
        menu: Menu,
        fab: FloatingActionButton,
        items: List<PageMenuOption>
    ) {
        items.forEachIndexed { index, item ->
            if (item.isFab) {
                addFab(item, fab)
            } else {
                menu.add(-1, index, index, item.title)
            }
            menuExtra[index] = item
        }
    }

    protected fun addFab(
        menuOption: PageMenuOption,
        fab: FloatingActionButton
    ) {
        fab.run {
            visibility = View.VISIBLE
            setOnClickListener {
                onMenuItemClick(menuOption)
            }

            val iconResolved = when {
                menuOption.type == "file" && menuOption.iconPath.isEmpty() ->
                    AppCompatResources.getDrawable(context, R.drawable.baseline_folder_24)

                menuOption.iconPath.isNotEmpty() -> {
                    if (PathUtil.isNetworkUri(menuOption.iconPath)) {
                        menuOption.iconPath
                    } else {
                        PathResolver(context, menuOption.pageConfigPath)
                            .resolvePath(menuOption.iconPath)
                            ?.absolutePath
                    }
                }

                else -> AppCompatResources.getDrawable(context, R.drawable.baseline_menu_24)
            }

            load(iconResolved) {
                crossfade(true)
                error(R.drawable.baseline_menu_24)
            }
        }
    }
}