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
    val id: String? = null,
    val name: String = "",
    val path: String = "",
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("last_modified")
    val lastModified: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("has_git")
    val hasGit: Boolean? = null,
    @SerialName("session_count")
    val sessionCount: Int? = null,
    val sessions: List<Session>? = null,
    @SerialName("isArchived")
    val isArchived: Boolean? = null,
    @SerialName("isStarred")
    val isStarred: Boolean? = null
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
 * 从 GET /api/providers/sessions/running 或项目内嵌 sessions 获取。
 *
 * @param id 会话唯一标识
 * @param projectId 所属项目标识
 * @param title 会话标题
 * @param summary 会话摘要（可选）
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param lastActiveAt 最后活跃时间（可选）
 * @param messageCount 消息数量（可选）
 * @param provider 会话使用的 AI 提供商
 * @param providerSessionId 提供商会话 ID（可选）
 * @param isArchived 是否已归档
 */
@Serializable
data class Session(
    val id: String,
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("projectId")
    val projectIdAlt: String? = null,
    val title: String? = null,
    val summary: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("last_active_at")
    val lastActiveAt: String? = null,
    @SerialName("message_count")
    val messageCount: Int? = null,
    @SerialName("messageCount")
    val messageCountAlt: Int? = null,
    val provider: String? = null,
    @SerialName("provider_session_id")
    val providerSessionId: String? = null,
    @SerialName("isArchived")
    val isArchived: Boolean? = null
) {
    /** 统一获取 projectId（兼容不同字段名） */
    fun getProjectIdSafe(): String? = projectId ?: projectIdAlt

    /** 统一获取 messageCount（兼容不同字段名） */
    fun getMessageCountSafe(): Int? = messageCount ?: messageCountAlt
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
 * @param success 是否成功
 * @param sessionId 服务端分配的 app session id
 * @param session 会话详情（可选）
 * @param error 错误信息
 */
@Serializable
data class CreateSessionResponse(
    val success: Boolean = false,
    @SerialName("sessionId")
    val sessionId: String? = null,
    @SerialName("session_id")
    val sessionIdAlt: String? = null,
    val session: Session? = null,
    val error: String? = null
) {
    /** 统一获取 sessionId（兼容不同字段名） */
    fun getSessionIdSafe(): String? = sessionId ?: sessionIdAlt ?: session?.id
}

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
