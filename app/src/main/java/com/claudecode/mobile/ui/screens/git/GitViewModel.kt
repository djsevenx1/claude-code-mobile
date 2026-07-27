package com.claudecode.mobile.ui.screens.git

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.claudecode.mobile.network.NetworkModule
import com.claudecode.mobile.network.dto.GitCommitRequest
import com.claudecode.mobile.network.dto.GitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

// ============================================================
// Git 管理页面 ViewModel
// 管理 Git 状态加载、文件勾选、提交变更等操作
// 参考 ProjectListViewModel / ChatViewModel 的模式：
// AndroidViewModel + StateFlow + viewModelScope + 自定义 Factory
// ============================================================

/**
 * Git 状态加载 UI 状态密封类
 *
 * 确保状态转换的完备性，便于 Compose 端按分支渲染不同 UI：
 * - Loading: 首次加载中
 * - Success: 加载成功且存在文件变更
 * - Empty: 工作区干净（无变更），仍携带 GitStatus 以展示分支信息
 * - Error: 加载失败
 */
sealed class GitUiState {
    /** 加载中状态 - 首次加载 Git 状态 */
    object Loading : GitUiState()

    /** 加载成功 - 存在文件变更 */
    data class Success(val status: GitStatus) : GitUiState()

    /** 空状态 - 工作区干净 (无未提交变更)，保留分支等基本信息 */
    data class Empty(val status: GitStatus) : GitUiState()

    /** 出错状态 - 加载失败 */
    data class Error(val message: String) : GitUiState()
}

/**
 * Git 管理页面完整 UI 状态
 *
 * 聚合页面所需的全部 UI 状态字段，通过单一 StateFlow 暴露给 Compose 端。
 *
 * @param uiState 加载状态 (Loading/Success/Empty/Error)
 * @param isRefreshing 是否正在下拉/手动刷新
 * @param isCommitting 是否正在提交
 * @param commitMessage 提交信息输入框文本
 * @param selectedFiles 已勾选的文件路径集合 (用于选择性提交)
 * @param selectAll 是否全选
 * @param commitResult 提交成功后的提示信息 (null 表示无)
 * @param commitError 提交失败时的错误信息 (null 表示无)
 * @param projectPath 当前项目在服务器上的路径 (用于调用 Git API)
 * @param projectName 项目展示名称 (TopAppBar 显示)
 */
data class GitScreenState(
    val uiState: GitUiState = GitUiState.Loading,
    val isRefreshing: Boolean = false,
    val isCommitting: Boolean = false,
    val commitMessage: String = "",
    val selectedFiles: Set<String> = emptySet(),
    val selectAll: Boolean = false,
    val commitResult: String? = null,
    val commitError: String? = null,
    val projectPath: String = "",
    val projectName: String = ""
)

/**
 * Git 管理页面 ViewModel
 *
 * 继承 AndroidViewModel 以获取 Application 上下文。
 * 通过 [NetworkModule.createCloudApiFromConfig] 获取 CloudApi 实例。
 *
 * 职责：
 * 1. 解析 projectId -> projectPath (通过项目列表接口)
 * 2. 加载 Git 状态 (调用 GET /api/git/status)
 * 3. 管理文件勾选与全选状态
 * 4. 提交变更 (调用 POST /api/git/commit)
 *
 * @param application 应用上下文 (由 AndroidViewModel 提供)
 * @param projectId 项目 ID (从导航参数传入)
 */
