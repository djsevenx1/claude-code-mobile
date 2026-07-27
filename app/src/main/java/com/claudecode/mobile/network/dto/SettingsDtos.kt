package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户设置相关数据传输对象 (DTO)
 *
 * 对应 CloudCLI 服务端 /api/settings 系列接口。
 */

/**
 * 用户设置
 *
 * GET /api/settings 获取，PUT /api/settings 更新。
 *
 * CloudCLI 服务端的设置项较多，这里选取移动端常用配置并保留扩展性，
 * 对未知字段做宽松解析（忽略）以兼容不同服务端版本。
 *
 * @param theme 主题（light/dark/auto）
 * @param defaultProvider 默认 AI 提供商（claude/cursor/codex/opencode 等）
 * @param defaultModel 默认模型标识
 * @param autoApproveTools 是否自动批准工具调用
 * @param showTokenUsage 是否显示 Token 用量
 * @param streamingOutput 是否流式输出
 * @param notificationsEnabled 是否启用通知
 * @param language 界面语言
 * @param customSystemPrompt 自定义系统提示词（可选）
 * @param maxTurns 单次会话最大轮次（可选）
 * @param raw 原始键值集合，用于透传服务端新增的未映射字段（可选）
 */
@Serializable
data class UserSettings(
    val theme: String? = null,
    @SerialName("defaultProvider")
    val defaultProvider: String? = null,
    @SerialName("defaultModel")
    val defaultModel: String? = null,
    @SerialName("autoApproveTools")
    val autoApproveTools: Boolean? = null,
    @SerialName("showTokenUsage")
    val showTokenUsage: Boolean? = null,
    @SerialName("streamingOutput")
    val streamingOutput: Boolean? = null,
    @SerialName("notificationsEnabled")
    val notificationsEnabled: Boolean? = null,
    val language: String? = null,
    @SerialName("customSystemPrompt")
    val customSystemPrompt: String? = null,
    @SerialName("maxTurns")
    val maxTurns: Int? = null
)

/**
 * 设置更新响应
 *
 * @param success 是否更新成功
 * @param settings 更新后的完整设置（可选）
 * @param error 错误信息
 */
@Serializable
data class UpdateSettingsResponse(
    val success: Boolean = false,
    val settings: UserSettings? = null,
    val error: String? = null
)
