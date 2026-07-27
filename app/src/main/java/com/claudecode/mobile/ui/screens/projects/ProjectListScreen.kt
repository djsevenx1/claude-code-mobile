@file:OptIn(ExperimentalMaterial3Api::class)

package com.claudecode.mobile.ui.screens.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.network.dto.Project
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// ============================================================
// 项目列表页面
// CloudCLI 移动客户端的主页面，展示用户的所有代码项目
// 支持：项目列表展示、下拉刷新、新建项目、点击进入聊天
// ============================================================

/**
 * 项目列表页面入口 Composable
 *
 * @param viewModel 项目列表 ViewModel
 * @param onNavigateToChat 导航到聊天页面的回调 (参数: projectId, sessionId)
 * @param onNavigateToSettings 导航到设置页面的回调
 * @param modifier 修饰符
 */
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onNavigateToChat: (projectId: String, sessionId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 收集导航事件 (一次性事件，通过 Channel 承载)
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { nav ->
            onNavigateToChat(nav.projectId, nav.sessionId)
        }
    }

    // 新建项目对话框
    if (uiState.showCreateDialog) {
        CreateProjectDialog(
            state = uiState,
            onNameChange = viewModel::updateNewProjectName,
            onPathChange = viewModel::updateNewProjectPath,
            onDisplayNameChange = viewModel::updateNewProjectDisplayName,
            onDismiss = viewModel::hideCreateDialog,
            onCreate = viewModel::createProject
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("项目列表") },
                actions = {
                    // 设置按钮
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 右下角新建项目按钮
            FloatingActionButton(
                onClick = viewModel::showCreateDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "新建项目"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 根据 UI 状态渲染不同内容
            when (val state = uiState.uiState) {
                is ProjectListUiState.Loading -> {
                    // 加载中状态：全屏圆形进度指示器
                    LoadingState()
                }

                is ProjectListUiState.Empty -> {
                    // 空状态：文件夹图标 + 提示文字 + 新建项目按钮
                    EmptyStateView(onCreateProject = viewModel::showCreateDialog)
                }

                is ProjectListUiState.Error -> {
                    // 错误状态：错误信息 + 重试按钮
                    ErrorStateView(
                        message = state.message,
                        onRetry = viewModel::loadProjects
                    )
                }

                is ProjectListUiState.Success -> {
                    // 成功状态：项目列表 (支持下拉刷新)
                    ProjectListContent(
                        projects = state.projects,
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = viewModel::refreshProjects,
                        onProjectClick = viewModel::onProjectClicked
                    )
                }
            }

            // 会话加载遮罩 (点击项目后加载会话时显示)
            if (uiState.isLoadingSessions) {
                SessionLoadingOverlay()
            }
        }
    }
}

// ============================================================
// 子组件: 项目列表内容 (含下拉刷新)
// ============================================================

/**
 * 项目列表内容区域
 *
 * 使用 Material 3 的 [PullToRefreshBox] 实现下拉刷新，
 * 内部 LazyColumn 渲染项目卡片列表。
 *
 * @param projects 项目列表
 * @param isRefreshing 是否正在刷新
 * @param onRefresh 下拉刷新回调
 * @param onProjectClick 项目点击回调
 */
@Composable
private fun ProjectListContent(
    projects: List<Project>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onProjectClick: (Project) -> Unit
) {
    // PullToRefreshBox 是 SwipeRefreshLayout 的 Compose 替代方案
    // 内部使用下拉手势 + 圆形进度指示器 (CircularProgressIndicator)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = projects,
                key = { project -> project.id ?: project.name }
            ) { project ->
                ProjectCard(
                    project = project,
                    onClick = { onProjectClick(project) }
                )
            }
        }
    }
}

// ============================================================
// 子组件: 项目卡片
// ============================================================

/**
 * 单个项目卡片
 *
 * 展示内容：项目名称、路径、最后修改时间、会话数、Git 标识
 * 点击后触发导航到聊天页
 *
 * @param project 项目数据
 * @param onClick 点击回调
 */
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // --- 项目名称行 (图标 + 名称 + Git 标识) ---
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = getProjectDisplayName(project),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Git 仓库标识
                if (project.hasGit == true) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = "Git 仓库",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 项目路径 ---
            Text(
                text = project.path.ifBlank { "路径未设置" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- 底部信息行: 最后修改时间 + 会话数 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 最后修改时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatTime(project.lastModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // 会话数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${project.sessionCount ?: 0} 个会话",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// ============================================================
// 子组件: 加载中状态
// ============================================================

/**
 * 全屏加载状态
 * 显示居中的 CircularProgressIndicator 和加载提示文字
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在加载项目列表...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================
// 子组件: 空状态
// ============================================================

/**
 * 空状态视图
 * 显示文件夹图标 + "暂无项目"提示 + "新建项目"按钮
 *
 * @param onCreateProject 点击新建项目的回调
 */
@Composable
private fun EmptyStateView(onCreateProject: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 文件夹图标
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 提示标题
            Text(
                text = "暂无项目",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提示描述
            Text(
                text = "点击下方按钮创建您的第一个项目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 新建项目按钮
            Button(
                onClick = onCreateProject,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建项目")
            }
        }
    }
}

// ============================================================
// 子组件: 错误状态
// ============================================================

/**
 * 错误状态视图
 * 显示错误图标 + 错误信息 + 重试按钮
 *
 * @param message 错误信息
 * @param onRetry 重试回调
 */
@Composable
private fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 错误图标
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 错误标题
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 错误详细信息
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 重试按钮
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("重试")
            }
        }
    }
}

