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
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.claudecode.mobile.network.TokenManager
import kotlinx.coroutines.launch

// ============================================================
// 主 Activity (WebView 封装方案)
// 直接加载 claudecodeui Web 端，UI 和功能与 Web 端完全一致
// 仅保留登录页用于配置服务器地址
// ============================================================

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val tokenManager by lazy { TokenManager(applicationContext) }

    // 文件上传回调
    private var filePathCallback: ((Array<Uri>?) -> Unit)? = null

    // 文件选择器
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.invoke(uris?.toTypedArray())
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 异步检查登录状态
        var serverUrl: String? = null
        var token: String? = null
        var isLoggedIn = false

        lifecycleScope.launch {
            serverUrl = tokenManager.getServerUrl()
            token = tokenManager.getToken()
            isLoggedIn = !token.isNullOrBlank() && !serverUrl.isNullOrBlank()

            if (isLoggedIn && serverUrl != null) {
                // 已登录: 直接显示 WebView
                showWebView(serverUrl!!, token!!)
            } else {
                // 未登录: 显示登录页
                showLogin()
            }
            splashScreen.setKeepOnScreenCondition { false }
        }
    }

    // ============================================================
    // WebView 显示
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(baseUrl: String, token: String) {
        webView = WebView(this).also { wv ->

            // --- WebView 配置 ---
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                // 支持缩放
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // 大视图模式
                loadWithOverviewMode = true
                useWideViewPort = true
                // 允许跨域
                allowUniversalAccessFromFileURLs = true
            }

            // --- Cookie 管理 ---
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
            }

            // --- 注入 token 到 Cookie 和 localStorage ---
            // claudecodeui 使用 JWT token, 通过 Cookie 或 localStorage 传递
            val cookieDomain = Uri.parse(baseUrl).host ?: ""
            CookieManager.getInstance().setCookie(baseUrl, "token=$token; path=/; max-age=2592000")

            // --- JavaScript 接口: 注入 token ---
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun getToken(): String = token

                    @JavascriptInterface
                    fun getServerUrl(): String = baseUrl
                },
                "AndroidBridge"
            )

            // --- WebViewClient: 页面加载完成后注入 CSS 修复 ---
            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    // 外部链接用浏览器打开
                    if (!url.startsWith(baseUrl)) {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    // 注入 token 到 localStorage (claudecodeui 可能用 localStorage 存储 token)
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

                    // 注入 CSS 修复头部遮挡问题
                    view.evaluateJavascript(
                        """
                        (function() {
                            var style = document.getElementById('mobile-fix-css');
                            if (style) return; // 避免重复注入

                            style = document.createElement('style');
                            style.id = 'mobile-fix-css';
                            style.textContent = `
                                /* === 移动端修复 CSS === */

                                /* 1. 修复 safe-area 双重填充 */
                                #root {
                                    padding-top: 0px !important;
                                    padding-left: 0px !important;
                                    padding-right: 0px !important;
                                }

                                /* 2. 修复 fixed inset-0 被下推 */
                                body.pwa-mode .fixed.inset-0,
                                .fixed.inset-0 {
                                    top: 0px !important;
                                    left: 0px !important;
                                    right: 0px !important;
                                }

                                /* 3. 确保 header 不遮挡内容 */
                                .pwa-header-safe {
                                    padding-top: env(safe-area-inset-top, 0px) !important;
                                }

                                /* 4. 禁止 body 滚动溢出 */
                                html, body {
                                    overflow: hidden !important;
                                    overscroll-behavior: none !important;
                                }

                                /* 5. 移动端底部安全区 */
                                .chat-composer-shell {
                                    padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px)) !important;
                                }

                                /* 6. 确保主内容区正确填充 */
                                .flex.h-full,
                                .flex.flex-1 {
                                    min-height: 0 !important;
                                }

                                /* 7. 移除可能的遮挡层 */
                                [class*="overlay"]:not(.visible) {
                                    pointer-events: none !important;
                                }

                                /* 8. 确保 WebView 模式下不显示 PWA 安装提示等 */
                                [class*="install-prompt"],
                                [class*="pwa-install"] {
                                    display: none !important;
                                }

                                /* 9. 适配底部导航栏安全区 */
                                .mobile-nav,
                                [class*="mobile-nav"] {
                                    padding-bottom: env(safe-area-inset-bottom, 0px) !important;
                                }

                                /* 10. 聊天消息区域确保可滚动 */
                                .overflow-y-auto,
                                [class*="chat-messages"] {
                                    -webkit-overflow-scrolling: touch !important;
                                    overscroll-behavior: contain !important;
                                }
                            `;
                            document.head.appendChild(style);
                            console.log('Mobile fix CSS injected');
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
                    // 只处理主框架错误
                    if (request.isForMainFrame) {
                        showErrorPage(baseUrl)
                    }
                }
            }

            // --- WebChromeClient: 支持文件选择和全屏 ---
            webChromeClient = object : WebChromeClient() {

                // 文件上传
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ((Array<Uri>?) -> Unit)?,
                    params: FileChooserParams?
                ): Boolean {
                    filePathCallback = callback
                    fileChooserLauncher.launch("*/*")
                    return true
                }

                // 全屏视频
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    // 简单实现: 全屏时隐藏 WebView
                    (window.decorView as? ViewGroup)?.addView(view)
                }

                override fun onHideCustomView() {
                    // 退出全屏
                }
            }

            // 加载 Web 端
            loadUrl(baseUrl)
        }

        setContentView(webView)
    }

    // ============================================================
    // 登录页 (简单的服务器配置)
    // ============================================================

    private fun showLogin() {
        // 使用 Compose 显示简单的登录界面
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }

    // ============================================================
    // 错误页面
    // ============================================================

    private fun showErrorPage(baseUrl: String) {
        webView.loadUrl("about:blank")
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

    // ============================================================
    // 生命周期
    // ============================================================

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
