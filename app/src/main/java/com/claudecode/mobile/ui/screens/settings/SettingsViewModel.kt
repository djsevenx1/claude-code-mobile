package com.claudecode.mobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.data.repository.SettingsRepository
import com.claudecode.mobile.data.repository.ThemeMode
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.TokenManager
import com.claudecode.mobile.network.dto.ModelInfo
import com.claudecode.mobile.network.dto.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ============================================================
// 设置页面 ViewModel
// 管理用户设置：服务器信息、主题、默认模型、AI 行为开关、登出
// 参考 LoginViewModel 的模式：AndroidViewModel + StateFlow + viewModelScope
// ============================================================

/**
 * 设置页面的完整 UI 状态数据
 *
 * 聚合所有设置项的当前值与加载/保存状态，通过单一 StateFlow 暴露给 Compose 端。
 *
 * @param serverUrl 服务器地址（从 TokenManager 读取，只读展示）
 * @param username 当前登录用户名（从 TokenManager 读取，只读展示）
 * @param settings 从服务端加载的用户设置 (UserSettings)，null 表示尚未加载
 * @param themeMode 当前主题模式 (本地持久化，通过 SettingsRepository 管理)
 * @param defaultModel 当前默认模型标识 (本地持久化，通过 SettingsRepository 管理)
 * @param availableModels 可用模型列表 (从 API getModels 获取)
 * @param isLoading 是否正在加载设置
 * @param isSaving 是否正在保存设置到服务端
 * @param error 错误信息 (null 表示无错误)
 * @param isLoggedOut 是否已登出 (true 时 Screen 端导航到登录页)
 */
