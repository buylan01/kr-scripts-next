package com.krscripts.app

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.krscripts.app.config.IconPathAnalysis
import com.krscripts.app.config.PageConfigReader
import com.krscripts.app.config.PageConfigSh
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.ExecutionMode
import com.krscripts.app.model.PageMenuOption
import com.krscripts.app.model.PageNode
import com.krscripts.app.shell.ShellHiddenTask
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.dialog.DialogLogFragment
import com.krscripts.app.ui.dialog.ProgressBarDialog
import com.krscripts.app.ui.param.FileChooserRender
import com.krscripts.app.util.chooseFilePath
import com.krscripts.app.util.handleFileSelectorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class KrActivity: AppCompatActivity() {

    protected val progressBarDialog = ProgressBarDialog(this)
    protected var menuExtra: MutableMap<Int, PageMenuOption> = mutableMapOf()
    protected var menuHandler: String? = null
    protected var fileSelectorInterface: FileChooserRender.FileSelectedInterface? = null

    protected suspend fun PageNode.getConfig(context: Activity, parent: PageNode? = null): ConfigNode? {
        return withContext(Dispatchers.IO) {
            when {
                pageConfigSh.isNotEmpty() -> {
                    PageConfigSh(context, pageConfigSh, parent).getConfig()
                }

                pageConfigPath.isNotEmpty() -> {
                    val parent = pageConfigDir.substringBeforeLast("/")
                    PageConfigReader(context.applicationContext, pageConfigPath, parent).readConfigXml()
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
        fileSelectorInterface = object: FileChooserRender.FileSelectedInterface{
            override fun onFileSelected(path: String?) {
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

            override fun mimeType(): String? {
                return menuOption.mime.ifEmpty { null }
            }

            override fun suffix(): String? {
                return menuOption.suffix.ifEmpty { null }
            }

            override fun type(): Int {
                return when(menuOption.type) {
                    "folder" -> FileChooserRender.FileSelectedInterface.TYPE_FOLDER
                    "file" -> FileChooserRender.FileSelectedInterface.TYPE_FILE
                    else -> FileChooserRender.FileSelectedInterface.TYPE_FILE
                }
            }
        }
        fileSelectorInterface?.let { chooseFilePath(it) }
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

            if (menuOption.type == "file" && menuOption.iconPath.isEmpty()) {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.baseline_folder_24))
            } else if (menuOption.iconPath.isNotEmpty()) {
                lifecycleScope.launch {
                    val icon = IconPathAnalysis().loadLogo(context, menuOption, false)
                    if (icon != null) {
                        setImageDrawable(icon)
                    } else {
                        setImageDrawable(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.baseline_menu_24
                            )
                        )
                    }
                }
            } else {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.baseline_menu_24))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleFileSelectorResult(this, resultCode, requestCode, data, fileSelectorInterface)
        fileSelectorInterface = null
        super.onActivityResult(requestCode, resultCode, data)
    }
}