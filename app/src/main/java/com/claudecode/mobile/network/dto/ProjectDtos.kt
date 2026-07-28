package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 项目与会话相关数据传输对象 (DTO)
 *
 * 严格对应 claudecodeui 服务端的实际数据结构。
 */

/**
 * 项目信息
 *
 * GET /api/projects 返回项目列表，每个项目内嵌 sessions 数组。
 *
 * @param id 项目唯一标识（数据库 ID）
 * @param name 项目名称（路径最后一段）
 * @param path 项目在服务器上的绝对路径
 * @param displayName 自定义展示名称（可选）
 * @param lastModified 最后修改时间
 * @param createdAt 创建时间（可选）
 * @param hasGit 是否包含 Git 仓库
 * @param sessionCount 关联会话数量（可选）
 * @param sessions 内嵌的会话列表（GET /api/projects 返回）
 * @param isArchived 是否已归档
 * @param isStarred 是否已星标
 */
@Serializable
data class Project(
    // 服务端返回 projectId (驼峰), 旧版可能返回 id
    @SerialName("projectId")
    val id: String? = null,
    // 服务端不返回 name, 由 path 推导; 保留字段兼容旧版
    val name: String = "",
    // 服务端返回 path 和 fullPath
    val path: String = "",
    // 服务端返回 displayName (驼峰), 非 display_name
    @SerialName("displayName")
    val displayName: String? = null,
    val fullPath: String? = null,
    @SerialName("last_modified")
    val lastModified: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("has_git")
    val hasGit: Boolean? = null,
    @SerialName("session_count")
    val sessionCount: Int? = null,
    val sessions: List<Session>? = null,
    val sessionMeta: SessionMeta? = null,
    @SerialName("isArchived")
    val isArchived: Boolean? = null,
    @SerialName("isStarred")
    val isStarred: Boolean? = null
) {
    /** 统一获取项目 ID (兼容 projectId 和 id) */
    fun getIdSafe(): String? = id

    /** 统一获取展示名称 */
    fun getDisplayNameSafe(): String =
        displayName?.takeIf { it.isNotBlank() }
            ?: name.takeIf { it.isNotBlank() }
            ?: path.substringAfterLast("/").takeIf { it.isNotBlank() }
            ?: id
            ?: "未命名项目"

    /** 统一获取路径 */
    fun getPathSafe(): String = path.ifBlank { fullPath ?: "" }
}

/**
 * 会话分页元信息
 *
 * GET /api/projects 返回的 sessionMeta 字段。
 */
@Serializable
data class SessionMeta(
    val hasMore: Boolean = false,
    val total: Int = 0
)

/**
 * 创建项目请求
 *
 * POST /api/projects/create-project
 *
 * @param path 项目路径（服务器上的路径）
 * @param customName 自定义展示名称（可选）
 */
@Serializable
data class CreateProjectRequest(
    val path: String,
    @SerialName("customName")
    val customName: String? = null
)

/**
 * 创建项目响应
 *
 * @param success 是否成功
 * @param project 创建后的项目信息
 * @param message 结果消息
 */
@Serializable
data class CreateProjectResponse(
    val success: Boolean = false,
    val project: Project? = null,
    val message: String? = null
)

/**
 * 会话信息
 *
 * 对应 claudecodeui 服务端的 SessionSummary 类型。
 * GET /api/projects 返回的项目内嵌 sessions 使用此格式。
 *
 * 服务端实际返回的字段 (SessionSummary):
 *   id, provider, summary, messageCount, lastActivity
 *
 * 其他端点 (如 /api/providers/sessions/running) 可能返回更多字段，
 * 通过 @SerialName 兼容驼峰和蛇形两种命名风格。
 *
 * @param id 会话唯一标识 (服务端必填)
 * @param projectId 所属项目标识 (内嵌 sessions 中不返回，由上层补充)
 * @param title 会话标题 (服务端不返回此字段，使用 summary 代替)
 * @param summary 会话摘要 / 自定义名称
 * @param lastActivity 最后活跃时间 (驼峰，服务端返回此字段)
 * @param messageCount 消息数量 (驼峰)
 * @param provider 会话使用的 AI 提供商
 * @param isArchived 是否已归档
 */
