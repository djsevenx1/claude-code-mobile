package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI 模型相关数据传输对象 (DTO)
 *
 * 对应 CloudCLI 服务端 /api/providers/{provider}/models 接口。
 */

/**
 * 模型信息
 *
 * 描述一个可选的 AI 模型。
 *
 * @param id 模型标识（调用时使用，如 claude-sonnet-4-20250514）
 * @param name 模型展示名称
 * @param provider 所属提供商（claude/anthropic/openai/cursor 等）
 * @param contextWindow 上下文窗口大小（单位：token，可选）
 * @param maxOutput 最大输出 token 数（可选）
 * @param inputPricePerMToken 输入价格（每百万 token，可选）
 * @param outputPricePerMToken 输出价格（每百万 token，可选）
 * @param supportsStreaming 是否支持流式输出
 * @param supportsVision 是否支持视觉（图像）输入
 * @param description 模型描述（可选）
 * @param deprecated 是否已弃用
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val provider: String? = null,
    @SerialName("context_window")
    val contextWindow: Int? = null,
    @SerialName("max_output")
    val maxOutput: Int? = null,
    @SerialName("input_price_per_million_tokens")
    val inputPricePerMToken: Double? = null,
    @SerialName("output_price_per_million_tokens")
    val outputPricePerMToken: Double? = null,
    @SerialName("supports_streaming")
    val supportsStreaming: Boolean? = null,
    @SerialName("supports_vision")
    val supportsVision: Boolean? = null,
    val description: String? = null,
    val deprecated: Boolean = false
)

/**
 * 模型列表响应包装
 *
 * @param models 模型列表
 * @param provider 所属提供商
 */
@Serializable
data class ModelListResponse(
    val models: List<ModelInfo> = emptyList(),
    val provider: String? = null
)

/**
 * 模型列表 API 包装响应
 *
 * GET /api/providers/{provider}/models 返回:
 * { "success": true, "data": { "provider": "claude", "models": [...], "cache": {...} } }
 *
 * @param success 是否成功
 * @param data 模型数据
 */
@Serializable
data class ModelsResponse(
    val success: Boolean = false,
    val data: ModelsData? = null
)

/**
 * 模型列表数据
 *
 * @param provider 提供商名称
 * @param models 模型列表
 * @param cache 缓存信息（可选）
 */
@Serializable
data class ModelsData(
    val provider: String? = null,
    val models: List<ModelInfo> = emptyList(),
    val cache: kotlinx.serialization.json.JsonElement? = null
)

/**
 * 健康检查响应
 *
 * GET /health
 *
 * @param status 服务状态（"ok" 表示正常）
 * @param timestamp 服务器时间戳（ISO 8601 字符串）
 * @param version 服务端版本号
 * @param installMode 安装模式（git/npm）
 */
@Serializable
data class HealthStatus(
    val status: String = "",
    val timestamp: String? = null,
    val version: String? = null,
    @SerialName("installMode")
    val installMode: String? = null
)
