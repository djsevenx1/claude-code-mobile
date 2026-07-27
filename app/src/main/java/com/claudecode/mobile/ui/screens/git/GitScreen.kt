@file:OptIn(ExperimentalMaterial3Api::class)

package com.claudecode.mobile.ui.screens.git

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.network.dto.GitFileChange
import com.claudecode.mobile.network.dto.GitStatus

// ============================================================
// Git 管理页面
// 展示项目 Git 仓库状态、文件变更列表，并支持勾选文件提交
// 功能：分支信息展示、文件变更列表、全选/单选、提交信息输入、提交变更
// ============================================================

/**
 * Git 管理页面入口 Composable
 *
 * @param viewModel Git 管理 ViewModel
 * @param onBack 返回按钮回调
 * @param modifier 修饰符
 */
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Snackbar 宿主状态，用于展示提交成功/失败提示
    val snackbarHostState = remember { SnackbarHostState() }

    // 提交结果变化时展示 Snackbar
    LaunchedEffect(uiState.commitResult) {
        uiState.commitResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCommitResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git 管理") },
                navigationIcon = {
                    // 返回按钮
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 刷新按钮
                    IconButton(
                        onClick = viewModel::refreshGitStatus,
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 根据 UI 状态渲染不同内容
            when (val state = uiState.uiState) {
                is GitUiState.Loading -> {
                    // 加载中状态：全屏圆形进度指示器
                    LoadingState()
                }

                is GitUiState.Error -> {
                    // 错误状态：错误信息 + 重试按钮
                    ErrorStateView(
                        message = state.message,
                        onRetry = viewModel::loadGitStatus
                    )
                }

                is GitUiState.Empty -> {
                    // 空状态：工作区干净，仍展示分支信息
                    Column(modifier = Modifier.fillMaxSize()) {
                        BranchInfoCard(status = state.status)
                        CleanWorkspaceView()
                    }
                }

                is GitUiState.Success -> {
                    // 成功状态：分支信息 + 文件变更列表 + 提交区域
                    GitScreenContent(
                        state = uiState,
                        status = state.status,
                        onToggleFile = viewModel::toggleFileSelection,
                        onToggleSelectAll = viewModel::toggleSelectAll,
                        onCommitMessageChange = viewModel::updateCommitMessage,
                        onCommit = viewModel::commitChanges,
                        onClearError = viewModel::clearCommitError
                    )
                }
            }
        }
    }
}

// ============================================================
// 子组件: 成功状态内容区 (分支 + 文件列表 + 提交)
// ============================================================

/**
 * 成功状态下的页面内容
 *
 * 由分支信息卡片、文件变更列表、提交区域三部分组成。
 * 文件列表与提交区域可滚动，提交区域固定在底部。
 *
 * @param state 页面状态
 * @param status Git 状态
 * @param onToggleFile 切换文件勾选回调
 * @param onToggleSelectAll 切换全选回调
 * @param onCommitMessageChange 提交信息变更回调
 * @param onCommit 提交回调
 * @param onClearError 清除提交错误回调
 */
@Composable
private fun GitScreenContent(
    state: GitScreenState,
    status: GitStatus,
    onToggleFile: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onCommit: () -> Unit,
    onClearError: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：分支信息卡片
        BranchInfoCard(status = status)

        // 中部：文件变更列表 (可滚动，占据剩余空间)
        Box(modifier = Modifier.weight(1f)) {
            FileChangeList(
                files = status.files,
                selectedFiles = state.selectedFiles,
                selectAllState = computeToggleableState(
                    selectedCount = state.selectedFiles.size,
                    totalCount = status.files.size
                ),
                onToggleFile = onToggleFile,
                onToggleSelectAll = onToggleSelectAll
            )
        }

        HorizontalDivider()

        // 底部：提交信息输入与提交按钮
        CommitSection(
            message = state.commitMessage,
            onMessageChange = onCommitMessageChange,
            isCommitting = state.isCommitting,
            commitError = state.commitError,
            onCommit = onCommit,
            onClearError = onClearError,
            enabled = status.files.isNotEmpty()
        )
    }
}

