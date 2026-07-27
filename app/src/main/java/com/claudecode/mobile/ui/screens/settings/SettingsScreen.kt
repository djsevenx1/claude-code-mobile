@file:OptIn(ExperimentalMaterial3Api::class)

package com.claudecode.mobile.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.data.repository.ThemeMode
import com.claudecode.mobile.network.dto.ModelInfo

// ============================================================
// 设置页面
// 展示与管理用户偏好设置：服务器信息、主题、AI 模型、关于、登出
// ============================================================

/** 应用版本号 */
private const val APP_VERSION = "1.1.1"

/** 报告问题页面 URL */
private const val ISSUE_URL = "https://github.com/djsevenx1/claude-code-mobile/issues"

/** 社区页面 URL */
private const val COMMUNITY_URL = "https://github.com/djsevenx1/claude-code-mobile"

/**
 * 设置页面入口 Composable
 *
 * @param viewModel 设置 ViewModel
 * @param onBack 返回上一页回调
 * @param onLoggedOut 登出完成后的回调 (导航到登录页)
 * @param modifier 修饰符
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 登出确认对话框状态
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 监听登出状态，触发导航回调
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

    // 登出确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？退出后需要重新登录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出登录", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部加载/保存进度条
            if (uiState.isLoading || uiState.isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 设置列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // --- 错误信息横幅 ---
                if (uiState.error != null) {
                    item {
                        ErrorBanner(
                            message = uiState.error!!,
                            onRetry = viewModel::loadSettings,
                            onDismiss = viewModel::clearError
                        )
                    }
                }

                // ==================== 服务器信息组 ====================
                item { SectionHeader("服务器信息") }
                item {
                    InfoRow(
                        icon = Icons.Filled.Dns,
                        title = "服务器地址",
                        value = uiState.serverUrl.ifBlank { "未配置" }
                    )
                }
                item {
                    InfoRow(
                        icon = Icons.Filled.Person,
                        title = "当前用户名",
                        value = uiState.username.ifBlank { "未登录" }
                    )
                }

                item { SectionDivider() }

                // ==================== 外观组 ====================
                item { SectionHeader("外观") }
                item {
                    ThemeModeSelector(
                        icon = Icons.Filled.Palette,
                        currentMode = uiState.themeMode,
                        onSelect = viewModel::setThemeMode
                    )
                }

                item { SectionDivider() }

                // ==================== AI 模型组 ====================
                item { SectionHeader("AI 模型") }
                item {
                    ModelSelectorRow(
                        currentModel = uiState.defaultModel,
                        availableModels = uiState.availableModels,
                        onSelect = viewModel::setDefaultModel
                    )
                }
                item {
                    SwitchRow(
                        icon = Icons.Filled.Build,
                        title = "自动批准工具",
                        subtitle = "允许 AI 自动执行工具调用，无需手动确认",
                        checked = uiState.settings?.autoApproveTools ?: false,
                        enabled = uiState.settings != null,
                        onCheckedChange = viewModel::toggleAutoApproveTools
                    )
                }
                item {
                    SwitchRow(
                        icon = Icons.Filled.Bolt,
                        title = "流式输出",
                        subtitle = "实时逐字输出 AI 回复内容",
                        checked = uiState.settings?.streamingOutput ?: false,
                        enabled = uiState.settings != null,
                        onCheckedChange = viewModel::toggleStreamingOutput
                    )
                }
                item {
                    SwitchRow(
                        icon = Icons.Filled.DataUsage,
                        title = "显示 Token 用量",
                        subtitle = "在对话中展示每次请求的 Token 消耗",
                        checked = uiState.settings?.showTokenUsage ?: false,
                        enabled = uiState.settings != null,
                        onCheckedChange = viewModel::toggleShowTokenUsage
                    )
                }

                item { SectionDivider() }

                // ==================== 关于组 ====================
                item { SectionHeader("关于") }
                item {
                    InfoRow(
                        icon = Icons.Filled.Info,
                        title = "应用版本",
                        value = APP_VERSION
                    )
                }
                item {
                    ClickableRow(
                        icon = Icons.Filled.BugReport,
                        title = "报告问题",
                        subtitle = "提交 Bug 反馈或功能建议",
                        onClick = {
                            openUrl(context, ISSUE_URL)
                        }
                    )
                }
                item {
                    ClickableRow(
                        icon = Icons.Filled.Group,
                        title = "加入社区",
                        subtitle = "参与讨论与获取最新动态",
                        onClick = {
                            openUrl(context, COMMUNITY_URL)
                        }
                    )
                }

                // ==================== 底部登出按钮 ====================
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    LogoutButton(
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
        }
    }
}

// ============================================================
// 子组件: 分组标题
// ============================================================

/**
 * 分组标题
 *
 * @param title 分组名称
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 分组之间的分隔线
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// ============================================================
// 子组件: 只读信息行
// ============================================================

/**
 * 只读信息行 (用于展示服务器地址、用户名、版本号等)
 *
 * @param icon 前置图标
 * @param title 标题
 * @param value 值 (只读展示)
 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// 子组件: 开关行
// ============================================================

/**
 * 带开关的设置行
 *
 * @param icon 前置图标
 * @param title 标题
 * @param subtitle 副标题 / 描述 (可选)
 * @param checked 开关当前状态
 * @param enabled 是否可交互
 * @param onCheckedChange 开关切换回调
 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

// ============================================================
// 子组件: 主题模式选择 (RadioButton 列表)
// ============================================================

/**
 * 主题模式选择器
 *
 * 使用 RadioButton 列表展示三种主题模式：跟随系统 / 亮色 / 暗色。
 *
 * @param icon 前置图标
 * @param currentMode 当前选中的主题模式
 * @param onSelect 选择回调
 */
