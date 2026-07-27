package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 项目与会话相关数据传输对象 (DTO)
 *
 * 对应 CloudCLI 服务端 /api/projects/ 系列接口。
 */

/**
 * 项目信息
 *
 * GET /api/projects 返回项目列表，GET /api/projects/{id} 返回单个项目。
 *
 * @param id 项目唯一标识（路径或名称）
 * @param name 项目显示名称
 * @param path 项目在服务器上的绝对路径
 * @param displayName 自定义展示名称（可选）
 * @param lastModified 最后修改时间（ISO 8601 字符串）
 * @param createdAt 创建时间（可选）
 * @param hasGit 是否包含 Git 仓库
 * @param sessionCount 关联会话数量（可选）
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
    val sessionCount: Int? = null
)

/**
 * 项目列表响应包装
 *
 * 部分 CloudCLI 版本以对象形式返回，部分以数组形式返回。
 * 此类用于对象包装场景，数组场景直接使用 List<Project>。
 *
 * @param projects 项目列表
 */
@Serializable
data class ProjectListResponse(
    val projects: List<Project> = emptyList()
)

/**
 * 创建项目请求
 *
 * POST /api/projects
 *
 * @param name 项目名称
 * @param path 项目路径（可选，缺省时在服务器默认工作区下创建）
 * @param displayName 自定义展示名称（可选）
 */
@Serializable
data class CreateProjectRequest(
    val name: String,
    val path: String? = null,
    @SerialName("display_name")
    val displayName: String? = null
)

/**
 * 创建项目响应
 *
 * @param success 是否成功
 * @param project 创建后的项目信息
 * @param error 错误信息
 */
@Serializable
data class CreateProjectResponse(
    val success: Boolean = false,
    val project: Project? = null,
    val error: String? = null
)

/**
 * 会话信息
 *
 * GET /api/projects/{id}/sessions 返回该项目下的会话列表。
 *
 * @param id 会话唯一标识
 * @param projectId 所属项目标识
 * @param title 会话标题（通常取自首条消息）
 * @param summary 会话摘要（可选）
 * @param createdAt 创建时间（ISO 8601 字符串）
 * @param updatedAt 最后更新时间（ISO 8601 字符串）
 * @param lastActiveAt 最后活跃时间（可选，部分版本字段名不同）
 * @param messageCount 消息数量（可选）
 * @param provider 会话使用的 AI 提供商（claude/cursor/codex/opencode 等，可选）
 */
@Serializable
data class Session(
    val id: String,
    @SerialName("project_id")
    val projectId: String? = null,
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
    val provider: String? = null
)

/**
 * 会话列表响应包装
 *
 * @param sessions 会话列表
 */
@Serializable
data class SessionListResponse(
    val sessions: List<Session> = emptyList()
)
