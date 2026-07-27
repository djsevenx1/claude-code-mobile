package com.claudecode.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Git 相关数据传输对象 (DTO)
 *
 * 对应 CloudCLI 服务端 /api/git/ 系列接口。
 */

/**
 * Git 文件变更
 *
 * 表示工作区或暂存区中单个文件的变更状态。
 *
 * @param path 文件相对路径
 * @param status 状态标识（M=修改, A=新增, D=删除, R=重命名, ?=未跟踪 等）
 * @param staged 是否已加入暂存区
 * @param oldPath 旧路径（重命名场景，可选）
 */
@Serializable
data class GitFileChange(
    val path: String,
    val status: String = "",
    val staged: Boolean = false,
    @SerialName("old_path")
    val oldPath: String? = null
)

/**
 * Git 仓库状态
 *
 * GET /api/git/status 获取指定项目（通过查询参数 path 指定）的 Git 状态。
 *
 * @param branch 当前分支名
 * @param ahead 领先远程的提交数
 * @param behind 落后远程的提交数
 * @param clean 工作区是否干净（无未提交变更）
 * @param files 变更文件列表
 * @param remote 当前远程名（如 origin，可选）
 * @param upstream 上游跟踪分支（可选）
 * @param isGitRepository 当前路径是否为 Git 仓库
 * @param error 错误信息（可选，例如非 Git 仓库时）
 */
@Serializable
data class GitStatus(
    val branch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val clean: Boolean = true,
    val files: List<GitFileChange> = emptyList(),
    val remote: String? = null,
    val upstream: String? = null,
    @SerialName("isGitRepository")
    val isGitRepository: Boolean = true,
    val error: String? = null
)

/**
 * Git 提交请求
 *
 * POST /api/git/commit
 *
 * @param path 项目路径（用于定位仓库）
 * @param message 提交信息
 * @param stageAll 是否在提交前暂存全部变更（git add -A）
 * @param files 需要暂存的具体文件列表（与 stageAll 互斥，可选）
 */
@Serializable
data class GitCommitRequest(
    val path: String,
    val message: String,
    @SerialName("stageAll")
    val stageAll: Boolean = true,
    val files: List<String>? = null
)

/**
 * Git 提交响应
 *
 * @param success 是否提交成功
 * @param commitHash 提交哈希（成功时返回）
 * @param branch 提交所在分支
 * @param summary 提交摘要信息
 * @param error 错误信息
 */
@Serializable
data class GitCommitResponse(
    val success: Boolean = false,
    @SerialName("commit_hash")
    val commitHash: String? = null,
    val branch: String? = null,
    val summary: String? = null,
    val error: String? = null
)
