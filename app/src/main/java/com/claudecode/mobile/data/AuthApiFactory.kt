package com.claudecode.mobile.data

import com.claudecode.mobile.network.CloudApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ============================================================
// 认证 API 工厂
// 专门用于登录页面创建 CloudApi 实例
// 支持信任所有证书 (用于自签名 HTTPS 开发服务器)
// ============================================================

object AuthApiFactory {

    /**
     * JSON 序列化配置 (与 NetworkModule 保持一致)
     */
    private val json = Json {
        ignoreUnknownKeys = true       // 忽略未知字段，增强兼容性
        isLenient = true               // 宽松解析
        coerceInputValues = true       // 强制将无效值转为默认值
        explicitNulls = false          // 不输出 null 字段
        encodeDefaults = true          // 编码默认值
    }

    /**
     * 创建 CloudApi 实例
     *
     * 与 NetworkModule.createCloudApi 不同，本方法支持信任所有证书，
     * 适用于登录页面在配置服务器阶段连接自签名证书服务器的场景。
     *
     * @param serverUrl 服务器基础地址 (如 https://cloudcli.ai)
     * @param trustAllCerts 是否信任所有证书 (用于自签名证书)
     * @return CloudApi 实例
     */
    fun create(serverUrl: String, trustAllCerts: Boolean = false): CloudApi {
        // 构建 OkHttpClient
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // 添加请求日志拦截器
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })

        // 如果需要信任所有证书，配置自定义 SSL
        if (trustAllCerts) {
            configureTrustAllCerts(clientBuilder)
        }

        val client = clientBuilder.build()

        // 规范化 URL 并确保以斜杠结尾 (Retrofit 要求)
        val normalizedUrl = normalizeUrl(serverUrl).let {
            if (it.endsWith("/")) it else "$it/"
        }

        // 构建 Retrofit 实例并创建 CloudApi
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(CloudApi::class.java)
    }

    /**
     * 规范化 URL (补全协议、去尾部斜杠)
     */
    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        while (u.endsWith("/")) {
            u = u.dropLast(1)
        }
        return u
    }

    /**
     * 配置 OkHttpClient 信任所有 SSL 证书
     * 仅用于开发环境连接自签名证书的服务器
     */
    private fun configureTrustAllCerts(builder: OkHttpClient.Builder) {
        try {
            // 创建信任所有证书的 TrustManager
            val trustAllManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            // 初始化 SSL 上下文
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())

            // 设置信任所有主机名
            val hostnameVerifier = HostnameVerifier { _, _ -> true }

            builder.sslSocketFactory(sslContext.socketFactory, trustAllManager)
            builder.hostnameVerifier(hostnameVerifier)
        } catch (e: Exception) {
            // 配置失败时忽略，回退到默认 SSL 验证
            e.printStackTrace()
        }
    }
}
