package `fun`.lzy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `fun`.lzy.data.MonitorLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val checkStatus by viewModel.lastCheckStatus.collectAsStateWithLifecycle()
    val logs by viewModel.monitorLogs.collectAsStateWithLifecycle()

    var showApiKey by remember { mutableStateOf(false) }

    // Dynamic rotation animation for header spinning indicator when running
    val infiniteTransition = rememberInfiniteTransition(label = "spinner_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Runtime Permission Launcher for Android 13+ (POST_NOTIFICATIONS)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.startService()
            Toast.makeText(context, "通知权限已授予，前台哨兵已处于监控就绪", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "请授予接收通知权限，否则异常发生时设备无法接收到故障通告", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF3F5F7)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF3F5F7)),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            
            // 1. App Beautiful Bento Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DeepSeek 哨兵",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (isRunning) "后台常驻监护中" else "监控待命中",
                            fontSize = 13.sp,
                            color = if (isRunning) Color(0xFF2563EB) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Floating animated badge
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle)
                                    .border(2.5.dp, Color.White, CircleShape)
                            ) {
                                // Notch effect
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF2563EB), CircleShape)
                                        .align(Alignment.TopCenter)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "待命",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Bento Grid Blocks Row Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    
                    // Card A: Status Card (Large Full-Width)
                    val latestLog = logs.firstOrNull()
                    val latencyDisplay = if (latestLog != null && latestLog.isSuccess) {
                        "${latestLog.latencyMs}ms"
                    } else if (!isRunning) {
                        "0ms"
                    } else {
                        "待测"
                    }

                    BentoStatusCard(
                        isRunning = isRunning,
                        checkStatus = checkStatus,
                        latencyText = latencyDisplay
                    )

                    // Card Row B: Timer + Configuration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Timer (Primary Blue Accent Style from HTML template)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF2563EB))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "刷新周期",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                                Column {
                                    Text(
                                        text = "53s",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "自动循环检测",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Right: Configuration Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Max Token",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold
                                )
                                Column {
                                    Text(
                                        text = "01",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "最小化损耗模式",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // Card Row C: Security + Alert Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left: Security Box (Slate-800 Theme from design template)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF1E293B))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFFFB923C), CircleShape)
                                    )
                                    Text(
                                        text = "SECURE",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "API 密钥",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "沙箱加密存储\n不 Root 不可读",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.45f),
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }

                        // Right: Alert Setting Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "超时阈值",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold
                                )
                                Column {
                                    Text(
                                        text = "3s",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "超时即发通知",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bento Form Panel: DeepSeek Key configuration & Secure Note
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardBorder()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "钥匙",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "参数密匙库",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { viewModel.onApiKeyChange(it) },
                            label = { Text("填入 DeepSeek API Key", fontSize = 13.sp) },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val icon = if (showApiKey) Icons.Default.Info else Icons.Default.Lock
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "切换可见性",
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "API Key 使用 Android Keystore 加密后保存在本机应用沙盒内，应用数据备份已关闭。",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.saveApiKey()
                                Toast.makeText(context, "API Key 保存成功并加密加密隔离！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_api_key_button")
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            )
                        ) {
                            Text("安全应用密钥", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // 4. Persistent Service Switch Board (Start/Stop Control Suite)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) Color(0xFFEFF6FF) else Color.White
                    ),
                    border = CardBorder()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "常驻网关管控",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "将在您安卓系统内单独开辟高优先级进程管道常驻监控，53秒周期自主发起检测，当3秒内超时未响应时立即发出声响震动级别强通知报警。",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isRunning) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.startService()
                                        Toast.makeText(context, "前台常驻服务已自启动，开始53s自动侦测", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_service_button")
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("启动后台守护监控 (53秒周期)", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.stopService()
                                    Toast.makeText(context, "哨兵前台常驻已安全停用", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stop_service_button")
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("中止并关闭后台常驻", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Bento Activity Logs Panel Header
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isRunning) Color(0xFF10B981) else Color.Gray, CircleShape)
                        )
                        Text(
                            text = "最近活动日志",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF475569)
                        )
                    }

                    if (logs.isNotEmpty()) {
                        Text(
                            text = "清空全部",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier
                                .clickable {
                                    viewModel.clearLogs()
                                    Toast.makeText(context, "历史日志已成功删除", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 6. Dashed-style Bento Log Preview container
            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "后台监控未收集到测试项",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "存储并启用秘钥常驻后，53s检测反馈在此记录",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            } else {
                items(logs) { log ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        LogItemCard(log)
                    }
                }
            }
        }
    }
}

@Composable
fun BentoStatusCard(
    isRunning: Boolean,
    checkStatus: String,
    latencyText: String
) {
    val containerBgColor = Color.White
    val borderCol = Color(0xFFE2E8F0)

    val badgeText = when {
        !isRunning -> "监控就绪"
        checkStatus == "正常" -> "连接正常"
        checkStatus == "异常" -> "检测异常"
        checkStatus == "连接中..." -> "极速诊断"
        checkStatus == "未配置秘钥" -> "缺损秘钥"
        else -> checkStatus
    }

    val badgeColor = when {
        !isRunning -> Color(0xFFF1F5F9)
        checkStatus == "正常" -> Color(0xFFDCFCE7)
        checkStatus == "异常" -> Color(0xFFFCE7F3)
        checkStatus == "连接中..." -> Color(0xFFFEF9C3)
        checkStatus == "未配置秘钥" -> Color(0xFFF3E8FF)
        else -> Color(0xFFF1F5F9)
    }

    val badgeTextColor = when {
        !isRunning -> Color(0xFF475569)
        checkStatus == "正常" -> Color(0xFF15803D)
        checkStatus == "异常" -> Color(0xFFB91C1C)
        checkStatus == "连接中..." -> Color(0xFF854D0E)
        checkStatus == "未配置秘钥" -> Color(0xFF6B21A8)
        else -> Color(0xFF475569)
    }

    // Top large Bento block
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerBgColor),
        border = CardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }

                Text(
                    text = "V4-FLASH",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
            }

            Column {
                Text(
                    text = latencyText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "当前 API 延迟响应",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Keep compatible implementation with GreetingScreenshotTest in Applet
@Composable
fun StatusWidget(isRunning: Boolean, checkStatus: String) {
    BentoStatusCard(
        isRunning = isRunning,
        checkStatus = checkStatus,
        latencyText = "检测中"
    )
}

@Composable
fun LogItemCard(log: MonitorLog) {
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = format.format(Date(log.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (log.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                CircleShape
                            )
                    )
                    Text(
                        text = if (log.isSuccess) "测试通过" else "异常中断",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (log.isSuccess) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                }
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "响应时耗 ${log.latencyMs}ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )

                Text(
                    text = if (log.statusCode > 0) "HTTP ${log.statusCode}" else "CODE ${log.statusCode}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (log.isSuccess) Color(0xFF15803D) else Color(0xFFB91C1C)
                )
            }

            if (!log.isSuccess && !log.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(8.dp)
                ) {
                    Text(
                        text = log.errorMessage,
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CardBorder() = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
