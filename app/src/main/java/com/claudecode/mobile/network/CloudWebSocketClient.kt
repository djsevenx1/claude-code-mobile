package com.claudecode.mobile.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ============================================================================
// WebSocket 消息帧模型定义
// ============================================================================

/**
 * 聊天消息发送选项
 *
 * 携带模型选择与执行参数，作为 [ChatMessage.options] 一起发送给服务端。
 *
 * @param model 模型标识（如 claude-sonnet-4-20250514）
 * @param provider AI 提供商（claude/cursor/codex/opencode）
 * @param systemPrompt 自定义系统提示词
 * @param maxTurns 单次会话最大轮次
 * @param allowedTools 允许使用的工具列表
 * @param autoApproveTools 是否自动批准工具调用（无需人工确认）
 * @param workingDir 工作目录（与 cwd 作用相同，部分版本使用此字段）
 */
@Serializable
data class ChatOptions(
    val model: String? = null,
    val provider: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null,
    @SerialName("max_turns")
    val maxTurns: Int? = null,
    @SerialName("allowed_tools")
    val allowedTools: List<String>? = null,
    @SerialName("auto_approve_tools")
    val autoApproveTools: Boolean? = null,
    @SerialName("working_dir")
    val workingDir: String? = null
)

/**
 * 发送给服务端的聊天消息（chat.send）
 *
 * 通过 WebSocket 发送，触发一次 AI 对话。
 * 严格对应 claudecodeui WebSocket /ws 端点的 chat.send 协议。
 *
 * @param type 消息类型，固定为 "chat.send"
 * @param sessionId 会话标识（由 POST /api/providers/sessions 创建）
 * @param message 用户输入的提示词/命令
 * @param projectPath 项目路径（服务器上的绝对路径）
 * @param model 模型标识
 * @param provider AI 提供商
 * @param systemPrompt 自定义系统提示词
 * @param maxTurns 最大轮次
 * @param allowedTools 允许使用的工具
 * @param autoApproveTools 自动批准工具
 */
@Serializable
data class ChatMessage(
    val type: String = "chat.send",
    @SerialName("sessionId")
    val sessionId: String? = null,
    val message: String,
    @SerialName("projectPath")
    val projectPath: String? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("systemPrompt")
    val systemPrompt: String? = null,
    @SerialName("maxTurns")
    val maxTurns: Int? = null,
    @SerialName("allowedTools")
    val allowedTools: List<String>? = null,
    @SerialName("autoApproveTools")
    val autoApproveTools: Boolean? = null
)

/**
 * chat.abort 消息 - 中止当前正在运行的会话
 *
 * @param type 消息类型，固定为 "chat.abort"
 * @param sessionId 会话标识
 */
@Serializable
data class ChatAbortMessage(
    val type: String = "chat.abort",
    @SerialName("sessionId")
    val sessionId: String
)

/**
 * chat.subscribe 消息中的会话订阅项
 *
 * @param sessionId 会话标识
 * @param lastSeq 上次收到的最大事件序号
 */
@Serializable
data class SessionSubscription(
    @SerialName("sessionId")
    val sessionId: String,
    @SerialName("lastSeq")
    val lastSeq: Int = 0
)

/**
 * chat.subscribe 消息 - 订阅会话实时事件流
 *
 * 用于断线重连后重放丢失的事件。
 *
 * @param type 消息类型，固定为 "chat.subscribe"
 * @param sessions 要订阅的会话列表
 */
@Serializable
data class ChatSubscribeMessage(
    val type: String = "chat.subscribe",
    val sessions: List<SessionSubscription>
)

/**
 * chat.permission-response 消息 - 响应工具审批请求
 *
 * @param type 消息类型，固定为 "chat.permission-response"
 * @param sessionId 会话标识
 * @param permissionId 权限请求标识
 * @param approved 是否批准
 */
@Serializable
data class ChatPermissionResponse(
    val type: String = "chat.permission-response",
    @SerialName("sessionId")
    val sessionId: String,
    @SerialName("permissionId")
    val permissionId: String,
    val approved: Boolean
)

/**
 * 标准化消息帧
 *
 * CloudCLI WebSocket 服务端采用以 `kind` 字段标识帧类型的标准化格式。
 * 此类为原始帧的松散表示，保留原始数据以便上层按需解析。
 *
 * @param kind 帧类型（见 [FrameKind]）
 * @param data 帧数据负载（JSON 对象）
 * @param sessionId 关联会话标识（可选）
 * @param seq 事件序号（单调递增，用于断线重连重放）
 * @param timestamp 时间戳（可选，ISO 8601 字符串）
 */
