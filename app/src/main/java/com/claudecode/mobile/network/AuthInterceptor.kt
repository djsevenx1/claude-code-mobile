package com.claudecode.mobile.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器
 *
 * 负责为所有经过 OkHttp 的请求自动附加 JWT 鉴权信息，并处理服务端的 token 刷新与过期机制。
 *
 * 工作流程：
 * 1. 请求阶段：从 [TokenManager] 读取当前 JWT token，以 `Authorization: Bearer <token>` 头附加。
 *    跳过登录/注册/健康检查等公开端点（避免无 token 时产生不必要的 401）。
 * 2. 响应阶段：
 *    a) 若响应头包含 `X-Refreshed-Token`，说明服务端已签发新 token（当旧 token 过半生命周期时触发），
 *       将新 token 持久化保存，实现无感续期。
 *    b) 若响应码为 401/403，说明 token 已失效或无效，清除本地登录态并通知上层（触发重新登录）。
 *
 * 说明：OkHttp 的拦截器链运行在后台调度线程，因此使用 [runBlocking] 同步读取 DataStore 是安全的，
 * 不会阻塞主线程。DataStore 在首次读取后会缓存数据，后续读取开销极小。
 *
 * @param tokenManager token 与服务器配置管理器
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    /** token 过期回调，由上层（如 ViewModel/Application）注册以触发跳转登录页等行为 */
    @Volatile
    private var onTokenExpired: (() -> Unit)? = null

    /**
     * 设置 token 过期回调。
     *
     * @param callback 当收到 401/403 响应且确认 token 失效时回调（在后台线程触发）
     */
    fun setOnTokenExpired(callback: () -> Unit) {
        onTokenExpired = callback
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestUrl = request.url.encodedPath

        // 公开端点无需附加 token（登录、注册、认证状态检查、健康检查）
        val isPublicEndpoint = PUBLIC_ENDPOINTS.any { endpoint ->
            requestUrl.endsWith(endpoint)
        }

        // 同步读取当前 token（运行在 OkHttp 后台线程，runBlocking 安全）
        val token = if (isPublicEndpoint) null else runBlocking { tokenManager.getToken() }

        // 构建附加了 Authorization 头的请求
        val authedRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        // 执行请求
        val response = chain.proceed(authedRequest)

        // ---- 处理 token 自动刷新 ----
        // 服务端在 token 过半生命周期时会通过该响应头下发新 token
        val refreshedToken = response.header(HEADER_REFRESHED_TOKEN)
        if (!refreshedToken.isNullOrBlank()) {
            runBlocking { tokenManager.saveToken(refreshedToken) }
        }

        // ---- 处理 token 过期 ----
        // 401 = 未认证（无 token 或 token 无效）；403 = token 验证失败
        // 仅对受保护端点的失败做登出处理，避免公开端点误触发
        if ((response.code == 401 || response.code == 403) && !isPublicEndpoint && !token.isNullOrBlank()) {
            runBlocking { tokenManager.clearAuth() }
            onTokenExpired?.invoke()
        }

        return response
    }

    companion object {
        /** 服务端下发刷新 token 的响应头名称 */
        private const val HEADER_REFRESHED_TOKEN = "X-Refreshed-Token"

        /** 无需鉴权的公开端点后缀 */
        private val PUBLIC_ENDPOINTS = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/status",
            "/health"
        )
    }
}
