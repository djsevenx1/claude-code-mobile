package com.claudecode.mobile.ui.screens.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.dto.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

// ============================================================
// 会话列表页面 ViewModel
// 管理所有会话的加载、下拉刷新、删除与搜索过滤
// 参考 LoginViewModel / ProjectListViewModel 的模式：
//   AndroidViewModel + MutableStateFlow/StateFlow + viewModelScope.launch
// ============================================================

/**
 * 会话列表 UI 状态密封类
 *
 * 确保状态转换的完备性，便于 Compose 端按分支渲染不同 UI：
 * - Loading: 首次加载中，显示全屏加载指示器
 * - Success: 加载成功且列表非空，展示会话卡片列表
 * - Empty: 加载成功但无会话（或搜索无结果），展示空状态
 * - Error: 加载失败，展示错误信息与重试按钮
 */
sealed class SessionListUiState {
    /** 加载中状态 - 首次加载会话列表 */
    object Loading : SessionListUiState()

    /** 加载成功 - 已有会话数据 */
    data class Success(val sessions: List<Session>) : SessionListUiState()

    /** 空状态 - 已加载但无会话，或搜索无匹配结果 */
    object Empty : SessionListUiState()

    /** 出错状态 - 加载失败 */
    data class Error(val message: String) : SessionListUiState()
}

/**
 * 会话列表页面的完整 UI 状态数据
 *
 * 聚合所有页面所需的 UI 状态字段，通过单一 StateFlow 暴露给 Compose 端。
 *
 * @param uiState 列表加载状态 (Loading/Success/Empty/Error)
 * @param isRefreshing 是否正在下拉刷新
 * @param searchQuery 当前搜索关键词
 * @param isDeleting 是否正在删除会话
 */
data class SessionListScreenState(
    val uiState: SessionListUiState = SessionListUiState.Loading,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val isDeleting: Boolean = false
)

/**
 * 会话列表 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application 上下文。
 * 通过 [NetworkModule.createCloudApiFromConfig] 获取 CloudApi 实例，
 * 服务器 URL 与 token 由 TokenManager (DataStore) 自动管理。
 *
 * 职责：
 * 1. 加载所有会话列表 (首次进入 + 重试)
 * 2. 下拉刷新会话列表
 * 3. 按标题搜索过滤会话
 * 4. 删除指定会话
 */