@Serializable
data class Session(
    // 服务端返回 id (小写)，设为可空+默认值防止解析崩溃
    val id: String? = null,
    // 内嵌 sessions 不返回 project_id，由 SessionListViewModel 从父项目补充
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("projectId")
    val projectIdAlt: String? = null,
    // 服务端不返回 title，使用 summary 代替显示
    val title: String? = null,
    val summary: String? = null,
    // 服务端返回 lastActivity (驼峰)
    @SerialName("lastActivity")
    val lastActivity: String? = null,
    // 兼容旧版蛇形命名
    @SerialName("last_active_at")
    val lastActiveAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    // 服务端返回 messageCount (驼峰)
    @SerialName("messageCount")
    val messageCount: Int? = null,
    // 兼容旧版蛇形命名
    @SerialName("message_count")
    val messageCountAlt: Int? = null,
    val provider: String? = null,
    @SerialName("provider_session_id")
    val providerSessionId: String? = null,
    @SerialName("isArchived")
    val isArchived: Boolean? = null
) {
    /** 统一获取会话 ID (防止 id 为 null 导致崩溃) */
    fun getIdSafe(): String = id ?: ""

    /** 统一获取 projectId（兼容不同字段名） */
    fun getProjectIdSafe(): String? = projectId ?: projectIdAlt

    /** 统一获取 messageCount（兼容不同字段名） */
    fun getMessageCountSafe(): Int? = messageCount ?: messageCountAlt

    /** 统一获取最后活跃时间 (优先 lastActivity，回退到其他时间字段) */
    fun getLastActivitySafe(): String? =
        lastActivity ?: lastActiveAt ?: updatedAt ?: createdAt

    /** 统一获取显示标题 (服务端不返回 title，使用 summary 代替) */
    fun getTitleSafe(): String =
        title?.takeIf { it.isNotBlank() }
            ?: summary?.takeIf { it.isNotBlank() }
            ?: "未命名对话"
}

/**
 * 通用删除/操作响应
 *
 * @param success 是否成功
 * @param error 错误信息
 */
@Serializable
data class DeleteResponse(
    val success: Boolean = false,
    val error: String? = null
)

// ============================================================
// 包装响应 DTO (claudecodeui 使用 {success, data} 格式)
// ============================================================

/**
 * 会话列表包装响应
 *
 * GET /api/providers/sessions/running 和 /archived 返回:
 * { "success": true, "data": { "sessions": [...] } }
 *
 * @param success 是否成功
 * @param data 会话数据
 */
@Serializable
data class SessionsResponse(
    val success: Boolean = false,
    val data: SessionsData? = null
)

/**
 * 会话列表数据
 *
 * @param sessions 会话数组
 */
@Serializable
data class SessionsData(
    val sessions: List<Session> = emptyList()
)

/**
 * 已归档项目列表包装响应
 *
 * GET /api/projects/archived 返回:
 * { "success": true, "data": { "projects": [...] } }
 *
 * @param success 是否成功
 * @param data 项目数据
 */
@Serializable
data class ArchivedProjectsResponse(
    val success: Boolean = false,
    val data: ArchivedProjectsData? = null
)

/**
 * 已归档项目列表数据
 *
 * @param projects 项目数组
 */
@Serializable
data class ArchivedProjectsData(
    val projects: List<Project> = emptyList()
)

// ============================================================
// 会话创建与消息历史 DTO
// ============================================================

/**
 * 创建会话请求
 *
 * POST /api/providers/sessions
 * 在首次发送 chat.send 之前必须调用此接口获取 app session id。
 *
 * @param provider AI 提供商（claude/cursor/codex/opencode）
 * @param projectPath 项目路径（服务器上的绝对路径）
 */
