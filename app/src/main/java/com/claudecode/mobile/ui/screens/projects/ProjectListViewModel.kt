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
 *
 * 星标/重命名/删除/归档相关状态:
 * @param showRenameDialog 是否展示重命名对话框
 * @param renameProjectId 正在重命名的项目标识
 * @param renameProjectName 重命名对话框中的新名称输入
 * @param renameProjectOriginalName 正在重命名的项目原始名称 (用于对话框提示)
 * @param renameError 重命名时的错误信息 (null 表示无错误)
 * @param isRenaming 是否正在执行重命名请求
 * @param showDeleteConfirmDialog 是否展示删除确认对话框
 * @param deleteProjectId 待删除项目标识
 * @param deleteProjectName 待删除项目名称 (用于确认对话框展示)
 * @param isDeleting 是否正在执行删除请求
 * @param showArchivedSheet 是否展示已归档项目底部 Sheet
 * @param archivedProjects 已归档项目列表
 * @param isLoadingArchived 是否正在加载已归档项目
 * @param isRestoring 是否正在恢复归档项目
 * @param snackbarMessage 一次性提示消息 (星标/删除/归档/恢复操作结果，展示后清空)
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
    val searchQuery: String = "",
    // 重命名对话框状态
    val showRenameDialog: Boolean = false,
    val renameProjectId: String? = null,
    val renameProjectName: String = "",
    val renameProjectOriginalName: String = "",
    val renameError: String? = null,
    val isRenaming: Boolean = false,
    // 删除确认对话框状态
    val showDeleteConfirmDialog: Boolean = false,
    val deleteProjectId: String? = null,
    val deleteProjectName: String = "",
    val isDeleting: Boolean = false,
    // 已归档项目状态
    val showArchivedSheet: Boolean = false,
    val archivedProjects: List<Project> = emptyList(),
    val isLoadingArchived: Boolean = false,
    val isRestoring: Boolean = false,
    // 一次性提示消息
    val snackbarMessage: String? = null
)

