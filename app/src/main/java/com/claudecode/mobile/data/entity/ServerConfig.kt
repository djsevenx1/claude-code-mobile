package com.claudecode.mobile.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 服务器配置实体
 *
 * 用于持久化保存用户配置的 CloudCLI 服务器信息。
 * 一台设备可以保存多个服务器，其中一个可被标记为默认服务器。
 *
 * @property id 自增主键
 * @property name 服务器名称（用户可读，用于列表展示）
 * @property url 服务器地址，例如 https://cloudcli.ai
 * @property isDefault 是否为默认服务器，同一时刻仅允许一个为 true
 * @property trustAllCerts 是否信任所有证书（自签名证书场景）
 * @property username 用户名（用于鉴权，可空）
 * @property token JWT token（用于鉴权，可空）
 */
@Entity(
    tableName = "servers",
    indices = [Index(value = ["url"], unique = true)]
)
data class ServerConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val url: String,
    val isDefault: Boolean = false,
    val trustAllCerts: Boolean = false,
    val username: String? = null,
    val token: String? = null
) {
    /**
     * 对 URL 进行规范化处理：
     * 1. 去除首尾空白
     * 2. 自动补全协议头（默认 http）
     * 3. 去除末尾多余的斜杠
     *
     * @return 规范化后的 URL 字符串
     */
    fun normalizedUrl(): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        // 缺少协议头时默认补 http
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        // 去除末尾多余的斜杠
        while (u.endsWith("/")) {
            u = u.dropLast(1)
        }
        return u
    }
}
