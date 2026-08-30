package com.krscripts.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.databinding.ActivitySplashBinding
import com.krscripts.core.executor.ScriptEnvironment
import com.krscripts.core.shell.KeepShellPublic
import com.krscripts.core.shell.ShellExecutor
import com.krscripts.core.ui.dialog.DialogHelper
import com.krscripts.core.util.PermissionUtil.checkAccessFiles
import com.krscripts.core.util.PermissionUtil.requestAccessFilesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.IOException
import kotlin.coroutines.resume

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    lateinit var binding: ActivitySplashBinding
    private var logs = ArrayList<String>()
    private var isRoot: Boolean? = null
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
        binding.splashLogView.text = getString(R.string.pop_permission_checking)
        lifecycleScope.launch {
            checkRoot {
                withContext(Dispatchers.Main) {
                    checkFileManage {
                        startToFinish()
                    }
                }
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

    private suspend fun checkRoot(next: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            while (true) {
                isRoot = KeepShellPublic.checkRoot()
                if (isRoot == true) {
                    next()
                    return@withContext
                } else {
                    val shouldRetry = suspendCancellableCoroutine { continuation ->
                        lifecycleScope.launch {
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
                    }

                    if (!shouldRetry) {
                        next()
                        return@withContext
                    }
                }
            }
        }
    }

    private fun startToFinish() {
        val config = KrScriptConfig().init(this)
        lifecycleScope.launch {
            if (config.beforeStartSh.isNotEmpty()) {
                runBeforeStart(this@SplashActivity, config) { log ->
                    onLogOutput(binding.splashLogView, log)
                }
            } else {
                binding.splashLogView.text = getString(R.string.pop_started)
            }
            gotoHome()
        }

    }

    private fun gotoHome() {
        val intent = if (intent.getBooleanExtra("JumpActionPage", false)) {
            Intent(this, ActionPage::class.java).putExtras(intent)
        } else {
            Intent(this, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.fade_in_fast,
            0
        )
        startActivity(intent, options.toBundle())
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
                process = if (isRoot == true) ShellExecutor.superUserRuntime else ShellExecutor.runtime

                DataOutputStream(process.outputStream).use { outputStream ->
                    ScriptEnvironment.executeAsync(
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