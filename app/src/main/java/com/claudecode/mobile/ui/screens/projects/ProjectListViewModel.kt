package com.claudecode.mobile.ui.screens.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.dto.CreateProjectRequest
import com.claudecode.mobile.network.dto.Project
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ============================================================
// 项目列表页面 ViewModel
// 管理项目列表的加载、刷新、创建，以及点击项目后的会话加载与导航
// 参考 LoginViewModel 的模式：AndroidViewModel + StateFlow + viewModelScope
// ============================================================

/**
 * 项目列表 UI 状态密封类
 *
 * 确保状态转换的完备性，便于 Compose 端按分支渲染不同 UI：
 * - Loading: 首次加载中，显示全屏加载指示器
 * - Success: 加载成功且列表非空，展示项目卡片列表
 * - Empty: 加载成功但无项目，展示空状态引导
 * - Error: 加载失败，展示错误信息与重试按钮
 */
sealed class ProjectListUiState {
    /** 加载中状态 - 首次加载项目列表 */
    object Loading : ProjectListUiState()

    /** 加载成功 - 已有项目数据 */
    data class Success(val projects: List<Project>) : ProjectListUiState()

    /** 空状态 - 已加载但无项目 */
    object Empty : ProjectListUiState()

    /** 出错状态 - 加载失败 */
    data class Error(val message: String) : ProjectListUiState()
}

/**
 * 聊天页面导航事件
 *
 * 点击项目卡片后，ViewModel 先加载该项目下的会话列表，
 * 再携带 projectId 与 sessionId 触发一次性导航事件。
 * 使用 Channel 承载，避免 StateFlow 重放导致重复导航。
 *
 * @param projectId 项目标识
 * @param sessionId 会话标识 (为空表示创建新会话)
 */
data class ChatNavigation(
    val projectId: String,
    val sessionId: String
)

/**
 * 项目列表页面的完整 UI 状态数据
 *
 * 聚合所有页面所需的 UI 状态字段，通过单一 StateFlow 暴露给 Compose 端。
 *
 * @param uiState 列表加载状态 (Loading/Success/Empty/Error)
 * @param isRefreshing 是否正在下拉刷新
 * @param isCreating 是否正在创建项目
 * @param createError 创建项目时的错误信息 (null 表示无错误)
 * @param showCreateDialog 是否展示新建项目对话框
 * @param newProjectName 新建项目 - 项目名称
 * @param newProjectPath 新建项目 - 项目路径 (可选)
 * @param newProjectDisplayName 新建项目 - 展示名称 (可选)
 * @param isLoadingSessions 是否正在加载会话列表 (点击项目后)
 * @param searchQuery 当前搜索关键字 (用于实时过滤项目列表)
 */
data class ProjectListScreenState(
    val uiState: ProjectListUiState = ProjectListUiState.Loading,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val createError: String? = null,
    val showCreateDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectPath: String = "",
    val newProjectDisplayName: String = "",
    val isLoadingSessions: Boolean = false,
    val searchQuery: String = ""
)

/**
 * 项目列表 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application 上下文。
 * 通过 [NetworkModule.createCloudApiFromConfig] 获取 CloudApi 实例，
 * 服务器 URL 与 token 由 TokenManager (DataStore) 自动管理。
 *
 * 职责：
 * 1. 加载项目列表 (首次进入 + 重试)
 * 2. 下拉刷新项目列表
 * 3. 点击项目后加载会话列表并触发导航
 * 4. 创建新项目
 */
class ProjectListViewModel(application: Application) : AndroidViewModel(application) {

    // UI 状态 StateFlow - 可观察的状态流，Compose 端通过 collectAsStateWithLifecycle 收集
    private val _uiState = MutableStateFlow(ProjectListScreenState())
    val uiState: StateFlow<ProjectListScreenState> = _uiState.asStateFlow()

