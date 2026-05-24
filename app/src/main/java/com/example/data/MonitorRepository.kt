package com.example.data

import kotlinx.coroutines.flow.Flow

class MonitorRepository(private val monitorLogDao: MonitorLogDao) {
    val allLogs: Flow<List<MonitorLog>> = monitorLogDao.getAllLogs()
    
    fun getLatestLogs(limit: Int): Flow<List<MonitorLog>> = monitorLogDao.getLatestLogs(limit)

    suspend fun insertLog(log: MonitorLog) {
        monitorLogDao.insertLog(log)
    }

    suspend fun clearLogs() {
        monitorLogDao.clearAllLogs()
    }
}
