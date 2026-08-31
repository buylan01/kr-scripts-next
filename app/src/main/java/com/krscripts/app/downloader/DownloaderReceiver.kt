package com.krscripts.app.downloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.krscripts.app.shared.FilePathResolver

class DownloaderReceiver(
    private val onReceived: (String) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent?.action) {
            try {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val downloadManager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                val uri = downloadManager.getUriForDownloadedFile(downloadId)

                val path = FilePathResolver().getPath(context, uri)
                if (!path.isNullOrEmpty()) {
                    Downloader(context).saveTaskCompleted(downloadId, path)
                    onReceived(path)
                }
            } catch (_: Exception) {
            }
        }

    }

    companion object {
        private var downloaderReceiver: DownloaderReceiver? = null

        fun autoRegister(context: Context, onReceived: (String) -> Unit) {
            if (downloaderReceiver == null) {
                downloaderReceiver = DownloaderReceiver(onReceived)
                val intentFilter = IntentFilter()
                intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                ContextCompat.registerReceiver(
                    context,
                    downloaderReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        }

        fun autoUnRegister(context: Context) {
            if (downloaderReceiver != null) {
                context.unregisterReceiver(downloaderReceiver)
                downloaderReceiver = null
            }
        }
    }
}