// ============================================================
// 子组件: 分支信息卡片
// ============================================================

/**
 * 分支信息卡片
 *
 * 展示当前分支名、远程跟踪信息、领先/落后提交数、工作区干净状态。
 *
 * @param status Git 状态
 */
@Composable
private fun BranchInfoCard(status: GitStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 分支名行 (分支图标 + 分支名)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = "分支",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status.branch ?: "(无分支)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // 领先/落后提交数 (仅当存在时展示)
            if (status.ahead > 0 || status.behind > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (status.ahead > 0) {
                        StatusBadge(
                            icon = Icons.Filled.SwapVert,
                            text = "领先 ${status.ahead}",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (status.behind > 0) {
                        StatusBadge(
                            icon = Icons.Filled.SwapVert,
                            text = "落后 ${status.behind}",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 状态徽章 (用于展示领先/落后提交数等小标签)
 */
@Composable
private fun StatusBadge(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

// ============================================================
// 子组件: 文件变更列表
// ============================================================

/**
 * 文件变更列表
 *
 * 顶部为"全选"行 (TriStateCheckbox)，下方为文件列表。
 * 每个文件行包含：勾选框、状态图标、文件路径、状态标签。
 *
 * @param files 变更文件列表
 * @param selectedFiles 已选中文件路径集合
 * @param selectAllState 全选复选框状态 (On/Off/Indeterminate)
 * @param onToggleFile 切换单个文件勾选回调
 * @param onToggleSelectAll 切换全选回调
 */
@Composable
private fun FileChangeList(
    files: List<GitFileChange>,
    selectedFiles: Set<String>,
    selectAllState: ToggleableState,
    onToggleFile: (String) -> Unit,
    onToggleSelectAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 全选行 (粘性标题)
        item(key = "select_all") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(
                    state = selectAllState,
                    onClick = onToggleSelectAll
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "全选 (${selectedFiles.size}/${files.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider()
        }

        // 文件列表项
        items(
            items = files,
            key = { file -> file.path }
        ) { file ->
            val style = getFileStatusStyle(file)
            FileChangeItem(
                file = file,
                style = style,
                isSelected = file.path in selectedFiles,
                onToggle = { onToggleFile(file.path) }
            )
        }
    }
}

/**
 * 单个文件变更项
 *
 * @param file 文件变更数据
 * @param style 状态样式 (颜色 + 图标 + 标签)
 * @param isSelected 是否已选中
 * @param onToggle 切换勾选回调
 */
@Composable
private fun FileChangeItem(
    file: GitFileChange,
    style: FileStatusStyle,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 勾选框
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )

        // 状态图标
        Icon(
            imageVector = style.icon,
            contentDescription = style.label,
            tint = style.color,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 文件路径与状态标签
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 重命名场景下展示旧路径
            if (!file.oldPath.isNullOrBlank()) {
                Text(
                    text = "← ${file.oldPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 状态标签徽章
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = style.color.copy(alpha = 0.12f)
        ) {
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color = style.color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

// ============================================================
// 子组件: 提交区域
// ============================================================

/**
 * 提交区域
 *
 * 包含提交信息输入框与提交按钮，固定在页面底部。
 * 提交中时禁用输入与按钮，并展示加载指示器。
 *
 * @param message 提交信息文本
 * @param onMessageChange 提交信息变更回调
 * @param isCommitting 是否正在提交
 * @param commitError 提交错误信息 (null 表示无错误)
 * @param onCommit 提交回调
 * @param onClearError 清除错误回调
 * @param enabled 是否允许提交 (无文件变更时禁用)
 */
@Composable
private fun CommitSection(
    message: String,
    onMessageChange: (String) -> Unit,
    isCommitting: Boolean,
    commitError: String?,
    onCommit: () -> Unit,
    onClearError: () -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 提交信息输入框
        OutlinedTextField(
            value = message,
            onValueChange = {
                onMessageChange(it)
                // 输入时清除错误提示
                if (commitError != null) onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("提交信息") },
            placeholder = { Text("请输入提交信息...") },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            enabled = !isCommitting,
            isError = commitError != null,
            shape = RoundedCornerShape(8.dp),
            supportingText = {
                if (commitError != null) {
                    Text(
                        text = commitError,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        // 提交按钮
        Button(
            onClick = onCommit,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isCommitting && message.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isCommitting) {
                // 提交中：展示进度指示器
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("提交中...")
            } else {
                Text("提交")
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
                text = "正在加载 Git 状态...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================
// 子组件: 工作区干净状态
// ============================================================

/**
 * 工作区干净状态视图
 * 表示当前无未提交变更，展示提示信息
 */
@Composable
private fun CleanWorkspaceView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "工作区干净",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "没有未提交的变更",
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
            Icon(
                imageVector = Icons.Filled.CallSplit,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
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
// 辅助: 文件状态样式
// ============================================================

/**
 * 文件状态样式
 *
 * @param color 状态颜色 (modified=黄, staged=绿, untracked=灰, deleted=红)
 * @param icon 状态图标
 * @param label 状态中文标签
 */
private data class FileStatusStyle(
    val color: Color,
    val icon: ImageVector,
    val label: String
)

/**
 * Git 文件状态语义颜色
 *
 * 使用固定颜色值以保持 Git 状态语义的视觉一致性：
 * - 绿色：已暂存 / 已新增
 * - 黄色：已修改 (未暂存)
 * - 灰色：未跟踪
 * - 红色：已删除
 * - 蓝色：已重命名
 */
private object GitStatusColors {
    val Staged = Color(0xFF4CAF50)       // 已暂存 - 绿色
    val Added = Color(0xFF66BB6A)        // 已新增 - 浅绿
    val Modified = Color(0xFFFFA000)     // 已修改 - 琥珀/黄
    val Untracked = Color(0xFF9E9E9E)    // 未跟踪 - 灰色
    val Deleted = Color(0xFFE53935)       // 已删除 - 红色
    val Renamed = Color(0xFF1E88E5)      // 已重命名 - 蓝色
}

/**
 * 根据文件变更状态获取对应的样式 (颜色 + 图标 + 标签)
 *
 * 优先根据 staged 字段判断：已暂存统一显示为绿色对勾。
 * 未暂存时再按 status 首字符 (M/A/D/R/?) 区分。
 *
 * @param file 文件变更
 * @return 对应的状态样式
 */
private fun getFileStatusStyle(file: GitFileChange): FileStatusStyle {
    // 已暂存文件统一显示为绿色对勾
    if (file.staged) {
        return FileStatusStyle(
            color = GitStatusColors.Staged,
            icon = Icons.Filled.CheckCircle,
            label = "已暂存"
        )
    }

    // 未暂存文件按状态字符区分
    return when {
        file.status.startsWith("?") -> FileStatusStyle(
            GitStatusColors.Untracked, Icons.Filled.Add, "未跟踪"
        )
        file.status.startsWith("M") -> FileStatusStyle(
            GitStatusColors.Modified, Icons.Filled.Edit, "已修改"
        )
        file.status.startsWith("D") -> FileStatusStyle(
            GitStatusColors.Deleted, Icons.Filled.Delete, "已删除"
        )
        file.status.startsWith("A") -> FileStatusStyle(
            GitStatusColors.Added, Icons.Filled.Add, "已新增"
        )
        file.status.startsWith("R") -> FileStatusStyle(
            GitStatusColors.Renamed, Icons.Filled.SwapHoriz, "已重命名"
        )
        else -> FileStatusStyle(
            GitStatusColors.Untracked, Icons.Filled.InsertDriveFile, "变更"
        )
    }
}

/**
 * 计算全选复选框的三态状态
 *
 * - 全部选中：On
 * - 全部未选：Off
 * - 部分选中：Indeterminate
 *
 * @param selectedCount 已选中文件数
 * @param totalCount 文件总数
 * @return TriStateCheckbox 所需的状态
 */
private fun computeToggleableState(
    selectedCount: Int,
    totalCount: Int
): ToggleableState {
    return when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount >= totalCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}
