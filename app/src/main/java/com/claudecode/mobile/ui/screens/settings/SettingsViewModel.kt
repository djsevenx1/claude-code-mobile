package com.claudecode.mobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.data.repository.SettingsRepository
import com.claudecode.mobile.data.repository.ThemeMode
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.TokenManager
import com.claudecode.mobile.network.dto.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 设置页面的完整 UI 状态数据
 *
 * 所有用户偏好均存储在本地 DataStore，不依赖服务端 API。
 * 仅"可用模型列表"从服务端获取。
 */
data class SettingsScreenState(
    val serverUrl: String = "",
    val username: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultModel: String = "",
    val autoApproveTools: Boolean = false,
    val streamingOutput: Boolean = true,
    val showTokenUsage: Boolean = true,
    val availableModels: List<ModelInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

/**
 * 设置 ViewModel
 *
 * 所有设置项使用本地 DataStore 持久化，与 Web 端 localStorage 策略一致。
 * 仅模型列表从服务端 API 获取，失败不影响其他设置。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsScreenState())
    val uiState: StateFlow<SettingsScreenState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载所有设置数据
     *
     * 1. 本地偏好 (服务器信息、主题、模型、开关) - 立即可用
     * 2. 可用模型列表 (API) - 失败不阻塞
     */
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // --- 加载本地数据 ---
                val serverUrl = tokenManager.getServerUrl() ?: ""
                val username = tokenManager.getUsername() ?: ""
                val themeMode = settingsRepository.themeModeFlow.first()
                val defaultModel = settingsRepository.defaultModelFlow.first()
                val autoApprove = settingsRepository.autoApproveToolsFlow.first()
                val streaming = settingsRepository.streamingOutputFlow.first()
                val showToken = settingsRepository.showTokenUsageFlow.first()

                _uiState.update {
                    it.copy(
                        serverUrl = serverUrl,
                        username = username,
                        themeMode = themeMode,
                        defaultModel = defaultModel,
                        autoApproveTools = autoApprove,
                        streamingOutput = streaming,
                        showTokenUsage = showToken,
                        isLoading = false
                    )
                }

                // --- 加载可用模型列表 (失败不影响整体) ---
                try {
                    val api = NetworkModule.createCloudApiFromConfig()
                    if (api != null) {
                        val models = api.getModels("claude")
                        _uiState.update { it.copy(availableModels = models) }
                    }
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

    // ==================== 主题模式 ====================

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    // ==================== 默认模型 ====================

    fun setDefaultModel(model: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultModel(model)
            _uiState.update { it.copy(defaultModel = model) }
        }
    }

    // ==================== AI 行为开关 (纯本地) ====================

    fun toggleAutoApproveTools(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoApproveTools(enabled)
            _uiState.update { it.copy(autoApproveTools = enabled) }
        }
    }

    fun toggleStreamingOutput(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStreamingOutput(enabled)
            _uiState.update { it.copy(streamingOutput = enabled) }
        }
    }

    fun toggleShowTokenUsage(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTokenUsage(enabled)
            _uiState.update { it.copy(showTokenUsage = enabled) }
        }
    }

    // ==================== 登出 ====================

    fun logout() {
        viewModelScope.launch {
            // 尝试调用服务端登出（失败不影响本地清理）
            try {
                val api = NetworkModule.createCloudApiFromConfig()
                api?.logout()
            } catch (e: Exception) {
                // 忽略服务端登出错误
            }
            // 清除本地认证信息
            tokenManager.clearAuth()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    // ==================== 错误处理 ====================

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatErrorMessage(e: Exception): String {
        return when {
            e is HttpException -> {
                when (e.code()) {
                    401 -> "登录已过期，请重新登录"
                    403 -> "没有权限"
                    404 -> "服务器接口不存在，请检查服务器地址"
                    in 500..599 -> "服务器内部错误 (${e.code()})，请稍后重试"
                    else -> "请求失败 (${e.code()})"
                }
            }
            e is java.net.UnknownHostException -> "无法连接到服务器，请检查网络"
            e is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
            e is java.net.ConnectException -> "连接被拒绝，请确认服务器是否正在运行"
            e is javax.net.ssl.SSLException -> "SSL 证书验证失败"
            e.message?.contains(Regex("[\\u4e00-\\u9fa5]")) == true -> e.message!!
            else -> "操作失败: ${e.message ?: "未知错误"}"
        }
    }
}