// ============================================================
// 子组件: 会话加载遮罩
// ============================================================

/**
 * 会话加载遮罩层
 *
 * 点击项目卡片后、导航到聊天页前，短暂展示此遮罩
 * 防止用户重复点击，并提示正在准备会话。
 */
@Composable
private fun SessionLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Text(
                    text = "正在加载会话...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================
// 子组件: 新建项目对话框
// ============================================================

/**
 * 新建项目对话框
 *
 * 表单字段：项目名称 (必填)、项目路径 (可选)、展示名称 (可选)
 * 创建过程中禁用输入与按钮，展示加载指示器。
 *
 * @param state 页面状态 (读取表单字段与创建状态)
 * @param onNameChange 名称变更回调
 * @param onPathChange 路径变更回调
 * @param onDisplayNameChange 展示名称变更回调
 * @param onDismiss 关闭对话框回调
 * @param onCreate 创建项目回调
 */
@Composable
private fun CreateProjectDialog(
    state: ProjectListScreenState,
    onNameChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 创建过程中不允许点击外部关闭
            if (!state.isCreating) onDismiss()
        },
        title = { Text("新建项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 项目名称 (必填)
                OutlinedTextField(
                    value = state.newProjectName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("项目名称 *") },
                    placeholder = { Text("my-project") },
                    singleLine = true,
                    enabled = !state.isCreating,
                    isError = state.createError != null,
                    shape = RoundedCornerShape(8.dp)
                )

                // 项目路径 (可选)
                OutlinedTextField(
                    value = state.newProjectPath,
                    onValueChange = onPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("项目路径 (可选)") },
                    placeholder = { Text("/home/user/projects/my-project") },
                    singleLine = true,
                    enabled = !state.isCreating,
                    shape = RoundedCornerShape(8.dp)
                )

                // 展示名称 (可选)
                OutlinedTextField(
                    value = state.newProjectDisplayName,
                    onValueChange = onDisplayNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("展示名称 (可选)") },
                    placeholder = { Text("我的项目") },
                    singleLine = true,
                    enabled = !state.isCreating,
                    shape = RoundedCornerShape(8.dp)
                )

                // 错误信息展示
                if (state.createError != null) {
                    Text(
                        text = state.createError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCreate,
                enabled = !state.isCreating
            ) {
                if (state.isCreating) {
                    // 创建中: 显示进度指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("创建")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isCreating
            ) {
                Text("取消")
            }
        }
    )
}

// ============================================================
// 辅助函数
// ============================================================

/**
 * 获取项目展示名称
 *
 * 优先级：displayName (非空) > name (非空) > id > "未命名项目"
 *
 * @param project 项目数据
 * @return 用于展示的项目名称
 */
private fun getProjectDisplayName(project: Project): String {
    return project.displayName?.takeIf { it.isNotBlank() }
        ?: project.name.takeIf { it.isNotBlank() }
        ?: project.id
        ?: "未命名项目"
}

/**
 * 格式化时间字符串
 *
 * 尝试解析 ISO 8601 时间字符串 (支持带时区和不带时区两种格式)，
 * 格式化为 "yyyy-MM-dd HH:mm" 的本地时间展示。
 * 解析失败时返回原始字符串。
 *
 * @param isoString ISO 8601 格式的时间字符串 (可为 null)
 * @return 格式化后的时间字符串，或 "未知"
 */
private fun formatTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "未知"

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    return try {
        // 尝试按 Instant (带时区的 ISO 8601) 解析
        val instant = Instant.parse(isoString)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        zonedDateTime.format(formatter)
    } catch (e: DateTimeParseException) {
        try {
            // 尝试按 LocalDateTime (不带时区) 解析
            val localDateTime = LocalDateTime.parse(isoString)
            localDateTime.format(formatter)
        } catch (e2: DateTimeParseException) {
            try {
                // 尝试按 ZonedDateTime 解析 (部分版本带完整时区信息)
                val zonedDateTime = ZonedDateTime.parse(isoString)
                zonedDateTime.format(formatter)
            } catch (e3: DateTimeParseException) {
                // 所有解析均失败，返回原始字符串
                isoString
            }
        }
    }
}