data class SettingsScreenState(
    val serverUrl: String = "",
    val username: String = "",
    val settings: UserSettings? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultModel: String = "",
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

/**
 * 设置 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application 上下文。
 *
 * 数据来源：
 * - 服务器信息 (URL、用户名)：[TokenManager] (DataStore)
 * - 用户设置 (autoApproveTools 等)：服务端 API (getSettings / updateSettings)
 * - 主题模式与默认模型：[SettingsRepository] (本地 DataStore，离线可用)
 * - 可用模型列表：服务端 API (getModels)
 *
 * 职责：
 * 1. 加载服务器信息、用户设置、本地偏好与可用模型
 * 2. 切换主题模式 (本地持久化)
 * 3. 选择默认模型 (本地持久化)
 * 4. 切换自动批准工具 / 流式输出 / 显示 Token 用量 (立即同步到服务端)
 * 5. 登出 (清除 TokenManager 中的登录态)
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // Token 管理器 (来自 network 层，基于 DataStore 持久化)
    private val tokenManager = TokenManager(application)

    // 设置仓库 (本地偏好持久化，基于 DataStore)
    private val settingsRepository = SettingsRepository(application)

    // UI 状态 StateFlow - 可观察的状态流
    private val _uiState = MutableStateFlow(SettingsScreenState())
    val uiState: StateFlow<SettingsScreenState> = _uiState.asStateFlow()

    init {
        // 进入页面时自动加载设置
        loadSettings()
    }

    // ==================== 加载设置 ====================

    /**
     * 加载所有设置数据
     *
     * 加载顺序：
     * 1. 本地数据 (服务器信息、主题、默认模型) - 立即可用，无需网络
     * 2. 服务端用户设置 (getSettings) - 需要网络
     * 3. 可用模型列表 (getModels) - 需要网络，失败不影响整体加载
     */
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // --- 1. 加载本地数据 ---
                val serverUrl = tokenManager.getServerUrl() ?: ""
                val username = tokenManager.getUsername() ?: ""
                val themeMode = settingsRepository.themeModeFlow.first()
                val defaultModel = settingsRepository.defaultModelFlow.first()

                _uiState.update {
                    it.copy(
                        serverUrl = serverUrl,
                        username = username,
                        themeMode = themeMode,
                        defaultModel = defaultModel
                    )
                }

                // --- 2. 加载服务端用户设置 ---
                val api = NetworkModule.createCloudApiFromConfig()
                if (api == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "未配置服务器地址，请先登录"
                        )
                    }
                    return@launch
                }

                val settings = api.getSettings()
                _uiState.update {
                    it.copy(
                        settings = settings,
                        isLoading = false
                    )
                }

                // --- 3. 加载可用模型列表 (失败不影响整体加载) ---
                try {
                    val models = api.getModels("claude")
                    _uiState.update { it.copy(availableModels = models) }
                } catch (e: Exception) {
                    // 模型列表加载失败属于非致命错误，静默忽略
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 主题模式 (本地持久化) ====================

    /**
     * 切换主题模式
     *
     * 通过 SettingsRepository 持久化到本地 DataStore，
     * 立即更新 UI 状态。
     *
     * @param mode 目标主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    // ==================== 默认模型 (本地持久化) ====================

    /**
     * 设置默认模型
     *
     * 通过 SettingsRepository 持久化到本地 DataStore，
     * 立即更新 UI 状态。
     *
     * @param model 模型标识 (如 "claude-sonnet-4-5")
     */
    fun setDefaultModel(model: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultModel(model)
            _uiState.update { it.copy(defaultModel = model) }
        }
    }

    // ==================== AI 行为开关 (同步到服务端) ====================

    /**
     * 切换"自动批准工具"开关
     *
     * @param enabled 是否启用
     */
    fun toggleAutoApproveTools(enabled: Boolean) {
        val current = _uiState.value.settings ?: return
        val updated = current.copy(autoApproveTools = enabled)
        _uiState.update { it.copy(settings = updated) }
        saveSettingsToServer(updated)
    }

    /**
     * 切换"流式输出"开关
     *
     * @param enabled 是否启用
     */
    fun toggleStreamingOutput(enabled: Boolean) {
        val current = _uiState.value.settings ?: return
        val updated = current.copy(streamingOutput = enabled)
        _uiState.update { it.copy(settings = updated) }
        saveSettingsToServer(updated)
    }

    /**
     * 切换"显示 Token 用量"开关
     *
     * @param enabled 是否启用
     */
    fun toggleShowTokenUsage(enabled: Boolean) {
        val current = _uiState.value.settings ?: return
        val updated = current.copy(showTokenUsage = enabled)
        _uiState.update { it.copy(settings = updated) }
        saveSettingsToServer(updated)
    }

    /**
     * 保存设置到服务端
     *
     * 调用 updateSettings API 同步完整设置对象。
     * 失败时设置 error 字段，由 UI 层展示。
     *
     * @param settings 待保存的完整用户设置
     */
    private fun saveSettingsToServer(settings: UserSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.updateSettings(settings)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "保存设置失败")
                }

                // 如果服务端返回了更新后的设置，同步到本地状态
                val savedSettings = response.settings ?: settings
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        settings = savedSettings,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 登出 ====================

    /**
     * 退出登录
     *
     * 调用 TokenManager.clearAuth() 清除 token 与用户信息，
     * 设置 isLoggedOut = true 触发 Screen 端导航到登录页。
     * 注意：保留服务器 URL 配置，方便下次登录。
     */
    fun logout() {
        viewModelScope.launch {
            tokenManager.clearAuth()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    // ==================== 错误处理 ====================

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化异常错误信息
     *
     * 根据异常类型生成用户友好的中文错误提示，
     * 与 LoginViewModel / ProjectListViewModel 中的错误处理逻辑保持一致。
     *
     * @param e 捕获的异常
     * @return 中文错误描述
     */
    private fun formatErrorMessage(e: Exception): String {
        return when {
            // HTTP 异常 (服务器返回非 2xx 状态码)
            e is HttpException -> {
                when (e.code()) {
                    401 -> "登录已过期，请重新登录"
                    403 -> "没有权限访问设置"
                    404 -> "服务器接口不存在，请检查服务器地址"
                    in 500..599 -> "服务器内部错误 (${e.code()})，请稍后重试"
                    else -> "请求失败 (${e.code()})"
                }
            }
            // 网络连接异常
            e is java.net.UnknownHostException -> "无法连接到服务器，请检查网络"
            e is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
            e is java.net.ConnectException -> "连接被拒绝，请确认服务器是否正在运行"
            e is javax.net.ssl.SSLException -> "SSL 证书验证失败"
            // 已格式化的业务错误 (包含中文描述的 RuntimeException)
            e.message?.contains(Regex("[\\u4e00-\\u9fa5]")) == true -> e.message!!
            // 其他异常
            else -> "操作失败: ${e.message ?: "未知错误"}"
        }
    }

    // --- 说明 ---
    // 本 ViewModel 继承 AndroidViewModel，在 Compose 中使用 viewModel() 时，
    // 系统会通过 SavedStateViewModelFactory 自动注入 Application 上下文，
    // 因此无需手动定义 ViewModelProvider.Factory。
}
