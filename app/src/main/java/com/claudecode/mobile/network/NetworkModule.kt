package com.claudecode.mobile.network

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块依赖注入容器
 *
 * 集中创建与管理网络层各组件，作为整个应用的唯一网络配置入口。
 * 采用单例对象模式，通过 [init] 完成初始化（应在 [com.claudecode.mobile.ClaudeCodeApp.onCreate] 中调用）。
 *
 * 提供的依赖：
 * - [TokenManager]：token 与服务器配置持久化
 * - [AuthInterceptor]：鉴权拦截器（自动附加 token、处理刷新与过期）
 * - [OkHttpClient]：共享的 HTTP 客户端（含鉴权与日志拦截器）
 * - [Json]：共享的 kotlinx.serialization JSON 解析器
 * - [CloudApi]：Retrofit API 接口实现（按服务器 URL 动态创建）
 * - [CloudWebSocketClient]：WebSocket 客户端工厂
 *
 * 由于服务器 URL 是用户可配置的（自托管），Retrofit 实例不能在编译期固定 baseUrl，
 * 而是在运行时通过 [createCloudApi] 按需创建；OkHttpClient 与 Json 则全局共享。
 */
object NetworkModule {

    /** 应用上下文（init 后赋值） */
    private lateinit var appContext: Context

    /** token 管理器 */
    private val _tokenManager: TokenManager by lazy { TokenManager(appContext) }
    val tokenManager: TokenManager get() = _tokenManager

    /** 鉴权拦截器 */
    private val _authInterceptor: AuthInterceptor by lazy { AuthInterceptor(_tokenManager) }
    val authInterceptor: AuthInterceptor get() = _authInterceptor

    /**
     * JSON 解析器
     *
     * - ignoreUnknownKeys：忽略服务端新增的未知字段，保证前向兼容
     * - isLenient：宽松解析，容忍部分非标准 JSON
     * - coerceInputValues：将非法值强制转为默认值，避免解析崩溃
     * - explicitNulls：序列化时不输出 null 字段，减小请求体
     */
    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
            encodeDefaults = true
        }
    }

    /**
     * 共享的 OkHttp 客户端
     *
     * - 超时配置：连接 15s、读写 30s（适配可能较慢的自托管服务器）
     * - WebSocket ping 间隔 30s 保持长连接活跃
     * - 添加 [AuthInterceptor] 处理鉴权
     * - 添加 [HttpLoggingInterceptor] 日志（仅 debug 构建输出 BODY 级别）
     */
    val okHttpClient: OkHttpClient by lazy {
        val isDebug = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(_authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 初始化网络模块
     *
     * 应在 Application 启动时调用一次，传入应用上下文。
     * 后续各依赖通过 lazy 按需创建。
     *
     * @param context 应用上下文（内部取 applicationContext）
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 设置 token 过期回调
     *
     * 便于上层（如 Application）在 token 失效时执行全局跳转登录等逻辑。
     *
     * @param callback token 过期时回调
     */
    fun setOnTokenExpired(callback: () -> Unit) {
        _authInterceptor.setOnTokenExpired(callback)
    }

    /**
     * 创建 CloudApi 接口实现
     *
     * 由于服务器 URL 动态可配，每次切换服务器后应调用此方法重建 Retrofit 实例。
     * 内部复用共享的 [okHttpClient] 与 [json] 解析器。
     *
     * @param serverUrl 服务器根 URL（如 https://cloudcli.ai 或 http://192.168.1.100:3001）
     * @return CloudApi 接口实现
     */
    fun createCloudApi(serverUrl: String): CloudApi {
        // Retrofit 要求 baseUrl 以 / 结尾
        val normalizedUrl = _tokenManager.normalizeUrl(serverUrl).let {
            if (it.endsWith("/")) it else "$it/"
        }

        val contentType = "application/json".toMediaType()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(CloudApi::class.java)
    }

    /**
     * 创建 WebSocket 客户端
     *
     * 复用共享的 [okHttpClient] 与 [json]。
     * 每次调用返回新实例，调用方负责在不再使用时调用 [CloudWebSocketClient.shutdown]。
     *
     * @return 新的 WebSocket 客户端实例
     */
    fun createWebSocketClient(): CloudWebSocketClient {
        return CloudWebSocketClient(okHttpClient, json)
    }

    /**
     * 便捷方法：使用当前已保存的服务器 URL 与 token 创建 CloudApi
     *
     * 适合在已确定服务器配置后快速获取 API 实例。
     * 需在协程中调用。
     *
     * @return CloudApi 实例，若未配置服务器 URL 则返回 null
     */
    suspend fun createCloudApiFromConfig(): CloudApi? {
        val serverUrl = _tokenManager.getServerUrl() ?: return null
        return createCloudApi(serverUrl)
    }
}
