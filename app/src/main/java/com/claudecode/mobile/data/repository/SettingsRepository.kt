package com.claudecode.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 主题模式枚举
 *
 * - [SYSTEM]：跟随系统
 * - [LIGHT]：始终亮色
 * - [DARK]：始终暗色
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromValue(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** 应用级别的 DataStore 扩展属性，保证全局唯一实例 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

/**
 * 设置仓库
 *
 * 基于 Jetpack DataStore（Preferences）管理用户偏好设置，
 * 所有读写操作均为异步（suspend / Flow），保证线程安全。
 *
 * CloudCLI (claudecodeui) 的 Web 端将用户偏好存储在 localStorage，
 * 本应用同样采用纯本地存储策略，不依赖服务端 API。
 *
 * 管理的偏好项：
 * - 主题模式（跟随系统 / 亮色 / 暗色）
 * - 默认模型
 * - 自动批准工具
 * - 流式输出
 * - 显示 Token 用量
 * - 发送消息时自动滚动到底部
 */
class SettingsRepository(private val context: Context) {

    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
    private val KEY_AUTO_APPROVE_TOOLS = booleanPreferencesKey("auto_approve_tools")
    private val KEY_STREAMING_OUTPUT = booleanPreferencesKey("streaming_output")
    private val KEY_SHOW_TOKEN_USAGE = booleanPreferencesKey("show_token_usage")
    private val KEY_AUTO_SCROLL = booleanPreferencesKey("auto_scroll_on_send")

    private val DEFAULT_MODEL = "claude-sonnet-4-5"
    private val DEFAULT_AUTO_SCROLL = true
    private val DEFAULT_AUTO_APPROVE = false
    private val DEFAULT_STREAMING = true
    private val DEFAULT_SHOW_TOKEN = true

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data
        .map { prefs -> ThemeMode.fromValue(prefs[KEY_THEME_MODE]) }

    val defaultModelFlow: Flow<String> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_DEFAULT_MODEL] ?: DEFAULT_MODEL }

    val autoApproveToolsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_AUTO_APPROVE_TOOLS] ?: DEFAULT_AUTO_APPROVE }

    val streamingOutputFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_STREAMING_OUTPUT] ?: DEFAULT_STREAMING }

    val showTokenUsageFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_SHOW_TOKEN_USAGE] ?: DEFAULT_SHOW_TOKEN }

    val autoScrollOnSendFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_AUTO_SCROLL] ?: DEFAULT_AUTO_SCROLL }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDefaultModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_DEFAULT_MODEL] = model }
    }

    suspend fun setAutoApproveTools(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AUTO_APPROVE_TOOLS] = enabled }
    }

    suspend fun setStreamingOutput(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_STREAMING_OUTPUT] = enabled }
    }

    suspend fun setShowTokenUsage(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SHOW_TOKEN_USAGE] = enabled }
    }

    suspend fun setAutoScrollOnSend(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AUTO_SCROLL] = enabled }
    }
}
