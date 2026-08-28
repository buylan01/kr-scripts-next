package com.krscripts.core.ui.param

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.krscripts.core.R
import com.krscripts.core.model.ActionParamInfo
import com.krscripts.core.ui.adapter.AdapterAppChooser
import com.krscripts.core.ui.dialog.DialogAppChooser
import com.krscripts.core.ui.dialog.ProgressBarDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppChooserRender(
    override var actionParamInfo: ActionParamInfo,
    private var context: FragmentActivity,
) : ParamRenderer {
    private lateinit var editText: TextInputEditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var packages: ArrayList<AdapterAppChooser.AppInfo>
    private val packagesReady = CompletableDeferred<Unit>()
    private val progressDialog = ProgressBarDialog(context)
    private var value: String? = null

    override fun getValue(): String? {
        return value
    }

    override fun render(): View {
        val layout = LayoutInflater.from(context).inflate(R.layout.kr_param_edit_text, null)
        editText = layout.findViewById(R.id.kr_param_text)
        inputLayout = layout.findViewById(R.id.textInputLayout)
        editText.apply {
            hint = context.getString(R.string.kr_please_choose_app)
            setCursorVisible(false)
            setFocusable(false)
            setFocusableInTouchMode(false)
        }

        initView()

        inputLayout.apply {
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.baseline_android_24)
            setEndIconOnClickListener { openAppChooser() }
        }

        editText.setOnClickListener { openAppChooser() }

        editText.tag = actionParamInfo.name

        return layout
    }

    private fun openAppChooser() {
        context.lifecycleScope.launch {
            if (!::packages.isInitialized) {
                progressDialog.showDialog()
                packagesReady.await()
                progressDialog.hideDialog()
            }
            setSelectStatus()
            DialogAppChooser(
                packages,
                actionParamInfo.multiple
            ) {
                updateView(it)
            }.show(context.supportFragmentManager, "app-chooser")
        }
    }

    private suspend fun loadPackages(includeMissing: Boolean = false): List<AdapterAppChooser.AppInfo> {
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val currentOptions = actionParamInfo.optionsFromShell

            val filter = currentOptions?.map { it.value }

            val packages = pm.getInstalledPackages(0)
            val filteredPackages = filter?.let { filter ->
                packages.filter { filter.contains(it.packageName) }
            } ?: packages

            val options = filteredPackages.map {
                AdapterAppChooser.AppInfo(
                    appName = "" + it.applicationInfo!!.loadLabel(pm),
                    packageName = it.packageName
                )
            }.toMutableList()

            if (includeMissing) {
                actionParamInfo.optionsFromShell?.let { shellOptions ->
                    val existingPackageNames = options.map { it.packageName }.toSet()
                    val newItems = shellOptions
                        .filter { it.value !in existingPackageNames }
                        .map {
                            AdapterAppChooser.AppInfo(
                                appName = it.title ?: "",
                                packageName = it.value ?: ""
                            )
                        }
                    options.addAll(newItems)
                }
            }

            options
        }
    }

    private fun setSelectStatus() {
        packages.forEach { it.selected = false }

        if (value.isNullOrEmpty()) {
            return
        }

        val packageMap = packages.associateBy { it.packageName }

        packages.forEach { it.selected = false }

        if (actionParamInfo.multiple) {
            value?.split(actionParamInfo.separator)?.forEach { value ->
                packageMap[value]?.selected = true
            }
        } else {
            packageMap[value]?.selected = true
        }
    }

    private fun initView() {
        val lifecycleScope = context.lifecycleScope
        lifecycleScope.launch {
            packages = ArrayList(loadPackages(actionParamInfo.type == "packages"))

            packages.run {
                val packageMap = packages.associateBy { it.packageName }

                if (actionParamInfo.multiple) {
                    ParamLayoutRender.getCurrentValues(actionParamInfo)?.forEach { value ->
                        packageMap[value]?.selected = true
                    }

                    withContext(Dispatchers.Main) {
                        updateView((packages.filter { it.selected }))
                    }
                } else {
                    val preferredPackageName = actionParamInfo.valueFromShell ?: actionParamInfo.value
                    val app = preferredPackageName?.let { packageMap[it] }

                    withContext(Dispatchers.Main) {
                        app?.let {
                            value = it.packageName
                            editText.setText(it.appName)
                        }
                    }
                }
            }

            if (!packagesReady.isCompleted) {
                packagesReady.complete(Unit)
            }
        }
    }

    fun updateView(apps: List<AdapterAppChooser.AppInfo>) {
        value = apps.joinToString(actionParamInfo.separator) { it.packageName }
        editText.setText(apps.joinToString(",") { it.appName })
    }
}
