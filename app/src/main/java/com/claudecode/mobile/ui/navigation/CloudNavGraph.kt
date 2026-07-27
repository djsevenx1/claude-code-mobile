package com.claudecode.mobile.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.claudecode.mobile.ui.screens.auth.LoginScreen
import com.claudecode.mobile.ui.screens.auth.LoginViewModel
import com.claudecode.mobile.ui.screens.chat.ChatScreen
import com.claudecode.mobile.ui.screens.chat.ChatViewModel
import com.claudecode.mobile.ui.screens.projects.ProjectListScreen
import com.claudecode.mobile.ui.screens.projects.ProjectListViewModel

// ============================================================
// CloudCLI 导航图
// 定义应用内所有页面的路由和导航逻辑
// ============================================================

/**
 * 路由常量定义 - 集中管理所有导航路径
 */
object Routes {
    /** 登录页面 */
    const val LOGIN = "login"

    /** 服务器配置页面 (首次使用时) */
    const val SERVER_SETUP = "server_setup"

    /** 项目列表页面 */
    const val PROJECTS = "projects"

    /** 聊天页面 (带项目ID和会话ID参数) */
    const val CHAT = "chat/{projectId}/{sessionId}"

    /** 设置页面 */
    const val SETTINGS = "settings"

    /** Git 管理页面 (带项目ID参数) */
    const val GIT = "git/{projectId}"

    // --- 辅助方法: 构建带参数的路由 ---

    /** 构建聊天页面完整路由 */
    fun chatRoute(projectId: String, sessionId: String): String {
        return "chat/$projectId/$sessionId"
    }

    /** 构建Git管理页面完整路由 */
    fun gitRoute(projectId: String): String {
        return "git/$projectId"
    }
}

/**
 * CloudCLI 主导航图
 *
 * @param navController 导航控制器
 * @param startDestination 起始页面路由
 */
@Composable
fun CloudNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.LOGIN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // --- 登录页面 ---
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = viewModel(),
                onLoginSuccess = {
                    // 登录成功后导航到项目列表页
                    navController.navigate(Routes.PROJECTS) {
                        // 清除返回栈，防止按返回键回到登录页
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToServerSetup = {
                    navController.navigate(Routes.SERVER_SETUP)
                }
            )
        }

        // --- 服务器配置页面 ---
        composable(Routes.SERVER_SETUP) {
            PlaceholderScreen(
                title = "服务器配置",
                subtitle = "在此配置 CloudCLI 服务器连接信息",
                icon = Icons.Filled.Build,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 项目列表页面 (已实现) ---
        composable(Routes.PROJECTS) {
            ProjectListScreen(
                viewModel = viewModel(),
                onNavigateToChat = { projectId, sessionId ->
                    navController.navigate(Routes.chatRoute(projectId, sessionId))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        // --- 聊天页面 (已实现, 带项目ID和会话ID参数) ---
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            // 获取 Application 上下文用于创建 ChatViewModel
            val context = LocalContext.current
            val app = context.applicationContext as android.app.Application

            // 使用工厂创建带参数的 ChatViewModel
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(
                    application = app,
                    projectId = projectId,
                    sessionId = sessionId
                )
            )

            ChatScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 设置页面 ---
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                title = "设置",
                subtitle = "应用偏好设置与服务器管理",
                icon = Icons.Filled.Settings
            )
        }

        // --- Git 管理页面 (带项目ID参数) ---
        composable(
            route = Routes.GIT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            PlaceholderScreen(
                title = "Git 管理",
                subtitle = "项目: $projectId",
                icon = Icons.Filled.Code
            )
        }
    }
}

// ============================================================
// 占位页面 - 用于尚未实现的页面
// ============================================================

/**
 * 占位页面 Composable
 * 在正式页面开发完成前，展示页面标题和说明
 *
 * @param title 页面标题
 * @param subtitle 页面副标题/说明
 * @param icon 页面图标
 * @param onBack 返回按钮回调 (可选)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onBack: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "(页面开发中)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
