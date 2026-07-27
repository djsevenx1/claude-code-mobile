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
        /**
         * 将存储中的字符串值转换为 [ThemeMode]。
         * 无法识别的值默认回退为 [SYSTEM]。
         */
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
 * 当前管理的偏好项：
 * - 主题模式（跟随系统 / 亮色 / 暗色）
 * - 默认模型
 * - 发送消息时是否自动滚动到底部
 *
 * @param context 应用上下文（用于获取 DataStore 实例）
 */
class SettingsRepository(private val context: Context) {

    /** 主题模式偏好键 */
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

    /** 默认模型偏好键 */
    private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")

    /** 自动滚动偏好键 */
    private val KEY_AUTO_SCROLL = booleanPreferencesKey("auto_scroll_on_send")

    /** 默认模型缺省值 */
    private val DEFAULT_MODEL = "claude-sonnet-4-5"

    /** 自动滚动缺省值 */
    private val DEFAULT_AUTO_SCROLL = true

    /**
     * 观察当前主题模式。
     */
    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data
        .map { prefs -> ThemeMode.fromValue(prefs[KEY_THEME_MODE]) }

    /**
     * 观察当前默认模型。
     */
    val defaultModelFlow: Flow<String> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_DEFAULT_MODEL] ?: DEFAULT_MODEL }

    /**
     * 观察发送消息时是否自动滚动到底部。
     */
    val autoScrollOnSendFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[KEY_AUTO_SCROLL] ?: DEFAULT_AUTO_SCROLL }

    /**
     * 设置主题模式。
     *
     * @param mode 目标主题模式
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    /**
     * 设置默认模型。
     *
     * @param model 模型标识，例如 "claude-sonnet-4-5"
     */
    suspend fun setDefaultModel(model: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_MODEL] = model
        }
    }

    /**
     * 设置发送消息时是否自动滚动到底部。
     *
     * @param enabled 是否启用自动滚动
     */
    suspend fun setAutoScrollOnSend(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_SCROLL] = enabled
        }
    }
}
