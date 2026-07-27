package com.claudecode.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.claudecode.mobile.data.ServerConfig
import com.claudecode.mobile.data.ServerRepository
import com.claudecode.mobile.databinding.ActivityMainBinding
import com.claudecode.mobile.web.CloudWebViewClient
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorText: TextView

    private lateinit var serverRepo: ServerRepository
    private var currentServer: ServerConfig? = null

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data?.data != null) {
                    arrayOf(data.data!!)
                } else if (data?.clipData != null) {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                } else {
                    null
                }
            } else {
                null
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serverRepo = ServerRepository(this)

        setupViews()
        setupWebView()
        setupBackNavigation()
        requestNotificationPermission()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadDefaultServer()
        }
    }

    private fun setupViews() {
        webView = binding.webView
        swipeRefresh = binding.swipeRefresh
        progressBar = binding.progressBar
        errorView = binding.errorView
        errorText = binding.errorText

        setSupportActionBar(binding.toolbar)

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.colorPrimary)
        )

        binding.btnRetry.setOnClickListener {
            loadServer(currentServer)
        }

        binding.btnSwitchServer.setOnClickListener {
            showServerPicker()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString + " ClaudeCodeMobile/1.0"

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = CloudWebViewClient(this, trustAllCerts = false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()?.apply {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    type = "*/*"
                } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    Toast.makeText(this@MainActivity, "Cannot open file picker", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    binding.toolbar.subtitle = title
                }
            }
        }

        // Enable dark mode for WebView if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val nightMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                settings.forceDark = WebSettings.FORCE_DARK_ON
            }
        }

        WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun loadDefaultServer() {
        val server = serverRepo.getDefault()
        if (server != null) {
            loadServer(server)
        } else {
            showError("No server configured", "Add a server in Settings to get started.")
        }
    }

    private fun loadServer(server: ServerConfig?) {
        if (server == null) {
            showError("No server", "Please configure a server in Settings.")
            return
        }
        currentServer = server
        binding.toolbar.title = server.name
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // Re-create WebView client with trust setting
        webView.webViewClient = CloudWebViewClient(this, server.trustAllCerts)
        webView.loadUrl(server.normalizedUrl())
    }

    fun onPageLoadStarted() {
        swipeRefresh.isRefreshing = true
    }

    fun onPageLoadFinished(url: String?) {
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        CookieManager.getInstance().flush()
    }

    fun onPageLoadError(message: String) {
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun showError(title: String, message: String) {
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        binding.toolbar.title = title
        errorText.text = message
    }

    private fun showServerPicker() {
        val servers = serverRepo.getAll()
        if (servers.isEmpty()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val names = servers.map { it.name }.toTypedArray()
        val checked = servers.indexOfFirst { it.id == currentServer?.id }

        AlertDialog.Builder(this)
            .setTitle(R.string.select_server)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val selected = servers[which]
                serverRepo.setDefault(selected.id)
                loadServer(selected)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.settings) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                webView.reload()
                true
            }
            R.id.action_servers -> {
                showServerPicker()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_home -> {
                currentServer?.let { loadServer(it) }
                true
            }
            R.id.action_share -> {
                shareCurrentUrl()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentUrl() {
        val url = webView.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share URL"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Reload if server changed
        val server = serverRepo.getDefault()
        if (server != null && server.id != currentServer?.id) {
            loadServer(server)
        }
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