@Serializable
data class CloudFrame(
    val kind: String = "",
    val data: JsonElement? = null,
    @SerialName("sessionId")
    val sessionId: String? = null,
    @SerialName("session_id")
    val sessionIdAlt: String? = null,
    val seq: Int? = null,
    val timestamp: String? = null
) {
    /** 原始 JSON 文本，由解析时注入（不参与序列化） */
    @kotlinx.serialization.Transient
    var raw: String = ""

    /** 统一获取 sessionId（兼容不同字段名） */
    fun getSessionIdSafe(): String? = sessionId ?: sessionIdAlt
}

/**
 * 消息帧类型枚举
 *
 * 涵盖 CloudCLI WebSocket 标准帧格式的主要类型。
 * 对应 claudecodeui 服务端 NormalizedMessage 和协议帧。
 */
object FrameKind {
    // --- NormalizedMessage 种类 (AI 输出) ---

    /** 用户消息 */
    const val USER = "user"

    /** 助手文本输出 */
    const val ASSISTANT = "assistant"

    /** 助手文本输出（兼容旧格式） */
    const val ASSISTANT_TEXT = "assistant_text"

    /** 工具调用请求 */
    const val TOOL_USE = "tool_use"

    /** 工具执行结果 */
    const val TOOL_RESULT = "tool_result"

    /** 思考过程（扩展思考） */
    const val THINKING = "thinking"

    /** Token 用量统计 */
    const val TOKEN_USAGE = "token_usage"

    // --- 协议帧类型 ---

    /** 订阅确认（携带 isProcessing 和 pendingPermissions） */
    const val CHAT_SUBSCRIBED = "chat_subscribed"

    /** 会话更新增量 */
    const val SESSION_UPSERTED = "session_upserted"

    /** 项目快照构建进度 */
    const val LOADING_PROGRESS = "loading_progress"

    /** 运行结束（携带 exitCode, success, aborted） */
    const val COMPLETE = "complete"

    /** 协议错误 */
    const val PROTOCOL_ERROR = "protocol_error"

    /** 错误信息（兼容旧格式） */
    const val ERROR = "error"

    /** 会话信息（兼容旧格式） */
    const val SESSION_INFO = "session_info"

    /** 用户消息回显（兼容旧格式） */
    const val USER_MESSAGE = "user_message"

    /** 流结束标记（兼容旧格式） */
    const val DONE = "done"

    /** 连接确认 */
    const val CONNECTED = "connected"

    /** 工具审批请求 */
    const val PERMISSION_REQUEST = "permission_request"
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    /** 未连接 */
    data object Disconnected : ConnectionState()

    /** 正在连接 */
    data object Connecting : ConnectionState()

    /** 已连接 */
    data object Connected : ConnectionState()

    /** 连接失败 / 已断开（含原因） */
    data class Error(val message: String) : ConnectionState()
}

// ============================================================================
// WebSocket 客户端实现
// ============================================================================

/**
 * CloudCLI WebSocket 客户端
 *
 * 基于 OkHttp 4.x 的 WebSocket 实现，负责与 CloudCLI 服务端 /ws 端点建立长连接，
 * 进行实时聊天通信。
 *
 * 核心能力：
 * 1. **鉴权连接**：在连接 URL 中携带 token 参数完成身份认证。
 * 2. **发送消息**：通过 [send] 发送 [ChatMessage]，触发 AI 对话。
 * 3. **流式接收**：通过 [messageFlow]（SharedFlow）或 [CloudWebSocketListener] 回调接收消息帧。
 * 4. **状态观察**：通过 [connectionState] 实时观察连接状态。
 * 5. **自动重连**：连接异常断开时按指数退避策略自动重连。
 *
 * 使用示例：
 * ```kotlin
 * val client = CloudWebSocketClient(okHttpClient, json)
 * client.connect("https://cloudcli.ai", "jwt_token")
 * client.messageFlow.collect { frame -> // 处理帧
 * client.send(ChatMessage(sessionId = "xxx", message = "hello", projectPath = "/path"))
 * ```
 *
 * @param okHttpClient 共享的 OkHttp 客户端实例
 * @param json 共享的 JSON 解析器（应配置 ignoreUnknownKeys = true）
 */
class CloudWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {

    /** 协程作用域，管理重连等异步任务 */
    private val scope = CoroutineScope(SupervisorJob())

    /** 当前 WebSocket 实例（@Volatile 保证多线程可见性） */
    @Volatile
    private var webSocket: WebSocket? = null

    /** 当前连接的服务器 URL（用于重连） */
    @Volatile
    private var currentServerUrl: String? = null

