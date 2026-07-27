package com.claudecode.mobile.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Token 与服务器配置管理器
 *
 * 基于 Jetpack DataStore（Preferences）持久化以下信息：
 * - JWT 访问令牌（用于 REST 与 WebSocket 鉴权）
 * - 服务器地址（用户可配置的自托管 CloudCLI 服务器 URL）
 * - 登录用户信息（用户名、用户 ID）
 *
 * DataStore 相比 SharedPreferences 提供协程友好的异步访问、事务性写入，
 * 并且能避免在主线程进行磁盘 IO 导致的 ANR。
 *
 * @param context 应用上下文
 */
class TokenManager(private val context: Context) {

    companion object {
        // 顶层扩展属性，保证全局唯一的 DataStore 实例（DataStore 必须单例）
        private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "cloudcli_auth_prefs"
        )

        // ---- DataStore 键定义 ----
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_USER_ID = longPreferencesKey("user_id")
    }

    /**
     * 当前已保存的 JWT token（Flow 形式，便于 UI 响应式观察登录态变化）。
     */
    val tokenFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_TOKEN] }

    /**
     * 当前服务器 URL（Flow 形式）。
     */
    val serverUrlFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_SERVER_URL] }

    /**
     * 当前登录用户名（Flow 形式）。
     */
    val usernameFlow: Flow<String?> = context.authDataStore.data.map { it[KEY_USERNAME] }

    /**
     * 当前登录用户 ID（Flow 形式）。
     */
    val userIdFlow: Flow<Long?> = context.authDataStore.data.map { it[KEY_USER_ID] }

    /**
     * 同步读取当前 JWT token。
     * 应在协程中调用，避免阻塞主线程。
     *
     * @return token 字符串，未登录时返回 null
     */
    suspend fun getToken(): String? = context.authDataStore.data.first()[KEY_TOKEN]

    /**
     * 同步读取当前服务器 URL。
     *
     * @return 服务器 URL 字符串，未配置时返回 null
     */
    suspend fun getServerUrl(): String? = context.authDataStore.data.first()[KEY_SERVER_URL]

    /**
     * 同步读取当前登录用户名。
     *
     * @return 用户名，未登录时返回 null
     */
    suspend fun getUsername(): String? = context.authDataStore.data.first()[KEY_USERNAME]

    /**
     * 同步读取当前登录用户 ID。
     *
     * @return 用户 ID，未登录时返回 null
     */
    suspend fun getUserId(): Long? = context.authDataStore.data.first()[KEY_USER_ID]

    /**
     * 判断是否已登录（token 非空）。
     *
     * @return true 表示存在已保存的 token
     */
    suspend fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    /**
     * 保存登录凭证及用户信息。
     * 通常在登录/注册成功后调用。
     *
     * @param token JWT token
     * @param username 用户名
     * @param userId 用户 ID（可选）
     */
    suspend fun saveAuthInfo(token: String, username: String, userId: Long? = null) {
        context.authDataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USERNAME] = username
            if (userId != null) {
                prefs[KEY_USER_ID] = userId
            }
        }
    }

    /**
     * 仅更新 JWT token（用于服务端通过 X-Refreshed-Token 头下发新 token 时）。
     *
     * @param token 新的 JWT token
     */
    suspend fun saveToken(token: String) {
        context.authDataStore.edit { it[KEY_TOKEN] = token }
    }

    /**
     * 配置/切换服务器地址。
     * 由于服务器 URL 是动态可配的，Retrofit 与 OkHttpClient 需在切换后重建。
     *
     * @param url 服务器完整 URL（如 https://cloudcli.ai 或 http://192.168.1.100:3001）
     */
    suspend fun saveServerUrl(url: String) {
        context.authDataStore.edit { it[KEY_SERVER_URL] = normalizeUrl(url) }
    }

    /**
     * 清除登录态（登出）。
     * 移除 token 与用户信息，但保留服务器 URL 配置。
     */
    suspend fun clearAuth() {
        context.authDataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_USER_ID)
        }
    }

    /**
     * 清除全部数据（包括服务器配置）。
     * 通常在用户重置应用时调用。
     */
    suspend fun clearAll() {
        context.authDataStore.edit { it.clear() }
    }

    /**
     * 获取规整化的服务器 URL（去除尾部斜杠、补全协议）。
     * 与 [com.claudecode.mobile.data.ServerConfig.normalizedUrl] 保持一致逻辑。
     *
     * @param url 原始 URL
     * @return 规整后的 URL，空串原样返回
     */
    fun normalizeUrl(url: String): String {
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
}