@Composable
private fun ThemeModeSelector(
    icon: ImageVector,
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    // 主题模式选项列表 (枚举值 -> 中文显示名)
    val options = listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.LIGHT to "亮色",
        ThemeMode.DARK to "暗色"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 单选项列表
        options.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(40.dp))
                RadioButton(
                    selected = mode == currentMode,
                    onClick = { onSelect(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================
// 子组件: 模型选择 (DropdownMenu)
// ============================================================

/**
 * 默认模型选择行
 *
 * 使用 DropdownMenu 从可用模型列表中选择默认模型。
 * 当可用模型列表为空时，仅展示当前模型名称（不可展开）。
 *
 * @param currentModel 当前选中的模型标识
 * @param availableModels 可用模型列表
 * @param onSelect 选择回调
 */
@Composable
private fun ModelSelectorRow(
    currentModel: String,
    availableModels: List<ModelInfo>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // 获取当前模型的显示名称
    val currentModelName = availableModels.find { it.id == currentModel }?.name
        ?: currentModel.ifBlank { "未选择" }

    val canExpand = availableModels.isNotEmpty()

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "默认模型",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentModelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canExpand) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "展开选择",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 下拉菜单
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.name ?: model.id,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (model.name != null && model.name != model.id) {
                                Text(
                                    text = model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ============================================================
// 子组件: 可点击行
// ============================================================

/**
 * 可点击的设置行 (用于报告问题、加入社区等)
 *
 * @param icon 前置图标
 * @param title 标题
 * @param subtitle 副标题 / 描述 (可选)
 * @param onClick 点击回调
 */
@Composable
private fun ClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// 子组件: 错误信息横幅
// ============================================================

/**
 * 错误信息横幅
 *
 * 在设置列表顶部展示错误信息，附带重试与关闭按钮。
 *
 * @param message 错误信息
 * @param onRetry 重试回调
 * @param onDismiss 关闭回调
 */
@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    }
}

// ============================================================
// 子组件: 退出登录按钮
// ============================================================

/**
 * 退出登录按钮
 *
 * 红色轮廓按钮，点击后触发确认对话框 (由父组件管理对话框状态)。
 *
 * @param onClick 点击回调
 */
@Composable
private fun LogoutButton(
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// 辅助函数
// ============================================================

/**
 * 打开外部 URL
 *
 * 通过 Intent 启动系统浏览器打开指定 URL。
 * 如果没有可处理的应用，静默忽略异常。
 *
 * @param context 上下文
 * @param url 待打开的 URL
 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // 没有可处理 URL 的应用，静默忽略
    }
}
