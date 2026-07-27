package com.claudecode.mobile.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息实体
 *
 * 用于本地缓存会话中的消息记录。支持普通文本消息、工具调用消息
 * （tool_use / tool_result）以及流式传输中的消息。
 *
 * @property id 自增主键
 * @property sessionId 所属会话 ID（由服务器下发）
 * @property role 消息角色，取值：user / assistant / system / tool
 * @property content 消息正文内容
 * @property timestamp 消息时间戳（毫秒）
 * @property kind 消息类型，例如 text / tool_use / tool_result / error，可空
 * @property toolName 工具名称（仅在工具相关消息时有值），可空
 * @property isStreaming 是否正在流式传输中（传输完成后应置为 false）
 * @property projectId 关联的本地项目 ID（对应 [CachedProject] 的本地主键）
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["projectId"])
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val kind: String? = null,
    val toolName: String? = null,
    val isStreaming: Boolean = false,
    val projectId: Long = 0L
)
