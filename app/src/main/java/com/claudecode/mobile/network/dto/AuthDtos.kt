package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 认证相关数据传输对象 (DTO)
 *
 * 对应 CloudCLI 服务端 /api/auth/ 系列接口的请求与响应结构。
 */

/**
 * 登录请求
 *
 * POST /api/auth/login
 *
 * @param username 用户名
 * @param password 密码
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 用户基本信息
 *
 * 登录/注册成功后返回的精简用户信息。
 *
 * @param id 用户唯一标识
 * @param username 用户名
 */
@Serializable
data class UserInfo(
    val id: Long? = null,
    val username: String
)

/**
 * 登录响应
 *
 * 服务端返回示例:
 * ```json
 * { "success": true, "user": { "id": 1, "username": "admin" }, "token": "..." }
 * ```
 *
 * @param success 是否成功
 * @param user 用户信息
 * @param token JWT 访问令牌
 * @param error 错误信息（失败时返回）
 */
@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val user: UserInfo? = null,
    val token: String? = null,
    val error: String? = null
)

/**
 * 注册请求
 *
 * POST /api/auth/register
 *
 * 注意: CloudCLI 为单用户系统，仅当数据库中无用户时允许注册。
 *
 * @param username 用户名（不少于 3 个字符）
 * @param password 密码（不少于 6 个字符）
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

/**
 * 认证状态
 *
 * GET /api/auth/status
 *
 * 用于判断服务端是否已完成初始设置以及当前认证情况。
 *
 * @param needsSetup 是否需要初始化（数据库中尚无用户时为 true）
 * @param isAuthenticated 是否已认证
 */
@Serializable
data class AuthStatus(
    @SerialName("needsSetup")
    val needsSetup: Boolean = false,
    @SerialName("isAuthenticated")
    val isAuthenticated: Boolean = false
)