class GitViewModel(
    application: Application,
    private val projectId: String
) : AndroidViewModel(application) {

    // UI 状态 StateFlow - 可观察的状态流，Compose 端通过 collectAsStateWithLifecycle 收集
    private val _uiState = MutableStateFlow(GitScreenState())
    val uiState: StateFlow<GitScreenState> = _uiState.asStateFlow()

    init {
        // 进入页面时先解析项目路径，再加载 Git 状态
        loadProjectPath()
    }

    // ==================== 项目路径解析 ====================

    /**
     * 解析项目路径
     *
     * Git API 需要通过查询参数 path 定位仓库，而导航传入的是 projectId，
     * 因此先调用项目列表接口查找匹配的项目，取出其 path 字段。
     * 解析成功后自动触发 [loadGitStatus]。
     */
    private fun loadProjectPath() {
        viewModelScope.launch {
            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val projects = api.getProjects()
                val project = projects.find { it.id == projectId }

                if (project == null) {
                    _uiState.update {
                        it.copy(uiState = GitUiState.Error("未找到对应项目"))
                    }
                    return@launch
                }

                val path = project.path
                if (path.isBlank()) {
                    _uiState.update {
                        it.copy(uiState = GitUiState.Error("项目路径为空，无法获取 Git 状态"))
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        projectPath = path,
                        projectName = project.displayName?.takeIf { it.isNotBlank() }
                            ?: project.name.takeIf { it.isNotBlank() }
                            ?: projectId
                    )
                }

                // 路径解析成功后加载 Git 状态
                loadGitStatus()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uiState = GitUiState.Error(formatErrorMessage(e)))
                }
            }
        }
    }

    // ==================== Git 状态加载 ====================

    /**
     * 加载 Git 状态 (首次加载 / 重试)
     *
     * 设置 Loading 状态 -> 调用 GET /api/git/status -> 更新为 Success / Empty / Error
     * 同时重置文件勾选状态。
     */
    fun loadGitStatus() {
        val path = _uiState.value.projectPath
        if (path.isBlank()) {
            // 路径未就绪，重新解析
            loadProjectPath()
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    uiState = GitUiState.Loading,
                    selectedFiles = emptySet(),
                    selectAll = false,
                    commitError = null,
                    commitResult = null
                )
            }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val status = api.getGitStatus(path)

                updateStatusState(status)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uiState = GitUiState.Error(formatErrorMessage(e)))
                }
            }
        }
    }

    /**
     * 刷新 Git 状态
     *
     * 与 [loadGitStatus] 的区别：不切换到 Loading 状态 (保留当前列表)，
     * 仅设置 isRefreshing 标志位，由 UI 展示刷新指示器。
     */
    fun refreshGitStatus() {
        val path = _uiState.value.projectPath
        if (path.isBlank()) {
            loadProjectPath()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址")

                val status = api.getGitStatus(path)

                _uiState.update {
                    it.copy(isRefreshing = false)
                }
                updateStatusState(status)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        uiState = GitUiState.Error(formatErrorMessage(e))
                    )
                }
            }
        }
    }

    /**
     * 根据 GitStatus 更新 UI 状态
     *
     * 统一处理非 Git 仓库、工作区干净、存在变更三种情况，
     * 并在存在变更时重置文件勾选状态。
     */
    private fun updateStatusState(status: GitStatus) {
        // 非 Git 仓库或服务端返回错误
        if (!status.isGitRepository || status.error != null) {
            _uiState.update {
                it.copy(uiState = GitUiState.Error(status.error ?: "当前路径不是 Git 仓库"))
            }
            return
        }

        // 工作区干净 (无文件变更)
        if (status.clean || status.files.isEmpty()) {
            _uiState.update {
                it.copy(
                    uiState = GitUiState.Empty(status),
                    selectedFiles = emptySet(),
                    selectAll = false
                )
            }
            return
        }

        // 存在文件变更
        _uiState.update {
            it.copy(
                uiState = GitUiState.Success(status),
                selectedFiles = emptySet(),
                selectAll = false
            )
        }
    }

    // ==================== 文件勾选管理 ====================

    /**
     * 切换单个文件的勾选状态
     *
     * @param filePath 文件路径
     */
    fun toggleFileSelection(filePath: String) {
        _uiState.update { state ->
            val newSelected = if (filePath in state.selectedFiles) {
                state.selectedFiles - filePath
            } else {
                state.selectedFiles + filePath
            }
            // 同步全选状态：当所有文件都被选中时置为 true
            val allFiles = (state.uiState as? GitUiState.Success)?.status?.files
                ?.map { it.path }
                ?: emptyList()
            state.copy(
                selectedFiles = newSelected,
                selectAll = allFiles.isNotEmpty() && newSelected.containsAll(allFiles)
            )
        }
    }

    /**
     * 切换全选状态
     *
     * 选中时将所有变更文件加入选中集合，取消时清空。
     */
    fun toggleSelectAll() {
        _uiState.update { state ->
            val status = (state.uiState as? GitUiState.Success)?.status
            val allFiles = status?.files?.map { it.path } ?: emptyList()
            if (state.selectAll) {
                // 当前全选 -> 取消全选
                state.copy(selectAll = false, selectedFiles = emptySet())
            } else {
                // 当前未全选 -> 全选
                state.copy(selectAll = true, selectedFiles = allFiles.toSet())
            }
        }
    }

    // ==================== 提交变更 ====================

    /**
     * 提交 Git 变更
     *
     * 流程：
     * 1. 校验提交信息非空
     * 2. 校验选中文件非空 (非全选模式下)
     * 3. 构造 [GitCommitRequest] (全选时 stageAll=true，否则传入具体文件列表)
     * 4. 调用 POST /api/git/commit
     * 5. 成功后清空提交信息、刷新状态、展示成功提示
     */
    fun commitChanges() {
        val state = _uiState.value
        val message = state.commitMessage.trim()

        // 提交信息校验
        if (message.isBlank()) {
            _uiState.update { it.copy(commitError = "请输入提交信息") }
            return
        }

        // 路径校验
        val path = state.projectPath
        if (path.isBlank()) {
            _uiState.update { it.copy(commitError = "项目路径未知，无法提交") }
            return
        }

        // 选中文件校验 (非全选模式下必须选中至少一个文件)
        if (!state.selectAll && state.selectedFiles.isEmpty()) {
            _uiState.update { it.copy(commitError = "请选择要提交的文件") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCommitting = true, commitError = null, commitResult = null)
            }

            try {
                val api = NetworkModule.createCloudApiFromConfig()
                    ?: throw RuntimeException("未配置服务器地址，请先登录")

                val request = GitCommitRequest(
                    path = path,
                    message = message,
                    stageAll = state.selectAll,
                    files = if (state.selectAll) null else state.selectedFiles.toList()
                )

                val response = api.gitCommit(request)

                if (!response.success) {
                    throw RuntimeException(response.error ?: response.summary ?: "提交失败")
                }

                // 提交成功：清空表单并刷新状态
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        commitMessage = "",
                        selectedFiles = emptySet(),
                        selectAll = false,
                        commitResult = response.summary ?: "提交成功",
                        commitError = null
                    )
                }

                // 重新加载 Git 状态以展示提交后的最新状态
                refreshGitStatus()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCommitting = false,
                        commitError = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // ==================== 输入框与提示管理 ====================

    /** 更新提交信息输入框文本 */
    fun updateCommitMessage(text: String) {
        _uiState.update { it.copy(commitMessage = text) }
    }

    /** 清除提交错误信息 */
    fun clearCommitError() {
        _uiState.update { it.copy(commitError = null) }
    }

    /** 清除提交成功提示 (Snackbar 消费后调用) */
    fun clearCommitResult() {
        _uiState.update { it.copy(commitResult = null) }
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化异常错误信息
     *
     * 根据异常类型生成用户友好的中文错误提示，
     * 与 ProjectListViewModel 中的错误处理逻辑保持一致。
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
                    403 -> "没有权限执行此 Git 操作"
                    404 -> "Git 接口不存在，请检查服务器地址"
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

    // ============================================================
    // ViewModel 工厂 (传递 projectId)
    // ============================================================

    companion object {
        /**
         * 创建 GitViewModel 的工厂
         *
         * 由于 GitViewModel 需要额外的 projectId 参数，
         * 而 viewModel() 默认只能注入 Application，因此通过自定义 Factory 传递。
         *
         * 使用示例 (在 Compose 中):
         * ```kotlin
         * viewModel(factory = GitViewModel.provideFactory(app, projectId))
         * ```
         *
         * @param application 应用上下文
         * @param projectId 项目 ID
         * @return ViewModelProvider.Factory
         */
        fun provideFactory(
            application: Application,
            projectId: String
        ): ViewModelProvider.Factory = GitViewModelFactory(application, projectId)
    }
}

/**
 * GitViewModel 工厂类
 *
 * 用于在 Compose 中通过 viewModel(factory = ...) 创建带参数的 GitViewModel。
 * 实现 [ViewModelProvider.Factory] 接口，在 create() 中构造 ViewModel 实例。
 *
 * @param application 应用上下文
 * @param projectId 项目 ID
 */
private class GitViewModelFactory(
    private val application: Application,
    private val projectId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GitViewModel(application, projectId) as T
    }
}
