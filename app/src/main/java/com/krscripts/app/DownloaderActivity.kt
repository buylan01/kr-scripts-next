package com.krscripts.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.krscripts.app.databinding.ActivityDownloaderBinding
import com.krscripts.app.downloader.Downloader
import com.krscripts.app.shared.FilePathResolver
import com.krscripts.app.util.PermissionUtil.checkAccessFiles
import com.krscripts.app.util.PermissionUtil.requestAccessFilesDialog
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class DownloaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloaderBinding
    var progressPolling: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDownloaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.kr_downloader)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }

        intent.extras?.let {
            initDownload(
                it.getString("downloadUrl")!!,
                it.getString("taskId"),
                it.getBoolean("autoClose")
            )
        }
    }

    private fun initDownload(url: String, taskId: String?, autoClose: Boolean) {
        val downloader = Downloader(this)

        val taskAliasId = taskId ?: UUID.randomUUID().toString()

        if (!checkAccessFiles(this)) {
            downloader.saveTaskStatus(taskAliasId, 0)
            requestAccessFilesDialog(this)
        } else {
            val downloadId = downloader.download(url, null, null, taskAliasId)
            if (downloadId != null) {
                binding.krDownloadUrl.text = url
                downloader.saveTaskStatus(taskAliasId, 0)
                watchDownloadProgress(downloadId, autoClose, taskAliasId)
            } else {
                downloader.saveTaskStatus(taskAliasId, -1)
            }
        }
    }

    private fun watchDownloadProgress(downloadId: Long, autoClose: Boolean, taskAliasId: String) {

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        binding.krDownloadNameCopy.setOnClickListener {
            val myClipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val myClip = ClipData.newPlainText("text", binding.krDownloadName.text.toString())
            myClipboard.setPrimaryClip(myClip)
            Toast.makeText(this@DownloaderActivity, getString(com.krscripts.app.R.string.copy_success), Toast.LENGTH_SHORT).show()
        }
        binding.krDownloadUrlCopy.setOnClickListener {
            val myClipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val myClip = ClipData.newPlainText("text", binding.krDownloadUrl.text.toString())
            myClipboard.setPrimaryClip(myClip)
            Toast.makeText(this@DownloaderActivity, getString(com.krscripts.app.R.string.copy_success), Toast.LENGTH_SHORT).show()
        }

        val handler = Handler(Looper.getMainLooper())
        val downloader = Downloader(this)
        progressPolling = Timer()
        progressPolling?.schedule(object : TimerTask() {
            override fun run() {
                val cursor = downloadManager.query(query)
                var fileName = ""
                var absPath: String? = null
                if (cursor.moveToFirst()) {
                    val downloadBytesIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val totalBytes = cursor.getLong(totalBytesIdx)
                    val downloadBytes = cursor.getLong(downloadBytesIdx)
                    val ratio = (downloadBytes * 100 / totalBytes).toInt()
                    if (fileName.isEmpty()) {
                        try {
                            val nameColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                            fileName = cursor.getString(nameColumn)
                            absPath = FilePathResolver().getPath(this@DownloaderActivity,
                                fileName.toUri())
                            if (!absPath.isNullOrEmpty()) {
                                fileName = absPath
                            }
                        } catch (_: Exception) {
                        }
                    }

                    handler.post {
                        binding.krDownloadName.text = fileName
                        binding.krDownloadProgress.progress = ratio
                        binding.krDownloadProgress.isIndeterminate = false
                        setTitle(com.krscripts.app.R.string.kr_download_downloading)
                        downloader.saveTaskStatus(taskAliasId, ratio)
                    }

                    absPath?.let { path ->
                        if (ratio >= 100) {
                            // 保存下载成功后的路径
                            downloader.saveTaskCompleted(downloadId, path)

                            handler.post {
                                setTitle(com.krscripts.app.R.string.kr_download_completed)
                                binding.krDownloadProgress.visibility = View.GONE
                                stopWatchDownloadProgress()

                                val result = Intent()
                                result.putExtra("absPath", path)
                                setResult(0, result)

                                if (autoClose) {
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        }, 200, 500)
    }

    override fun onDestroy() {
        stopWatchDownloadProgress()
        super.onDestroy()
    }

    private fun stopWatchDownloadProgress() {
        if (progressPolling != null) {
            progressPolling?.cancel()
            progressPolling = null
        }
    }
}