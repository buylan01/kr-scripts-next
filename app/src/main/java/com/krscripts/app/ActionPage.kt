package com.krscripts.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.krscripts.app.databinding.ActivityActionPageBinding
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.PageMenuOption
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.shortcut.ActionShortcutManager
import com.krscripts.app.ui.page.PageFragment
import com.krscripts.app.ui.page.PageFragmentHost
import com.krscripts.app.ui.page.PageMenuLoader


open class ActionPage : KrActivity(), PageFragmentHost {

    private lateinit var pageConfigCompat: PageNode
    private var autoRunItemId: String? = null
    private lateinit var binding: ActivityActionPageBinding
    private var fragment: PageFragment? = null

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
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
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

                if (page.htmlPage.isNotEmpty()) {
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

        if (pageConfigCompat.configPath.isEmpty() && pageConfigCompat.configShell.isEmpty()) {
            setResult(2)
            finish()
        }

        if (savedInstanceState == null) {
            fragment = PageFragment.newInstance(
                pageConfig = pageConfigCompat,
                autoRunItemId = autoRunItemId
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_list, fragment!!)
                .commitNow()
        }
    }

    override fun onPageConfigLoaded(pageNode: PageNode, config: ConfigNode, pageId: Int) {
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
    }

    override fun openSubPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    override fun createShortcut(
        clickableNode: ClickableNode,
        intent: Intent
    ) {
        createShortcut(intent, clickableNode)
    }

    override fun onRunnableNodeCompleted(runnableNode: RunnableNode) {
        if (runnableNode.afterExecution == ActionAfterExecution.FINISH_ACTIVITY) {
            finish()
        }
    }

    override fun onReload() {
        fragment?.update()
    }
}
