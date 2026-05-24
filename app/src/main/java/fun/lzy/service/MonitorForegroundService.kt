package fun.lzy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fun.lzy.MainActivity
import fun.lzy.data.ApiKeyManager
import fun.lzy.data.AppDatabase
import fun.lzy.data.MonitorLog
import fun.lzy.data.MonitorRepository
import fun.lzy.network.DeepSeekRequest
import fun.lzy.network.Message
import fun.lzy.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

class MonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: MonitorRepository
    private lateinit var apiKeyManager: ApiKeyManager
    private val apiService = NetworkClient.createService()

    companion object {
        const val CHANNEL_ID = "deepseek_monitor_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val ABNORMAL_NOTIFICATION_ID = 1002

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        val isRunningState = MutableStateFlow(false)
        val lastCheckStatus = MutableStateFlow("未启动") // "未启动", "正常", "异常", "连接中...", "未配置秘钥"
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        repository = MonitorRepository(db.monitorLogDao())
        apiKeyManager = ApiKeyManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopRunning()
        } else {
            startRunning()
        }
        return START_STICKY
    }

    private fun startRunning() {
        if (isRunningState.value) return
        isRunningState.value = true

        val notification = buildForegroundNotification("DeepSeek 监控服务已启动", "正在等待周期检测...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            var hasSentAbnormalNotification = false

            while (isRunningState.value) {
                val apiKey = apiKeyManager.getApiKey()
                if (apiKey.isBlank()) {
                    repository.insertLog(
                        MonitorLog(
                            isSuccess = false,
                            latencyMs = 0,
                            statusCode = -1,
                            errorMessage = "未配置 API Key，请先填入有效秘钥"
                        )
                    )
                    updateNotificationText("监控挂起", "未配置 API Key，请在应用中配置")
                    lastCheckStatus.value = "未配置秘钥"
                    delay(10000L) // Checks config again in 10 seconds
                    continue
                }

                lastCheckStatus.value = "连接中..."
                val startTime = System.currentTimeMillis()
                var success = false
                var errMessage: String? = null
                var code = -1

                try {
                    val request = DeepSeekRequest(
                        model = "deepseek-v4-flash",
                        messages = listOf(Message(role = "user", content = "Hi")),
                        max_tokens = 1
                    )

                    val response = apiService.getCompletions(
                        authHeader = "Bearer ${apiKey.trim()}",
                        request = request
                    )

                    code = response.code()
                    if (response.isSuccessful) {
                        success = true
                    } else {
                        val errorBodyString = response.errorBody()?.string()
                        errMessage = "HTTP ${response.code()}: ${errorBodyString ?: "未知异常"}"
                    }
                } catch (e: SocketTimeoutException) {
                    errMessage = "请求超时 (已满 3 秒)"
                    code = -2
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "网络连接异常"
                    errMessage = if (msg.contains("timeout", ignoreCase = true)) {
                        "请求超时 (已满 3 秒)"
                    } else {
                        msg
                    }
                    code = -3
                }

                val latency = System.currentTimeMillis() - startTime

                // Record to Room
                repository.insertLog(
                    MonitorLog(
                        isSuccess = success,
                        latencyMs = latency,
                        statusCode = code,
                        errorMessage = errMessage
                    )
                )

                if (success) {
                    lastCheckStatus.value = "正常"
                    updateNotificationText("监控服务正在运行", "DeepSeek 连接正常, 耗时: ${latency}ms")
                    // Success resets the state of the abnormal notification so we alert again on next fault
                    hasSentAbnormalNotification = false
                } else {
                    lastCheckStatus.value = "异常"
                    updateNotificationText("监控服务发现异常", "上次检测超时或发生错误")

                    // Alarm ONLY ONCE on continuous failure streak
                    if (!hasSentAbnormalNotification) {
                        sendAbnormalNotification(errMessage ?: "请求失败")
                        hasSentAbnormalNotification = true
                    }
                }

                // Wait 53 seconds for next check
                delay(53000L)
            }
        }
    }

    private fun stopRunning() {
        isRunningState.value = false
        lastCheckStatus.value = "已停止"
        stopForeground(true)
        stopSelf()
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotificationText(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(title, text))
    }

    private fun sendAbnormalNotification(errorDetail: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeepSeek 异常")
            .setContentText(errorDetail)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ABNORMAL_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DeepSeek API 监控服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "实时在后台监控 DeepSeek API 连通性并进行本地提示"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        isRunningState.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
