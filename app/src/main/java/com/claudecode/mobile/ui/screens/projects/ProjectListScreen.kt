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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
    onNavigateToGit: (projectId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Snackbar 宿主状态 (用于展示星标/删除/归档/恢复等操作结果提示)
    val snackbarHostState = remember { SnackbarHostState() }

    // 一次性提示消息：当 snackbarMessage 变化时展示 Snackbar，展示后清空
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.clearSnackbarMessage()
    }

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

    // 重命名项目对话框
    if (uiState.showRenameDialog) {
        RenameProjectDialog(
            state = uiState,
            onNameChange = viewModel::updateRenameProjectName,
            onDismiss = viewModel::hideRenameDialog,
            onRename = {
                val projectId = uiState.renameProjectId
                if (!projectId.isNullOrBlank()) {
                    viewModel.renameProject(projectId, uiState.renameProjectName)
                }
            }
        )
    }

    // 删除项目确认对话框
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            state = uiState,
            onDismiss = viewModel::hideDeleteConfirmDialog,
            onDelete = {
                val projectId = uiState.deleteProjectId
                if (!projectId.isNullOrBlank()) {
                    viewModel.deleteProject(projectId)
                }
            }
        )
    }

    // 已归档项目列表底部 Sheet
    if (uiState.showArchivedSheet) {
        ArchivedProjectsSheet(
            state = uiState,
            onDismiss = viewModel::hideArchivedSheet,
            onRestore = { project -> project.id?.let { viewModel.restoreArchivedProject(it) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("项目列表") },
                actions = {
                    // 查看已归档项目
                    IconButton(onClick = viewModel::loadArchivedProjects) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "已归档项目"
                        )
                    }
                    // 刷新按钮
                    IconButton(onClick = viewModel::refreshProjects) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
                        )
                    }
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
                    // 成功状态：搜索框 + 项目列表 (支持下拉刷新)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部搜索框 (仅在列表加载成功时显示)
                        ProjectSearchBar(
                            query = uiState.searchQuery,
                            onQueryChange = viewModel::updateSearchQuery
                        )
                        // 项目列表内容 (按搜索关键字过滤后传入)
                        ProjectListContent(
                            projects = viewModel.filterProjects(
                                state.projects,
                                uiState.searchQuery
                            ),
                            searchQuery = uiState.searchQuery,
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = viewModel::refreshProjects,
                            onProjectClick = viewModel::onProjectClicked,
                            onToggleStar = { project ->
                                project.id?.let { viewModel.toggleStar(it) }
                            },
                            onRename = viewModel::showRenameDialog,
                            onArchive = { project ->
                                project.id?.let { viewModel.archiveProject(it) }
                            },
                            onDelete = viewModel::showDeleteConfirmDialog,
                            onNavigateToGit = { project ->
                                project.id?.let { onNavigateToGit(it) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
// 子组件: 搜索框
// ============================================================

/**
 * 项目搜索框
 *
 * 位于 TopAppBar 下方、项目列表上方，圆角样式的 OutlinedTextField。
 * - leadingIcon: 搜索图标
 * - trailingIcon: 清除按钮 (仅当输入框有文字时显示)
 * - 输入时实时回调 onQueryChange 触发列表过滤
 *
 * @param query 当前搜索关键字
 * @param onQueryChange 搜索关键字变更回调
 */
@Composable
private fun ProjectSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                text = "搜索项目...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            // 仅当有输入文字时显示清除按钮
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

// ============================================================
// 子组件: 项目列表内容 (含下拉刷新)
// ============================================================

/**
 * 项目列表内容区域
 *
 * 使用 Material 3 的 [PullToRefreshBox] 实现下拉刷新，
 * 内部 LazyColumn 渲染项目卡片列表。
 * 当传入的 [searchQuery] 非空且过滤后列表为空时，展示"未找到匹配项目"提示。
 *
 * @param projects 项目列表 (已按搜索关键字过滤)
 * @param searchQuery 当前搜索关键字 (用于判断是否为搜索无结果)
 * @param isRefreshing 是否正在刷新
 * @param onRefresh 下拉刷新回调
 * @param onProjectClick 项目点击回调
 * @param onToggleStar 切换星标回调
 * @param onRename 重命名项目回调
 * @param onArchive 归档项目回调
 * @param onDelete 删除项目回调 (弹出确认对话框)
 * @param modifier 修饰符
 */
@Composable
private fun ProjectListContent(
    projects: List<Project>,
    searchQuery: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onProjectClick: (Project) -> Unit,
    onToggleStar: (Project) -> Unit,
    onRename: (Project) -> Unit,
    onArchive: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onNavigateToGit: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (projects.isEmpty() && searchQuery.isNotBlank()) {
            // 搜索无结果：展示空提示 (区别于全局空状态)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "未找到匹配的项目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "尝试更换关键字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
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
                        onClick = { onProjectClick(project) },
                        onToggleStar = onToggleStar,
                        onRename = onRename,
                        onArchive = onArchive,
                        onDelete = onDelete,
                        onNavigateToGit = onNavigateToGit
                    )
                }
            }
        }

        // 刷新中遮罩
        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
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
 * 展示内容：项目名称、路径、最后修改时间、会话数、Git 标识、星标状态
 * 点击卡片主体触发导航到聊天页；右上角提供星标快捷按钮与更多操作菜单
 * (重命名、星标/取消星标、归档、删除)。
 *
 * @param project 项目数据
 * @param onClick 卡片点击回调 (进入聊天页)
 * @param onToggleStar 切换星标回调
 * @param onRename 重命名项目回调
 * @param onArchive 归档项目回调
 * @param onDelete 删除项目回调 (弹出确认对话框)
 * @param onNavigateToGit 导航到 Git 管理页面回调
 */
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onToggleStar: (Project) -> Unit,
    onRename: (Project) -> Unit,
    onArchive: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onNavigateToGit: (Project) -> Unit
) {
    // 是否已星标
    val isStarred = project.isStarred == true
    // 更多操作菜单展开状态 (每个卡片独立维护)
    var menuExpanded by remember { mutableStateOf(false) }

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
                // 星标快捷按钮 (已星标显示金色实心星星，未星标显示空心星星)
                IconButton(
                    onClick = { onToggleStar(project) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isStarred) "取消星标" else "星标",
                        tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Git 仓库标识
                if (project.hasGit == true) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = "Git 仓库",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // 更多操作菜单 (重命名、星标/取消星标、归档、删除)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        // 重命名
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                menuExpanded = false
                                onRename(project)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null
                                )
                            }
                        )
                        // 星标 / 取消星标
                        DropdownMenuItem(
                            text = { Text(if (isStarred) "取消星标" else "星标") },
                            onClick = {
                                menuExpanded = false
                                onToggleStar(project)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null
                                )
                            }
                        )
                        // 归档
                        DropdownMenuItem(
                            text = { Text("归档") },
                            onClick = {
                                menuExpanded = false
                                onArchive(project)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Archive,
                                    contentDescription = null
                                )
                            }
                        )
                        // Git 管理
                        DropdownMenuItem(
                            text = { Text("Git 管理") },
                            onClick = {
                                menuExpanded = false
                                onNavigateToGit(project)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Code,
                                    contentDescription = null
                                )
                            }
                        )
                        // 删除
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDelete(project)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
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
                // 会话数 (徽章样式，优化显示)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${project.sessionCount ?: 0} 会话",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
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
                // 项目路径 (必填) - claudecodeui API 要求 path 参数
                OutlinedTextField(
                    value = state.newProjectPath,
                    onValueChange = onPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("项目路径 *") },
                    placeholder = { Text("/home/user/projects/my-project") },
                    singleLine = true,
                    enabled = !state.isCreating,
                    isError = state.createError != null,
                    shape = RoundedCornerShape(8.dp)
                )

                // 自定义展示名称 (可选) - 对应 API 的 customName 参数
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
                enabled = !state.isCreating && state.newProjectPath.isNotBlank()
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
// 子组件: 重命名项目对话框
// ============================================================

