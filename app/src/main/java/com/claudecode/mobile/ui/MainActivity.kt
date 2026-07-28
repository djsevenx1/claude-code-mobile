package com.claudecode.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.claudecode.mobile.network.TokenManager
import kotlinx.coroutines.launch

// ============================================================
// 主 Activity (WebView 封装方案)
// 直接加载 claudecodeui Web 端，UI 和功能与 Web 端完全一致
// ============================================================

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var rootContainer: FrameLayout
    private val tokenManager by lazy { TokenManager(applicationContext) }

    private var filePathCallback: android.webkit.ValueCallback<Array<Uri>?>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris?.toTypedArray())
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 立即设置系统栏颜色，避免启动瞬间闪黑
        // 使用与网页背景一致的深色，网页加载后动态更新
        val initialBarColor = Color.parseColor("#1A1A1A")
        window.statusBarColor = initialBarColor
        window.navigationBarColor = initialBarColor

        lifecycleScope.launch {
            val serverUrl = tokenManager.getServerUrl()
            val token = tokenManager.getToken()
            val isLoggedIn = !token.isNullOrBlank() && !serverUrl.isNullOrBlank()

            if (isLoggedIn && serverUrl != null && token != null) {
                showWebView(serverUrl, token)
            } else {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
            splashScreen.setKeepOnScreenCondition { false }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(baseUrl: String, token: String) {
        webView = WebView(this)

        // --- WebView 配置 ---
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowUniversalAccessFromFileURLs = true
        }

        // --- Cookie 管理 ---
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        CookieManager.getInstance().setCookie(baseUrl, "token=$token; path=/; max-age=2592000")

        // --- 容器（用原生 padding 处理系统栏） ---
        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 与 splash 和系统栏初始颜色一致，网页加载后动态更新
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        rootContainer.addView(webView)

        // --- JavaScript 接口 ---
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun getToken(): String = token

                @JavascriptInterface
                fun getServerUrl(): String = baseUrl

                @JavascriptInterface
                fun setSystemBarColor(color: String) {
                    runOnUiThread {
                        try {
                            val parsed = parseWebColor(color)
                            rootContainer.setBackgroundColor(parsed)
                            window.statusBarColor = parsed
                            window.navigationBarColor = parsed
                        } catch(e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },
            "AndroidBridge"
        )

        // --- WebViewClient ---
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (!url.startsWith(baseUrl)) {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // 注入 token 到 localStorage
                view.evaluateJavascript(
                    """
                    (function() {
                        try {
                            if (typeof localStorage !== 'undefined') {
                                var token = AndroidBridge.getToken();
                                if (token) {
                                    localStorage.setItem('token', token);
                                    localStorage.setItem('accessToken', token);
                                    localStorage.setItem('jwt_token', token);
                                }
                            }
                        } catch(e) { console.log('Token inject error:', e); }
                    })();
                    """.trimIndent(),
                    null
                )

                // 读取网页背景色，动态设置系统栏颜色
                view.evaluateJavascript(
                    """
                    (function() {
                        function getBg(el) {
                            if (!el) return null;
                            try {
                                var bg = window.getComputedStyle(el).backgroundColor;
                                if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return bg;
                            } catch(e) {}
                            return null;
                        }
                        // 依次尝试 body -> html -> #root -> header
                        var color = getBg(document.body)
                            || getBg(document.documentElement)
                            || getBg(document.getElementById('root'))
                            || getBg(document.querySelector('header'))
                            || '#0F0F0F';
                        try {
                            AndroidBridge.setSystemBarColor(color);
                        } catch(e) {
                            console.log('setSystemBarColor error:', e);
                        }
                    })();
                    """.trimIndent(),
                    null
                )

                // 注入 CSS：仅处理滚动优化和隐藏 PWA 提示
                view.evaluateJavascript(
                    """
                    (function() {
                        if (document.getElementById('mobile-fix-css')) return;
                        var style = document.createElement('style');
                        style.id = 'mobile-fix-css';
                        style.textContent = `
                            html, body {
                                overflow: hidden !important;
                                overscroll-behavior: none !important;
                            }
                            .flex.h-full, .flex.flex-1 { min-height: 0 !important; }
                            .overflow-y-auto, [class*="chat-messages"] {
                                -webkit-overflow-scrolling: touch !important;
                                overscroll-behavior: contain !important;
                            }
                            [class*="install-prompt"], [class*="pwa-install"] { display: none !important; }
                            [class*="overlay"]:not(.visible) { pointer-events: none !important; }
                        `;
                        document.head.appendChild(style);
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showErrorPage(baseUrl)
                }
            }
        }

        // --- WebChromeClient ---
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>?>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                fileChooserLauncher.launch("*/*")
                return true
            }
        }

        // 加载 Web 端
        webView.loadUrl(baseUrl)

        // 应用 WindowInsets：状态栏高度作为顶部 padding，导航栏高度作为底部 padding
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setContentView(rootContainer)
    }

    /**
     * 解析网页返回的颜色字符串为 Android Color int。
     * 支持 "rgb(r, g, b)"、"rgba(r, g, b, a)"、"#RRGGBB" 格式。
     */
    private fun parseWebColor(color: String): Int {
        return try {
            val trimmed = color.trim()
            when {
                trimmed.startsWith("#") -> Color.parseColor(trimmed)
                trimmed.startsWith("rgb") -> {
                    val nums = trimmed
                        .replace(Regex("[^0-9,.]"), "")
                        .split(",")
                        .map { it.trim().toFloat() }
                    val r = nums.getOrElse(0) { 0f }.toInt()
                    val g = nums.getOrElse(1) { 0f }.toInt()
                    val b = nums.getOrElse(2) { 0f }.toInt()
                    Color.rgb(r, g, b)
                }
                else -> Color.parseColor("#1A1A1A")
            }
        } catch(e: Exception) {
            Color.parseColor("#1A1A1A")
        }
    }

    private fun showErrorPage(baseUrl: String) {
        val html = """
            <html><body style="background:#1a1a2e;color:#e0e0e0;font-family:sans-serif;
            display:flex;flex-direction:column;align-items:center;justify-content:center;
            height:100vh;margin:0;padding:20px;text-align:center;">
            <h2 style="color:#e94560;">连接失败</h2>
            <p>无法连接到服务器</p>
            <p style="color:#888;font-size:14px;word-break:break-all;">$baseUrl</p>
            <p style="color:#666;font-size:13px;margin-top:20px;">请检查网络连接或服务器地址</p>
            <button onclick="location.reload()" style="margin-top:20px;padding:10px 30px;
            background:#e94560;color:white;border:none;border-radius:8px;font-size:16px;
            cursor:pointer;">重试</button>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
