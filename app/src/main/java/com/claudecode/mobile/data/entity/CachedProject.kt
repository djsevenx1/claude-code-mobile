package com.claudecode.mobile.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 缓存项目实体
 *
 * 用于缓存从服务器获取的项目列表，避免每次都发起网络请求。
 * 每个项目与一个服务器配置相关联。
 *
 * @property id 服务器返回的项目 ID（作为主键）
 * @property name 项目名称
 * @property path 项目在本地的路径
 * @property isStarred 是否被收藏
 * @property lastActive 最后活跃时间（服务器返回的字符串格式），可空
 * @property serverId 关联的服务器 ID（对应 [ServerConfig] 的主键）
 */
@Entity(
    tableName = "cached_projects",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["path"])
    ]
)
data class CachedProject(
    @PrimaryKey
    val id: String,
    val name: String,
    val path: String,
    val isStarred: Boolean = false,
    val lastActive: String? = null,
    val serverId: Long = 0L
)
