package com.claudecode.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.claudecode.mobile.data.dao.ChatMessageDao
import com.claudecode.mobile.data.dao.ProjectDao
import com.claudecode.mobile.data.dao.ServerDao
import com.claudecode.mobile.data.entity.CachedProject
import com.claudecode.mobile.data.entity.ChatMessage
import com.claudecode.mobile.data.entity.ServerConfig

/**
 * CloudCLI 客户端本地数据库
 *
 * 基于 Room 的应用主数据库，托管以下三张表：
 * - [ServerConfig]：服务器配置
 * - [ChatMessage]：聊天消息缓存
 * - [CachedProject]：项目缓存
 *
 * 数据库版本为 1，schema 导出已关闭（exportSchema = false）。
 * 通过 [getInstance] 获取全局单例，避免重复创建带来的开销与并发问题。
 */
@Database(
    entities = [
        ServerConfig::class,
        ChatMessage::class,
        CachedProject::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CloudDatabase : RoomDatabase() {

    /** 服务器配置 DAO */
    abstract fun serverDao(): ServerDao

    /** 聊天消息 DAO */
    abstract fun chatMessageDao(): ChatMessageDao

    /** 缓存项目 DAO */
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: CloudDatabase? = null

        /** 数据库文件名 */
        private const val DB_NAME = "cloud_cli.db"

        /**
         * 获取数据库单例实例。
         *
         * 使用双重检查锁定（double-checked locking）保证线程安全，
         * 整个应用生命周期内只创建一次数据库实例。
         *
         * @param context 应用上下文
         * @return 数据库单例
         */
        fun getInstance(context: Context): CloudDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloudDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
