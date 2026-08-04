package com.krscripts.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.databinding.ActivityActionPageOnlineBinding
import com.krscripts.app.util.chooseFilePath
import com.krscripts.app.util.handleFileSelectorResult
import com.krscripts.common.shared.FilePathResolver
import com.krscripts.common.ui.DialogHelper
import com.krscripts.common.util.PermissionUtil.checkAccessFiles
import com.krscripts.common.util.PermissionUtil.requestAccessFilesDialog
import com.krscripts.core.R
import com.krscripts.core.WebViewInjector
import com.krscripts.core.downloader.Downloader
import com.krscripts.core.model.PageNode
import com.krscripts.core.ui.PageMenuLoader
import com.krscripts.core.ui.ParamsFileChooserRender
import com.krscripts.core.ui.ParamsFileChooserRender.FileSelectedInterface
import com.krscripts.core.ui.ParamsFileChooserRender.FileSelectedInterface.Companion.TYPE_FILE
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

class ActionPageOnline : KrActivity() {

    private lateinit var binding: ActivityActionPageOnlineBinding
    private var pageConfigCompat: PageNode? = null

    private var fileChooser = object : ParamsFileChooserRender.FileChooserInterface {
        override fun openFileChooser(fileSelectedInterface: FileSelectedInterface): Boolean {
            fileSelectorInterface = fileSelectedInterface
            return chooseFilePath(fileSelectedInterface)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityActionPageOnlineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setSupportActionBar(binding.toolbar)
        setTitle(com.krscripts.app.R.string.app_name)

        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        loadIntentData()
    }

    override fun onReload() {
        binding.krOnlineWebview.reload()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        menuOptions.clear()

        pageConfigCompat?.let { node ->
            PageMenuLoader(applicationContext, node).load()?.let {
                menuOptions.addAll(it)
            }
        }

        if (menu != null) {
            menuOptions.forEachIndexed { index, option ->
                if (option.isFab) {
                    addFab(option, binding.floatingActionButton)
                } else {
                    menu.add(-1, index, index, option.title)
                }
            }
        }

        return true // super.onCreateOptionsMenu(menu)
    }

    private fun loadIntentData() {
        intent.extras?.let { extras ->
            if (extras.containsKey("title")) {
                title = extras.getString("title")!!
            }

            when {
                extras.containsKey("page") -> {
                    pageConfigCompat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) extras.getSerializable(
                        "page",
                        PageNode::class.java
                    ) else @Suppress("DEPRECATION") extras.getSerializable("page") as PageNode
                    menuHandler = pageConfigCompat?.pageHandlerSh
                    initWebview(pageConfigCompat?.onlineHtmlPage)
                }
                extras.containsKey("config") -> initWebview(extras.getString("config"))
                extras.containsKey("url") -> initWebview(extras.getString("url"))
                extras.containsKey("downloadUrl") -> initDownload(
                    extras.getString("downloadUrl")!!,
                    extras.getString("taskId"),
                    extras.getBoolean("autoClose")
                )
            }
        }
        intent.dataString.takeIf { !it.isNullOrEmpty() }?.let { initWebview(it) }
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

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebview(url: String?) {
        val credible = url?.startsWith("file:///android_asset")
        binding.krOnlineWebview.visibility = View.VISIBLE
        binding.krOnlineWebview.settings.apply {
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            if (credible == true) {
                allowFileAccess = true
                allowUniversalAccessFromFileURLs = true
                allowFileAccessFromFileURLs = true
            }

            allowContentAccess = true
            useWideViewPort = true
        }

        val cookieManager: CookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.krOnlineWebview, true)

        binding.krOnlineWebview.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return fileChooser.openFileChooser(object : FileSelectedInterface {
                    override fun type(): Int = TYPE_FILE
                    override fun suffix(): String? = null
                    override fun mimeType(): String = "*/*"

                    override fun onFileSelected(path: Uri?) {
                        if (path == null) {
                            filePathCallback?.onReceiveValue(null)
                            return
                        }
                        filePathCallback?.onReceiveValue(arrayOf(path))
                    }
                })
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                DialogHelper.animDialog(
                    this@ActionPageOnline,
                    MaterialAlertDialogBuilder(this@ActionPageOnline)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.btn_confirm) { _, _ -> }
                        .setOnDismissListener {
                            result?.confirm()
                        }
                )
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                DialogHelper.animDialog(
                    this@ActionPageOnline,
                    MaterialAlertDialogBuilder(this@ActionPageOnline)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.btn_confirm) { _, _ ->
                            result?.confirm()
                        }
                        .setNeutralButton(R.string.btn_cancel) { _, _ ->
                            result?.cancel()
                        }
                )
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
            }
        }

        binding.krOnlineWebview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                view?.run {
                    setTitle(this.title)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                try {
                    val requestUrl = request?.url
                    if (requestUrl != null && requestUrl.scheme?.startsWith("http") != true) {
                        val intent = Intent(Intent.ACTION_VIEW, requestUrl.toString().toUri())
                        startActivity(intent)
                        return true
                    } else {
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                } catch (_: Exception) {
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }
        }

        url?.let {
            binding.krOnlineWebview.loadUrl(it)
            WebViewInjector(binding.krOnlineWebview).inject(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.krOnlineWebview.canGoBack()) {
            binding.krOnlineWebview.goBack()
            return true
        } else {
            return super.onKeyDown(keyCode, event)
        }
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

    var progressPolling: Timer? = null
    /**
     * 监视下载进度
     */
    private fun watchDownloadProgress(downloadId: Long, autoClose: Boolean, taskAliasId: String) {
        binding.krDownloadState.visibility = View.VISIBLE

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        binding.krDownloadNameCopy.setOnClickListener {
            val myClipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val myClip = ClipData.newPlainText("text", binding.krDownloadName.text.toString())
            myClipboard.setPrimaryClip(myClip)
            Toast.makeText(this@ActionPageOnline, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
        }
        binding.krDownloadUrlCopy.setOnClickListener {
            val myClipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val myClip = ClipData.newPlainText("text", binding.krDownloadUrl.text.toString())
            myClipboard.setPrimaryClip(myClip)
            Toast.makeText(this@ActionPageOnline, getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
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
                            absPath = FilePathResolver().getPath(this@ActionPageOnline,
                                fileName.toUri())
                            if (!absPath.isNullOrEmpty()) {
                                fileName = absPath
                            }
                        } catch (_: java.lang.Exception) {
                        }
                    }

                    handler.post {
                        binding.krDownloadName.text = fileName
                        binding.krDownloadProgress.progress = ratio
                        binding.krDownloadProgress.isIndeterminate = false
                        setTitle(R.string.kr_download_downloading)
                        downloader.saveTaskStatus(taskAliasId, ratio)
                    }

                    absPath?.let { path ->
                        if (ratio >= 100) {
                            // 保存下载成功后的路径
                            downloader.saveTaskCompleted(downloadId, path)

                            handler.post {
                                setTitle(R.string.kr_download_completed)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleFileSelectorResult(this, resultCode, requestCode, data, fileSelectorInterface, true)
        fileSelectorInterface = null
        super.onActivityResult(requestCode, resultCode, data)
    }
}
