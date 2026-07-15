package com.projectkr.shell

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.omarea.common.shell.ShellExecutor
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import com.projectkr.shell.permissions.CheckRootStatus
import com.projectkr.shell.util.PermissionUtil.checkManageFile
import com.projectkr.shell.util.PermissionUtil.showManageFileDialog
import java.io.BufferedReader
import java.io.DataOutputStream

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    lateinit var binding: ActivitySplashBinding

    private val manageFileRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkFileManage { startToFinish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScriptEnvironmen.isInited()) {
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

    /**
     * 开始检查必需权限
     */
    private fun checkPermissions() {
        binding.startLogo.visibility = View.VISIBLE
        checkRoot {
            binding.startStateText.text = getString(R.string.pio_permission_checking)
            hasRoot = true
            checkFileManage {
                startToFinish()
            }
        }
    }

    private fun checkFileManage(next: Runnable) {
        Thread {
            CheckRootStatus.grantPermission(this)
            if (!checkManageFile(this)) {
                myHandler.post {
                    showManageFileDialog(this, manageFileRequester) {
                        next.run()
                    }
                }
            } else {
                next.run()
            }
        }.start()
    }

    private var hasRoot = false
    private var myHandler = Handler(Looper.getMainLooper())

    private fun checkRoot(next: Runnable) {
        CheckRootStatus(this, next).forceGetRoot()
    }

    /**
     * 启动完成
     */
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)

        val config = KrScriptConfig().init(this)
        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(this, config, UpdateLogViewHandler(binding.startStateText) {
                gotoHome()
            }).start()
        } else {
            gotoHome()
        }
    }

    private fun gotoHome() {
        if (this.intent != null && this.intent.hasExtra("JumpActionPage") && this.intent.getBooleanExtra("JumpActionPage", false)) {
            val actionPage = Intent(this.applicationContext, ActionPage::class.java)
            actionPage.putExtras(this.intent)
            startActivity(actionPage)
        } else {
            val home = Intent(this.applicationContext, MainActivity::class.java)
            startActivity(home)
        }
        finish()
    }

    private class UpdateLogViewHandler(private var logView: TextView, private val onExit: Runnable) {
        private val handler = Handler(Looper.getMainLooper())
        private var notificationMessageRows = ArrayList<String>()
        private var someIgnored = false

        fun onLogOutput(log: String) {
            handler.post {
                synchronized(notificationMessageRows) {
                    if (notificationMessageRows.size > 6) {
                        notificationMessageRows.remove(notificationMessageRows.first())
                        someIgnored = true
                    }
                    notificationMessageRows.add(log)
                    logView.text =
                        notificationMessageRows.joinToString("\n", if (someIgnored) "……\n" else "").trim()
                }
            }
        }

        fun onExit() {
            handler.post { onExit.run() }
        }
    }

    private class BeforeStartThread(private var context: Context, private val config: KrScriptConfig, private var updateLogViewHandler: UpdateLogViewHandler) : Thread() {
        val params: HashMap<String, String> = config.variables

        override fun run() {
            try {
                val process = if (CheckRootStatus.lastCheckResult) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                if (process != null) {
                    val outputStream = DataOutputStream(process.outputStream)

                    ScriptEnvironmen.executeShell(context, outputStream, config.beforeStartSh, params, null, "pio-splash")

                    StreamReadThread(process.inputStream.bufferedReader(), updateLogViewHandler).start()
                    StreamReadThread(process.errorStream.bufferedReader(), updateLogViewHandler).start()

                    process.waitFor()
                    updateLogViewHandler.onExit()
                } else {
                    updateLogViewHandler.onExit()
                }
            } catch (_: Exception) {
                updateLogViewHandler.onExit()
            }
        }
    }

    private class StreamReadThread(private var reader: BufferedReader, private var updateLogViewHandler: UpdateLogViewHandler) : Thread() {
        override fun run() {
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line == null) {
                    break
                } else {
                    updateLogViewHandler.onLogOutput(line)
                }
            }
        }
    }
}