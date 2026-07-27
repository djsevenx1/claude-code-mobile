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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.claudecode.mobile.ui.screens.git.GitScreen
import com.claudecode.mobile.ui.screens.git.GitViewModel
import com.claudecode.mobile.ui.screens.projects.ProjectListScreen
import com.claudecode.mobile.ui.screens.projects.ProjectListViewModel
import com.claudecode.mobile.ui.screens.sessions.SessionListScreen
import com.claudecode.mobile.ui.screens.settings.SettingsScreen

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

    /** 主页面 (包含底部 Tab: 项目 + 对话 + 设置) */
    const val MAIN = "main"

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
                    // 登录成功后导航到主页面 (底部 Tab 导航)
                    navController.navigate(Routes.MAIN) {
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

        // --- 主页面 (底部 Tab 导航: 项目 + 对话 + 设置) ---
        composable(Routes.MAIN) {
            MainScreen(navController = navController)
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

        // --- Git 管理页面 (带项目ID参数) ---
        composable(
            route = Routes.GIT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""

            // 获取 Application 上下文用于创建 GitViewModel
            val context = LocalContext.current
            val app = context.applicationContext as android.app.Application

            // 使用工厂创建带参数的 GitViewModel
            val gitViewModel: GitViewModel = viewModel(
                factory = GitViewModel.provideFactory(
                    application = app,
                    projectId = projectId
                )
            )

            GitScreen(
                viewModel = gitViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ============================================================
// 主页面 - 底部 Tab 导航容器
// ============================================================

/**
 * 主页面 Composable
 * 包含底部导航栏，支持在 项目 / 对话 / 设置 三个 Tab 之间切换
 *
 * @param navController 导航控制器 (用于跳转到聊天页等子页面)
 */
@Composable
fun MainScreen(navController: NavHostController) {
    // 当前选中的 Tab 索引: 0=项目, 1=对话, 2=设置
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Tab 1: 项目
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = "项目") },
                    label = { Text("项目") }
                )
                // Tab 2: 对话
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "对话") },
                    label = { Text("对话") }
                )
                // Tab 3: 设置
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                // 项目 Tab: 显示项目列表
                0 -> ProjectListScreen(
                    viewModel = viewModel(),
                    onNavigateToChat = { projectId, sessionId ->
                        navController.navigate(Routes.chatRoute(projectId, sessionId))
                    },
                    onNavigateToSettings = {
                        // 设置已移至底部 Tab，直接切换到设置 Tab
                        selectedTab = 2
                    },
                    onNavigateToGit = { projectId ->
                        navController.navigate(Routes.gitRoute(projectId))
                    }
                )
                // 对话 Tab: 显示会话列表
                1 -> SessionListScreen(
                    viewModel = viewModel(),
                    onNavigateToChat = { projectId, sessionId ->
                        navController.navigate(Routes.chatRoute(projectId, sessionId))
                    }
                )
                // 设置 Tab: 显示设置页
                2 -> SettingsScreen(
                    viewModel = viewModel(),
                    onBack = {
                        // 返回到项目 Tab
                        selectedTab = 0
                    },
                    onLoggedOut = {
                        // 登出后导航到登录页，并清除主页面返回栈
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                )
            }
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
