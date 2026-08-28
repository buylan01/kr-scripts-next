package com.krscripts.core.shell

import android.content.Context
import android.widget.Toast
import com.krscripts.core.R
import com.krscripts.core.executor.ShellExecutor
import com.krscripts.core.model.RunnableNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


object ShellHiddenTask {

    fun startTask(
        context: Context,
        script: String,
        params: HashMap<String, String>?,
        nodeInfo: RunnableNode,
        onFinish: () -> Unit
    ) {
        val errorRows = ArrayList<String>()
        val shellEventSource = ShellEventSource()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch {
            shellEventSource.events.collect { event ->
                when(event) {
                    is ShellEvent.Log -> {
                        if (event.type == ShellLogType.OUTPUT_ERROR) {
                            synchronized(errorRows) {
                                errorRows.add(event.text)
                            }
                        }
                    }
                    is ShellEvent.Exited -> {
                        if (errorRows.isNotEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.kr_script_task_has_error) + ": " + errorRows.joinToString(", ").trim(),
                                Toast.LENGTH_LONG
                            ).show()
                            errorRows.clear()
                        }
                        shellEventSource.destroy()
                        scope.cancel()

                        try {
                            onFinish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    else -> {}
                }
            }
        }

        ShellExecutor().execute(
            context = context,
            nodeInfo = nodeInfo,
            cmd = script,
            params = params,
            shellEventSource = shellEventSource
        )
    }
}
