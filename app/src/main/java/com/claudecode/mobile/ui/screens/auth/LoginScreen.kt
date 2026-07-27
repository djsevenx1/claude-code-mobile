package com.claudecode.mobile.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claudecode.mobile.ui.theme.LoginGradientEnd
import com.claudecode.mobile.ui.theme.LoginGradientStart

// ============================================================
// 登录/注册页面
// CloudCLI 移动客户端的认证入口页面
// ============================================================

/**
 * 登录页面入口 Composable
 *
 * @param viewModel 登录 ViewModel
 * @param onLoginSuccess 登录成功回调 (导航到主页)
 * @param onNavigateToServerSetup 导航到服务器配置页面回调
 * @param modifier 修饰符
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToServerSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 收集 UI 状态 (跟随生命周期感知)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 监听登录成功状态，触发导航回调
    LaunchedEffect(uiState.uiState) {
        if (uiState.uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    LoginContent(
        state = uiState,
        onServerUrlChange = viewModel::updateServerUrl,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onToggleTrustAllCerts = viewModel::toggleTrustAllCerts,
        onTogglePasswordVisible = viewModel::togglePasswordVisible,
        onToggleAuthMode = viewModel::toggleAuthMode,
        onToggleServerField = viewModel::toggleServerField,
        onCheckServerHealth = viewModel::checkServerHealth,
        onSubmit = {
            // 提交前隐藏键盘
            keyboardController?.hide()
            viewModel.authenticate()
        },
        onClearError = viewModel::clearError,
        modifier = modifier
    )
}

/**
 * 登录页面内容布局
 * 使用纵向滚动 + imePadding 确保键盘弹出时内容可滚动访问
 */
