package com.claudecode.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.claudecode.mobile.network.TokenManager
import com.claudecode.mobile.ui.navigation.CloudNavGraph
import com.claudecode.mobile.ui.navigation.Routes
import com.claudecode.mobile.ui.theme.CloudCLITheme
import kotlinx.coroutines.launch

// ============================================================
// 主 Activity (Compose 版本)
// 替换旧的 WebView 版本，使用 Jetpack Compose 构建 UI
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装 Splash Screen (必须在 super.onCreate 之前调用)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 启用边缘到边缘显示 (让内容延伸到状态栏和导航栏区域)
        enableEdgeToEdge()

        // 登录状态: null=检查中, true=已登录, false=未登录
        var isLoggedIn by mutableStateOf<Boolean?>(null)

        // 异步检查登录状态 (使用 network 层的 TokenManager)
        val tokenManager = TokenManager(applicationContext)
        lifecycleScope.launch {
            isLoggedIn = tokenManager.isLoggedIn()
        }

        // 控制 Splash Screen 持续显示，直到登录状态检查完成
        splashScreen.setKeepOnScreenCondition {
            isLoggedIn == null
        }

        setContent {
            // 应用 CloudCLI 主题
            CloudCLITheme {
                // Surface 作为根容器，应用背景色
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 根据登录状态决定起始页面
                    val startDestination = when (isLoggedIn) {
                        true -> Routes.MAIN        // 已登录 -> 主页面 (底部 Tab 导航)
                        false -> Routes.LOGIN      // 未登录 -> 登录页
                        null -> Routes.LOGIN       // 检查中 -> 默认登录页 (Splash 会覆盖)
                    }

                    val navController = rememberNavController()

                    CloudNavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
