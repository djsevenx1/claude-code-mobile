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
// 简单的服务器配置页面: 输入服务器地址 -> WebView 打开登录页
// 登录成功后自动提取 token 并保存
// ============================================================

class LoginActivity : ComponentActivity() {

    private val tokenManager by lazy { TokenManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                LoginScreen(
                    onConnect = { serverUrl ->
                        connectToServer(serverUrl)
                    }
                )
            }
        }
    }

    private fun connectToServer(serverUrl: String) {
        lifecycleScope.launch {
            val normalized = tokenManager.normalizeUrl(serverUrl)
            tokenManager.saveServerUrl(normalized)

            // 用 WebView 打开服务器的登录页, 自动提取 token
            openWebViewLogin(normalized)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openWebViewLogin(baseUrl: String) {
        val webView = WebView(this).also { wv ->
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
                removeAllCookies(null)
            }

            // JS 接口: 检测登录成功
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onLoginSuccess(token: String, username: String) {
                        // 在 WebView 的 JS 线程回调, 需切到主线程
                        runOnUiThread {
                            lifecycleScope.launch {
                                tokenManager.saveAuthInfo(token, username)
                                // 登录成功, 跳转到 MainActivity
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    }
                },
                "LoginBridge"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    // 注入 JS: 检测登录成功并提取 token
                    view.evaluateJavascript(
                        """
                        (function() {
                            // 方法1: 从 Cookie 提取 token
                            var cookies = document.cookie;
                            var tokenMatch = cookies.match(/(?:token|jwt_token|accessToken)=([^;]+)/);
                            var token = tokenMatch ? tokenMatch[1] : null;

                            // 方法2: 从 localStorage 提取 token
                            if (!token) {
                                try {
                                    token = localStorage.getItem('token')
                                        || localStorage.getItem('accessToken')
                                        || localStorage.getItem('jwt_token');
                                } catch(e) {}
                            }

                            // 方法3: 从 URL hash/query 提取 token (某些 OAuth 流程)
                            if (!token) {
                                var urlParams = new URLSearchParams(window.location.search);
                                token = urlParams.get('token') || urlParams.get('access_token');
                            }

                            // 如果找到 token, 通知 Android 端
                            if (token && token.length > 10) {
                                var username = 'user';
                                try {
                                    // 尝试从页面获取用户名
                                    var userEl = document.querySelector('[class*="user-name"], [class*="username"]');
                                    if (userEl) username = userEl.textContent || 'user';
                                } catch(e) {}

                                LoginBridge.onLoginSuccess(token, username);
                            }

                            // 监听登录表单提交
                            if (!token) {
                                var loginForm = document.querySelector('form');
                                if (loginForm && !loginForm.dataset.androidHooked) {
                                    loginForm.dataset.androidHooked = '1';
                                    loginForm.addEventListener('submit', function() {
                                        setTimeout(function() {
                                            // 登录提交后延迟检查 token
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

            // 加载登录页
            loadUrl(baseUrl)
        }

        setContentView(webView)
    }
}

// ============================================================
// 登录界面 (Compose)
// ============================================================

@Composable
private fun LoginScreen(
    onConnect: (String) -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / 标题
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

            // 服务器地址输入框
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

            // 连接按钮
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

            // 说明文字
            Text(
                text = "输入你的 claudecodeui 服务器地址\n连接后在页面中登录",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 18.sp
            )
        }
    }
}
