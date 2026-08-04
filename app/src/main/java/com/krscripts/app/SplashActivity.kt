package com.krscripts.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.databinding.ActivitySplashBinding
import com.krscripts.common.shell.KeepShellPublic
import com.krscripts.common.shell.ShellExecutor.getRuntime
import com.krscripts.common.ui.DialogHelper
import com.krscripts.common.util.PermissionType
import com.krscripts.common.util.PermissionUtil.checkAccessFiles
import com.krscripts.common.util.PermissionUtil.ensureShizukuPermission
import com.krscripts.common.util.PermissionUtil.requestAccessFilesDialog
import com.krscripts.core.executor.ScriptEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import java.io.IOException
import kotlin.coroutines.resume

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    lateinit var binding: ActivitySplashBinding
    private var logs = ArrayList<String>()
    private var permissionType = PermissionType.NONE
    private val manageFileRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkFileManage { startToFinish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScriptEnvironment.isInitialed) {
            if (isTaskRoot) {
                gotoHome()
            }
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        checkPermissions()
    }

    private fun checkPermissions() {
        binding.startStateText.text = getString(R.string.pio_permission_checking)
        lifecycleScope.launch {
            checkRootOrShizuku()

            withContext(Dispatchers.Main) {
                checkFileManage {
                    startToFinish()
                }
            }
        }
    }

    private suspend fun checkRootOrShizuku() {
        while (true) {

            if (KeepShellPublic.checkRoot()) permissionType = PermissionType.ROOT

            if (permissionType == PermissionType.ROOT) {
                return
            }

            if (Shizuku.pingBinder()) {
                val shizukuGranted = ensureShizukuPermission()
                if (shizukuGranted) {
                    permissionType = PermissionType.ADB_ROOT
                    return
                }
            }

            val shouldRetry = suspendCancellableCoroutine { continuation ->
                val dialog = requestRoot(
                    this@SplashActivity,
                    onRetry = {
                        continuation.resume(true)
                    },
                    onSkip = {
                        continuation.resume(false)
                    }
                )
                continuation.invokeOnCancellation {
                    dialog?.dismiss()
                }
            }
            if (!shouldRetry) {
                return
            }
        }
    }

    private fun checkFileManage(next: () -> Unit) {
        if (!checkAccessFiles(this)) {
            requestAccessFilesDialog(this@SplashActivity, manageFileRequester) {
                next()
            }
        } else {
            next()
        }
    }

    private fun startToFinish() {
        val config = KrScriptConfig().init(this)
        lifecycleScope.launch {
            if (config.beforeStartSh.isNotEmpty()) {
                runBeforeStart(this@SplashActivity, config) { log ->
                    onLogOutput(binding.startStateText, log)
                }
            } else {
                binding.startStateText.text = getString(R.string.pop_started)
            }
            gotoHome()
        }

    }

    private fun gotoHome() {
        if (intent.getBooleanExtra("JumpActionPage", false)) {
            val actionPage = Intent(applicationContext, ActionPage::class.java)
            actionPage.putExtras(intent)
            startActivity(actionPage)
        } else {
            val home = Intent(applicationContext, MainActivity::class.java)
            startActivity(home)
        }
        finish()
    }

    suspend fun onLogOutput(
        logView: TextView,
        log: String
    ) {
        withContext(Dispatchers.Main) {
            synchronized(logs) {
                val ignore = logs.size > 6
                if (ignore) {
                    logs.removeFirstOrNull()
                }
                logs.add(log)
                logView.text = logs.joinToString("\n", if (ignore) "……\n" else "").trim()
            }
        }
    }

    private suspend fun runBeforeStart(
        context: Context,
        config: KrScriptConfig,
        onLog: suspend (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                process = getRuntime(permissionType)

                DataOutputStream(process.outputStream).use { outputStream ->
                    ScriptEnvironment.executeShell(
                        context,
                        outputStream,
                        config.beforeStartSh,
                        config.variables,
                        null,
                        "splash"
                    )
                }

                coroutineScope {
                    launch(Dispatchers.IO) {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { onLog(it) }
                        }
                    }
                    launch(Dispatchers.IO) {
                        process.errorStream.bufferedReader().useLines { lines ->
                            lines.forEach { onLog(it) }
                        }
                    }
                    withContext(Dispatchers.IO) {
                        process.waitFor()
                    }
                }
            } catch (e: IOException) {
                onLog(e.localizedMessage ?: "beforeStart failed")
            } finally {
                process?.destroy()
            }
        }
    }

    private fun requestRoot(
        context: Context,
        onRetry: () -> Unit,
        onSkip: () -> Unit
    ): Dialog? {
        val prefs = context.getSharedPreferences("app_settings", MODE_PRIVATE)
        val hasSkipped = prefs.getBoolean("skip_root", false)
        if (hasSkipped) {
            onSkip()
            return null
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.error_root_title))
            .setCancelable(false)
            .setMessage(R.string.error_root_message)
            .setPositiveButton(R.string.btn_retry) { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton(R.string.btn_exit) { dialog, _ ->
                dialog.dismiss()
                (context as? Activity)?.finishAffinity()
            }
        if (!context.resources.getBoolean(R.bool.force_root)) {
            builder.setNeutralButton(com.krscripts.core.R.string.btn_skip) { dialog, _ ->
                dialog.dismiss()
                prefs.edit { putBoolean("skip_root", true) }
                onSkip()
            }
        }
        return DialogHelper.animDialog(context, builder)
    }
}