package com.krscripts.core.executor

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.krscripts.core.model.ExecutionMode
import com.krscripts.core.model.RunnableNode
import com.krscripts.core.shell.ShellEventSource
import java.io.DataOutputStream
import java.util.UUID


/**
 * Created by Hello on 2018/04/01.
 * Refactor by buylan on 2026/07/23.
 */
class ShellExecutor {
    private var started = false
    private val sessionTag = "kr_" + UUID.randomUUID()
    private fun killProcess(context: Context?) {
        ScriptEnvironment.executeResultRoot(
            context!!,
            "kill -s 1 `pgrep -f $sessionTag`",
            null
        )
    }

    fun execute(
        context: Context?,
        nodeInfo: RunnableNode,
        cmd: String?,
        onExit: Runnable?,
        params: HashMap<String, String>?,
        shellEventSource: ShellEventSource
    ): Process? {
        if (started) {
            return null
        }

        val process = ScriptEnvironment.runtime
        if (process == null) {
            Toast.makeText(context, "未能启动命令行进程", Toast.LENGTH_SHORT).show()
            onExit?.run()
        } else {
            val forceStopRunnable: Runnable? =
                if (nodeInfo.interruptable || nodeInfo.executionMode == ExecutionMode.BACKGROUND)
                    Runnable {
                        killProcess(context)
                        process.inputStream.runCatching { close() }
                        process.outputStream.runCatching { close() }
                        process.errorStream.runCatching { close() }

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                process.destroyForcibly()
                            } else {
                                process.destroy()
                            }
                        } catch (ex: Exception) {
                            Log.e("KrScriptError", "" + ex.message)
                        }
                    }
                else null
            ShellLogWatcher.setWatcher(context!!, process, shellEventSource, onExit)

            val outputStream = process.outputStream
            val dataOutputStream = DataOutputStream(outputStream)
            try {
                shellEventSource.postStart(forceStopRunnable)
                shellEventSource.postWrite(cmd + "\n")

                ScriptEnvironment.executeShell(
                    context,
                    dataOutputStream,
                    cmd,
                    params,
                    nodeInfo,
                    sessionTag
                )
            } catch (_: Exception) {
                process.destroy()
            }
            started = true
        }
        return process
    }
}
