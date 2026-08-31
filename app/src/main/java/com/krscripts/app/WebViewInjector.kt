package com.krscripts.app

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.Keep
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.downloader.Downloader
import com.krscripts.app.executor.AssetsExtractor
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.executor.ScriptEnvironment.execute
import com.krscripts.app.model.NodeInfoBase
import com.krscripts.app.shell.KeepShellPublic.checkRoot
import com.krscripts.app.shell.ShellEvent
import com.krscripts.app.shell.ShellEventSource
import com.krscripts.app.shell.ShellExecutor
import com.krscripts.app.shell.ShellLogType
import com.krscripts.app.ui.dialog.DialogHelper
import com.krscripts.app.util.PermissionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID


class WebViewInjector(
    private val webView: WebView
) {
    private val context: Context = webView.context

    fun inject(activity: Activity) {

        webView.addJavascriptInterface(
            KrWebBridge(context),
            "KrWebBridge" //::class.simpleName!!
        )
        webView.setDownloadListener { url, _, contentDisposition, mimetype, contentLength ->
            if (!PermissionUtil.checkAccessFiles(context)) {
                PermissionUtil.requestAccessFilesDialog(activity)
            } else {
                DialogHelper.animDialog(
                    context,
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.kr_download_confirm)
                        .setMessage(url + " (" + contentLength + "Bytes" + ")")
                        .setPositiveButton(
                            R.string.btn_confirm
                        ) { _, _ ->
                            Downloader(context).download(
                                url,
                                contentDisposition,
                                mimetype,
                                UUID.randomUUID().toString(),
                                null
                            )
                        }
                        .setNegativeButton(
                            R.string.btn_cancel
                        ) { _, _ -> }
                ).setCancelable(false)
            }
        }
    }

    @Keep
    @Suppress("unused")
    private inner class KrWebBridge(private val context: Context) {
        private val virtualRootNode = NodeInfoBase("")

        /**
         * 检查是否具有ROOT权限
         */
        @JavascriptInterface
        fun rootCheck(): Boolean {
            return checkRoot()
        }

        /**
         * 同步执行shell脚本 并返回结果（不包含错误信息）
         *
         * @param script 脚本内容
         * @return 执行过程中的输出内容
         */
        @JavascriptInterface
        fun executeShell(script: String?): String {
            if (!script.isNullOrEmpty()) {
                return execute(context, script, virtualRootNode)
            }
            return ""
        }

        @JavascriptInterface
        fun executeShellAsync(script: String?, callbackFunction: String?, env: String?): Boolean {
            val params = HashMap<String, String>()
            var process: Process? = null
            try {
                if (!env.isNullOrEmpty()) {
                    val paramsObject = JSONObject(env)
                    val it = paramsObject.keys()
                    while (it.hasNext()) {
                        val key = it.next()
                        params[key] = paramsObject.getString(key)
                    }
                }
                process = ShellExecutor.superUserRuntime
            } catch (ex: Exception) {
                Toast.makeText(context, ex.message, Toast.LENGTH_SHORT).show()
            }

            if (process != null) {
                val outputStream = process.outputStream
                val dataOutputStream = DataOutputStream(outputStream)
                val shellEventSource = ShellEventSource()

                setHandler(process, callbackFunction, shellEventSource) { }

                ScriptEnvironment.executeAsync(
                    context,
                    dataOutputStream,
                    script,
                    params,
                    null,
                    null
                )
                return true
            } else {
                return false
            }
        }

        /**
         * 提取assets中的文件
         *
         * @param assets 要提取的文件
         * @return 提取成功后所在的目录
         */
        @JavascriptInterface
        fun extractAssets(assets: String?): String? {
            return AssetsExtractor(context).extractResource(assets)
        }

        fun setHandler(
            process: Process,
            callbackFunction: String?,
            shellEventSource: ShellEventSource,
            onExit: Runnable?
        ) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            fun readStream(stream: InputStream, onLine: (String) -> Unit): Thread = Thread {
                stream.bufferedReader().use { reader ->
                    var line: String?
                    try {
                        while (true) {
                            try {
                                line = reader.readLine() ?: break
                            } catch (e: IOException) {
                                break
                            }
                            onLine(line)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            val reader = readStream(process.inputStream, shellEventSource::postRead)
            val readerError = readStream(process.errorStream, shellEventSource::postReadError)

            val waitExit = Thread {
                val status = try {
                    process.waitFor()
                } catch (e: InterruptedException) {
                    -1
                }
                val json = JSONObject().apply {
                    put("type", -2)
                    put("message", status.toString())
                }
                webView.post {
                    webView.evaluateJavascript(
                        "$callbackFunction($json)"
                    ) { }
                }

                if (reader.isAlive) {
                    reader.interrupt()
                }
                if (readerError.isAlive) {
                    readerError.interrupt()
                }
                onExit?.run()
            }

            scope.launch {
                shellEventSource.events.collect { events ->
                    when (events) {
                        is ShellEvent.Log -> {
                            val eventTypeInt = when(events.type) {
                                ShellLogType.OUTPUT -> 2
                                ShellLogType.OUTPUT_ERROR -> 4
                                ShellLogType.INPUT -> 6
                            }
                            val json = JSONObject().apply {
                                put("type", eventTypeInt)
                                put("message", "${events.text}\n")
                            }
                            webView.post {
                                webView.evaluateJavascript(
                                    "$callbackFunction($json)"
                                ) { }
                            }
                        }

                        is ShellEvent.Exited -> {
                            shellEventSource.destroy()
                            scope.cancel()
                        }

                        else -> {}
                    }
                }
            }

            reader.start()
            readerError.start()
            waitExit.start()
        }
    }
}