class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    // UI 状态 StateFlow - 可观察的状态流，Compose 端通过 collectAsStateWithLifecycle 收集
    private val _uiState = MutableStateFlow(SessionListScreenState())
    val uiState: StateFlow<SessionListScreenState> = _uiState.asStateFlow()

    // 全量会话列表缓存（未经搜索过滤），用于在搜索关键词变化时重新过滤
    private var allSessions: List<Session> = emptyList()

    init {
        // 进入页面时自动加载会话列表
        loadSessions()
    }

    // ==================== 会话列表加载 ====================

    /**
     * 加载所有会话列表 (首次加载 / 重试)
     *
     * 与 Web 端保持一致：通过 GET /api/projects 获取所有项目，
     * 从每个项目的内嵌 sessions 字段中提取会话并合并为统一列表。
     * (GET /api/providers/sessions/running 仅返回当前活跃的 CLI 进程，不含历史会话)
     */
    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(uiState = SessionListUiState.Loading) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val projects = api.getProjects()
                val sessions = projects.flatMap { it.sessions ?: emptyList() }
                    .sortedByDescending { it.updatedAt ?: it.lastActiveAt ?: it.createdAt ?: "" }
                allSessions = sessions
                applySearchFilter()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uiState = SessionListUiState.Error(formatErrorMessage(e)))
                }
            }
        }
    }

    /**
     * 下拉刷新会话列表
     *
     * 与 [loadSessions] 的区别：不切换到 Loading 状态 (保留当前列表)，
     * 仅设置 isRefreshing 标志位，由 UI 层展示刷新指示器。
     */
    fun refreshSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址")

                val projects = api.getProjects()
                val sessions = projects.flatMap { it.sessions ?: emptyList() }
                    .sortedByDescending { it.updatedAt ?: it.lastActiveAt ?: it.createdAt ?: "" }
                allSessions = sessions
                _uiState.update { it.copy(isRefreshing = false) }
                applySearchFilter()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        uiState = SessionListUiState.Error(formatErrorMessage(e))
                    )
                }
            }
        }
    }

    // ==================== 搜索过滤 ====================

    /**
     * 更新搜索关键词并重新过滤列表
     *
     * @param query 新的搜索关键词
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applySearchFilter()
    }

    /**
     * 清空搜索关键词
     */
    fun clearSearchQuery() {
        _uiState.update { it.copy(searchQuery = "") }
        applySearchFilter()
    }

    /**
     * 根据当前搜索关键词过滤会话列表
     *
     * 过滤逻辑：按 title 字段进行大小写不敏感的包含匹配。
     * 若全量列表为空则显示 Empty 状态；
     * 若搜索关键词为空则显示全部会话；
     * 否则显示过滤后的结果（过滤后为空也显示 Empty 状态）。
     */
    private fun applySearchFilter() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val current = allSessions

        val uiState = when {
            // 全量列表为空
            current.isEmpty() -> SessionListUiState.Empty
            // 无搜索关键词，显示全部
            query.isBlank() -> SessionListUiState.Success(current)
            // 按标题或摘要过滤
            else -> {
                val filtered = current.filter { session ->
                    session.title?.contains(query, ignoreCase = true) == true ||
                    session.summary?.contains(query, ignoreCase = true) == true
                }
                if (filtered.isEmpty()) {
                    SessionListUiState.Empty
                } else {
                    SessionListUiState.Success(filtered)
                }
            }
        }

        _uiState.update { it.copy(uiState = uiState) }
    }

    // ==================== 删除会话 ====================

    /**
     * 删除指定会话
     *
     * 调用 API 删除 -> 成功后从本地列表移除并重新过滤 -> 失败时保留列表
     *
     * @param sessionId 待删除的会话标识
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.deleteSession(sessionId)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "删除会话失败")
                }

                // 从本地缓存中移除已删除的会话，并重新应用搜索过滤
                allSessions = allSessions.filter { it.id != sessionId }
                _uiState.update { it.copy(isDeleting = false) }
                applySearchFilter()
            } catch (e: Exception) {
                // 删除失败时保留当前列表，仅重置删除状态
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
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
                    403 -> "没有权限访问会话列表"
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

// ============================================================
// 辅助函数: 相对时间格式化
// ============================================================

/**
 * 将 ISO 8601 时间字符串格式化为中文相对时间
 *
 * 支持带时区和不带时区两种 ISO 格式，依次尝试 Instant / LocalDateTime / ZonedDateTime 解析。
 * 格式化结果示例："刚刚"、"3 分钟前"、"4 小时前"、"1 天前"、"2 个月前"、"1 年前"。
 *
 * @param isoString ISO 8601 格式的时间字符串 (可为 null)
 * @return 中文相对时间描述，解析失败或为空时返回 "未知"
 */
internal fun formatRelativeTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "未知"

    val instant = parseInstant(isoString) ?: return isoString
    val now = Instant.now()
    val duration = Duration.between(instant, now)
    val seconds = duration.seconds

    // 未来时间或不足 1 分钟，显示 "刚刚"
    if (seconds < 60) return "刚刚"

    val minutes = seconds / 60
    if (minutes < 60) return "$minutes 分钟前"

    val hours = minutes / 60
    if (hours < 24) return "$hours 小时前"

    val days = hours / 24
    if (days < 30) return "$days 天前"

    val months = days / 30
    if (months < 12) return "$months 个月前"

    val years = days / 365
    return "$years 年前"
}

/**
 * 尝试将 ISO 8601 时间字符串解析为 Instant
 *
 * 依次尝试三种格式：
 * 1. Instant (带时区的 ISO 8601，如 2024-01-01T12:00:00Z)
 * 2. LocalDateTime (不带时区，如 2024-01-01T12:00:00) -> 按系统时区转换为 Instant
 * 3. ZonedDateTime (带完整时区信息，如 2024-01-01T12:00:00+08:00)
 *
 * @param isoString ISO 8601 时间字符串
 * @return 解析成功返回 Instant，全部失败返回 null
 */
private fun parseInstant(isoString: String): Instant? {
    return try {
        Instant.parse(isoString)
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(isoString).atZone(ZoneId.systemDefault()).toInstant()
        } catch (e2: DateTimeParseException) {
            try {
                ZonedDateTime.parse(isoString).toInstant()
            } catch (e3: DateTimeParseException) {
                null
            }
        }
    }
}