/**
 * 重命名项目对话框
 *
 * 预填当前项目名称，用户输入新名称后提交。
 * 重命名过程中禁用输入与按钮，展示加载指示器。
 *
 * @param state 页面状态 (读取重命名表单字段与状态)
 * @param onNameChange 新名称变更回调
 * @param onDismiss 关闭对话框回调
 * @param onRename 提交重命名回调
 */
@Composable
private fun RenameProjectDialog(
    state: ProjectListScreenState,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 重命名过程中不允许点击外部关闭
            if (!state.isRenaming) onDismiss()
        },
        title = { Text("重命名项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 原始名称提示
                if (state.renameProjectOriginalName.isNotBlank()) {
                    Text(
                        text = "当前名称: ${state.renameProjectOriginalName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 新名称输入框
                OutlinedTextField(
                    value = state.renameProjectName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新名称 *") },
                    placeholder = { Text("请输入新的项目名称") },
                    singleLine = true,
                    enabled = !state.isRenaming,
                    isError = state.renameError != null,
                    shape = RoundedCornerShape(8.dp)
                )
                // 错误信息展示
                if (state.renameError != null) {
                    Text(
                        text = state.renameError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRename,
                enabled = !state.isRenaming && state.renameProjectName.isNotBlank()
            ) {
                if (state.isRenaming) {
                    // 重命名中: 显示进度指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("确定")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isRenaming
            ) {
                Text("取消")
            }
        }
    )
}

// ============================================================
// 子组件: 删除项目确认对话框
// ============================================================

/**
 * 删除项目确认对话框
 *
 * 展示待删除项目名称，需用户二次确认后才会执行删除。
 * 删除过程中禁用按钮，展示加载指示器。
 *
 * @param state 页面状态 (读取待删除项目信息与状态)
 * @param onDismiss 关闭对话框回调
 * @param onDelete 确认删除回调
 */
@Composable
private fun DeleteConfirmDialog(
    state: ProjectListScreenState,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // 删除过程中不允许点击外部关闭
            if (!state.isDeleting) onDismiss()
        },
        title = { Text("删除项目") },
        text = {
            Text(
                text = "确定要删除项目 \"${state.deleteProjectName}\" 吗？\n此操作将移除该项目及其关联数据，且不可撤销。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                enabled = !state.isDeleting
            ) {
                if (state.isDeleting) {
                    // 删除中: 显示进度指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isDeleting
            ) {
                Text("取消")
            }
        }
    )
}

// ============================================================
// 子组件: 已归档项目底部 Sheet
// ============================================================

/**
 * 已归档项目列表底部 Sheet
 *
 * 展示通过 GET /api/projects/archived 获取的已归档项目，
 * 支持单个恢复操作 (POST /api/projects/{projectId}/restore)。
 *
 * @param state 页面状态 (读取已归档项目列表与加载状态)
 * @param onDismiss 关闭 Sheet 回调
 * @param onRestore 恢复项目回调
 */
@Composable
private fun ArchivedProjectsSheet(
    state: ProjectListScreenState,
    onDismiss: () -> Unit,
    onRestore: (Project) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        // Sheet 标题
        Text(
            text = "已归档项目",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        when {
            // 加载中
            state.isLoadingArchived -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
            // 归档列表为空
            state.archivedProjects.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "暂无已归档项目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 已归档项目列表
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.archivedProjects,
                        key = { project -> project.id ?: project.name }
                    ) { project ->
                        ArchivedProjectItem(
                            project = project,
                            isRestoring = state.isRestoring,
                            onRestore = { onRestore(project) }
                        )
                    }
                    // 底部留白
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * 已归档项目列表项
 *
 * @param project 已归档项目数据
 * @param isRestoring 是否正在恢复中
 * @param onRestore 恢复回调
 */
@Composable
private fun ArchivedProjectItem(
    project: Project,
    isRestoring: Boolean,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getProjectDisplayName(project),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (project.path.isNotBlank()) {
                    Text(
                        text = project.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 恢复按钮
            TextButton(
                onClick = onRestore,
                enabled = !isRestoring
            ) {
                Icon(
                    imageVector = Icons.Filled.Unarchive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("恢复")
            }
        }
    }
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
