package com.krscripts.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.databinding.ActivityMainBinding
import com.krscripts.app.model.ActionAfterExecution
import com.krscripts.app.model.ClickableNode
import com.krscripts.app.model.ConfigNode
import com.krscripts.app.model.PageNode
import com.krscripts.app.model.RunnableNode
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.ui.page.PageFragment
import com.krscripts.app.ui.page.PageFragmentHost
import kotlinx.coroutines.launch

class MainActivity : KrActivity(), PageFragmentHost {

    private var krScriptConfig = KrScriptConfig()
    lateinit var binding: ActivityMainBinding
    private var pageConfigCache = mutableListOf<PageNode>()

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

            addInfoMenuItem()
        }

        lifecycleScope.launch {
            progressBarDialog.showDialog(getString(R.string.please_wait))

            krScriptConfig = KrScriptConfig()
            pageConfigCache = krScriptConfig.pageListConfig
            binding.viewPager.apply {
                adapter = PageFragmentAdapter(this@MainActivity, pageConfigCache)
                offscreenPageLimit = 2
            }

            buildBottomBar()

            progressBarDialog.hideDialog()
        }
    }

    private fun MaterialToolbar.addInfoMenuItem() {
        menu.add(null)
            .setIcon(R.drawable.baseline_info_24)
            .setOnMenuItemClickListener {
                onInfoAlertClicked()
            }
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun buildBottomBar() {
        val navMenu = binding.bottomNavView.menu
        navMenu.clear()

        binding.toolbar.menu.clear()
        pageConfigCache.forEachIndexed { index, page ->
            val menuName = page.configPath.substringAfterLast('/').ifEmpty {
                page.configShell.substringAfterLast('/')
            }
            navMenu.add(menuName).apply {
                setIcon(R.drawable.baseline_bookmark_24)
                setOnMenuItemClickListener {
                    binding.viewPager.setCurrentItem(index, true)
                    false
                }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavView.menu[position].isChecked = true
            }
        })
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

    override fun onPageConfigLoaded(pageNode: PageNode, config: ConfigNode, pageId: Int) {
        binding.bottomNavView.menu[pageId].title = config.title
        createOptionsMenu(binding.toolbar.menu, binding.fab, config.pageMenuOptions)
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
        binding.toolbar.menu.clear()
        binding.toolbar.addInfoMenuItem()

        val adapter = binding.viewPager.adapter as? PageFragmentAdapter ?: return
        for (position in 0 until adapter.itemCount) {
            val itemId = adapter.getItemId(position)
            val tag = "f$itemId"
            val fragment = supportFragmentManager.findFragmentByTag(tag) as? PageFragment
            fragment?.update()
        }
    }

    inner class PageFragmentAdapter(
        activity: FragmentActivity,
        private val configCache: List<PageNode>
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
            val fragment = PageFragment.newInstance(
                pageConfig = configCache[position],
                pageId = position
            )
            return fragment
        }
    }
}
