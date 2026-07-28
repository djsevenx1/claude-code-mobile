package com.claudecode.mobile.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.data.db.CloudDatabase
import com.claudecode.mobile.data.entity.ChatMessage as EntityChatMessage
import com.claudecode.mobile.data.repository.ChatRepository
import com.claudecode.mobile.network.ChatMessage
import com.claudecode.mobile.network.CloudFrame
import com.claudecode.mobile.network.CloudWebSocketClient
import com.claudecode.mobile.network.ConnectionState
import com.claudecode.mobile.network.FrameKind
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.TokenManager
import com.claudecode.mobile.network.dto.CreateSessionRequest
import com.claudecode.mobile.network.dto.HistoryMessage
import com.claudecode.mobile.network.dto.ModelInfo
import com.claudecode.mobile.network.dto.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ============================================================
// 聊天页面 ViewModel
// 管理 WebSocket 连接、消息收发与 UI 状态
// 参照 LoginViewModel 的模式: AndroidViewModel + StateFlow + viewModelScope
// ============================================================

/**
 * 消息角色枚举
 *
 * 用于区分消息来源，决定 UI 中的渲染样式 (气泡对齐方向与配色)。
 */
enum class MessageRole {
    /** 用户消息 */
    USER,

    /** AI 助手消息 */
    ASSISTANT,

    /** 系统消息 (如 token 用量统计) */
    SYSTEM,

    /** 错误消息 */
    ERROR
}

/**
 * UI 消息数据模型
 *
 * 面向 UI 层的消息表示，包含流式状态与思考过程等附加字段。
 * 与 [EntityChatMessage] (Room 实体) 区分，后者用于本地持久化。
 *
 * @param id 唯一标识 (默认随机 UUID)
 * @param role 消息角色
 * @param content 消息正文内容
 * @param isStreaming 是否正在流式传输中 (AI 回复未结束时为 true)
 * @param timestamp 消息时间戳 (毫秒)
 * @param toolName 工具名称 (仅工具调用消息有值)
 * @param thinking 思考过程内容 (扩展思考，可空)
 */
data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val thinking: String? = null
)

/**
 * 聊天页面完整 UI 状态
 *
 * 汇聚聊天界面所需的全部状态，通过 StateFlow 暴露给 Compose 层观察。
 *
 * @param messages 消息列表 (按时间顺序，最新在末尾)
 * @param connectionState WebSocket 连接状态
 * @param inputText 输入框当前文本
 * @param isSending 是否正在发送 (等待 AI 回复中)
 * @param projectName 项目名称 (TopAppBar 显示)
 * @param sessionId 当前会话 ID
 * @param tokenUsage 最近一次 token 用量信息
 * @param availableModels 可用模型列表 (从服务端加载)
 * @param selectedModel 当前选中的模型标识 (传给 ChatOptions.model)
 */
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val inputText: String = "",
    val isSending: Boolean = false,
    val projectName: String = "",
    val sessionId: String = "",
    val projectPath: String = "",
    val tokenUsage: String? = null,
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val isCreatingSession: Boolean = false,
    val pendingPermission: PendingPermission? = null
)

/**
 * 待处理的工具审批请求
 */
data class PendingPermission(
    val permissionId: String,
    val toolName: String,
    val input: String
)

/**
 * 聊天页面 ViewModel
 *
 * 职责：
 * 1. 在 init 块中建立 WebSocket 连接 (读取 TokenManager 中的服务器地址与 token)
 * 2. 订阅 [CloudWebSocketClient.messageFlow]，将 [CloudFrame] 解析为 [ChatUiMessage]
 * 3. 提供 [sendMessage] 发送用户消息 (构造 [ChatMessage] 经 WebSocket 发出)
 * 4. 管理连接状态与发送状态，同步到 [ChatUiState]
 * 5. 在 [onCleared] 中断开 WebSocket，避免内存泄漏
 *
 * @param application 应用上下文 (由 AndroidViewModel 提供)
 * @param projectId 项目 ID (从导航参数传入，用于查询项目信息)
 * @param initialSessionId 初始会话 ID (空串表示新建会话)
 */
