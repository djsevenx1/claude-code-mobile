package com.claudecode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ============================================================
// 错误视图组件
// 提供统一的错误展示 UI，支持重试操作
// ============================================================

/**
 * 错误类型枚举 - 用于选择合适的图标和提示
 */
enum class ErrorType(val icon: ImageVector) {
    /** 网络错误 (连接超时、无法访问等) */
    NETWORK(Icons.Filled.WifiOff),

    /** 服务器错误 (502、503 等) */
    SERVER(Icons.Filled.CloudOff),

    /** 通用错误 */
    GENERAL(Icons.Filled.ErrorOutline)
}

/**
 * 全屏错误视图 - 用于页面级错误展示
 *
 * @param title 错误标题
 * @param message 错误详细描述
 * @param errorType 错误类型 (决定图标)，默认为通用错误
 * @param onRetry 重试按钮回调 (为 null 时不显示重试按钮)
 * @param retryText 重试按钮文本
 * @param modifier 修饰符
 */
@Composable
fun ErrorView(
    title: String = "出错了",
    message: String,
    errorType: ErrorType = ErrorType.GENERAL,
    onRetry: (() -> Unit)? = null,
    retryText: String = "重试",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 错误图标
            Icon(
                imageVector = errorType.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 错误标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 错误详细描述
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // 重试按钮 (如果提供了回调)
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = retryText)
                }
            }
        }
    }
}

/**
 * 紧凑型错误提示 - 用于卡片或小型区域内错误展示
 *
 * @param message 错误信息
 * @param onRetry 重试回调 (可选)
 * @param modifier 修饰符
 */
@Composable
fun CompactErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) {
                Text(text = "重试")
            }
        }
    }
}
