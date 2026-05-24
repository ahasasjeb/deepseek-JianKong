package fun.lzy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorLogDao {
    @Query("SELECT * FROM monitor_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<MonitorLog>>

    @Query("SELECT * FROM monitor_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestLogs(limit: Int): Flow<List<MonitorLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MonitorLog)

    @Query("DELETE FROM monitor_logs")
    suspend fun clearAllLogs()
}