@Composable
private fun LoginContent(
    state: LoginScreenState,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onToggleTrustAllCerts: () -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onToggleServerField: () -> Unit,
    onCheckServerHealth: () -> Unit,
    onSubmit: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isLoading = state.uiState is LoginUiState.Loading

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)   // 纵向滚动 (键盘弹出时可访问底部内容)
                .imePadding()                   // 键盘弹出时自动添加底部 padding
                .navigationBarsPadding()        // 避开系统导航栏
        ) {
            // --- 顶部 Logo 区域 (品牌渐变背景) ---
            BrandHeader()

            // --- 表单卡片区域 ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- 认证模式切换 Tab ---
                    AuthModeTabRow(
                        currentMode = state.authMode,
                        onModeSelected = { mode ->
                            if (mode != state.authMode) {
                                onToggleAuthMode()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- 服务器地址输入框 (首次使用或手动展开时显示) ---
                    if (state.showServerField) {
                        ServerUrlField(
                            serverUrl = state.serverUrl,
                            onServerUrlChange = onServerUrlChange,
                            onCheckHealth = onCheckServerHealth,
                            isCheckingHealth = state.isCheckingHealth,
                            serverHealthOk = state.serverHealthOk,
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // --- 用户名输入框 ---
                    UsernameField(
                        username = state.username,
                        onUsernameChange = onUsernameChange,
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- 密码输入框 ---
                    PasswordField(
                        password = state.password,
                        onPasswordChange = onPasswordChange,
                        passwordVisible = state.passwordVisible,
                        onTogglePasswordVisible = onTogglePasswordVisible,
                        label = "密码",
                        enabled = !isLoading,
                        imeAction = if (state.authMode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                        onDone = { if (state.authMode == AuthMode.LOGIN) onSubmit() }
                    )

                    // --- 确认密码输入框 (仅注册模式) ---
                    if (state.authMode == AuthMode.REGISTER) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PasswordField(
                            password = state.confirmPassword,
                            onPasswordChange = onConfirmPasswordChange,
                            passwordVisible = state.passwordVisible,
                            onTogglePasswordVisible = onTogglePasswordVisible,
                            label = "确认密码",
                            enabled = !isLoading,
                            imeAction = ImeAction.Done,
                            onDone = onSubmit
                        )
                    }

                    // --- 信任所有证书开关 ---
                    Spacer(modifier = Modifier.height(16.dp))
                    TrustAllCertsRow(
                        checked = state.trustAllCerts,
                        onToggle = onToggleTrustAllCerts,
                        enabled = !isLoading
                    )

                    // --- 错误信息展示 ---
                    val errorMessage = (state.uiState as? LoginUiState.Error)?.message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorMessageBox(
                            message = errorMessage,
                            onDismiss = onClearError
                        )
                    }

                    // --- 登录/注册按钮 ---
                    Spacer(modifier = Modifier.height(24.dp))
                    SubmitButton(
                        authMode = state.authMode,
                        isLoading = isLoading,
                        loadingMessage = (state.uiState as? LoginUiState.Loading)?.message ?: "",
                        onClick = onSubmit
                    )

                    // --- 切换登录/注册模式 ---
                    Spacer(modifier = Modifier.height(16.dp))
                    AuthModeSwitchText(
                        currentMode = state.authMode,
                        onToggleMode = onToggleAuthMode
                    )

                    // --- 服务器配置展开/折叠 (当服务器输入框隐藏时) ---
                    if (!state.showServerField) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onToggleServerField) {
                            Icon(
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "切换服务器",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // --- 底部版本信息 ---
            Spacer(modifier = Modifier.height(32.dp))
            FooterInfo()
        }
    }
}

// ============================================================
// 子组件: 品牌头部区域
// ============================================================

/**
 * 顶部品牌区域 - 包含 Logo 图标、应用名称和标语
 * 使用品牌色渐变背景
 */
@Composable
private fun BrandHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LoginGradientStart,
                        LoginGradientEnd
                    )
                )
            )
            .statusBarsPadding(),  // 避开状态栏
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(top = 32.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo 图标 (终端图标，圆形背景)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = "CloudCLI Logo",
                    modifier = Modifier.size(44.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 应用名称
            Text(
                text = "CloudCLI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 标语
            Text(
                text = "Claude Code 移动客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

// ============================================================
// 子组件: 认证模式 Tab 切换
// ============================================================

/**
 * 登录/注册模式切换 TabRow
 */
@Composable
private fun AuthModeTabRow(
    currentMode: AuthMode,
    onModeSelected: (AuthMode) -> Unit
) {
    val selectedIndex = if (currentMode == AuthMode.LOGIN) 0 else 1

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Tab(
            selected = currentMode == AuthMode.LOGIN,
            onClick = { onModeSelected(AuthMode.LOGIN) },
            text = { Text(text = AuthMode.LOGIN.displayName) }
        )
        Tab(
            selected = currentMode == AuthMode.REGISTER,
            onClick = { onModeSelected(AuthMode.REGISTER) },
            text = { Text(text = AuthMode.REGISTER.displayName) }
        )
    }
}

// ============================================================
// 子组件: 服务器地址输入框
// ============================================================

/**
 * 服务器地址输入框 (含健康检查状态指示器)
 */
@Composable
private fun ServerUrlField(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onCheckHealth: () -> Unit,
    isCheckingHealth: Boolean,
    serverHealthOk: Boolean?,
    enabled: Boolean
) {
    Column {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务器地址") },
            placeholder = { Text("https://cloudcli.ai", fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Filled.Dns, contentDescription = null)
            },
            trailingIcon = {
                // 服务器健康状态指示器
                when {
                    isCheckingHealth -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    serverHealthOk == true -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "服务器正常",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    serverHealthOk == false -> {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = "服务器不可达",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // 服务器地址帮助文本
        Text(
            text = "例如: http://192.168.1.100:3001 或 https://cloudcli.ai",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        // 手动触发健康检查按钮
        if (serverUrl.isNotBlank() && !isCheckingHealth) {
            TextButton(
                onClick = onCheckHealth,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            ) {
                Text("测试连接", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ============================================================
// 子组件: 用户名输入框
// ============================================================

/**
 * 用户名输入框
 */
@Composable
private fun UsernameField(
    username: String,
    onUsernameChange: (String) -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("用户名") },
        leadingIcon = {
            Icon(Icons.Filled.Person, contentDescription = null)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ============================================================
// 子组件: 密码输入框
// ============================================================

/**
 * 密码输入框 (支持可见性切换)
 *
 * @param password 密码值
 * @param onPasswordChange 密码变更回调
 * @param passwordVisible 密码是否明文显示
 * @param onTogglePasswordVisible 切换密码可见性回调
 * @param label 输入框标签
 * @param enabled 是否可编辑
 * @param imeAction 键盘动作按钮类型
 * @param onDone 键盘 Done 动作回调
 */
@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Filled.Lock, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisible) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                    else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                )
            }
        },
        // 密码可见性: 明文 / 密文
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ============================================================
// 子组件: 信任所有证书开关
// ============================================================

/**
 * 信任所有证书开关行 (用于自签名 HTTPS 证书)
 */
@Composable
private fun TrustAllCertsRow(
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "信任所有证书",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "用于自签名 HTTPS 证书的开发服务器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled
            )
        }
    }
}

// ============================================================
// 子组件: 错误信息展示
// ============================================================

/**
 * 错误消息卡片 (带关闭按钮)
 */
@Composable
private fun ErrorMessageBox(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onDismiss,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("关闭", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ============================================================
// 子组件: 提交按钮
// ============================================================

/**
 * 登录/注册提交按钮 (含加载状态)
 */
@Composable
private fun SubmitButton(
    authMode: AuthMode,
    isLoading: Boolean,
    loadingMessage: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isLoading) {
            // 加载中: 显示进度指示器 + 加载文本
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = loadingMessage,
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            // 正常状态: 显示按钮文本
            Text(
                text = authMode.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// 子组件: 模式切换文字
// ============================================================

/**
 * 底部切换登录/注册模式的文字按钮
 */
@Composable
private fun AuthModeSwitchText(
    currentMode: AuthMode,
    onToggleMode: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (currentMode == AuthMode.LOGIN) "还没有账号？" else "已有账号？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onToggleMode) {
            Text(
                text = if (currentMode == AuthMode.LOGIN) "立即注册" else "去登录",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// 子组件: 底部版本信息
// ============================================================

/**
 * 页面底部的版本和版权信息
 */
@Composable
private fun FooterInfo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = "CloudCLI v2.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Powered by Claude Code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        )
    }
}
