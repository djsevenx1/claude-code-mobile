package com.claudecode.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
    private val tokenManager by lazy { TokenManager(applicationContext) }

    // 状态栏高度（px），用于注入到 WebView CSS
    private var statusBarHeightPx: Int = 0

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

        // 预读状态栏高度（资源方式，作为初始值，无需等待 insets）
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeightPx = resources.getDimensionPixelSize(resourceId)
        }

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

        // --- JavaScript 接口 ---
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun getToken(): String = token

                @JavascriptInterface
                fun getServerUrl(): String = baseUrl

                @JavascriptInterface
                fun getStatusBarHeight(): Int = statusBarHeightPx
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

                // 注入 CSS 修复头部遮挡（使用原生状态栏高度）
                view.evaluateJavascript(
                    """
                    (function() {
                        if (document.getElementById('mobile-fix-css')) {
                            // 已注入过，更新状态栏高度即可
                            var existing = document.getElementById('mobile-fix-css');
                            var sbh = AndroidBridge.getStatusBarHeight();
                            existing.textContent = ':root{--sb-h:' + sbh + 'px;}';
                            return;
                        }
                        var sbh = AndroidBridge.getStatusBarHeight();
                        var style = document.createElement('style');
                        style.id = 'mobile-fix-css';
                        style.textContent = `
                            :root{--sb-h:${'$'}{sbh}px;}
                            /* 顶部固定栏下移，避免被状态栏遮挡 */
                            .fixed.top-0, header.fixed, nav.fixed, [class*="header"][class*="fixed"],
                            .sticky.top-0, header.sticky {
                                top: var(--sb-h, 0px) !important;
                            }
                            /* 全屏遮罩层也下移 */
                            .fixed.inset-0 {
                                top: var(--sb-h, 0px) !important;
                            }
                            /* body 顶部留出状态栏空间 */
                            html, body {
                                padding-top: var(--sb-h, 0px) !important;
                                box-sizing: border-box !important;
                                overflow: hidden !important;
                                overscroll-behavior: none !important;
                            }
                            /* 底部安全区 */
                            .chat-composer-shell {
                                padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px)) !important;
                            }
                            .mobile-nav, [class*="mobile-nav"] {
                                padding-bottom: env(safe-area-inset-bottom, 0px) !important;
                            }
                            /* 滚动优化 */
                            .flex.h-full, .flex.flex-1 { min-height: 0 !important; }
                            .overflow-y-auto, [class*="chat-messages"] {
                                -webkit-overflow-scrolling: touch !important;
                                overscroll-behavior: contain !important;
                            }
                            /* 隐藏 PWA 安装提示 */
                            [class*="install-prompt"], [class*="pwa-install"] { display: none !important; }
                            [class*="overlay"]:not(.visible) { pointer-events: none !important; }
                        `;
                        document.head.appendChild(style);
                        console.log('Mobile fix CSS injected, statusBarHeight=' + sbh + 'px');
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

        // --- 用 FrameLayout 包裹 WebView ---
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(webView)

        // 监听 WindowInsets 获取精确状态栏高度，更新 CSS 变量
        // 注意：不使用原生 padding，而是通过 CSS 注入处理，
        // 这样状态栏区域会自动显示网页背景色，不会出现颜色不匹配
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (systemBars.top > 0 && systemBars.top != statusBarHeightPx) {
                statusBarHeightPx = systemBars.top
                // 重新注入 CSS 更新状态栏高度
                if (::webView.isInitialized) {
                    webView.evaluateJavascript(
                        """
                        (function() {
                            var sbh = AndroidBridge.getStatusBarHeight();
                            var el = document.getElementById('mobile-fix-css');
                            if (el) {
                                el.textContent = el.textContent.replace(/--sb-h:\d+px;/, '--sb-h:' + sbh + 'px;');
                            }
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
            insets
        }

        setContentView(container)
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
