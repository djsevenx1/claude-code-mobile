package com.claudecode.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.claudecode.mobile.data.entity.CachedProject
import kotlinx.coroutines.flow.Flow

/**
 * 缓存项目数据访问对象（DAO）
 *
 * 提供对 [CachedProject] 表的增删查操作，用于管理从服务器
 * 拉取并缓存到本地的项目列表。
 */
@Dao
interface ProjectDao {

    /**
     * 获取全部缓存项目，按收藏优先、名称升序排列。
     * 返回 Flow，数据变化时自动推送最新结果。
     */
    @Query("SELECT * FROM cached_projects ORDER BY isStarred DESC, name ASC")
    fun getAll(): Flow<List<CachedProject>>

    /**
     * 根据主键 ID 获取单个缓存项目。
     *
     * @param id 项目 ID（服务器返回）
     * @return 对应的项目，若不存在则返回 null
     */
    @Query("SELECT * FROM cached_projects WHERE id = :id")
    suspend fun getById(id: String): CachedProject?

    /**
     * 插入一条缓存项目。当主键冲突时进行替换（用于刷新缓存）。
     *
     * @param project 待插入的项目
     * @return 新插入记录的主键 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: CachedProject): Long

    /**
     * 根据主键 ID 删除缓存项目。
     *
     * @param id 项目 ID
     */
    @Query("DELETE FROM cached_projects WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * 删除指定服务器关联的全部缓存项目。
     * 通常在删除服务器配置时同步清理其项目缓存。
     *
     * @param serverId 服务器主键 ID
     */
    @Query("DELETE FROM cached_projects WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: Long)
}
