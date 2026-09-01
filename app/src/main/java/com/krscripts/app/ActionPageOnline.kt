package com.krscripts.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.krscripts.app.contracts.FilePickerContract
import com.krscripts.app.contracts.FilePickerRequest
import com.krscripts.app.databinding.ActivityActionPageOnlineBinding
import com.krscripts.app.model.FileType
import com.krscripts.app.model.PageNode
import com.krscripts.app.ui.PageMenuLoader
import com.krscripts.app.ui.dialog.DialogHelper

class ActionPageOnline : KrActivity() {

    private lateinit var binding: ActivityActionPageOnlineBinding
    private var pageConfigCompat: PageNode? = null
    private var pendingFileRequest: FilePickerRequest? = null

    private val launcher = registerForActivityResult(FilePickerContract()) { result ->
        val request = pendingFileRequest
        pendingFileRequest = null
        result.uri?.let { request?.onSelected(it) }
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

        binding.toolbar.apply {
            setTitle(R.string.app_name)
            setNavigationOnClickListener {
                finish()
            }
            setOnMenuItemClickListener { menuItem ->
                menuExtra[menuItem.itemId]?.let {
                    onMenuItemClick(it)
                } ?: false
            }
        }

        loadIntentData()

        pageConfigCompat?.let { node ->
            binding.toolbar.menu.clear()
            PageMenuLoader(applicationContext, node).load()?.let {
                createOptionsMenu(binding.toolbar.menu, binding.floatingActionButton, it)
            }
        }
    }

    override fun onReload() {
        binding.krOnlineWebview.reload()
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
                extras.containsKey("downloadUrl") -> {
                    startActivity(Intent(this, DownloaderActivity::class.java).apply {
                        putExtras(extras)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    finish()
                }
            }
        }
        intent.dataString.takeIf { !it.isNullOrEmpty() }?.let { initWebview(it) }
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
                val type = when(fileChooserParams?.mode) {
                    FileChooserParams.MODE_OPEN -> FileType.FILE
                    FileChooserParams.MODE_OPEN_FOLDER -> FileType.FOLDER
                    else -> FileType.FILE
                }

                val data = FilePickerRequest.SystemPicker(
                    fileType = type,
                    onSelected = {
                        filePathCallback?.onReceiveValue(arrayOf(it))
                    },
                    mime = fileChooserParams?.acceptTypes?.firstOrNull() ?: "*/*"
                )

                pendingFileRequest = data
                launcher.launch(data)
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val dialog = MaterialAlertDialogBuilder(this@ActionPageOnline)
                    .setTitle("此网页显示")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_confirm) { _, _ ->
                        result?.confirm()
                    }

                DialogHelper.animDialog(this@ActionPageOnline, dialog)
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val dialog = MaterialAlertDialogBuilder(this@ActionPageOnline)
                    .setTitle("此网页显示")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_confirm) { _, _ ->
                        result?.confirm()
                    }
                    .setNeutralButton(R.string.btn_cancel) { _, _ ->
                        result?.cancel()
                    }

                DialogHelper.animDialog(this@ActionPageOnline, dialog)
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
}
