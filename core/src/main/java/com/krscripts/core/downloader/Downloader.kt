package com.krscripts.core.downloader

import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import com.krscripts.common.shared.FileWrite
import com.krscripts.common.ui.DialogHelper
import com.krscripts.core.R
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

class Downloader(private var context: Context) {
    companion object {
        private const val HISTORY_CONFIG = "kr_downloader"
    }

    fun download(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        taskAliasId: String,
        fileName: String? = null
    ): Long? {
        try {
            // 指定下载地址
            val request = DownloadManager.Request(url.toUri())
            // 设置通知的显示类型，下载进行时和完成后显示通知
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            // 允许在计费流量下下载
            request.setAllowedOverMetered(true)
            // 允许该记录在下载管理界面可见
            request.setVisibleInDownloadsUi(true)
            // 允许漫游时下载
            request.setAllowedOverRoaming(true)
            // 设置下载文件保存的路径和文件名
            val outName = if(fileName.isNullOrEmpty()) URLUtil.guessFileName(url, contentDisposition, mimeType) else fileName
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, outName)
            // 添加一个下载任务
            val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            if (taskAliasId.isNotEmpty()) {
                addTaskHistory(downloadId, taskAliasId, url)
            }
            Toast.makeText(context, context.getString(R.string.kr_download_create_success), Toast.LENGTH_SHORT).show()
            // 注册下载完成事件监听
            DownloaderReceiver.autoRegister(context.applicationContext)
            return downloadId
        } catch (ex: Exception) {
            DialogHelper.helpInfo(context, context.getString(R.string.kr_download_create_fail), "" + ex.message)
            return null
        }
    }

    // 保存下载记录
    private fun addTaskHistory(downloadId: Long, taskAliasId: String, url: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)

        val history = JSONObject()
        history.put("url", url)
        history.put("taskAliasId", taskAliasId)

        historyList.edit { putString(downloadId.toString(), history.toString(2)) }
        // FileWrite.writePrivateFile("".toByteArray(Charset.defaultCharset()), "downloader/", context)
    }

    // 保存任务状态、进度
    fun saveTaskStatus(taskAliasId: String, ratio: Int) {
        FileWrite.writePrivateFile(ratio.toString().toByteArray(Charset.defaultCharset()), "downloader/status/$taskAliasId", context)
    }

    // 保存下载成功后的路径
    fun saveTaskCompleted(downloadId: Long, absPath: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)
        val historyStr = historyList.getString(downloadId.toString(), null)
        var taskAliasId: String? = ""
        if (historyStr != null) {
            val history = JSONObject(historyStr)
            history.put("absPath", absPath)
            historyList.edit { putString(downloadId.toString(), history.toString(2)) }
            taskAliasId = history.getString("taskAliasId")
        }
        try {
            val file = File(absPath)
            if (file.exists() && file.canRead()) {
                val md5 = file.getFileMD5()?.lowercase(Locale.getDefault())
                FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()), "downloader/path/$md5", context)
                taskAliasId?.run {
                    FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()), "downloader/result/$taskAliasId", context)
                }
            }
        } catch (_: java.lang.Exception) {

        }
    }
}