@Serializable
data class CreateSessionRequest(
    val provider: String = "claude",
    @SerialName("projectPath")
    val projectPath: String
)

/**
 * 创建会话响应
 *
 * POST /api/providers/sessions 返回:
 * { "success": true, "data": { "sessionId": "...", ... } }
 *
 * @param success 是否成功
 * @param data 会话数据 (createAppSession 的返回值)
 * @param error 错误信息
 */
@Serializable
data class CreateSessionResponse(
    val success: Boolean = false,
    val data: CreateSessionData? = null,
    val error: String? = null
) {
    /** 统一获取 sessionId（兼容不同字段名） */
    fun getSessionIdSafe(): String? {
        return data?.sessionId ?: data?.sessionIdAlt ?: data?.session?.id
    }
}

/**
 * 创建会话返回数据
 *
 * @param sessionId 会话标识 (camelCase)
 * @param sessionIdAlt 会话标识 (snake_case 兼容)
 * @param session 会话详情（可选）
 */
@Serializable
data class CreateSessionData(
    @SerialName("sessionId")
    val sessionId: String? = null,
    @SerialName("session_id")
    val sessionIdAlt: String? = null,
    @SerialName("id")
    val id: String? = null,
    val session: Session? = null
)

/**
 * 消息历史响应
 *
 * GET /api/providers/sessions/{sessionId}/messages
 *
 * @param success 是否成功
 * @param data 消息数据
 */
@Serializable
data class MessageHistoryResponse(
    val success: Boolean = false,
    val data: MessageHistoryData? = null
)

/**
 * 消息历史数据
 *
 * @param messages 消息列表
 */
@Serializable
data class MessageHistoryData(
    val messages: List<HistoryMessage> = emptyList()
)

/**
 * 历史消息项
 *
 * 对应 claudecodeui 服务端 NormalizedMessage 格式。
 *
 * @param id 消息标识
 * @param kind 消息类型（user/assistant/tool_use/tool_result/thinking 等）
 * @param content 消息文本内容
 * @param text 文本内容（部分版本使用此字段）
 * @param role 消息角色（user/assistant）
 * @param toolName 工具名称（仅工具调用消息）
 * @param toolInput 工具输入参数
 * @param toolResult 工具执行结果
 * @param thinking 思考过程内容
 * @param timestamp 时间戳
 * @param seq 事件序号
 * @param model 使用的模型
 * @param tokenUsage token 用量信息
 */
@Serializable
data class HistoryMessage(
    val id: String? = null,
    val kind: String = "",
    val content: String? = null,
    val text: String? = null,
    val role: String? = null,
    @SerialName("toolName")
    val toolName: String? = null,
    @SerialName("tool_name")
    val toolNameAlt: String? = null,
    @SerialName("toolInput")
    val toolInput: String? = null,
    @SerialName("tool_input")
    val toolInputAlt: String? = null,
    @SerialName("toolResult")
    val toolResult: String? = null,
    @SerialName("tool_result")
    val toolResultAlt: String? = null,
    val thinking: String? = null,
    val timestamp: String? = null,
    val seq: Int? = null,
    val model: String? = null,
    @SerialName("tokenUsage")
    val tokenUsage: JsonElement? = null,
    @SerialName("token_usage")
    val tokenUsageAlt: JsonElement? = null
) {
    /** 统一获取文本内容 */
    fun getTextSafe(): String? = content ?: text

    /** 统一获取工具名称 */
    fun getToolNameSafe(): String? = toolName ?: toolNameAlt

    /** 统一获取工具输入 */
    fun getToolInputSafe(): String? = toolInput ?: toolInputAlt

    /** 统一获取工具结果 */
    fun getToolResultSafe(): String? = toolResult ?: toolResultAlt

    /** 统一获取 token 用量 */
    fun getTokenUsageSafe(): JsonElement? = tokenUsage ?: tokenUsageAlt
}
