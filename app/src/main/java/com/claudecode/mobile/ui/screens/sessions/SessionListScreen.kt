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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    // 刷新按钮
                    IconButton(onClick = viewModel::refreshSessions) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
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
                                    onSessionClick = { session ->
                                        onNavigateToChat(
                                            session.getProjectIdSafe() ?: "",
                                            session.id
                                        )
                                    },
                                    onDeleteSession = viewModel::deleteSession
                                )
                            }
                        }
                    }
                }
            }
        }
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
 * @param onSessionClick 会话点击回调
 * @param onDeleteSession 删除会话回调
 */
@Composable
private fun SessionListContent(
    sessions: List<Session>,
    isRefreshing: Boolean,
    isDeleting: Boolean,
    onSessionClick: (Session) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = sessions,
                key = { session -> session.id }
            ) { session ->
                SessionCard(
                    session = session,
                    isDeleting = isDeleting,
                    onClick = { onSessionClick(session) },
                    onDelete = { onDeleteSession(session.id) }
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
 * 展示内容：终端图标 + 会话标题 + 相对时间 + 消息数 + 删除按钮
 * 点击卡片触发导航到聊天页
 *
 * @param session 会话数据
 * @param isDeleting 是否正在执行删除操作
 * @param onClick 卡片点击回调
 * @param onDelete 删除按钮点击回调
 */
@Composable
private fun SessionCard(
    session: Session,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                            session.lastActiveAt ?: session.updatedAt ?: session.createdAt
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
    return session.title?.takeIf { it.isNotBlank() }
        ?: session.summary?.takeIf { it.isNotBlank() }
        ?: "未命名会话"
}
