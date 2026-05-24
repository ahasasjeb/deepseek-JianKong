package `fun`.lzy.data

import kotlinx.coroutines.flow.Flow

class MonitorRepository(private val monitorLogDao: MonitorLogDao) {
    companion object {
        private const val DISPLAY_LOG_LIMIT = 200
        private const val STORED_LOG_LIMIT = 500
    }

    val allLogs: Flow<List<MonitorLog>> = monitorLogDao.getLatestLogs(DISPLAY_LOG_LIMIT)
    
    fun getLatestLogs(limit: Int): Flow<List<MonitorLog>> = monitorLogDao.getLatestLogs(limit)

    suspend fun insertLog(log: MonitorLog) {
        monitorLogDao.insertLog(log)
        monitorLogDao.trimToLatest(STORED_LOG_LIMIT)
    }

    suspend fun clearLogs() {
        monitorLogDao.clearAllLogs()
    }
}
