package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitor_logs")
data class MonitorLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val latencyMs: Long,
    val statusCode: Int,
    val errorMessage: String? = null,
    val model: String = "deepseek-v4-flash"
)