class ChatViewModel(
    application: Application,
    private val projectId: String,
    private val initialSessionId: String
) : AndroidViewModel(application) {

    // --- 依赖组件 ---

    /** Token 管理器 (读取服务器地址与鉴权 token) */
    private val tokenManager = TokenManager(application)

    /** 聊天仓库 (Room 本地消息缓存) */
    private val chatRepository = ChatRepository(
        CloudDatabase.getInstance(application).chatMessageDao()
    )

    /** 项目 DAO (查询项目名称) */
    private val projectDao = CloudDatabase.getInstance(application).projectDao()

    /** WebSocket 客户端 (通过 NetworkModule 创建，复用共享 OkHttpClient 与 Json) */
    private val webSocketClient: CloudWebSocketClient = NetworkModule.createWebSocketClient()

    // --- UI 状态 ---

    private val _uiState = MutableStateFlow(ChatUiState(sessionId = initialSessionId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前会话 ID (可能由服务端创建后更新) */
    private var currentSessionId: String = initialSessionId

    /** 最后收到的事件序号 (用于断线重连重放) */
    private var lastSeq: Int = 0

    /** 已持久化到 Room 的消息 ID 集合 (避免重复保存) */
    private val savedMessageIds = mutableSetOf<String>()

    init {
        connectWebSocket()
        observeConnectionState()
        observeIncomingFrames()
        loadProjectInfoAndPath()
        loadModels()
    }

    // ============================================================
    // WebSocket 连接管理
    // ============================================================

    /**
     * 建立 WebSocket 连接
     *
     * 从 TokenManager 读取服务器地址与 token，调用 [CloudWebSocketClient.connect]
     * 连接到 CloudCLI 服务端的 /ws 端点。连接状态会通过 [observeConnectionState] 同步到 UI。
     */
    private fun connectWebSocket() {
        viewModelScope.launch {
            val serverUrl = tokenManager.getServerUrl()
            val token = tokenManager.getToken()

            if (serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(connectionState = ConnectionState.Error("未配置服务器或未登录"))
                }
                return@launch
            }

            // 连接 WebSocket (内部会先将状态置为 Connecting，握手成功后置为 Connected)
            try {
                webSocketClient.connect(serverUrl, token)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(connectionState = ConnectionState.Error("连接失败: ${e.message}"))
                }
            }
        }
    }

    /**
     * 观察连接状态变化，同步到 UI 状态
     *
     * CloudWebSocketClient 内部维护 [ConnectionState] StateFlow，
     * 此处将其转发到 [ChatUiState.connectionState] 供 UI 渲染连接指示器。
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    /**
     * 观察接收到的消息帧并解析为 UI 消息
     *
     * 订阅 [CloudWebSocketClient.messageFlow] (SharedFlow)，
     * 每收到一帧 [CloudFrame] 即调用 [handleFrame] 进行分发处理。
     */
    private fun observeIncomingFrames() {
        viewModelScope.launch {
            webSocketClient.messageFlow.collect { frame ->
                try {
                    handleFrame(frame)
                } catch (e: Exception) {
                    // 单帧处理异常不影响后续消息接收
                }
            }
        }
    }

    // ============================================================
    // 消息帧处理
    // ============================================================

    /**
     * 解析并处理单条消息帧
     *
     * 根据 [CloudFrame.kind] 分发到对应的处理方法。
     * 兼容 claudecodeui 服务端的 NormalizedMessage 和协议帧格式。
     */
    private fun handleFrame(frame: CloudFrame) {
        // 更新 lastSeq（用于断线重连重放）
        frame.seq?.let { if (it > lastSeq) lastSeq = it }

        when (frame.kind) {
            // AI 输出消息
            FrameKind.ASSISTANT_TEXT, FrameKind.ASSISTANT -> handleAssistantText(frame)
            FrameKind.USER_MESSAGE, FrameKind.USER -> handleUserMessageEcho(frame)
            FrameKind.THINKING -> handleThinking(frame)
            FrameKind.TOOL_USE -> handleToolUse(frame)
            FrameKind.TOOL_RESULT -> handleToolResult(frame)
            FrameKind.TOKEN_USAGE -> handleTokenUsage(frame)

            // 协议帧
            FrameKind.ERROR, FrameKind.PROTOCOL_ERROR -> handleError(frame)
            FrameKind.DONE, FrameKind.COMPLETE -> handleDone(frame)
            FrameKind.SESSION_INFO, FrameKind.SESSION_UPSERTED -> handleSessionInfo(frame)
            FrameKind.CHAT_SUBSCRIBED -> handleChatSubscribed(frame)
            FrameKind.LOADING_PROGRESS -> { /* 加载进度，暂不处理 */ }
            FrameKind.PERMISSION_REQUEST -> handlePermissionRequest(frame)
            FrameKind.CONNECTED -> { /* 连接确认帧，无需处理 */ }
            else -> { /* 未知帧类型，忽略 */ }
        }
    }

    /**
     * 处理 AI 文本输出 (流式追加)
     *
     * ASSISTANT_TEXT 帧为流式增量，若当前最后一条是正在流式输出的助手文本消息，
     * 则将新文本追加到其 content；否则新建一条流式助手消息。
     */
    private fun handleAssistantText(frame: CloudFrame) {
        val text = CloudWebSocketClient.getString(frame, "text") ?: return
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastIndex = messages.lastIndex
            // 若最后一条是正在流式输出的助手文本消息 (非工具调用)，则追加文本
            if (lastIndex >= 0
                && messages[lastIndex].role == MessageRole.ASSISTANT
                && messages[lastIndex].isStreaming
                && messages[lastIndex].toolName == null
            ) {
                messages[lastIndex] = messages[lastIndex].copy(
                    content = messages[lastIndex].content + text
                )
            } else {
                // 否则新建一条流式助手消息
                messages.add(
                    ChatUiMessage(
                        role = MessageRole.ASSISTANT,
                        content = text,
                        isStreaming = true
                    )
                )
            }
            state.copy(messages = messages)
        }
    }

    /**
     * 处理用户消息回显
     *
     * 服务端会回显用户消息 (USER_MESSAGE 帧)，由于发送时已在本地添加用户消息，
     * 此处忽略以避免重复显示。
     */
    private fun handleUserMessageEcho(frame: CloudFrame) {
        // 服务端回显的用户消息，本地已存在，无需重复添加
    }

    /**
     * 处理会话信息帧，更新当前 sessionId
     *
     * SESSION_INFO 帧携带会话标识，用于新建会话时获取服务端分配的 sessionId，
     * 或在会话切换时更新当前会话。
     */
    private fun handleSessionInfo(frame: CloudFrame) {
        val sessionId = frame.getSessionIdSafe()
            ?: CloudWebSocketClient.getString(frame, "sessionId")
            ?: CloudWebSocketClient.getString(frame, "session_id")
        if (!sessionId.isNullOrBlank()) {
            currentSessionId = sessionId
            _uiState.update { it.copy(sessionId = sessionId) }
        }
    }

    /**
     * 处理思考过程帧 (扩展思考)
     *
     * THINKING 帧包含 AI 的思考过程文本，附加到当前流式助手消息的 thinking 字段。
     */
    private fun handleThinking(frame: CloudFrame) {
        val text = CloudWebSocketClient.getString(frame, "text")
            ?: CloudWebSocketClient.getString(frame, "thinking")
            ?: return
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastIndex = messages.lastIndex
            if (lastIndex >= 0
                && messages[lastIndex].role == MessageRole.ASSISTANT
                && messages[lastIndex].isStreaming
            ) {
                // 追加到当前流式助手消息的 thinking 字段
                messages[lastIndex] = messages[lastIndex].copy(
                    thinking = (messages[lastIndex].thinking ?: "") + text
                )
            } else {
                // 新建一条流式助手消息 (仅含思考过程)
                messages.add(
                    ChatUiMessage(
                        role = MessageRole.ASSISTANT,
                        content = "",
                        isStreaming = true,
                        thinking = text
                    )
                )
            }
            state.copy(messages = messages)
        }
    }

    /**
     * 处理工具调用帧
     *
     * TOOL_USE 帧表示 AI 发起了工具调用，显示工具名称与参数。
     * 将其作为带 toolName 标识的助手消息添加到列表。
     */
    private fun handleToolUse(frame: CloudFrame) {
        val toolName = CloudWebSocketClient.getString(frame, "name")
            ?: CloudWebSocketClient.getString(frame, "tool")
            ?: "工具"
        val input = CloudWebSocketClient.getString(frame, "input")
            ?: CloudWebSocketClient.getString(frame, "arguments")
            ?: frame.raw
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            messages.add(
                ChatUiMessage(
                    role = MessageRole.ASSISTANT,
                    content = input,
                    toolName = toolName,
                    isStreaming = true
                )
            )
            state.copy(messages = messages)
        }
    }

    /**
     * 处理工具执行结果帧
     *
     * TOOL_RESULT 帧携带工具执行结果，更新对应的工具调用消息内容。
     */
    private fun handleToolResult(frame: CloudFrame) {
        val content = CloudWebSocketClient.getString(frame, "content")
            ?: CloudWebSocketClient.getString(frame, "result")
            ?: CloudWebSocketClient.getString(frame, "output")
            ?: frame.raw
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastIndex = messages.lastIndex
            // 若最后一条是工具调用消息，追加执行结果并标记完成
            if (lastIndex >= 0 && messages[lastIndex].toolName != null) {
                messages[lastIndex] = messages[lastIndex].copy(
                    content = messages[lastIndex].content + "\n[结果] " + content,
                    isStreaming = false
                )
            } else {
                // 否则作为系统消息添加
                messages.add(
                    ChatUiMessage(
                        role = MessageRole.SYSTEM,
                        content = "[工具结果] $content"
                    )
                )
            }
            state.copy(messages = messages)
        }
    }

    /**
     * 处理 token 用量统计帧
     *
     * TOKEN_USAGE 帧携带本次对话的 token 消耗统计，更新到 UI 状态供底部显示。
     */
    private fun handleTokenUsage(frame: CloudFrame) {
        val inputTokens = CloudWebSocketClient.getString(frame, "input_tokens")
            ?: CloudWebSocketClient.getString(frame, "inputTokens")
        val outputTokens = CloudWebSocketClient.getString(frame, "output_tokens")
            ?: CloudWebSocketClient.getString(frame, "outputTokens")
        val totalTokens = CloudWebSocketClient.getString(frame, "total_tokens")
            ?: CloudWebSocketClient.getString(frame, "totalTokens")
        val usage = buildString {
            if (!inputTokens.isNullOrBlank()) append("输入: $inputTokens  ")
            if (!outputTokens.isNullOrBlank()) append("输出: $outputTokens  ")
            if (!totalTokens.isNullOrBlank()) append("总计: $totalTokens")
        }.trim().ifBlank { frame.raw }

        _uiState.update { it.copy(tokenUsage = usage) }
    }

    /**
     * 处理错误帧
     *
     * ERROR 帧携带服务端返回的错误信息，添加为错误消息并解除发送锁定。
     */
    private fun handleError(frame: CloudFrame) {
        val message = CloudWebSocketClient.getString(frame, "message")
            ?: CloudWebSocketClient.getString(frame, "error")
            ?: "未知错误"
        val errorUiMessage = ChatUiMessage(
            role = MessageRole.ERROR,
            content = message
        )
        _uiState.update { state ->
            state.copy(
                messages = state.messages + errorUiMessage,
                isSending = false
            )
        }
        persistMessage(errorUiMessage)
    }

    /**
     * 处理流结束帧，标记回复完成
     *
     * DONE / COMPLETE 帧表示本次 AI 回复已全部输出完毕。
     * COMPLETE 帧还携带 exitCode, success, aborted 等信息。
     * 将所有流式消息标记为完成，解除发送锁定。
     *
     * @param frame 结束帧（COMPLETE 帧携带额外信息）
     */
    private fun handleDone(frame: CloudFrame) {
        // 检查是否为中止
        val aborted = CloudWebSocketClient.getString(frame, "aborted")?.toBoolean() ?: false
        val success = CloudWebSocketClient.getString(frame, "success")?.toBoolean() ?: true

        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            // 将所有正在流式输出的消息标记为完成
            for (i in messages.indices) {
                if (messages[i].isStreaming) {
                    messages[i] = messages[i].copy(isStreaming = false)
                }
            }
            state.copy(messages = messages, isSending = false)
        }

        // 若被中止，添加提示
        if (aborted) {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatUiMessage(
                        role = MessageRole.SYSTEM,
                        content = "已停止生成"
                    )
                )
            }
        }

        // 将最终消息持久化到本地缓存
        _uiState.value.messages.forEach { persistMessage(it) }
    }

    /**
     * 处理订阅确认帧
     *
     * CHAT_SUBSCRIBED 帧携带 isProcessing 和 pendingPermissions，
     * 表示已成功订阅会话事件流。
     */
    private fun handleChatSubscribed(frame: CloudFrame) {
        val isProcessing = CloudWebSocketClient.getString(frame, "isProcessing")?.toBoolean() ?: false
        if (isProcessing) {
            _uiState.update { it.copy(isSending = true) }
        }
    }

    /**
     * 处理工具审批请求帧
     *
     * PERMISSION_REQUEST 帧表示 AI 请求执行工具，需要用户确认。
     * 更新 UI 状态以展示审批对话框。
     */
    private fun handlePermissionRequest(frame: CloudFrame) {
        val permissionId = CloudWebSocketClient.getString(frame, "permissionId")
            ?: CloudWebSocketClient.getString(frame, "permission_id")
            ?: return
        val toolName = CloudWebSocketClient.getString(frame, "toolName")
            ?: CloudWebSocketClient.getString(frame, "tool_name")
            ?: "工具"
        val input = CloudWebSocketClient.getString(frame, "input")
            ?: CloudWebSocketClient.getString(frame, "arguments")
            ?: frame.raw

        _uiState.update {
            it.copy(pendingPermission = PendingPermission(permissionId, toolName, input))
        }
    }

    // ============================================================
    // 发送消息
    // ============================================================

    /**
     * 发送一条用户消息
     *
     * 流程：
     * 1. 校验输入文本非空且当前非发送中状态
     * 2. 确保已创建会话 (sessionId 非空)，若为空则先创建
     * 3. 构造 chat.send 消息 (type="chat.send", sessionId, message, projectPath, model)
     * 4. 本地立即添加用户消息到 UI (乐观更新)
     * 5. 通过 WebSocket 发送给服务端
     * 6. 若发送失败 (WebSocket 未连接)，添加错误提示
     */
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        if (_uiState.value.isSending) return

        // 若 sessionId 为空，先创建会话再发送
        if (currentSessionId.isBlank()) {
            createSessionAndSend(text)
            return
        }

        doSendMessage(text)
    }

    /**
     * 执行实际的消息发送
     */
    private fun doSendMessage(text: String) {
        // 构造 chat.send 消息 (使用 claudecodeui WebSocket 协议格式)
        val chatMessage = ChatMessage(
            sessionId = currentSessionId,
            message = text,
            projectPath = _uiState.value.projectPath.ifBlank { null },
            model = _uiState.value.selectedModel.ifBlank { null }
        )

        // 本地立即添加用户消息 (乐观更新)
        val userUiMessage = ChatUiMessage(
            role = MessageRole.USER,
            content = text
        )
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userUiMessage,
                inputText = "",
                isSending = true
            )
        }

        // 持久化用户消息到本地缓存
        persistMessage(userUiMessage)

        // 通过 WebSocket 发送
        val sent = webSocketClient.send(chatMessage)
        if (!sent) {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatUiMessage(
                        role = MessageRole.ERROR,
                        content = "消息发送失败，WebSocket 未连接"
                    ),
                    isSending = false
                )
            }
        }
    }

    /**
     * 创建会话并发送消息
     *
     * 当 sessionId 为空时，先调用 POST /api/providers/sessions 创建会话，
     * 获取 sessionId 后再发送消息。
     */
    private fun createSessionAndSend(text: String) {
        val projectPath = _uiState.value.projectPath
        if (projectPath.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatUiMessage(
                        role = MessageRole.ERROR,
                        content = "无法创建会话：项目路径未知，请重新进入页面"
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(isCreatingSession = true) }

        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址")

                val response = api.createSession(
                    CreateSessionRequest(provider = "claude", projectPath = projectPath)
                )

                val newSessionId = response.getSessionIdSafe()
                if (newSessionId.isNullOrBlank()) {
                    throw RuntimeException(response.error ?: "创建会话失败")
                }

                currentSessionId = newSessionId
                _uiState.update {
                    it.copy(
                        sessionId = newSessionId,
                        isCreatingSession = false
                    )
                }

                // 订阅会话事件流
                webSocketClient.sendSubscribe(newSessionId, 0)

                // 发送消息
                doSendMessage(text)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingSession = false,
                        messages = it.messages + ChatUiMessage(
                            role = MessageRole.ERROR,
                            content = "创建会话失败: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * 中止当前 AI 回复
     *
     * 发送 chat.abort 消息到服务端，中止正在运行的会话。
     */
    fun abortGeneration() {
        if (!currentSessionId.isBlank() && _uiState.value.isSending) {
            webSocketClient.sendAbort(currentSessionId)
            _uiState.update { it.copy(isSending = false) }
        }
    }

    /**
     * 响应工具审批请求
     *
     * @param approved 是否批准工具执行
     */
    fun respondToPermission(approved: Boolean) {
        val permission = _uiState.value.pendingPermission ?: return
        webSocketClient.sendPermissionResponse(currentSessionId, permission.permissionId, approved)
        _uiState.update { it.copy(pendingPermission = null) }
    }

    // ============================================================
    // 输入框操作
    // ============================================================

    /**
     * 更新输入框文本
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ============================================================
    // 模型选择
    // ============================================================

    /**
     * 加载可用模型列表
     *
     * 调用 /api/providers/claude/models 获取 Claude 系列可用模型，
     * 加载成功后若当前未选中任何模型，则默认选中列表首个模型。
     * 加载失败时静默忽略，不影响主聊天流程。
     */
    private fun loadModels() {
        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig()
                if (api != null) {
                    val response = api.getModels("claude")
                    val models = response.data?.models ?: emptyList()
                    _uiState.update {
                        it.copy(
                            availableModels = models,
                            selectedModel = it.selectedModel.ifBlank {
                                models.firstOrNull()?.id ?: ""
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                // 模型列表加载失败不影响聊天主流程，静默忽略
            }
        }
    }

    /**
     * 选择模型
     *
     * 由底部输入栏的模型选择器调用，更新 [ChatUiState.selectedModel]，
     * 后续发送消息时会通过 [ChatOptions.model] 传给服务端。
     *
     * @param modelId 模型标识
     */
    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    // ============================================================
    // 历史消息与项目信息加载
    // ============================================================

    /**
     * 加载项目信息 (名称与路径)
     *
     * 先从 Room 本地缓存查询项目名称，同时通过 API 获取项目路径。
     * 项目路径用于创建会话 (POST /api/providers/sessions) 和发送消息 (chat.send)。
     */
    private fun loadProjectInfoAndPath() {
        viewModelScope.launch {
            try {
                // 先从本地加载项目名称
                val project = projectDao.getById(projectId)
                _uiState.update { it.copy(projectName = project?.name ?: projectId) }

                // 通过 API 获取项目路径
                val api = NetworkModule.createCloudApiFromConfig()
                if (api != null) {
                    val projects = api.getProjects()
                    val serverProject = projects.find { it.id == projectId }
                    if (serverProject != null) {
                        _uiState.update {
                            it.copy(
                                projectName = serverProject.getDisplayNameSafe(),
                                projectPath = serverProject.getPathSafe()
                            )
                        }

                        // 若已有 sessionId，加载服务端消息历史
                        if (initialSessionId.isNotBlank()) {
                            loadServerMessageHistory(initialSessionId)
                        }
                    }
                }
            } catch (e: Exception) {
                // API 加载失败不影响本地缓存的使用
            }
        }
    }

    /**
     * 从服务端加载会话消息历史
     *
     * 调用 GET /api/providers/sessions/{sessionId}/messages 获取服务端历史消息，
     * 转换为 [ChatUiMessage] 后更新 UI。
     * 同时订阅会话事件流以便接收后续实时消息。
     *
     * @param sessionId 会话标识
     */
    private fun loadServerMessageHistory(sessionId: String) {
        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig() ?: return@launch
                val response = api.getSessionMessages(sessionId)

                if (response.success && response.data != null) {
                    val historyMessages = response.data.messages
                    if (historyMessages.isNotEmpty()) {
                        val uiMessages = historyMessages.map { it.toUiMessage() }
                        _uiState.update { it.copy(messages = uiMessages) }
                    }
                }

                // 订阅会话事件流 (断线重连后可重放)
                webSocketClient.sendSubscribe(sessionId, lastSeq)

            } catch (e: Exception) {
                // 服务端历史加载失败，回退到本地缓存
                loadLocalHistory(sessionId)
            }
        }
    }

    /**
     * 从本地 Room 缓存加载历史消息 (回退方案)
     */
    private fun loadLocalHistory(sessionId: String) {
        viewModelScope.launch {
            val history = chatRepository.observeMessages(sessionId).first()
            if (history.isNotEmpty()) {
                val uiMessages = history.map { it.toUiMessage() }
                _uiState.update { it.copy(messages = it.messages + uiMessages) }
            }
        }
    }

    /**
     * 持久化单条消息到本地缓存
     *
     * 使用 [savedMessageIds] 集合避免重复保存。
     * 仅当 currentSessionId 非空时才执行保存。
     */
    private fun persistMessage(uiMessage: ChatUiMessage) {
        val sid = currentSessionId
        if (sid.isBlank() || uiMessage.id in savedMessageIds) return
        savedMessageIds.add(uiMessage.id)
        viewModelScope.launch {
            chatRepository.saveMessage(uiMessage.toEntity(sid))
        }
    }

    // ============================================================
    // 生命周期
    // ============================================================

    /**
     * ViewModel 销毁时清理资源
     *
     * 断开 WebSocket 连接并释放客户端资源，避免内存泄漏。
     */
    override fun onCleared() {
        super.onCleared()
        // 主动断开连接 (标记为手动断开，不触发自动重连)
        webSocketClient.disconnect()
        // 释放客户端内部协程作用域等资源
        webSocketClient.shutdown()
    }

    // ============================================================
    // 数据转换辅助方法
    // ============================================================

    /**
     * Room 实体消息 -> UI 消息
     *
     * 将持久化的 [EntityChatMessage] 转换为 UI 层使用的 [ChatUiMessage]。
     */
    private fun EntityChatMessage.toUiMessage(): ChatUiMessage {
        val roleEnum = when (role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            "error" -> MessageRole.ERROR
            "tool" -> MessageRole.ASSISTANT  // 工具消息映射为助手角色
            else -> MessageRole.SYSTEM
        }
        return ChatUiMessage(
            id = id.toString(),
            role = roleEnum,
            content = content,
            isStreaming = false,
            timestamp = timestamp,
            toolName = toolName
        )
    }

    /**
     * 服务端历史消息 -> UI 消息
     *
     * 将 [HistoryMessage] (claudecodeui 服务端 NormalizedMessage 格式)
     * 转换为 UI 层使用的 [ChatUiMessage]。
     */
    private fun HistoryMessage.toUiMessage(): ChatUiMessage {
        val roleEnum = when {
            kind == "user" || role == "user" -> MessageRole.USER
            kind == "tool_use" || kind == "tool_result" -> MessageRole.ASSISTANT
            kind == "error" -> MessageRole.ERROR
            else -> MessageRole.ASSISTANT
        }
        val content = when (kind) {
            "tool_use" -> getToolInputSafe() ?: getTextSafe() ?: ""
            "tool_result" -> getToolResultSafe() ?: getTextSafe() ?: ""
            else -> getTextSafe() ?: ""
        }
        val toolName = when (kind) {
            "tool_use", "tool_result" -> getToolNameSafe()
            else -> null
        }
        return ChatUiMessage(
            id = id ?: UUID.randomUUID().toString(),
            role = roleEnum,
            content = content,
            isStreaming = false,
            timestamp = System.currentTimeMillis(),
            toolName = toolName,
            thinking = thinking
        )
    }

    /**
     * UI 消息 -> Room 实体消息
     *
     * 将 [ChatUiMessage] 转换为可持久化的 [EntityChatMessage]。
     *
     * @param sessionId 当前会话 ID
     */
    private fun ChatUiMessage.toEntity(sessionId: String): EntityChatMessage {
        val roleStr = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.ERROR -> "error"
        }
        return EntityChatMessage(
            sessionId = sessionId,
            role = roleStr,
            content = content,
            timestamp = timestamp,
            kind = if (toolName != null) "tool_use" else null,
            toolName = toolName,
            isStreaming = isStreaming,
            projectId = 0L
        )
    }

    // ============================================================
    // ViewModel 工厂 (传递 projectId 与 sessionId)
    // ============================================================

    companion object {
        /**
         * 创建 ChatViewModel 的工厂
         *
         * 由于 ChatViewModel 需要额外的 projectId 与 sessionId 参数，
         * 而(viewModel())默认只能注入 Application，因此通过自定义 Factory 传递。
         *
         * 使用示例 (在 Compose 中):
         * ```kotlin
         * viewModel(factory = ChatViewModel.provideFactory(app, projectId, sessionId))
         * ```
         *
         * @param application 应用上下文
         * @param projectId 项目 ID
         * @param sessionId 会话 ID
         * @return ViewModelProvider.Factory
         */
        fun provideFactory(
            application: Application,
            projectId: String,
            sessionId: String
        ): ViewModelProvider.Factory = ChatViewModelFactory(application, projectId, sessionId)
    }
}

/**
 * ChatViewModel 工厂类
 *
 * 用于在 Compose 中通过 viewModel(factory = ...) 创建带参数的 ChatViewModel。
 * 实现 [ViewModelProvider.Factory] 接口，在 create() 中构造 ViewModel 实例。
 *
 * @param application 应用上下文
 * @param projectId 项目 ID
 * @param sessionId 会话 ID
 */
private class ChatViewModelFactory(
    private val application: Application,
    private val projectId: String,
    private val sessionId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(application, projectId, sessionId) as T
    }
}