/**
 * 项目列表 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application 上下文。
 * 通过 [NetworkModule.createCloudApiFromConfig] 获取 CloudApi 实例，
 * 服务器 URL 与 token 由 TokenManager (DataStore) 自动管理。
 *
 * 职责：
 * 1. 加载项目列表 (首次进入 + 重试)，星标项目排在前面
 * 2. 下拉刷新项目列表
 * 3. 点击项目后加载会话列表并触发导航
 * 4. 创建新项目
 * 5. 项目星标/取消星标 (乐观更新 + 刷新列表)
 * 6. 项目重命名
 * 7. 项目删除 (带确认对话框)
 * 8. 项目归档与恢复 (查看已归档项目)
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

                // 加载并按星标优先排序 (星标项目排在前面)
                val projects = sortProjects(api.getProjects())

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

                // 刷新并按星标优先排序 (星标项目排在前面)
                val projects = sortProjects(api.getProjects())

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
     *
     * claudecodeui API 要求:
     * - path: 项目路径（必填，服务器上的路径）
     * - customName: 自定义展示名称（可选）
     */
    fun createProject() {
        val state = _uiState.value
        val path = state.newProjectPath.trim()

        // 输入验证 - 路径为必填项
        if (path.isBlank()) {
            _uiState.update { it.copy(createError = "请输入项目路径") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, createError = null) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val request = CreateProjectRequest(
                    path = path,
                    customName = state.newProjectDisplayName.trim().ifBlank { null }
                )

                val response = api.createProject(request)

                if (!response.success) {
                    throw RuntimeException(response.message ?: response.project?.name ?: "创建项目失败")
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

    // ==================== 项目星标 ====================

    /**
     * 切换项目星标状态 (星标 / 取消星标)
     *
     * 采用乐观更新策略：先在本地切换星标状态并重新排序 (星标项目排到前面)，
     * 随后调用 API 持久化。成功后静默刷新列表以同步服务端最新状态；
     * 失败时回滚本地状态并提示错误。
     *
     * API: POST /api/projects/{projectId}/toggle-star
     *
     * @param projectId 项目标识
     */
    fun toggleStar(projectId: String) {
        if (projectId.isBlank()) return

        viewModelScope.launch {
            // 乐观更新：本地切换星标状态并重新排序
            updateProjectsInState { projects ->
                sortProjects(
                    projects.map { project ->
                        if (project.id == projectId) {
                            project.copy(isStarred = !(project.isStarred ?: false))
                        } else {
                            project
                        }
                    }
                )
            }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                api.toggleProjectStar(projectId)

                // 成功后静默刷新列表以同步服务端最新状态
                reloadProjectsSilently()
            } catch (e: Exception) {
                // 失败：回滚本地星标状态
                updateProjectsInState { projects ->
                    sortProjects(
                        projects.map { project ->
                            if (project.id == projectId) {
                                project.copy(isStarred = !(project.isStarred ?: false))
                            } else {
                                project
                            }
                        }
                    )
                }
                _uiState.update { it.copy(snackbarMessage = formatErrorMessage(e)) }
            }
        }
    }

    // ==================== 项目重命名 ====================

    /**
     * 重命名项目
     *
     * 验证新名称非空 -> 调用重命名 API -> 成功后关闭对话框并刷新列表。
     *
     * API: PUT /api/projects/{projectId}/rename
     * 请求体: { "name": newName }
     *
     * @param projectId 项目标识
     * @param newName 新名称
     */
    fun renameProject(projectId: String, newName: String) {
        val trimmedName = newName.trim()

        // 输入验证 - 新名称不能为空
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(renameError = "请输入新的项目名称") }
            return
        }

        // 与原名称相同则不重复请求
        val original = _uiState.value.renameProjectOriginalName
        if (trimmedName == original) {
            _uiState.update { it.copy(showRenameDialog = false, renameError = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRenaming = true, renameError = null) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.renameProject(projectId, mapOf("name" to trimmedName))

                if (!response.success) {
                    throw RuntimeException(response.error ?: "重命名失败")
                }

                // 重命名成功：关闭对话框并清空表单
                _uiState.update {
                    it.copy(
                        isRenaming = false,
                        showRenameDialog = false,
                        renameError = null,
                        renameProjectId = null,
                        renameProjectName = "",
                        renameProjectOriginalName = ""
                    )
                }

                // 重新加载项目列表以展示新名称
                reloadProjectsSilently()
                _uiState.update { it.copy(snackbarMessage = "重命名成功") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRenaming = false,
                        renameError = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 项目删除 ====================

    /**
     * 删除项目
     *
     * 调用删除 API (force = false)，成功后从列表移除并提示。
     * 注意：claudecodeui 的 DELETE /api/projects/{projectId} 在 force=false 时
     * 表现为归档，此处保留与后端一致的语义。
     *
     * API: DELETE /api/projects/{projectId}?force=false
     *
     * @param projectId 项目标识
     */
    fun deleteProject(projectId: String) {
        if (projectId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.deleteProject(projectId, force = false)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "删除项目失败")
                }

                // 删除成功：关闭确认对话框并从列表移除
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirmDialog = false,
                        deleteProjectId = null,
                        deleteProjectName = ""
                    )
                }

                // 从当前列表中移除已删除项目 (无需等待完整刷新)
                updateProjectsInState { projects ->
                    projects.filterNot { it.id == projectId }
                }

                _uiState.update { it.copy(snackbarMessage = "项目已删除") }

                // 静默刷新以同步服务端状态 (若列表变空会切换到 Empty)
                reloadProjectsSilently()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        snackbarMessage = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 项目归档与恢复 ====================

    /**
     * 归档项目
     *
     * 调用 DELETE API (force = false) 将项目归档 (软删除)。
     * 归档后从当前列表移除。
     *
     * API: DELETE /api/projects/{projectId}?force=false
     *
     * @param projectId 项目标识
     */
    fun archiveProject(projectId: String) {
        if (projectId.isBlank()) return

        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.deleteProject(projectId, force = false)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "归档项目失败")
                }

                // 归档成功：从当前列表移除
                updateProjectsInState { projects ->
                    projects.filterNot { it.id == projectId }
                }

                _uiState.update { it.copy(snackbarMessage = "项目已归档") }

                // 静默刷新以同步服务端状态
                reloadProjectsSilently()
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = formatErrorMessage(e)) }
            }
        }
    }

    /**
     * 加载已归档项目列表
     *
     * 展示已归档项目底部 Sheet 前调用，拉取归档项目数据。
     *
     * API: GET /api/projects/archived
     * 响应包装格式: { "success": true, "data": { "projects": [...] } }
     */
    fun loadArchivedProjects() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showArchivedSheet = true,
                    isLoadingArchived = true,
                    archivedProjects = emptyList()
                )
            }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.getArchivedProjects()
                val archived = response.data?.projects ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoadingArchived = false,
                        archivedProjects = archived
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingArchived = false,
                        snackbarMessage = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    /**
     * 恢复已归档项目
     *
     * 从已归档列表中恢复项目到正常列表。
     * 成功后从归档列表移除并刷新项目列表。
     *
     * API: POST /api/projects/{projectId}/restore
     *
     * @param projectId 项目标识
     */
    fun restoreArchivedProject(projectId: String) {
        if (projectId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val response = api.restoreProject(projectId)

                if (!response.success) {
                    throw RuntimeException(response.error ?: "恢复项目失败")
                }

                // 恢复成功：从归档列表移除
                _uiState.update { state ->
                    state.copy(
                        isRestoring = false,
                        archivedProjects = state.archivedProjects.filterNot { it.id == projectId },
                        snackbarMessage = "项目已恢复"
                    )
                }

                // 刷新正常项目列表以展示恢复的项目
                reloadProjectsSilently()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        snackbarMessage = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 操作对话框状态管理 ====================

    /**
     * 显示重命名对话框 (预填当前项目名称)
     *
     * @param project 待重命名的项目
     */
    fun showRenameDialog(project: Project) {
        val projectId = project.id ?: return
        val currentName = getProjectDisplayName(project)
        _uiState.update {
            it.copy(
                showRenameDialog = true,
                renameProjectId = projectId,
                renameProjectName = currentName,
                renameProjectOriginalName = currentName,
                renameError = null
            )
        }
    }

    /** 隐藏重命名对话框 (重命名进行中时不允许关闭) */
    fun hideRenameDialog() {
        if (_uiState.value.isRenaming) return
        _uiState.update {
            it.copy(
                showRenameDialog = false,
                renameProjectId = null,
                renameProjectName = "",
                renameProjectOriginalName = "",
                renameError = null
            )
        }
    }

    /** 更新重命名对话框中的新名称输入 */
    fun updateRenameProjectName(name: String) {
        _uiState.update { it.copy(renameProjectName = name, renameError = null) }
    }

    /**
     * 显示删除确认对话框
     *
     * @param project 待删除的项目
     */
    fun showDeleteConfirmDialog(project: Project) {
        val projectId = project.id ?: return
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = true,
                deleteProjectId = projectId,
                deleteProjectName = getProjectDisplayName(project)
            )
        }
    }

    /** 隐藏删除确认对话框 (删除进行中时不允许关闭) */
    fun hideDeleteConfirmDialog() {
        if (_uiState.value.isDeleting) return
        _uiState.update {
            it.copy(
                showDeleteConfirmDialog = false,
                deleteProjectId = null,
                deleteProjectName = ""
            )
        }
    }

    /** 隐藏已归档项目底部 Sheet */
    fun hideArchivedSheet() {
        _uiState.update {
            it.copy(
                showArchivedSheet = false,
                archivedProjects = emptyList()
            )
        }
    }

    /** 清除一次性提示消息 (Snackbar 展示后调用) */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ==================== 辅助方法 ====================

    /**
     * 排序项目列表：星标项目排在前面，其余保持原有相对顺序
     *
     * Kotlin 的 sortedByDescending 使用稳定排序，因此未星标项目间的相对顺序不变。
     *
     * @param projects 原始项目列表
     * @return 星标优先排序后的列表
     */
    private fun sortProjects(projects: List<Project>): List<Project> {
        return projects.sortedByDescending { it.isStarred == true }
    }

    /**
     * 在当前 Success 状态中变换项目列表 (用于乐观更新)
     *
     * 仅当当前处于 Success 状态时生效，其余状态保持不变。
     *
     * @param transform 列表变换函数
     */
    private fun updateProjectsInState(transform: (List<Project>) -> List<Project>) {
        _uiState.update { state ->
            val currentUiState = state.uiState
            if (currentUiState is ProjectListUiState.Success) {
                val newProjects = transform(currentUiState.projects)
                state.copy(
                    uiState = if (newProjects.isEmpty()) {
                        ProjectListUiState.Empty
                    } else {
                        currentUiState.copy(projects = newProjects)
                    }
                )
            } else {
                state
            }
        }
    }

    /**
     * 静默重新加载项目列表 (不显示加载/刷新指示器)
     *
     * 用于星标、重命名、归档、删除、恢复等操作后同步服务端最新状态。
     * 加载失败时仅通过 snackbar 提示，不切换到 Error 状态 (避免打断用户操作)。
     */
    private fun reloadProjectsSilently() {
        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig() ?: return@launch
                val projects = sortProjects(api.getProjects())
                if (projects.isEmpty()) {
                    _uiState.update { it.copy(uiState = ProjectListUiState.Empty) }
                } else {
                    _uiState.update { it.copy(uiState = ProjectListUiState.Success(projects)) }
                }
            } catch (e: Exception) {
                // 静默刷新失败不打扰用户，仅在 snackbar 提示
                _uiState.update { it.copy(snackbarMessage = "刷新列表失败: ${formatErrorMessage(e)}") }
            }
        }
    }

    /**
     * 获取项目展示名称 (ViewModel 内部使用)
     *
     * 优先级：displayName (非空) > name (非空) > id > "未命名项目"
     *
     * @param project 项目数据
     * @return 用于展示的项目名称
     */
    private fun getProjectDisplayName(project: Project): String {
        return project.displayName?.takeIf { it.isNotBlank() }
            ?: project.name.takeIf { it.isNotBlank() }
            ?: project.id
            ?: "未命名项目"
    }

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
