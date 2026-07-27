package com.claudecode.mobile.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.data.AuthApiFactory
import com.claudecode.mobile.data.db.CloudDatabase
import com.claudecode.mobile.data.entity.ServerConfig
import com.claudecode.mobile.network.CloudApi
import com.claudecode.mobile.network.TokenManager
import com.claudecode.mobile.network.dto.LoginRequest
import com.claudecode.mobile.network.dto.LoginResponse
import com.claudecode.mobile.network.dto.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ============================================================
// 登录页面 ViewModel
// 管理登录/注册状态，调用后端 API，持久化 Token
// 使用项目已有的 network 层 (TokenManager, CloudApi) 和 data 层 (Room)
// ============================================================

/**
 * 登录 UI 状态密封类 - 确保状态转换的完备性
 */
sealed class LoginUiState {
    /** 空闲状态 - 初始状态 */
    object Idle : LoginUiState()

    /** 加载中状态 - 正在请求服务器 */
    data class Loading(val message: String = "正在处理...") : LoginUiState()

    /** 登录/注册成功 */
    object Success : LoginUiState()

    /** 出错状态 */
    data class Error(val message: String) : LoginUiState()
}

/**
 * 认证模式 - 登录或注册
 */
enum class AuthMode(val displayName: String) {
    LOGIN("登录"),
    REGISTER("注册")
}

/**
 * 登录页面的完整 UI 状态数据
 */
data class LoginScreenState(
    val uiState: LoginUiState = LoginUiState.Idle,
    val authMode: AuthMode = AuthMode.LOGIN,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val trustAllCerts: Boolean = false,
    val showServerField: Boolean = false,  // 是否显示服务器地址输入框
    val passwordVisible: Boolean = false,   // 密码是否可见
    val serverHealthOk: Boolean? = null,    // 服务器健康状态: null=未检查, true=正常, false=异常
    val isCheckingHealth: Boolean = false   // 是否正在检查服务器健康状态
)