    /** 当前鉴权 token（用于重连） */
    @Volatile
    private var currentToken: String? = null

    /** 用户主动断开标志（主动断开时不触发自动重连） */
    private val manualDisconnect = AtomicBoolean(false)

    /** 重连次数计数器 */
    private val reconnectAttempts = AtomicInteger(0)

    /** 重连任务句柄 */
    private var reconnectJob: Job? = null

    /** 最大重连次数 */
    private val maxReconnectAttempts = 5

    /** 基础重连延迟（毫秒），实际延迟按指数退避递增 */
    private val baseReconnectDelayMs = 1000L

    // ---- 可观察状态 ----

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    /** 连接状态流，UI 层可观察以更新连接指示器 */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<CloudFrame>(
        replay = 0,
        extraBufferCapacity = 64
    )
    /** 消息帧流，所有从服务端接收的帧都会推送到此流 */
    val messageFlow: SharedFlow<CloudFrame> = _messageFlow.asSharedFlow()

    /** 可选的回调监听器，与 [messageFlow] 并行触发 */
    @Volatile
    var listener: CloudWebSocketListener? = null

    /**
     * WebSocket 事件回调接口
     *
     * 提供 [messageFlow] 之外的回调式接入方式，便于不使用协程的场景。
     */
    interface CloudWebSocketListener {
        /** 连接成功建立 */
        fun onConnected() {}
        /** 收到一帧消息 */
        fun onMessage(frame: CloudFrame) {}
        /** 连接断开 */
        fun onDisconnected(code: Int, reason: String) {}
        /** 连接发生错误 */
        fun onError(t: Throwable) {}
    }

    /**
     * 建立 WebSocket 连接
     *
     * 将 HTTP(S) URL 转换为 WS(S) URL，并在查询参数中附加 token 完成鉴权。
     * 若当前已有连接，会先断开再重连。
     *
     * @param serverUrl 服务器 HTTP(S) URL（如 https://cloudcli.ai）
     * @param token JWT 鉴权 token
     */
    fun connect(serverUrl: String, token: String) {
        if (token.isBlank()) {
            _connectionState.value = ConnectionState.Error("Token 为空，无法建立连接")
            return
        }

        manualDisconnect.set(false)
        currentServerUrl = serverUrl
        currentToken = token

        // 断开既有连接
        disconnectInternal()

        _connectionState.value = ConnectionState.Connecting

        val wsUrl = buildWebSocketUrl(serverUrl, token)
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts.set(0)
                _connectionState.value = ConnectionState.Connected
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // 二进制消息按 UTF-8 文本处理（CloudCLI 主要使用文本帧）
                handleTextMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                listener?.onDisconnected(code, reason)
                if (!manualDisconnect.get()) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "连接失败")
                listener?.onError(t)
                if (!manualDisconnect.get()) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * 发送聊天消息 (chat.send)
     *
     * @param message 聊天消息
     * @return true 表示消息已入队发送，false 表示连接未建立
     */
    fun send(message: ChatMessage): Boolean {
        val ws = webSocket ?: return false
        val jsonStr = json.encodeToString(ChatMessage.serializer(), message)
        return ws.send(jsonStr)
    }

    /**
     * 中止当前会话 (chat.abort)
     *
     * @param sessionId 要中止的会话标识
     * @return true 表示消息已入队发送
     */
    fun sendAbort(sessionId: String): Boolean {
        val ws = webSocket ?: return false
        val msg = ChatAbortMessage(sessionId = sessionId)
        val jsonStr = json.encodeToString(ChatAbortMessage.serializer(), msg)
        return ws.send(jsonStr)
    }

    /**
     * 订阅会话事件流 (chat.subscribe)
     *
     * 用于断线重连后重放丢失的事件。
     *
     * @param sessionId 会话标识
     * @param lastSeq 上次收到的最大事件序号
     * @return true 表示消息已入队发送
     */
    fun sendSubscribe(sessionId: String, lastSeq: Int = 0): Boolean {
        val ws = webSocket ?: return false
        val msg = ChatSubscribeMessage(sessions = listOf(SessionSubscription(sessionId, lastSeq)))
        val jsonStr = json.encodeToString(ChatSubscribeMessage.serializer(), msg)
        return ws.send(jsonStr)
    }

