package com.omarea.krscript.executor;

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.RunnableNode.Companion.shellModeBgTask
import com.omarea.krscript.model.ShellHandlerBase
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
        shellHandlerBase: ShellHandlerBase
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
                if (nodeInfo.interruptable || nodeInfo.shell == shellModeBgTask)
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
            SimpleShellWatcher().setHandler(context, process, shellHandlerBase, onExit)

            val outputStream = process.outputStream
            val dataOutputStream = DataOutputStream(outputStream)
            try {
                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(
                        ShellHandlerBase.EVENT_START,
                        "shell@android:\n"
                    )
                )
                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(
                        ShellHandlerBase.EVENT_START,
                        cmd + "\n\n"
                    )
                )

                shellHandlerBase.onStart(forceStopRunnable)

                dataOutputStream.writeBytes("sleep 0.2;\n")

                ScriptEnvironment.executeShell(
                    context!!,
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
