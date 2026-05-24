package `fun`.lzy.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.lzy.data.ApiKeyManager
import `fun`.lzy.data.AppDatabase
import `fun`.lzy.data.MonitorLog
import `fun`.lzy.data.MonitorRepository
import `fun`.lzy.service.MonitorForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MonitorRepository
    private val apiKeyManager: ApiKeyManager

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MonitorRepository(db.monitorLogDao())
        apiKeyManager = ApiKeyManager(application)
        _apiKeyInput.value = apiKeyManager.getApiKey()
    }

    val monitorLogs: StateFlow<List<MonitorLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isServiceRunning: StateFlow<Boolean> = MonitorForegroundService.isRunningState
    val lastCheckStatus: StateFlow<String> = MonitorForegroundService.lastCheckStatus

    fun onApiKeyChange(newKey: String) {
        _apiKeyInput.value = newKey
    }

    fun saveApiKey() {
        apiKeyManager.saveApiKey(_apiKeyInput.value)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun startService() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
