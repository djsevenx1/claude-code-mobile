package com.claudecode.mobile.data.repository

import com.claudecode.mobile.data.dao.ChatMessageDao
import com.claudecode.mobile.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 聊天仓库
 *
 * 作为聊天消息本地缓存的数据源入口，封装 [ChatMessageDao] 的底层操作，
 * 对上层（ViewModel / UI）提供简洁、语义化的 API。
 *
 * 主要职责：
 * - 管理本地消息缓存的生命周期（保存、更新、删除）
 * - 以 Flow 形式提供消息流，便于 UI 实时响应数据变化
 *
 * @param chatMessageDao 聊天消息 DAO
 */
class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    /**
     * 观察指定会话下的消息流。
     *
     * 消息按时间戳升序排列，数据库中该会话的消息发生任何变化时，
     * Flow 都会自动推送最新的消息列表。
     *
     * @param sessionId 会话 ID
     * @return 该会话下的消息流
     */
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        chatMessageDao.getBySession(sessionId)

    /**
     * 保存一条消息到本地缓存。
     *
     * 若消息已存在相同主键则替换（用于流式消息的增量更新场景）。
     *
     * @param message 待保存的消息
     * @return 保存后记录的主键 ID
     */
    suspend fun saveMessage(message: ChatMessage): Long =
        chatMessageDao.insert(message)

    /**
     * 批量保存消息到本地缓存。
     *
     * 适用于从服务器拉取历史消息后一次性写入本地的场景。
     *
     * @param messages 待保存的消息列表
     */
    suspend fun saveMessages(messages: List<ChatMessage>) {
        messages.forEach { chatMessageDao.insert(it) }
    }

    /**
     * 更新一条已有的消息（按主键匹配）。
     *
     * 典型场景：流式传输结束后将 isStreaming 置为 false，
     * 或更新消息的最终内容。
     *
     * @param message 待更新的消息
     */
    suspend fun updateMessage(message: ChatMessage) {
        chatMessageDao.update(message)
    }

    /**
     * 删除指定会话下的全部消息。
     *
     * @param sessionId 会话 ID
     */
    suspend fun deleteSessionMessages(sessionId: String) {
        chatMessageDao.deleteBySession(sessionId)
    }

    /**
     * 清空全部本地消息缓存。
     */
    suspend fun clearAll() {
        chatMessageDao.deleteAll()
    }
}
