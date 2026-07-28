package com.claudecode.mobile.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.claudecode.mobile.network.TokenManager
import kotlinx.coroutines.launch

// ============================================================
// 登录 Activity
// 输入服务器地址 -> WebView 打开 Web 端登录页
// 登录成功后自动提取 token 并保存
// ============================================================

class LoginActivity : ComponentActivity() {

    private val tokenManager by lazy { TokenManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 从 DataStore 读取上次保存的服务器地址
                    var savedUrl by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        savedUrl = tokenManager.getServerUrl() ?: ""
                    }

                    LoginScreen(
                        initialServerUrl = savedUrl,
                        onConnect = { serverUrl ->
                            lifecycleScope.launch {
                                val normalized = tokenManager.normalizeUrl(serverUrl)
                                tokenManager.saveServerUrl(normalized)
                                openWebViewLogin(normalized)
                            }
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openWebViewLogin(baseUrl: String) {
        val webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            removeAllCookies(null)
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onLoginSuccess(token: String, username: String) {
                    runOnUiThread {
                        lifecycleScope.launch {
                            tokenManager.saveAuthInfo(token, username)
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            },
            "LoginBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // 注入 JS: 检测登录成功并提取 token
                view.evaluateJavascript(
                    """
                    (function() {
                        // 从 Cookie / localStorage / URL 提取 token
                        var token = null;
                        var cookies = document.cookie;
                        var m = cookies.match(/(?:token|jwt_token|accessToken)=([^;]+)/);
                        if (m) token = m[1];
                        if (!token) {
                            try {
                                token = localStorage.getItem('token')
                                    || localStorage.getItem('accessToken')
                                    || localStorage.getItem('jwt_token');
                            } catch(e) {}
                        }
                        if (!token) {
                            var p = new URLSearchParams(window.location.search);
                            token = p.get('token') || p.get('access_token');
                        }
                        if (token && token.length > 10) {
                            var u = 'user';
                            try {
                                var el = document.querySelector('[class*="user-name"], [class*="username"]');
                                if (el) u = el.textContent || 'user';
                            } catch(e) {}
                            LoginBridge.onLoginSuccess(token, u);
                        }
                        // 监听登录表单提交
                        if (!token) {
                            var form = document.querySelector('form');
                            if (form && !form.dataset.androidHooked) {
                                form.dataset.androidHooked = '1';
                                form.addEventListener('submit', function() {
                                    setTimeout(function() {
                                        var t = document.cookie.match(/(?:token|jwt_token|accessToken)=([^;]+)/);
                                        if (!t) {
                                            try {
                                                t = localStorage.getItem('token')
                                                    || localStorage.getItem('accessToken');
                                            } catch(e) {}
                                        }
                                        if (t && t.length > 10) {
                                            LoginBridge.onLoginSuccess(t, 'user');
                                        }
                                    }, 2000);
                                }, false);
                            }
                        }
                    })();
                    """.trimIndent(),
                    null
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }
        }

        webView.loadUrl(baseUrl)
        setContentView(webView)
    }
}

@Composable
private fun LoginScreen(
    initialServerUrl: String = "",
    onConnect: (String) -> Unit
) {
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Claude Code",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "输入服务器地址连接",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.168.1.100:3001") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (serverUrl.isNotBlank()) {
                    isLoading = true
                    onConnect(serverUrl)
                }
            },
            enabled = serverUrl.isNotBlank() && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("连接", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "输入你的 claudecodeui 服务器地址\n连接后在页面中登录",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            lineHeight = 18.sp
        )
    }
}
