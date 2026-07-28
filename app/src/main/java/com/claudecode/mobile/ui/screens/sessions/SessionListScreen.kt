@file:OptIn(ExperimentalMaterial3Api::class)

package com.claudecode.mobile.ui.screens.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.network.dto.Project
import com.claudecode.mobile.network.dto.Session

// ============================================================
// 会话列表页面
// 展示用户所有会话（跨项目），支持搜索、下拉刷新、删除与点击进入聊天
// ============================================================

/**
 * 会话列表页面入口 Composable
 *
 * @param viewModel 会话列表 ViewModel
 * @param onNavigateToChat 导航到聊天页面的回调 (参数: projectId, sessionId)
 * @param onBack 返回上一页回调 (可选，在底部 Tab 中使用时为 null)
 * @param modifier 修饰符
 */
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onNavigateToChat: (projectId: String, sessionId: String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                navigationIcon = {
                    // 仅当 onBack 不为空时显示返回按钮 (底部 Tab 中无需返回)
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    // 已归档会话入口按钮
                    IconButton(onClick = viewModel::showArchivedDialog) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "已归档会话"
                        )
                    }
                    // 刷新按钮
                    IconButton(onClick = viewModel::refreshSessions) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 新建对话按钮
            FloatingActionButton(
                onClick = viewModel::showNewSessionDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "新建对话"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- 搜索框 ---
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClear = viewModel::clearSearchQuery
                )

                // --- 列表内容区域 ---
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 根据 UI 状态渲染不同内容
                    when (val state = uiState.uiState) {
                        is SessionListUiState.Loading -> {
                            // 加载中状态：全屏圆形进度指示器
                            LoadingState()
                        }

                        is SessionListUiState.Empty -> {
                            // 空状态：会话图标 + 提示文字
                            EmptyStateView(
                                isSearching = uiState.searchQuery.isNotBlank()
                            )
                        }

                        is SessionListUiState.Error -> {
                            // 错误状态：错误信息 + 重试按钮
                            ErrorStateView(
                                message = state.message,
                                onRetry = viewModel::loadSessions
                            )
                        }

                        is SessionListUiState.Success -> {
                            // 成功状态：会话列表
                            if (state.sessions.isEmpty()) {
                                // 搜索过滤后无结果
                                EmptyStateView(isSearching = uiState.searchQuery.isNotBlank())
                            } else {
                                SessionListContent(
                                    sessions = state.sessions,
                                    isRefreshing = uiState.isRefreshing,
                                    isDeleting = uiState.isDeleting,
                                    isProcessing = uiState.isProcessing,
                                    onSessionClick = { session ->
                                        val projectId = session.getProjectIdSafe()
                                        val sessionId = session.getIdSafe()
                                        // 安全检查: projectId 或 sessionId 为空时不导航，防止崩溃
                                        if (!projectId.isNullOrBlank() && sessionId.isNotBlank()) {
                                            onNavigateToChat(projectId, sessionId)
                                        }
                                    },
                                    onDeleteSession = viewModel::deleteSession,
                                    onRenameSession = viewModel::showRenameDialog,
                                    onArchiveSession = { sessionId ->
                                        viewModel.archiveSession(sessionId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 重命名对话框 ---
    if (uiState.showRenameDialog) {
        RenameSessionDialog(
            title = uiState.renameText,
            onTitleChange = viewModel::updateRenameText,
            onDismiss = viewModel::dismissRenameDialog,
            onConfirm = {
                uiState.renamingSessionId?.let { sessionId ->
                    viewModel.renameSession(sessionId, uiState.renameText)
                }
            },
            isProcessing = uiState.isProcessing
        )
    }

    // --- 已归档会话列表对话框 ---
    if (uiState.showArchivedDialog) {
        ArchivedSessionsDialog(
            sessions = uiState.archivedSessions,
            isLoading = uiState.isLoadingArchived,
            isProcessing = uiState.isProcessing,
            onDismiss = viewModel::dismissArchivedDialog,
            onRestore = viewModel::restoreArchivedSession
        )
    }

    // --- 新建对话 (选择项目) 对话框 ---
    if (uiState.showNewSessionDialog) {
        NewSessionDialog(
            projects = uiState.availableProjects,
            isLoading = uiState.isLoadingProjects,
            onDismiss = viewModel::dismissNewSessionDialog,
            onSelectProject = { project ->
                viewModel.dismissNewSessionDialog()
                onNavigateToChat(project.id ?: "", "")
            }
        )
    }
}

// ============================================================
// 子组件: 搜索框
// ============================================================

/**
 * 搜索输入框
 *
 * 圆角样式的 OutlinedTextField，带搜索图标和清除按钮。
 *
 * @param query 当前搜索关键词
 * @param onQueryChange 关键词变更回调
 * @param onClear 清空搜索回调
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("搜索会话...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "清除搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

// ============================================================
// 子组件: 会话列表内容
// ============================================================

/**
 * 会话列表内容区域
 *
 * 使用 LazyColumn 渲染会话卡片列表。
 * 刷新时在列表上方叠加半透明遮罩与进度指示器。
 *
 * @param sessions 会话列表
 * @param isRefreshing 是否正在刷新
 * @param isDeleting 是否正在删除会话
 * @param isProcessing 是否正在执行重命名/归档/恢复等操作
 * @param onSessionClick 会话点击回调
 * @param onDeleteSession 删除会话回调
 * @param onRenameSession 重命名会话回调 (参数: 会话对象)
 * @param onArchiveSession 归档会话回调 (参数: 会话标识)
 */
@Composable
private fun SessionListContent(
    sessions: List<Session>,
    isRefreshing: Boolean,
    isDeleting: Boolean,
    isProcessing: Boolean,
    onSessionClick: (Session) -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (Session) -> Unit,
    onArchiveSession: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = sessions,
                key = { session -> session.getIdSafe() }
            ) { session ->
                SessionCard(
                    session = session,
                    isDeleting = isDeleting,
                    isProcessing = isProcessing,
                    onClick = { onSessionClick(session) },
                    onDelete = { onDeleteSession(session.getIdSafe()) },
                    onRename = { onRenameSession(session) },
                    onArchive = { onArchiveSession(session.getIdSafe()) }
                )
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
// 子组件: 会话卡片
// ============================================================

/**
 * 单个会话卡片
 *
 * 展示内容：终端图标 + 会话标题 + 相对时间 + 消息数 + 更多操作按钮 + 删除按钮
 * 点击卡片触发导航到聊天页
 *
 * 更多操作按钮 (三个点) 弹出下拉菜单，包含"重命名"和"归档"选项。
 *
 * @param session 会话数据
 * @param isDeleting 是否正在执行删除操作
 * @param isProcessing 是否正在执行重命名/归档等操作 (用于禁用更多操作按钮)
 * @param onClick 卡片点击回调
 * @param onDelete 删除按钮点击回调
 * @param onRename 重命名回调
 * @param onArchive 归档回调
 */
@Composable
private fun SessionCard(
    session: Session,
    isDeleting: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 终端图标
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // --- 会话信息 (标题 + 底部信息行) ---
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 会话标题
                Text(
                    text = getSessionDisplayTitle(session),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 底部信息行: 相对时间 + 消息数
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 相对时间
                    Text(
                        text = formatRelativeTime(
                            session.getLastActivitySafe()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 消息数
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${session.getMessageCountSafe() ?: 0} 条消息",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // --- 更多操作按钮 (三个点) + 下拉菜单 ---
            // 更多操作下拉菜单展开状态
            var menuExpanded by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !isProcessing
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 下拉菜单: 重命名 / 归档
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    // 重命名选项
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    // 归档选项
                    DropdownMenuItem(
                        text = { Text("归档") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onArchive()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 删除按钮
            IconButton(
                onClick = onDelete,
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除会话",
                        tint = MaterialTheme.colorScheme.error
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
                text = "正在加载会话列表...",
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
 * 显示会话图标 + "暂无会话"提示
 *
 * @param isSearching 是否处于搜索状态（搜索无结果时显示不同提示文案）
 */
@Composable
private fun EmptyStateView(isSearching: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 会话图标
            Icon(
                imageVector = Icons.Outlined.Chat,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 提示标题
            Text(
                text = if (isSearching) "未找到匹配的会话" else "暂无会话",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 提示描述
            Text(
                text = if (isSearching) "尝试更换关键词或清除搜索" else "开始对话后会显示在这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
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
                imageVector = Icons.Filled.SmartToy,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 重试按钮
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

// ============================================================
// 辅助函数
// ============================================================

/**
 * 获取会话展示标题
 *
 * 优先级：title (非空) > summary (非空) > "未命名会话"
 *
 * @param session 会话数据
 * @return 用于展示的会话标题
 */
private fun getSessionDisplayTitle(session: Session): String {
    return session.getTitleSafe()
}

// ============================================================
// 子组件: 重命名对话框
// ============================================================

/**
 * 会话重命名对话框
 *
 * 包含一个标题输入框 (OutlinedTextField)，确认后触发重命名回调。
 * 处理中 (isProcessing = true) 时禁用确认按钮并显示加载指示器。
 *
 * @param title 当前输入的标题
 * @param onTitleChange 标题变更回调
 * @param onDismiss 取消/关闭回调
 * @param onConfirm 确认重命名回调
 * @param isProcessing 是否正在处理重命名请求
 */
@Composable
private fun RenameSessionDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isProcessing: Boolean
) {
    AlertDialog(
        onDismissRequest = {
            // 处理中时不允许通过点击外部关闭
            if (!isProcessing) onDismiss()
        },
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入新的会话标题") },
                singleLine = true,
                enabled = !isProcessing
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("取消")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isProcessing && title.isNotBlank()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("确定")
                }
            }
        }
    )
}

// ============================================================
// 子组件: 已归档会话列表对话框
// ============================================================

/**
 * 已归档会话列表对话框
 *
 * 展示已归档的会话列表，每项提供"恢复"按钮。
 * 加载中 (isLoading = true) 时显示加载指示器。
 *
 * @param sessions 已归档会话列表
 * @param isLoading 是否正在加载已归档会话
 * @param isProcessing 是否正在执行恢复操作
 * @param onDismiss 关闭对话框回调
 * @param onRestore 恢复会话回调 (参数: 会话标识)
 */
@Composable
private fun ArchivedSessionsDialog(
    sessions: List<Session>,
    isLoading: Boolean,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已归档会话") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 400.dp)
            ) {
                when {
                    // 加载中状态
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在加载...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 无已归档会话
                    sessions.isEmpty() -> {
                        Text(
                            text = "暂无已归档会话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                    // 已归档会话列表
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = sessions,
                                key = { it.id }
                            ) { session ->
                                // 单条已归档会话项
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 会话标题 (权重 1 占满剩余空间)
                                    Text(
                                        text = getSessionDisplayTitle(session),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // 恢复按钮
                                    TextButton(
                                        onClick = { onRestore(session.getIdSafe()) },
                                        enabled = !isProcessing,
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text("恢复")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

// ============================================================
// 子组件: 新建对话 (选择项目) 对话框
// ============================================================

/**
 * 新建对话对话框
 *
 * 展示可选的项目列表，用户选择项目后导航到聊天页 (sessionId 为空)。
 * 加载中时显示加载指示器。
 *
 * @param projects 可选的项目列表
 * @param isLoading 是否正在加载项目列表
 * @param onDismiss 关闭对话框回调
 * @param onSelectProject 选择项目回调
 */
@Composable
private fun NewSessionDialog(
    projects: List<Project>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectProject: (Project) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 400.dp)
            ) {
                when {
                    // 加载中状态
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在加载项目列表...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // 无可用项目
                    projects.isEmpty() -> {
                        Text(
                            text = "暂无可用项目，请先创建项目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                    // 项目列表
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = projects,
                                key = { it.id ?: it.name }
                            ) { project ->
                                // 单个项目项 (可点击)
                                Card(
                                    onClick = { onSelectProject(project) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = project.displayName?.takeIf { it.isNotBlank() }
                                                    ?: project.name.takeIf { it.isNotBlank() }
                                                    ?: project.id
                                                    ?: "未命名项目",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (project.path.isNotBlank()) {
                                                Text(
                                                    text = project.path,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
