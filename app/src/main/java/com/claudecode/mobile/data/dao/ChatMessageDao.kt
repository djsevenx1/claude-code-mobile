package com.claudecode.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.claudecode.mobile.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息数据访问对象（DAO）
 *
 * 提供对 [ChatMessage] 表的增删改查操作，主要面向本地消息缓存场景。
 * 支持按会话 ID 观察消息流，便于 UI 实时展示最新消息。
 */
@Dao
interface ChatMessageDao {

    /**
     * 根据会话 ID 获取该会话下的全部消息，按时间戳升序排列。
     * 返回 Flow，消息变化时自动推送最新列表。
     *
     * @param sessionId 会话 ID
     * @return 该会话下的消息流
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<ChatMessage>>

    /**
     * 插入一条消息。当主键冲突时进行替换。
     *
     * @param message 待插入的消息
     * @return 新插入记录的主键 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    /**
     * 更新已有的消息（按主键匹配）。
     *
     * @param message 待更新的消息
     */
    @Update
    suspend fun update(message: ChatMessage)

    /**
     * 删除指定会话下的全部消息。
     *
     * @param sessionId 会话 ID
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /**
     * 删除全部消息（清空消息缓存）。
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
