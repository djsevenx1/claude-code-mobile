package com.claudecode.mobile.data

data class ServerConfig(
    val id: Long,
    val name: String,
    val url: String,
    val isDefault: Boolean = false,
    val trustAllCerts: Boolean = false
) {
    companion object {
        val DEFAULT_CLOUD = ServerConfig(
            id = -1L,
            name = "CloudCLI Cloud",
            url = "https://cloudcli.ai",
            isDefault = true,
            trustAllCerts = false
        )
    }

    fun normalizedUrl(): String {
        var u = url.trim()
        if (u.isEmpty()) return u
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        while (u.endsWith("/")) {
            u = u.dropLast(1)
        }
        return u
    }
}
