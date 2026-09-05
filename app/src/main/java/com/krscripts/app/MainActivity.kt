package com.krscripts.app

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.databinding.ActivityMainBinding
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.KrScriptActionHandler
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.ui.ActionListFragment
import com.krscripts.app.ui.dialog.DialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : KrActivity() {
    private val context = this
    private var krScriptConfig = KrScriptConfig()
    lateinit var binding: ActivityMainBinding
    private val pageConfigCache = mutableListOf<Pair<PageNode, ConfigNode>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        binding.toolbar.apply {
            setTitle(R.string.app_name)
            setOnMenuItemClickListener { menuItem ->
                menuExtra[menuItem.itemId]?.let {
                    onMenuItemClick(it)
                } ?: false
            }
        }

        lifecycleScope.launch {
            progressBarDialog.showDialog(getString(R.string.please_wait))

            krScriptConfig = KrScriptConfig()
            val pageConfigs = krScriptConfig.pageListConfig
            buildPages(pageConfigs)

            progressBarDialog.hideDialog()
        }
    }

    private suspend fun buildPages(
        pageConfigs: List<PageNode>
    ) {
        pageConfigs.forEachIndexed { index, page ->
            val config = page.getConfig(context)

            config?.let {
                config.pageHandlerSh?.let { menuHandler = it }
                pageConfigCache.add(Pair(page, config))
            } ?: Toast.makeText(
                context,
                getString(R.string.page_load_failed, index),
                Toast.LENGTH_SHORT
            ).show()
        }

        val navMenu = binding.bottomNavView.menu
        navMenu.clear()

        binding.toolbar.menu.clear()
        pageConfigCache.forEachIndexed { index, (page, config) ->
            createOptionsMenu(binding.toolbar.menu, binding.fab, config.pageMenuOptions)
            val menuName = config.title ?: page.configPath.substringAfterLast('/')
            navMenu.add(menuName).apply {
                setIcon(R.drawable.baseline_bookmark_24)
                setOnMenuItemClickListener {
                    binding.viewPager.setCurrentItem(index, true)
                    false
                }
            }
        }
        binding.toolbar.menu.add(null)
            .setIcon(R.drawable.baseline_info_24)
            .setOnMenuItemClickListener {
                onInfoAlertClicked()
            }
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        binding.viewPager.apply {
            adapter = PageFragmentAdapter(this@MainActivity, pageConfigCache)
            offscreenPageLimit = 2
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavView.menu[position].isChecked = true
            }
        })
    }

    private fun reloadTab(pageNode: PageNode, index: Int) {
        lifecycleScope.launch {
            val items = pageNode.getConfig(this@MainActivity)
            withContext(Dispatchers.Main) {
                items?.let { newItems ->
                    val itemId = (binding.viewPager.adapter as? PageFragmentAdapter)?.getItemId(index) ?: return@withContext
                    val tag = "f$itemId"
                    val fragment = supportFragmentManager.findFragmentByTag(tag) as? ActionListFragment
                    fragment?.update(newItems.content, getKrScriptActionHandler(pageNode, index))
                }
            }
        }
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, index: Int): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.afterExecution == ActionAfterExecution.FINISH_ACTIVITY) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    reloadTab(pageNode, index)
                }
            }

            override fun createShortcut(clickableNode: ClickableNode, createShortcutHandler: KrScriptActionHandler.CreateShortcutHandler) {
                val page = clickableNode as? PageNode
                    ?: if (clickableNode is RunnableNode) {
                        pageNode
                    } else {
                        return
                    }

                val intent = Intent()

                intent.component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                if (clickableNode is RunnableNode) {
                    intent.putExtra("autoRunItemId", clickableNode.key)
                }
                intent.putExtra("page", page)

                createShortcutHandler.onCreateShortcut(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                openPage(pageNode)
            }
        }
    }

    fun openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    private fun onInfoAlertClicked(): Boolean {
        val layoutInflater = LayoutInflater.from(this)
        val layout = layoutInflater.inflate(R.layout.dialog_about, null)

        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            ".null"
        }

        val tvAppVersion = layout.findViewById<TextView>(R.id.tv_app_version)
        tvAppVersion.text = getString(R.string.app_version, appVersion)

        val frameworkVersion = BuildConfig.FRAMEWORK_VERSION
        val tvFrameworkInfo = layout.findViewById<TextView>(R.id.tv_framework_info)
        tvFrameworkInfo.text = getString(R.string.framework_info, frameworkVersion)

        DialogHelper.animDialog(this, MaterialAlertDialogBuilder(this).setView(layout).setTitle(getString(R.string.title_about)))

        return true
    }

    inner class PageFragmentAdapter(
        activity: FragmentActivity,
        private val configCache: List<Pair<PageNode, ConfigNode>>
    ) : FragmentStateAdapter(activity) {

        private val sessionId: Long = System.nanoTime()

        override fun getItemCount() = configCache.size

        override fun getItemId(position: Int): Long {
            return sessionId + position
        }

        override fun containsItem(itemId: Long): Boolean {
            return itemId in sessionId until (sessionId + configCache.size)
        }

        override fun createFragment(position: Int): Fragment {
            val page = configCache[position].first
            val items = configCache[position].second
            return ActionListFragment.create(items.content, getKrScriptActionHandler(page, position), null, true)
        }
    }
}
