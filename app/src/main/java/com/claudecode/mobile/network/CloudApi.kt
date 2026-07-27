package com.claudecode.mobile.network

import com.claudecode.mobile.network.dto.AuthStatus
import com.claudecode.mobile.network.dto.CreateProjectRequest
import com.claudecode.mobile.network.dto.CreateProjectResponse
import com.claudecode.mobile.network.dto.GitCommitRequest
import com.claudecode.mobile.network.dto.GitCommitResponse
import com.claudecode.mobile.network.dto.GitStatus
import com.claudecode.mobile.network.dto.HealthStatus
import com.claudecode.mobile.network.dto.LoginRequest
import com.claudecode.mobile.network.dto.LoginResponse
import com.claudecode.mobile.network.dto.ModelInfo
import com.claudecode.mobile.network.dto.Project
import com.claudecode.mobile.network.dto.RegisterRequest
import com.claudecode.mobile.network.dto.Session
import com.claudecode.mobile.network.dto.UpdateSettingsResponse
import com.claudecode.mobile.network.dto.UserSettings
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CloudCLI REST API 接口定义
 *
 * 对应 CloudCLI（claudecodeui）服务端的 REST 端点。
 * 所有方法的 baseUrl 由 [NetworkModule] 在构建 Retrofit 实例时动态注入，
 * 以支持用户配置自托管服务器。
 *
 * 鉴权由 [AuthInterceptor] 统一处理，接口层无需关心 token 附加。
 *
 * 返回类型直接使用 DTO 或 DTO 列表；kotlinx-serialization 转换器会自动解析。
 * 对于服务端可能以数组或对象包装返回的接口，约定按数组解析，
 * [NetworkModule] 中配置的 Json 实例开启了宽松模式以兼容字段差异。
 */
interface CloudApi {

    // ===================== 认证相关 =====================

    /**
     * 用户登录
     *
     * POST /api/auth/login
     *
     * @param request 登录请求体（用户名 + 密码）
     * @return 登录响应，含 JWT token 与用户信息
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /**
     * 用户注册（初始化）
     *
     * POST /api/auth/register
     * 注意：CloudCLI 为单用户系统，仅当数据库中尚无用户时允许注册。
     *
     * @param request 注册请求体
     * @return 注册响应，结构与登录一致
     */
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    /**
     * 查询认证状态
     *
     * GET /api/auth/status
     * 用于判断服务端是否需要初始化设置。
     *
     * @return 认证状态信息
     */
    @GET("api/auth/status")
    suspend fun getAuthStatus(): AuthStatus

    // ===================== 项目相关 =====================

    /**
     * 获取项目列表
     *
     * GET /api/projects
     *
     * @return 项目列表
     */
    @GET("api/projects")
    suspend fun getProjects(): List<Project>

    /**
     * 创建项目
     *
     * POST /api/projects
     *
     * @param request 创建项目请求体
     * @return 创建结果，含新建项目信息
     */
    @POST("api/projects")
    suspend fun createProject(@Body request: CreateProjectRequest): CreateProjectResponse

    /**
     * 获取指定项目下的会话列表
     *
     * GET /api/projects/{projectId}/sessions
     *
     * @param projectId 项目标识
     * @return 会话列表
     */
    @GET("api/projects/{projectId}/sessions")
    suspend fun getProjectSessions(
        @Path("projectId") projectId: String
    ): List<Session>

    // ===================== 设置相关 =====================

    /**
     * 获取用户设置
     *
     * GET /api/settings
     *
     * @return 用户设置
     */
    @GET("api/settings")
    suspend fun getSettings(): UserSettings

    /**
     * 更新用户设置
     *
     * PUT /api/settings
     *
     * @param settings 新的设置内容
     * @return 更新结果
     */
    @PUT("api/settings")
    suspend fun updateSettings(@Body settings: UserSettings): UpdateSettingsResponse

    // ===================== 模型相关 =====================

    /**
     * 获取指定提供商可用模型列表
     *
     * GET /api/providers/{provider}/models
     *
     * @param provider 提供商标识（claude/openai/cursor/codex/opencode 等）
     * @return 模型信息列表
     */
    @GET("api/providers/{provider}/models")
    suspend fun getModels(@Path("provider") provider: String): List<ModelInfo>

    // ===================== Git 相关 =====================

    /**
     * 获取 Git 仓库状态
     *
     * GET /api/git/status
     *
     * @param path 项目路径（用于定位仓库）
     * @return Git 状态信息
     */
    @GET("api/git/status")
    suspend fun getGitStatus(@Query("path") path: String): GitStatus

    /**
     * 提交 Git 变更
     *
     * POST /api/git/commit
     *
     * @param request 提交请求体
     * @return 提交结果
     */
    @POST("api/git/commit")
    suspend fun gitCommit(@Body request: GitCommitRequest): GitCommitResponse

    // ===================== 系统相关 =====================

    /**
     * 健康检查
     *
     * GET /health
     * 公开端点，无需鉴权，用于检测服务器连通性与版本。
     *
     * @return 服务器健康状态
     */
    @GET("health")
    suspend fun healthCheck(): HealthStatus
}