    /**
     * 响应工具审批请求 (chat.permission-response)
     *
     * @param sessionId 会话标识
     * @param permissionId 权限请求标识
     * @param approved 是否批准
     * @return true 表示消息已入队发送
     */
    fun sendPermissionResponse(sessionId: String, permissionId: String, approved: Boolean): Boolean {
        val ws = webSocket ?: return false
        val msg = ChatPermissionResponse(sessionId = sessionId, permissionId = permissionId, approved = approved)
        val jsonStr = json.encodeToString(ChatPermissionResponse.serializer(), msg)
        return ws.send(jsonStr)
    }

    /**
     * 发送原始文本消息
     *
     * @param text 原始文本
     * @return true 表示已入队发送
     */
    fun sendText(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    /**
     * 主动断开连接
     *
     * 标记为手动断开，不触发自动重连。
     *
     * @param code 关闭码（默认 1000 正常关闭）
     * @param reason 关闭原因
     */
    fun disconnect(code: Int = 1000, reason: String = "client disconnect") {
        manualDisconnect.set(true)
        reconnectJob?.cancel()
        disconnectInternal()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 手动触发重连
     */
    fun reconnect() {
        val url = currentServerUrl ?: return
        val token = currentToken ?: return
        connect(url, token)
    }

    /**
     * 当前是否已连接
     */
    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    /**
     * 释放资源，彻底关闭客户端
     *
     * 应在 Activity/Service 销毁时调用，避免内存泄漏。
     */
    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    // ===================== 内部实现 =====================

    /**
     * 构建 WebSocket URL
     *
     * - 将 http(s):// 转为 ws(s)://
     * - 拼接 /ws 路径
     * - 附加 token 查询参数
     */
    private fun buildWebSocketUrl(serverUrl: String, token: String): String {
        var url = serverUrl.trim()
        while (url.endsWith("/")) {
            url = url.dropLast(1)
        }
        // 协议转换
        url = when {
            url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
            url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
            url.startsWith("wss://") -> url
            url.startsWith("ws://") -> url
            else -> "ws://$url"
        }
        // 拼接 /ws 路径与 token 参数
        return "$url/ws?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
    }

    /**
     * 解析并分发文本消息帧
     */
    private fun handleTextMessage(text: String) {
        val frame = try {
            val parsed = json.decodeFromString(CloudFrame.serializer(), text)
            parsed.raw = text
            parsed
        } catch (e: Exception) {
            // 无法按标准帧解析时，包装为未知类型帧
            CloudFrame(kind = "unknown").apply { raw = text }
        }

        // 推送到 Flow（使用 tryEmit，因 extraBufferCapacity 已配置）
        _messageFlow.tryEmit(frame)

        // 触发回调
        listener?.onMessage(frame)
    }

    /**
     * 调度自动重连（指数退避）
     *
     * 每次重连延迟 = baseDelay * 2^attempt，上限约 30 秒。
     * 超过最大重连次数后停止。
     */
    private fun scheduleReconnect() {
        if (manualDisconnect.get()) return

        val attempt = reconnectAttempts.incrementAndGet()
        if (attempt > maxReconnectAttempts) {
            _connectionState.value = ConnectionState.Error("超过最大重连次数 ($maxReconnectAttempts)")
            return
        }

        // 指数退避延迟，上限 30 秒
        val delayMs = minOf(baseReconnectDelayMs * (1L shl (attempt - 1)), 30_000L)

        reconnectJob = scope.launch {
            delay(delayMs)
            if (!manualDisconnect.get()) {
                val url = currentServerUrl
                val token = currentToken
                if (url != null && token != null) {
                    connect(url, token)
                }
            }
        }
    }

    /**
     * 内部断开连接（不修改状态、不触发重连）
     */
    private fun disconnectInternal() {
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "closing")
        } catch (_: Exception) {
            // 忽略关闭异常
        }
        webSocket = null
    }

    /**
     * 将原始帧解析为带类型的访问辅助。
     *
     * 提供按需从 [CloudFrame.data] 中提取常用字段的便捷方法。
     */
    companion object {

        /**
         * 从帧数据中提取字符串字段
         *
         * @param frame 消息帧
         * @param key 字段名
         * @return 字段值，不存在时返回 null
         */
        fun getString(frame: CloudFrame, key: String): String? {
            val obj = frame.data as? JsonObject ?: return null
            val element = obj[key] as? JsonPrimitive ?: return null
            return element.contentOrNull
        }

        /**
         * 从帧数据中提取长整数字段
         */
        fun getLong(frame: CloudFrame, key: String): Long? {
            val obj = frame.data as? JsonObject ?: return null
            val element = obj[key] ?: return null
            return (element as? JsonPrimitive)?.longOrNull
        }

        /**
         * 判断帧是否为指定类型
         */
        fun isKind(frame: CloudFrame, kind: String): Boolean = frame.kind == kind
    }
}
