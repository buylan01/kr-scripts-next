package com.krscripts.core

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.Keep
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.common.shell.KeepShellPublic.checkRoot
import com.krscripts.common.shell.ShellExecutor
import com.krscripts.common.ui.DialogHelper
import com.krscripts.common.util.PermissionUtil
import com.krscripts.core.downloader.Downloader
import com.krscripts.core.executor.ExtractAssets
import com.krscripts.core.executor.ScriptEnvironment
import com.krscripts.core.executor.ScriptEnvironment.executeResultRoot
import com.krscripts.core.model.NodeInfoBase
import com.krscripts.core.model.ShellHandlerBase
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
                return executeResultRoot(context, script, virtualRootNode)
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

                setHandler(process, callbackFunction) { }

                ScriptEnvironment.executeShell(
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
            return ExtractAssets(context).extractResource(assets)
        }

        fun setHandler(process: Process, callbackFunction: String?, onExit: Runnable?) {

            fun readStream(stream: InputStream, eventType: Int): Thread = Thread {
                stream.bufferedReader().use { reader ->
                    var line: String?
                    try {
                        while (true) {
                            try {
                                line = reader.readLine()
                                if (line == null) break
                            } catch (e: IOException) {
                                break
                            }

                            val json = JSONObject().apply {
                                put("type", eventType)
                                put("message", "$line\n")
                            }
                            webView.post {
                                webView.evaluateJavascript(
                                    "$callbackFunction($json)"
                                ) { }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            val reader = readStream(process.inputStream, ShellHandlerBase.EVENT_READ)
            val readerError = readStream(process.errorStream, ShellHandlerBase.EVENT_READ_ERROR)

            val waitExit = Thread {
                val status = try {
                    process.waitFor()
                } catch (e: InterruptedException) {
                    -1
                }
                val json = JSONObject().apply {
                    put("type", ShellHandlerBase.EVENT_EXIT)
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

            reader.start()
            readerError.start()
            waitExit.start()
        }
    }
}