/**
 * 登录 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application Context
 * 使用 network.TokenManager 管理 Token，使用 Room 数据库管理服务器配置
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    // Token 管理器 (来自 network 层，基于 DataStore 持久化)
    private val tokenManager = TokenManager(application)

    // Room 数据库 DAO (用于服务器配置的持久化，包含 trustAllCerts 设置)
    private val serverDao = CloudDatabase.getInstance(application).serverDao()

    // UI 状态 StateFlow - 可观察的状态流
    private val _uiState = MutableStateFlow(LoginScreenState())
    val uiState: StateFlow<LoginScreenState> = _uiState.asStateFlow()

    init {
        // 初始化时加载已保存的服务器配置和用户名
        loadSavedData()
    }

    /**
     * 加载已保存的数据 (服务器地址、用户名、证书信任设置)
     * 优先从 TokenManager 读取服务器地址和用户名，
     * 若无则回退到 Room 数据库中的默认服务器配置
     */
    private fun loadSavedData() {
        viewModelScope.launch {
            // 从 TokenManager 加载已保存的服务器地址和用户名
            val savedServerUrl = tokenManager.getServerUrl()
            val savedUsername = tokenManager.getUsername()

            // 从 Room 数据库加载默认服务器配置 (包含 trustAllCerts)
            val defaultServer = serverDao.getDefault()
            val savedTrustCerts = defaultServer?.trustAllCerts ?: false

            // 确定有效服务器地址: 优先 TokenManager，其次 Room 默认服务器
            val effectiveServerUrl = savedServerUrl ?: defaultServer?.url ?: ""

            _uiState.update { current ->
                current.copy(
                    serverUrl = effectiveServerUrl,
                    username = savedUsername ?: "",
                    trustAllCerts = savedTrustCerts,
                    // 如果没有已保存的服务器地址且没有默认服务器，则显示服务器输入框
                    showServerField = effectiveServerUrl.isBlank()
                )
            }

            // 如果有服务器地址，自动检查健康状态
            if (effectiveServerUrl.isNotBlank()) {
                checkServerHealth()
            }
        }
    }

    // --- 状态更新方法 ---

    /**
     * 更新服务器地址
     */
    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url, serverHealthOk = null) }
    }

    /**
     * 更新用户名
     */
    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    /**
     * 更新确认密码
     */
    fun updateConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password) }
    }

    /**
     * 切换证书信任设置
     */
    fun toggleTrustAllCerts() {
        _uiState.update { it.copy(trustAllCerts = !it.trustAllCerts) }
    }

    /**
     * 切换密码可见性
     */
    fun togglePasswordVisible() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    /**
     * 切换认证模式 (登录 <-> 注册)
     */
    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                authMode = if (it.authMode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
                // 切换模式时清除错误状态和确认密码
                uiState = LoginUiState.Idle,
                confirmPassword = ""
            )
        }
    }

    // --- 服务器健康检查 ---

    /**
     * 检查服务器健康状态
     * 向服务器发送健康检查请求 (GET /health)，验证服务器是否可达
     */
    fun checkServerHealth() {
        val currentUrl = _uiState.value.serverUrl.trim()
        if (currentUrl.isBlank()) {
            _uiState.update { it.copy(serverHealthOk = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingHealth = true) }

            try {
                // 创建 API 实例 (使用当前服务器地址和证书设置)
                val trustAll = _uiState.value.trustAllCerts
                val api = AuthApiFactory.create(currentUrl, trustAll)

                // 调用健康检查接口 (GET /health)
                // CloudApi.healthCheck() 返回 HealthStatus，无异常则表示服务器可达
                api.healthCheck()

                _uiState.update {
                    it.copy(
                        isCheckingHealth = false,
                        serverHealthOk = true
                    )
                }
            } catch (e: Exception) {
                // 健康检查失败 (网络异常、连接超时、SSL 错误等)
                _uiState.update {
                    it.copy(
                        isCheckingHealth = false,
                        serverHealthOk = false
                    )
                }
            }
        }
    }

    // --- 登录/注册核心逻辑 ---

    /**
     * 执行登录或注册操作
     * 根据当前 authMode 自动选择登录或注册流程
     */
    fun authenticate() {
        val state = _uiState.value

        // --- 输入验证 ---
        val validationError = validateInput(state)
        if (validationError != null) {
            _uiState.update { it.copy(uiState = LoginUiState.Error(validationError)) }
            return
        }

        val serverUrl = state.serverUrl.trim()
        val trustAll = state.trustAllCerts

        viewModelScope.launch {
            // 设置加载状态
            _uiState.update {
                it.copy(
                    uiState = LoginUiState.Loading(
                        if (state.authMode == AuthMode.LOGIN) "正在登录..." else "正在注册..."
                    )
                )
            }

            try {
                // 创建 API 实例 (使用 AuthApiFactory 支持信任所有证书)
                val api = AuthApiFactory.create(serverUrl, trustAll)

                // 根据模式执行登录或注册
                val response = when (state.authMode) {
                    AuthMode.LOGIN -> performLogin(api, state.username.trim(), state.password)
                    AuthMode.REGISTER -> performRegister(api, state.username.trim(), state.password)
                }

                // --- 保存认证信息 ---

                // 1. 保存 Token、用户名到 TokenManager (DataStore)
                val userId = response.user?.id
                tokenManager.saveAuthInfo(
                    token = response.token!!,
                    username = state.username.trim(),
                    userId = userId
                )

                // 2. 保存服务器地址到 TokenManager (供后续 API 调用使用)
                tokenManager.saveServerUrl(serverUrl)

                // 3. 保存服务器配置到 Room 数据库 (包含 trustAllCerts)
                saveServerConfig(serverUrl, trustAll, state.username.trim(), response.token!!)

                // 设置成功状态
                _uiState.update { it.copy(uiState = LoginUiState.Success) }

            } catch (e: Exception) {
                // 处理异常并显示错误信息
                val errorMessage = formatErrorMessage(e)
                _uiState.update { it.copy(uiState = LoginUiState.Error(errorMessage)) }
            }
        }
    }

    /**
     * 执行登录请求
     * CloudApi.login() 返回 LoginResponse (成功时包含 token 和用户信息)
     *
     * @return 登录响应
     */
    private suspend fun performLogin(api: CloudApi, username: String, password: String): LoginResponse {
        val request = LoginRequest(username = username, password = password)
        val response = api.login(request)

        // 检查业务层面的成功状态
        if (!response.success) {
            val errorMsg = response.error ?: "登录失败，请检查用户名和密码"
            throw RuntimeException(errorMsg)
        }

        // 验证 Token 不为空
        if (response.token.isNullOrBlank()) {
            throw RuntimeException("服务器返回的 Token 为空")
        }

        return response
    }

    /**
     * 执行注册请求
     * CloudApi.register() 返回 LoginResponse (注册成功后自动登录)
     * 注意: RegisterRequest 仅包含 username 和 password (confirmPassword 仅客户端验证)
     *
     * @return 注册响应 (与登录响应结构相同)
     */
    private suspend fun performRegister(api: CloudApi, username: String, password: String): LoginResponse {
        val request = RegisterRequest(username = username, password = password)
        val response = api.register(request)

        // 检查业务层面的成功状态
        if (!response.success) {
            val errorMsg = response.error ?: "注册失败"
            throw RuntimeException(errorMsg)
        }

        // 验证 Token 不为空
        if (response.token.isNullOrBlank()) {
            throw RuntimeException("注册成功，但服务器未返回 Token")
        }

        return response
    }

    /**
     * 保存服务器配置到 Room 数据库
     * 如果已存在同 URL 的服务器则更新，否则插入新记录
     */
    private suspend fun saveServerConfig(
        url: String,
        trustAllCerts: Boolean,
        username: String,
        token: String
    ) {
        // 查找是否已存在同 URL 的服务器配置
        // 由于 getAll() 返回 Flow，需要用 first() 获取当前快照值
        val existingList = serverDao.getAll().first()
        val existing = existingList.find { it.url == url }

        if (existing != null) {
            // 更新已有记录
            serverDao.update(
                existing.copy(
                    trustAllCerts = trustAllCerts,
                    username = username,
                    token = token,
                    isDefault = true
                )
            )
            serverDao.setDefault(existing.id)
        } else {
            // 插入新记录
            val newId = serverDao.insert(
                ServerConfig(
                    name = "CloudCLI Server",
                    url = url,
                    isDefault = true,
                    trustAllCerts = trustAllCerts,
                    username = username,
                    token = token
                )
            )
            serverDao.setDefault(newId)
        }
    }

    // --- 辅助方法 ---

    /**
     * 输入验证 - 检查必填字段和格式
     * @return 错误信息字符串，验证通过返回 null
     */
    private fun validateInput(state: LoginScreenState): String? {
        // 服务器地址验证
        if (state.showServerField && state.serverUrl.isBlank()) {
            return "请输入服务器地址"
        }

        // 用户名验证
        if (state.username.isBlank()) {
            return "请输入用户名"
        }
        if (state.username.length < 2) {
            return "用户名至少需要 2 个字符"
        }

        // 密码验证
        if (state.password.isBlank()) {
            return "请输入密码"
        }
        if (state.password.length < 6) {
            return "密码至少需要 6 个字符"
        }

        // 注册模式额外验证确认密码 (仅客户端验证)
        if (state.authMode == AuthMode.REGISTER) {
            if (state.confirmPassword.isBlank()) {
                return "请再次输入密码"
            }
            if (state.password != state.confirmPassword) {
                return "两次输入的密码不一致"
            }
        }

        return null
    }

    /**
     * 格式化异常错误信息
     * 根据异常类型生成用户友好的中文错误提示
     */
    private fun formatErrorMessage(e: Exception): String {
        return when {
            // HTTP 异常 (服务器返回非 2xx 状态码)
            e is HttpException -> {
                when (e.code()) {
                    401 -> "用户名或密码错误"
                    403 -> "账号已被禁用，请联系管理员"
                    404 -> "服务器接口不存在，请检查服务器地址"
                    409 -> "用户名已存在，请更换用户名"
                    400 -> "请求参数不合法，请检查输入"
                    in 500..599 -> "服务器内部错误 (${e.code()})，请稍后重试"
                    else -> "请求失败 (${e.code()})"
                }
            }
            // 网络连接异常
            e is java.net.UnknownHostException -> "无法连接到服务器，请检查网络或服务器地址"
            e is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
            e is java.net.ConnectException -> "连接被拒绝，请确认服务器是否正在运行"
            e is javax.net.ssl.SSLException -> "SSL 证书验证失败，可尝试开启「信任所有证书」选项"
            // 已格式化的业务错误 (包含中文描述的 RuntimeException)
            e.message?.contains(Regex("[\\u4e00-\\u9fa5]")) == true -> e.message!!
            // 其他异常
            else -> "操作失败: ${e.message ?: "未知错误"}"
        }
    }

    /**
     * 清除错误状态 (回到空闲)
     */
    fun clearError() {
        if (_uiState.value.uiState is LoginUiState.Error) {
            _uiState.update { it.copy(uiState = LoginUiState.Idle) }
        }
    }

    /**
     * 切换服务器输入框显示状态
     */
    fun toggleServerField() {
        _uiState.update { it.copy(showServerField = !it.showServerField) }
    }

    // --- 说明 ---
    // 本 ViewModel 继承 AndroidViewModel，在 Compose 中使用 viewModel() 时，
    // 系统会通过 SavedStateViewModelFactory 自动注入 Application 上下文，
    // 因此无需手动定义 ViewModelProvider.Factory。
}
