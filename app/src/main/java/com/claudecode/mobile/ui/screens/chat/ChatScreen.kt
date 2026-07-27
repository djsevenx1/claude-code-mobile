package com.claudecode.mobile.ui.screens.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.network.ConnectionState

// ============================================================
// 聊天页面
// CloudCLI 移动客户端的对话界面
// 使用 Jetpack Compose Material Design 3 实现
// ============================================================

/**
 * 聊天页面入口 Composable
 *
 * 整体结构：
 * - TopAppBar: 项目名称 + 返回按钮 + 连接状态指示器
 * - 消息列表 (LazyColumn): 按时间顺序展示对话消息
 * - 底部输入区: 多行输入框 + 发送按钮
 *
 * @param viewModel 聊天 ViewModel
 * @param onBack 返回按钮回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 消息列表滚动状态，用于自动滚动到最新消息
    val listState = rememberLazyListState()

    // 当消息数量变化时，自动滚动到最后一条 (最新消息)
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ChatTopAppBar(
                title = uiState.projectName.ifBlank { "对话" },
                connectionState = uiState.connectionState,
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- 连接中进度条 ---
            if (uiState.connectionState is ConnectionState.Connecting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // --- token 用量信息条 (若有) ---
            if (uiState.tokenUsage != null) {
                TokenUsageBar(usage = uiState.tokenUsage!!)
            }

            // --- 消息列表 ---
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message = message)
                }
            }

            // --- 底部输入区域 ---
            ChatInputBar(
                text = uiState.inputText,
                onTextChange = viewModel::updateInputText,
                onSend = {
                    keyboardController?.hide()
                    viewModel.sendMessage()
                },
                enabled = !uiState.isSending
            )
        }
    }
}

// ============================================================
// 子组件: 顶部应用栏
// ============================================================

/**
 * 聊天页面顶部应用栏
 *
 * 包含返回按钮、项目名称、连接状态指示器 (小圆点)。
 *
 * @param title 标题 (项目名称)
 * @param connectionState 连接状态
 * @param onBack 返回按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
    title: String,
    connectionState: ConnectionState,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 连接状态指示器 (小圆点)
                ConnectionStatusDot(connectionState = connectionState)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * 连接状态指示器 (小圆点)
 *
 * 根据连接状态显示不同颜色的小圆点：
 * - Connected (已连接): 绿色
 * - Connecting (连接中): 橙色 (带闪烁动画)
 * - Disconnected (已断开): 灰色
 * - Error (错误): 红色
 *
 * @param connectionState 连接状态
 */
@Composable
private fun ConnectionStatusDot(connectionState: ConnectionState) {
    // 根据连接状态确定圆点颜色
    val dotColor = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF4CAF50)      // 绿色
        is ConnectionState.Connecting -> Color(0xFFFF9800)     // 橙色
        is ConnectionState.Disconnected -> Color(0xFF9E9E9E)   // 灰色
        is ConnectionState.Error -> MaterialTheme.colorScheme.error // 红色
    }

    // 连接中时圆点透明度做闪烁动画
    val infiniteTransition = rememberInfiniteTransition(label = "connection_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(if (connectionState is ConnectionState.Connecting) alpha else 1f)
            .background(color = dotColor, shape = CircleShape)
    )
}

// ============================================================
// 子组件: Token 用量信息条
// ============================================================

/**
 * Token 用量信息条
 *
 * 在消息列表上方显示最近一次的 token 消耗统计。
 *
 * @param usage token 用量文本
 */
