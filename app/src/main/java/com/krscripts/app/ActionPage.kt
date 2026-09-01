package com.krscripts.app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.krscripts.app.databinding.ActivityActionPageBinding
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.AutoRunTask
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.KrScriptActionHandler
import com.krscripts.app.model.PageMenuOption
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.shortcut.ActionShortcutManager
import com.krscripts.app.ui.ActionListFragment
import com.krscripts.app.ui.PageMenuLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


open class ActionPage : KrActivity() {

    private var actionsLoaded = false
    private lateinit var pageConfigCompat: PageNode
    private var autoRunItemId: String? = null
    private lateinit var binding: ActivityActionPageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Jump to splash when is no initialed
        if (!ScriptEnvironment.isInitialed) {
            val initIntent = Intent(this.applicationContext, SplashActivity::class.java)
            initIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            initIntent.putExtras(this.intent)
            initIntent.putExtra("JumpActionPage", true)
            startActivity(initIntent)
            finish()
            return
        }

        enableEdgeToEdge()

        binding = ActivityActionPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        binding.toolbar.apply {
            setTitle(R.string.app_name)
            setNavigationOnClickListener {
                finish()
            }
            setOnMenuItemClickListener { menuItem ->
                menuExtra[menuItem.itemId]?.let {
                    onMenuItemClick(it)
                } ?: false
            }
        }

        intent?.extras?.let { extras ->

            val page = when {
                extras.containsKey("page") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getSerializable(
                    "page",
                    PageNode::class.java
                ) else @Suppress("DEPRECATION") extras.getSerializable("page") as PageNode

                extras.containsKey("shortcutId") -> ActionShortcutManager(this).getShortcutTarget(
                    extras.getString("shortcutId")
                )

                else -> null
            }

            page?.let { page ->
                autoRunItemId =
                    if (extras.containsKey("autoRunItemId")) extras.getString("autoRunItemId") else null

                if (page.activity.isNotEmpty()) {
                    if (TryOpenActivity(this, page.activity).tryOpen()) {
                        finish()
                        return
                    }
                }

                if (page.onlineHtmlPage.isNotEmpty()) {
                    try {
                        startActivity(Intent(this, ActionPageOnline::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("page", page)
                        })
                    } catch (_: Exception) {
                    }
                }

                if (page.link.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, page.link.toUri())
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this.startActivity(intent)
                }

                if (page.title.isNotEmpty()) {
                    binding.toolbar.title = page.title
                }
                pageConfigCompat = page
            } ?: {
                Toast.makeText(this, "页面信息无效", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        if (pageConfigCompat.pageConfigPath.isEmpty() && pageConfigCompat.pageConfigSh.isEmpty()) {
            setResult(2)
            finish()
        }

        loadPageConfig()
    }

    private var actionShortClickHandler = object : KrScriptActionHandler {
        override fun onActionCompleted(runnableNode: RunnableNode) {
            if (runnableNode.afterExecution == ActionAfterExecution.FINISH_ACTIVITY) {
                finishAndRemoveTask()
            } else if (runnableNode.reloadPage) {
                loadPageConfig()
            }
        }

        override fun createShortcut(clickableNode: ClickableNode, createShortcutHandler: KrScriptActionHandler.CreateShortcutHandler) {
            val page = clickableNode as? PageNode
                ?: if (clickableNode is RunnableNode) {
                    pageConfigCompat
                } else {
                    return
                }

            val intent = Intent()

            intent.component = ComponentName(this@ActionPage.applicationContext, ActionPage::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            if (clickableNode is RunnableNode) {
                intent.putExtra("autoRunItemId", clickableNode.key)
            }

            intent.putExtra("page", page)

            createShortcutHandler.onCreateShortcut(clickableNode, intent)
        }

        override fun onSubPageClick(pageNode: PageNode) {
            OpenPageHelper(this@ActionPage).openPage(pageNode)
        }
    }

    private suspend fun showDialog(msg: String) = withContext(Dispatchers.Main) {
        progressBarDialog.showDialog(msg)
    }

    private suspend fun hideDialog() = withContext(Dispatchers.Main) {
        progressBarDialog.hideDialog()
    }

    private fun loadPageConfig() {
        val activity = this

        lifecycleScope.launch(Dispatchers.IO) {
            pageConfigCompat.run {
                if (beforeRead.isNotEmpty()) {
                    showDialog(getString(R.string.kr_page_before_load))
                    ScriptEnvironment.execute(activity, beforeRead, this)
                }

                showDialog(getString(R.string.kr_page_loading))

                val config = getConfig(this@ActionPage, this)

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
                                        this@ActionPage,
                                        getString(R.string.kr_auto_run_item_losted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        val menuOptions: ArrayList<PageMenuOption> = ArrayList()

                        PageMenuLoader(applicationContext, pageConfigCompat).load()?.let {
                            menuOptions.addAll(it)
                        }

                        config.pageMenuOptions.let {
                            menuOptions.addAll(it)
                        }

                        binding.toolbar.menu.clear()
                        createOptionsMenu(binding.toolbar.menu, binding.actionPageFab, menuOptions)

                        menuHandler = if (config.pageHandlerSh.isNullOrEmpty()) {
                            pageConfigCompat.pageHandlerSh
                        } else {
                            (if (pageConfigCompat.pageHandlerSh.isNotEmpty()) "echo 已忽略引用处handler" else "") + config.pageHandlerSh
                        }

                        val fragment = ActionListFragment.create(
                            config.content,
                            actionShortClickHandler,
                            autoRunTask
                        )
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.main_list, fragment)
                            .commitAllowingStateLoss()
                        hideDialog()
                        actionsLoaded = true
                    }
                } ?: if (loadFail.isNotEmpty()) {
                        showDialog(getString(R.string.kr_page_load_fail))
                        ScriptEnvironment.execute(activity, loadFail, this)
                        hideDialog()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ActionPage,
                                getString(R.string.kr_page_load_fail),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        hideDialog()
                        finish()
                    }
            }
        }
    }
}
