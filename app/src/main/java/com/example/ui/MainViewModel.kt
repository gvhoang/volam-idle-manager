package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.KeepAliveService
import com.example.data.PreferencesManager
import com.example.model.AccountConfig
import com.example.model.CharacterStats
import com.example.model.NetworkDataInfo
import com.example.model.ViewLayoutMode
import com.example.network.TrafficMonitor
import com.example.webview.GameTabManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)
    val trafficMonitor = TrafficMonitor(application)
    val tabManager = GameTabManager(application, prefs, viewModelScope)

    private val _networkData = MutableStateFlow(NetworkDataInfo())
    val networkData: StateFlow<NetworkDataInfo> = _networkData.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _fps = MutableStateFlow(prefs.getFps())
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isSleepMode = MutableStateFlow(false)
    val isSleepMode: StateFlow<Boolean> = _isSleepMode.asStateFlow()

    private val _isSidebarVisible = MutableStateFlow(true)
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    private val _layoutMode = MutableStateFlow(ViewLayoutMode.FOUR_COLUMNS)
    val layoutMode: StateFlow<ViewLayoutMode> = _layoutMode.asStateFlow()

    private val _cluster = MutableStateFlow(prefs.getCluster())
    val cluster: StateFlow<String> = _cluster.asStateFlow()

    private val _editingAccount = MutableStateFlow<AccountConfig?>(null)
    val editingAccount: StateFlow<AccountConfig?> = _editingAccount.asStateFlow()

    private val _isBlackScreenSynced = MutableStateFlow(false)
    val isBlackScreenSynced: StateFlow<Boolean> = _isBlackScreenSynced.asStateFlow()

    val isSystemRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val isMasterAutoRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val characterStatsList: StateFlow<List<CharacterStats>> = tabManager.characterStatsList

    init {
        // Module 5: Traffic stats loop every 2 seconds
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val rxOffset = prefs.getRxOffset()
                val txOffset = prefs.getTxOffset()
                val sampled = trafficMonitor.sample(rxOffset, txOffset)
                _networkData.value = sampled
                delay(2000)
            }
        }
    }

    // MODULE 2: Master Login All
    fun onMasterLoginAllClicked() {
        tabManager.loginAll()
        KeepAliveService.start(getApplication())
    }

    // MODULE 2: Master Stop All
    fun onMasterStopAllClicked() {
        tabManager.stopAll()
        KeepAliveService.stop(getApplication())
    }

    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }

    fun toggleSidebar() {
        _isSidebarVisible.value = !_isSidebarVisible.value
    }

    fun setLayoutMode(mode: ViewLayoutMode) {
        _layoutMode.value = mode
    }

    // Toggle 30 FPS / 60 FPS
    fun toggleFps() {
        val nextFps = if (_fps.value == 60) 30 else 60
        _fps.value = nextFps
        tabManager.applyFps(nextFps)
    }

    // Toggle Sleep Mode (OLED black screen)
    fun setSleepMode(enabled: Boolean) {
        _isSleepMode.value = enabled
        if (enabled) {
            KeepAliveService.start(getApplication())
        }
    }

    fun changeCluster(newCluster: String) {
        _cluster.value = newCluster
        tabManager.switchCluster(newCluster)
    }

    // MODULE 7: Sync auto mode (Nhiệm Vụ, Phó Bản, Luyện Công)
    fun syncAutoMode(mode: String) {
        tabManager.syncAutoMode(mode)
    }

    // Đồng bộ Màn hình đen (Hệ thống -> Hiển Thị -> Màn hình đen) cho cả 4 Tab
    fun syncBlackScreen(enabled: Boolean) {
        _isBlackScreenSynced.value = enabled
        tabManager.syncBlackScreen(enabled)
    }

    fun resetNetworkCounters() {
        val current = _networkData.value
        prefs.resetTrafficCounters(current.totalRxBytes, current.totalTxBytes)
        val refreshed = trafficMonitor.sample(current.totalRxBytes, current.totalTxBytes)
        _networkData.value = refreshed
    }

    fun startEditingAccount(tabIndex: Int) {
        _editingAccount.value = prefs.getAccount(tabIndex)
    }

    fun saveEditingAccount(config: AccountConfig) {
        prefs.saveAccount(config)
        _editingAccount.value = null
        tabManager.onAccountChanged(config.tabIndex)
    }

    fun dismissAccountDialog() {
        _editingAccount.value = null
    }

    override fun onCleared() {
        tabManager.cleanup()
        super.onCleared()
    }
}