package com.claudecode.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.claudecode.mobile.data.entity.ServerConfig
import kotlinx.coroutines.flow.Flow

/**
 * 服务器配置数据访问对象（DAO）
 *
 * 提供对 [ServerConfig] 表的增删改查操作，支持以 Flow 形式
 * 观察数据变化，便于 UI 层自动响应数据库更新。
 */
@Dao
interface ServerDao {

    /**
     * 获取全部服务器配置，按默认服务器优先、名称升序排列。
     * 返回 Flow，数据库变更时自动推送最新结果。
     */
    @Query("SELECT * FROM servers ORDER BY isDefault DESC, name ASC")
    fun getAll(): Flow<List<ServerConfig>>

    /**
     * 获取当前默认服务器配置。
     *
     * @return 默认服务器，若不存在则返回 null
     */
    @Query("SELECT * FROM servers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ServerConfig?

    /**
     * 根据主键 ID 获取服务器配置。
     *
     * @param id 服务器主键
     * @return 对应的服务器配置，若不存在则返回 null
     */
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): ServerConfig?

    /**
     * 插入一条新的服务器配置。
     * 当主键冲突时进行替换。
     *
     * @param server 待插入的服务器配置
     * @return 新插入记录的主键 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerConfig): Long

    /**
     * 更新已有的服务器配置。
     *
     * @param server 待更新的服务器配置（按主键匹配）
     */
    @Update
    suspend fun update(server: ServerConfig)

    /**
     * 根据主键 ID 删除服务器配置。
     *
     * @param id 待删除的服务器主键
     */
    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * 将指定服务器设为默认服务器。
     *
     * 该操作在一个事务中完成：先将所有服务器的 isDefault 置为 false，
     * 再将目标服务器的 isDefault 置为 true，保证同一时刻仅有一个默认服务器。
     *
     * @param id 待设为默认的服务器主键
     */
    @Transaction
    suspend fun setDefault(id: Long) {
        // 先清除所有默认标记
        clearAllDefault()
        // 再为目标服务器设置默认标记
        markAsDefault(id)
    }

    /**
     * 清除所有服务器的默认标记。
     */
    @Query("UPDATE servers SET isDefault = 0")
    suspend fun clearAllDefault()

    /**
     * 将指定服务器标记为默认。
     */
    @Query("UPDATE servers SET isDefault = 1 WHERE id = :id")
    suspend fun markAsDefault(id: Long)
}
