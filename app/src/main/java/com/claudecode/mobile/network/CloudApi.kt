package com.claudecode.mobile.network

import com.claudecode.mobile.network.dto.AuthStatus
import com.claudecode.mobile.network.dto.CreateProjectRequest
import com.claudecode.mobile.network.dto.CreateProjectResponse
import com.claudecode.mobile.network.dto.CreateSessionRequest
import com.claudecode.mobile.network.dto.CreateSessionResponse
import com.claudecode.mobile.network.dto.DeleteResponse
import com.claudecode.mobile.network.dto.GitCommitRequest
import com.claudecode.mobile.network.dto.GitCommitResponse
import com.claudecode.mobile.network.dto.GitStatus
import com.claudecode.mobile.network.dto.HealthStatus
import com.claudecode.mobile.network.dto.LoginRequest
import com.claudecode.mobile.network.dto.LoginResponse
import com.claudecode.mobile.network.dto.MessageHistoryResponse
import com.claudecode.mobile.network.dto.ModelInfo
import com.claudecode.mobile.network.dto.Project
import com.claudecode.mobile.network.dto.RegisterRequest
import com.claudecode.mobile.network.dto.Session
import com.claudecode.mobile.network.dto.UserInfo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CloudCLI REST API 接口定义
 *
 * 严格对应 claudecodeui (CloudCLI) 服务端的实际 REST 端点。
 * 所有方法的 baseUrl 由 [NetworkModule] 在构建 Retrofit 实例时动态注入。
 *
 * 鉴权由 [AuthInterceptor] 统一处理，接口层无需关心 token 附加。
 */
interface CloudApi {

    // ===================== 认证相关 =====================

    /** POST /api/auth/login - 用户登录 */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    /** POST /api/auth/register - 用户注册（仅首次） */
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    /** GET /api/auth/status - 查询认证状态 */
    @GET("api/auth/status")
    suspend fun getAuthStatus(): AuthStatus

    /** GET /api/auth/user - 获取当前用户信息 */
    @GET("api/auth/user")
    suspend fun getUser(): UserInfo

    /** POST /api/auth/logout - 登出 */
    @POST("api/auth/logout")
    suspend fun logout(): DeleteResponse

    // ===================== 项目相关 =====================

    /** GET /api/projects - 获取项目列表（含会话） */
    @GET("api/projects")
    suspend fun getProjects(): List<Project>

    /** GET /api/projects/archived - 获取已归档项目 */
    @GET("api/projects/archived")
    suspend fun getArchivedProjects(): List<Project>

    /** POST /api/projects/create-project - 创建项目 */
    @POST("api/projects/create-project")
    suspend fun createProject(@Body request: CreateProjectRequest): CreateProjectResponse

    /** GET /api/projects/{projectId}/sessions - 获取项目会话列表（分页） */
    @GET("api/projects/{projectId}/sessions")
    suspend fun getProjectSessions(
        @Path("projectId") projectId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<Session>

    /** PUT /api/projects/{projectId}/rename - 重命名项目 */
    @PUT("api/projects/{projectId}/rename")
    suspend fun renameProject(
        @Path("projectId") projectId: String,
        @Body body: Map<String, String>
    ): DeleteResponse

    /** POST /api/projects/{projectId}/toggle-star - 切换项目星标 */
    @POST("api/projects/{projectId}/toggle-star")
    suspend fun toggleProjectStar(@Path("projectId") projectId: String): DeleteResponse

    /** POST /api/projects/{projectId}/restore - 恢复已归档项目 */
    @POST("api/projects/{projectId}/restore")
    suspend fun restoreProject(@Path("projectId") projectId: String): DeleteResponse

    /** DELETE /api/projects/{projectId} - 删除/归档项目 */
    @DELETE("api/projects/{projectId}")
    suspend fun deleteProject(
        @Path("projectId") projectId: String,
        @Query("force") force: Boolean = false
    ): DeleteResponse

    // ===================== 会话相关 (通过 /api/providers) =====================

    /** POST /api/providers/sessions - 创建新会话（发送消息前必须先调用） */
    @POST("api/providers/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): CreateSessionResponse

    /** GET /api/providers/sessions/running - 获取所有运行中的会话 */
    @GET("api/providers/sessions/running")
    suspend fun getRunningSessions(): List<Session>

    /** GET /api/providers/sessions/archived - 获取已归档的会话 */
    @GET("api/providers/sessions/archived")
    suspend fun getArchivedSessions(): List<Session>

    /** GET /api/providers/search/sessions - 搜索会话 */
    @GET("api/providers/search/sessions")
    suspend fun searchSessions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50
    ): List<Session>

    /** DELETE /api/providers/sessions/{sessionId} - 删除/归档会话 */
    @DELETE("api/providers/sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: String,
        @Query("force") force: Boolean = false
    ): DeleteResponse

    /** POST /api/providers/sessions/{sessionId}/restore - 恢复会话 */
    @POST("api/providers/sessions/{sessionId}/restore")
    suspend fun restoreSession(@Path("sessionId") sessionId: String): DeleteResponse

    /** GET /api/providers/sessions/{sessionId}/messages - 获取会话消息历史 */
    @GET("api/providers/sessions/{sessionId}/messages")
    suspend fun getSessionMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): MessageHistoryResponse

    /** POST /api/providers/{provider}/sessions/{sessionId}/active-model - 切换会话活跃模型 */
    @POST("api/providers/{provider}/sessions/{sessionId}/active-model")
    suspend fun setActiveModel(
        @Path("provider") provider: String,
        @Path("sessionId") sessionId: String,
        @Body body: Map<String, String>
    ): DeleteResponse

    /** PUT /api/providers/sessions/{sessionId} - 重命名会话 */
    @PUT("api/providers/sessions/{sessionId}")
    suspend fun renameSession(
        @Path("sessionId") sessionId: String,
        @Body body: Map<String, String>
    ): DeleteResponse

    // ===================== 模型相关 =====================

    /** GET /api/providers/{provider}/models - 获取指定提供商的模型列表 */
    @GET("api/providers/{provider}/models")
    suspend fun getModels(@Path("provider") provider: String): List<ModelInfo>

    /** GET /api/providers/capabilities - 获取所有提供商能力 */
    @GET("api/providers/capabilities")
    suspend fun getCapabilities(): Map<String, kotlinx.serialization.json.JsonElement>

    // ===================== Git 相关 =====================

    /** GET /api/git/status - 获取 Git 状态 */
    @GET("api/git/status")
    suspend fun getGitStatus(@Query("path") path: String): GitStatus

    /** POST /api/git/commit - 提交 Git 变更 */
    @POST("api/git/commit")
    suspend fun gitCommit(@Body request: GitCommitRequest): GitCommitResponse

    // ===================== 系统相关 =====================

    /** GET /health - 健康检查（无需鉴权） */
    @GET("health")
    suspend fun healthCheck(): HealthStatus
}