    // 导航事件 Channel - 用于一次性事件，避免 StateFlow 重放导致重复导航
    private val _navigationEvent = Channel<ChatNavigation>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        // 进入页面时自动加载项目列表
        loadProjects()
    }

    // ==================== 项目列表加载 ====================

    /**
     * 加载项目列表 (首次加载 / 重试)
     *
     * 设置 Loading 状态 -> 调用 API -> 更新为 Success / Empty / Error
     */
    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(uiState = ProjectListUiState.Loading) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val projects = api.getProjects()

                if (projects.isEmpty()) {
                    _uiState.update { it.copy(uiState = ProjectListUiState.Empty) }
                } else {
                    _uiState.update {
                        it.copy(uiState = ProjectListUiState.Success(projects))
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uiState = ProjectListUiState.Error(formatErrorMessage(e)))
                }
            }
        }
    }

    /**
     * 下拉刷新项目列表
     *
     * 与 [loadProjects] 的区别：不切换到 Loading 状态 (保留当前列表)，
     * 仅设置 isRefreshing 标志位，由 PullToRefreshBox 展示刷新指示器。
     */
    fun refreshProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址")

                val projects = api.getProjects()

                if (projects.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            uiState = ProjectListUiState.Empty,
                            isRefreshing = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            uiState = ProjectListUiState.Success(projects),
                            isRefreshing = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        uiState = ProjectListUiState.Error(formatErrorMessage(e))
                    )
                }
            }
        }
    }

    // ==================== 项目点击与会话加载 ====================

    /**
     * 点击项目卡片
     *
     * 先加载该项目下的会话列表，选出最新会话 (按更新时间降序)，
     * 然后通过 [navigationEvent] 发出导航事件。
     * 若会话列表为空或加载失败，仍导航到聊天页 (sessionId 为空)，
     * 由聊天页负责创建新会话。
     *
     * @param project 被点击的项目
     */
    fun onProjectClicked(project: Project) {
        val projectId = project.id
        if (projectId.isNullOrBlank()) {
            // 项目 ID 为空，无法导航
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                if (api != null) {
                    val sessions = api.getProjectSessions(projectId)
                    // 选择最新的会话 (按 updatedAt > lastActiveAt > createdAt 降序)
                    val latestSession = sessions.maxByOrNull { session ->
                        session.updatedAt
                            ?: session.lastActiveAt
                            ?: session.createdAt
                            ?: ""
                    }
                    _uiState.update { it.copy(isLoadingSessions = false) }
                    _navigationEvent.send(
                        ChatNavigation(projectId, latestSession?.id ?: "")
                    )
                } else {
                    // 未配置服务器，仍导航 (聊天页可处理)
                    _uiState.update { it.copy(isLoadingSessions = false) }
                    _navigationEvent.send(ChatNavigation(projectId, ""))
                }
            } catch (e: Exception) {
                // 会话加载失败时仍导航到聊天页 (使用空 sessionId)
                _uiState.update { it.copy(isLoadingSessions = false) }
                _navigationEvent.send(ChatNavigation(projectId, ""))
            }
        }
    }

    // ==================== 创建项目 ====================

    /**
     * 创建新项目
     *
     * 验证输入 -> 调用 createProject API -> 成功后关闭对话框并刷新列表
     */
    fun createProject() {
        val state = _uiState.value
        val name = state.newProjectName.trim()

        // 输入验证
        if (name.isBlank()) {
            _uiState.update { it.copy(createError = "请输入项目名称") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, createError = null) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val request = CreateProjectRequest(
                    name = name,
                    path = state.newProjectPath.trim().ifBlank { null },
                    displayName = state.newProjectDisplayName.trim().ifBlank { null }
                )

                val response = api.createProject(request)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "创建项目失败")
                }

                // 创建成功：关闭对话框、清空表单、刷新列表
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        showCreateDialog = false,
                        createError = null,
                        newProjectName = "",
                        newProjectPath = "",
                        newProjectDisplayName = ""
                    )
                }

                // 重新加载项目列表以展示新项目
                loadProjects()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        createError = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 对话框状态管理 ====================

    /** 显示新建项目对话框 (同时清空表单与错误) */
    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createError = null,
                newProjectName = "",
                newProjectPath = "",
                newProjectDisplayName = ""
            )
        }
    }

    /** 隐藏新建项目对话框 */
    fun hideCreateDialog() {
        _uiState.update {
            it.copy(showCreateDialog = false, createError = null)
        }
    }

    /** 更新新建项目名称 */
    fun updateNewProjectName(name: String) {
        _uiState.update { it.copy(newProjectName = name) }
    }

    /** 更新新建项目路径 */
    fun updateNewProjectPath(path: String) {
        _uiState.update { it.copy(newProjectPath = path) }
    }

    /** 更新新建项目展示名称 */
    fun updateNewProjectDisplayName(displayName: String) {
        _uiState.update { it.copy(newProjectDisplayName = displayName) }
    }

    /** 清除创建项目的错误信息 */
    fun clearCreateError() {
        _uiState.update { it.copy(createError = null) }
    }

    // ==================== 搜索过滤 ====================

    /**
     * 更新搜索关键字
     *
     * UI 层搜索框输入时调用，实时更新 [ProjectListScreenState.searchQuery]，
     * 由 Compose 端在渲染前调用 [filterProjects] 完成列表过滤。
     *
     * @param query 新的搜索关键字
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * 按搜索关键字过滤项目列表
     *
     * 搜索逻辑集中在此处 (ViewModel 层)，匹配项目的 name、path、displayName 字段，
     * 不区分大小写。当 query 为空白时直接返回原始列表。
     *
     * @param projects 原始项目列表 (Success 状态下的全部项目)
     * @param query 搜索关键字 (取自 [ProjectListScreenState.searchQuery])
     * @return 过滤后的项目列表
     */
    fun filterProjects(projects: List<Project>, query: String): List<Project> {
        if (query.isBlank()) return projects
        val lowerQuery = query.trim().lowercase()
        return projects.filter { project ->
            // 匹配项目名称
            project.name.lowercase().contains(lowerQuery) ||
            // 匹配项目路径
            project.path.lowercase().contains(lowerQuery) ||
            // 匹配展示名称 (字段可能为空)
            project.displayName?.lowercase()?.contains(lowerQuery) == true
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化异常错误信息
     *
     * 根据异常类型生成用户友好的中文错误提示，
     * 与 LoginViewModel 中的错误处理逻辑保持一致。
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
                    403 -> "没有权限访问项目列表"
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