@Composable
private fun TokenUsageBar(usage: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Text(
            text = "Token 用量: $usage",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

// ============================================================
// 子组件: 消息气泡
// ============================================================

/**
 * 单条消息气泡
 *
 * 根据 [ChatUiMessage.role] 分发到对应的渲染组件：
 * - USER: 右对齐气泡，主色背景
 * - ASSISTANT: 左对齐气泡，表面色背景，支持 Markdown 文本
 * - SYSTEM: 居中系统消息
 * - ERROR: 红色错误文字
 *
 * @param message 消息数据
 */
@Composable
private fun MessageBubble(message: ChatUiMessage) {
    when (message.role) {
        MessageRole.USER -> UserMessageBubble(message)
        MessageRole.ASSISTANT -> AssistantMessageBubble(message)
        MessageRole.SYSTEM -> SystemMessageBubble(message)
        MessageRole.ERROR -> ErrorMessageBubble(message)
    }
}

/**
 * 用户消息气泡
 *
 * 右对齐，主色背景，白色文字。
 *
 * @param message 用户消息
 */
@Composable
private fun UserMessageBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp
            ),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * AI 助手消息气泡
 *
 * 左对齐，表面色背景。支持以下特殊渲染：
 * - 工具调用: 显示工具名称和参数 (折叠样式)
 * - 思考过程: 灰色斜体，可折叠
 * - Markdown 风格文本: 支持加粗、行内代码、代码块
 *
 * @param message 助手消息
 */
@Composable
private fun AssistantMessageBubble(message: ChatUiMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // --- 工具调用消息 (折叠样式) ---
        if (message.toolName != null) {
            ToolCallCard(
                toolName = message.toolName,
                content = message.content
            )
            return@Column
        }

        // --- 思考过程 (可折叠，灰色斜体) ---
        if (!message.thinking.isNullOrBlank()) {
            ThinkingSection(thinking = message.thinking!!)
        }

        // --- 助手文本内容 (支持 Markdown) ---
        if (message.content.isNotBlank()) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MarkdownText(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 流式输出时显示打字光标
                    if (message.isStreaming) {
                        Text(
                            text = "▋",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 工具调用卡片 (折叠样式)
 *
 * 显示工具名称和参数内容，点击可展开/折叠。
 *
 * @param toolName 工具名称
 * @param content 工具参数或结果
 */
@Composable
private fun ToolCallCard(
    toolName: String,
    content: String
) {
    // 折叠状态 (默认折叠)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    ) {
        Column {
            // 工具名称行 (可点击展开/折叠)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "工具调用: $toolName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 展开时显示参数内容
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 思考过程区域 (可折叠，灰色斜体)
 *
 * 显示 AI 的扩展思考过程，默认折叠，点击标题可展开查看。
 *
 * @param thinking 思考过程文本
 */
@Composable
private fun ThinkingSection(thinking: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.widthIn(max = 300.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column {
            // 标题行 (可点击展开/折叠)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 展开时显示思考内容 (灰色斜体)
            if (expanded) {
                Text(
                    text = thinking,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 系统消息气泡
 *
 * 居中显示，使用次要色调背景。
 *
 * @param message 系统消息
 */
@Composable
private fun SystemMessageBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * 错误消息气泡
 *
 * 左对齐，红色错误文字，带错误图标。
 *
 * @param message 错误消息
 */
@Composable
private fun ErrorMessageBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ============================================================
// 子组件: 底部输入区域
// ============================================================

/**
 * 底部输入区域
 *
 * 包含多行输入框和发送按钮。
 * 发送中 (enabled = false) 时输入框禁用。
 *
 * @param text 当前输入文本
 * @param onTextChange 输入文本变化回调
 * @param onSend 发送按钮回调
 * @param enabled 输入框是否可用 (发送中禁用)
 */
@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()                // 键盘弹出时自动添加底部 padding
                .navigationBarsPadding()     // 避开系统导航栏
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // --- 多行输入框 ---
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = {
                    Text(
                        text = "输入消息...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                enabled = enabled,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Default
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // --- 发送按钮 ---
            IconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                if (!enabled) {
                    // 发送中显示加载指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (text.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ============================================================
// 辅助组件: Markdown 风格文本渲染
// ============================================================

/**
 * Markdown 风格文本渲染组件
 *
 * 支持以下 Markdown 语法：
 * - 代码块 (```...```): 等宽字体，带背景色
 * - 行内代码 (`...`): 等宽字体，带背景色
 * - 加粗 (**...**): 粗体
 *
 * 由于项目未引入第三方 Markdown 渲染库，此处实现轻量级解析，
 * 满足 AI 回复中常见的代码与格式化文本展示需求。
 *
 * @param text 待渲染的文本
 * @param modifier 修饰符
 * @param color 文本颜色
 */
@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    // 按代码块分隔符 (```) 分割文本
    val segments = remember(text) { text.split("```") }

    Column(modifier = modifier) {
        segments.forEachIndexed { index, segment ->
            if (segment.isBlank()) return@forEachIndexed

            if (index % 2 == 1) {
                // 奇数索引: 代码块内容
                CodeBlock(code = segment.trim())
            } else {
                // 偶数索引: 普通文本 (含行内格式)
                val annotated = remember(segment, color) {
                    parseInlineMarkdown(segment, color)
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 代码块渲染组件
 *
 * 等宽字体显示代码，带表面变体背景色。
 *
 * @param code 代码内容
 */
@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/**
 * 解析行内 Markdown 格式
 *
 * 处理行内代码 (`...`) 和加粗 (**...**) 标记，
 * 返回带样式的 [androidx.compose.ui.text.AnnotatedString]。
 *
 * @param text 原始文本
 * @param baseColor 基础文本颜色
 * @return 带格式的 AnnotatedString
 */
private fun parseInlineMarkdown(
    text: String,
    baseColor: Color
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // 加粗: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 行内代码: `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = baseColor.copy(alpha = 0.12f)
                            )
                        )
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 普通字符
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
