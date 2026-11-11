package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.KeepAliveService
import com.example.VolamIdleApp
import com.example.data.PreferencesManager
import com.example.model.AccountConfig
import com.example.model.CharacterStats
import com.example.model.NetworkDataInfo
import com.example.model.TabLimits
import com.example.model.ViewLayoutMode
import com.example.network.TrafficMonitor
import com.example.update.UpdateRepository
import com.example.update.UpdateUiState
import com.example.webview.AutoConfig
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

    // ===== Tự động cập nhật =====
    // Repository sống ở Application (process-scoped): vòng kiểm tra 12h không được chết
    // theo Activity, và kết quả cài đặt của process trước nằm trong prefs nó đọc lúc tạo.
    private val updateRepository: UpdateRepository? =
        (application as? VolamIdleApp)?.updateRepository

    /** Trạng thái UI; `null` repository (môi trường test) thì đứng yên ở Idle. */
    val updateState: StateFlow<UpdateUiState> =
        updateRepository?.state ?: MutableStateFlow(UpdateUiState.Idle)

    val appVersionName: String = BuildConfig.VERSION_NAME

    private val _networkData = MutableStateFlow(NetworkDataInfo())
    val networkData: StateFlow<NetworkDataInfo> = _networkData.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _fps = MutableStateFlow(prefs.getFps())
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isSleepMode = MutableStateFlow(false)
    val isSleepMode: StateFlow<Boolean> = _isSleepMode.asStateFlow()

    // Drawer mở mặc định ở bản cũ che 89% màn hình 360dp ngay lần đầu, đúng thứ thiết kế này
    // đang cố loại bỏ. Mặc định đóng; mở bằng ô ☰ trên dải hoặc nút trong bảng điều khiển nhanh.
    private val _isSidebarVisible = MutableStateFlow(false)
    val isSidebarVisible: StateFlow<Boolean> = _isSidebarVisible.asStateFlow()

    private val _layoutMode = MutableStateFlow(ViewLayoutMode.FOUR_COLUMNS)
    val layoutMode: StateFlow<ViewLayoutMode> = _layoutMode.asStateFlow()

    private val _cluster = MutableStateFlow(prefs.getCluster())
    val cluster: StateFlow<String> = _cluster.asStateFlow()

    private val _editingAccount = MutableStateFlow<AccountConfig?>(null)
    val editingAccount: StateFlow<AccountConfig?> = _editingAccount.asStateFlow()

    private val _isBlackScreenSynced = MutableStateFlow(false)
    val isBlackScreenSynced: StateFlow<Boolean> = _isBlackScreenSynced.asStateFlow()

    // Cấu hình 🤖 Tự động: nguồn sự thật là chuỗi JSON trong PreferencesManager.
    // StateFlow chỉ là bản đọc ra cho Compose; mặc định on = false khi chưa từng lưu.
    private val _autoConfig = MutableStateFlow(AutoConfig.fromJson(prefs.getAutoConfig()))
    val autoConfig: StateFlow<AutoConfig> = _autoConfig.asStateFlow()

    private val _isAutoDialogVisible = MutableStateFlow(false)
    val isAutoDialogVisible: StateFlow<Boolean> = _isAutoDialogVisible.asStateFlow()

    // ===== Bảng điều khiển nhanh (bottom sheet) =====
    // Nội dung 44dp header cũ (FPS, treo máy, chế độ xem, 4 nút per-tab, traffic, drawer)
    // chuyển hết vào đây để chrome luôn hiện chỉ còn đúng dải 32dp ở đáy.
    // `controlSheetTabIndex` là tab đích cho nhóm nút per-tab (▶ Mở / PC / ↻ / ⏹).
    private val _isControlSheetOpen = MutableStateFlow(false)
    val isControlSheetOpen: StateFlow<Boolean> = _isControlSheetOpen.asStateFlow()

    private val _controlSheetTabIndex = MutableStateFlow(0)
    val controlSheetTabIndex: StateFlow<Int> = _controlSheetTabIndex.asStateFlow()

    // Trạng thái UA PC từng tab. GameTabManager.toggleDesktopMode() trả về trạng thái MỚI nên
    // bản sao này luôn khớp với nguồn sự thật trong tabManager; nút PC trong sheet đọc từ đây
    // thay vì tự suy ra từ màu nền nút như mini-header cũ.
    // Kích thước = SLOT_COUNT chứ không phải 4: tab 4..7 phải có ô riêng, nếu không nút PC
    // trong sheet hiện sai trạng thái (rủi ro R6).
    private val _desktopModeStates = MutableStateFlow(List(TabLimits.SLOT_COUNT) { false })
    val desktopModeStates: StateFlow<List<Boolean>> = _desktopModeStates.asStateFlow()

    // ===== Số tab 1..8 =====
    // Nguồn sự thật là GameTabManager (owner duy nhất của clamp/persist/apply). UI chỉ đọc
    // StateFlow của manager ⇒ không bao giờ hiển thị một con số khác con số đang chạy.
    val tabCount: StateFlow<Int> = tabManager.tabCountState
    val maxTabCount: Int = tabManager.maxTabCount
    val isLowRam: Boolean = tabManager.isLowRam

    private val _tabCountWarnShown = MutableStateFlow(prefs.isTabWarnShown())
    val tabCountWarnShown: StateFlow<Boolean> = _tabCountWarnShown.asStateFlow()

    val isSystemRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val isMasterAutoRunning: StateFlow<Boolean> = tabManager.isSystemRunning
    val characterStatsList: StateFlow<List<CharacterStats>> = tabManager.characterStatsList

    init {
        // Manager tự hạ 60 → 30 FPS khi vượt 4 tab; UI phải thấy giá trị mới, nếu không
        // nút FPS trong sheet hiển thị sai và lần bấm kế tiếp sẽ "tăng" lên 60.
        tabManager.onFpsChanged = { newFps -> _fps.value = newFps }

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

    // ===== MODULE 8: Tự động trong game (bảng 🤖) =====
    // Đọc lại từ prefs mỗi lần mở: nếu tiến trình từng bị thu hồi giữa chừng, hộp thoại
    // vẫn mở ra đúng cấu hình đã ghi chứ không phải bản trong bộ nhớ.
    fun openAutoConfigDialog() {
        _autoConfig.value = AutoConfig.fromJson(prefs.getAutoConfig())
        _isAutoDialogVisible.value = true
    }

    fun dismissAutoConfigDialog() {
        _isAutoDialogVisible.value = false
    }

    fun saveAutoConfig(config: AutoConfig) {
        val json = config.toJson().toString()
        prefs.setAutoConfig(json)
        _autoConfig.value = config
        _isAutoDialogVisible.value = false
        // Đẩy ngay xuống các tab đang mở, không đợi nhịp 30 giây: bấm tắt phải có hiệu lực
        // trước lần mua kế tiếp.
        tabManager.updateAutoConfig(json)
    }

    fun resetAutoDailyCounters() {
        tabManager.resetAutoDailyCounters()
    }

    // ===== Bảng điều khiển nhanh =====
    fun openControlSheet(tabIndex: Int) {
        // Một cửa duy nhất cho mọi đường mở sheet, nên chặn synthetic click luôn ở đây:
        // mở sheet là một cú chạm chrome, không đi qua WebView nên lastTouchTimeMs không tự tiến.
        tabManager.markUiInteraction()
        _controlSheetTabIndex.value = tabIndex.coerceIn(0, (tabManager.tabCountState.value - 1).coerceAtLeast(0))
        _isControlSheetOpen.value = true
    }

    fun dismissControlSheet() {
        _isControlSheetOpen.value = false
    }

    /**
     * Đổi UA PC/Mobile cho 1 tab. Trạng thái mới lấy trực tiếp từ giá trị trả về của
     * GameTabManager.toggleDesktopMode() nên UI không phải tự đoán; `desktopModeStates`
     * chỉ là bản phản chiếu để nút PC trong sheet vẽ đúng màu.
     */
    fun toggleDesktopMode(tabIndex: Int) {
        val isPc = tabManager.toggleDesktopMode(tabIndex)
        val next = _desktopModeStates.value.toMutableList()
        val safeIndex = tabIndex.coerceIn(0, next.lastIndex)
        next[safeIndex] = isPc
        _desktopModeStates.value = next
    }

    fun reloadTab(tabIndex: Int) {
        tabManager.getOrCreateWebView(tabIndex).reload()
    }

    fun stopSingleTab(tabIndex: Int) {
        tabManager.stopSingleTab(tabIndex)
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

    // ===== Số tab =====

    /**
     * Owner duy nhất là `GameTabManager.setTabCount`. UI mirror giá trị TRẢ VỀ, không mirror
     * giá trị yêu cầu: nếu máy RAM thấp chặn ở 4 thì nút hiển thị đúng 4 ngay.
     */
    fun setTabCount(requested: Int) {
        val actual = tabManager.setTabCount(requested)
        _selectedTabIndex.value = _selectedTabIndex.value.coerceIn(0, (actual - 1).coerceAtLeast(0))
        _controlSheetTabIndex.value =
            _controlSheetTabIndex.value.coerceIn(0, (actual - 1).coerceAtLeast(0))
    }

    fun markTabCountWarned() {
        prefs.setTabWarnShown(true)
        _tabCountWarnShown.value = true
    }

    /** Có tab nào trong [from, to) đang IN_GAME/LOGGING_IN? Dùng để hỏi trước khi giảm. */
    fun hasBusyTabsInRange(from: Int, toExclusive: Int): Boolean {
        val stats = characterStatsList.value
        for (i in from until toExclusive) {
            when (stats.getOrNull(i)?.state) {
                com.example.model.TabLifecycleState.IN_GAME,
                com.example.model.TabLifecycleState.LOGGING_IN -> return true
                else -> Unit
            }
        }
        return false
    }

    // ===== Tự động cập nhật: 3 cú chạm riêng biệt, không cú nào tự động hoá cú sau =====
    // 1. kiểm tra (có thể tự chạy nền) 2. tải về 3. cài đặt. Repo không bao giờ làm 2 và 3
    // mà không có lời gọi từ đây, và đây chỉ được gọi từ tap trong ControlSheet.

    fun checkForUpdate() {
        viewModelScope.launch { updateRepository?.checkForUpdate(manual = true) }
    }

    fun startUpdateDownload() {
        viewModelScope.launch { updateRepository?.download() }
    }

    fun installUpdate() {
        viewModelScope.launch { updateRepository?.install() }
    }

    fun onActivityResumed() {
        // Kiểm lại AppOp cài đặt (không tin người dùng đã bật) + không tự cài.
        updateRepository?.onAppResumed()
    }

    fun releasesPageUrl(): String =
        updateRepository?.releasesPageUrl() ?: "https://github.com/gvhoang/volam-idle-manager/releases"

    fun shouldShowUpdateBanner(): Boolean = updateRepository?.shouldShowBanner() ?: false

    fun markUpdateBannerShown() {
        updateRepository?.markBannerShown()
    }

    /** Thông điệp "đã cập nhật" của process trước; đọc một lần rồi xoá. */
    fun consumeLaunchMessage(): String? = updateRepository?.consumeLaunchMessage()

    override fun onCleared() {
        tabManager.cleanup()
        super.onCleared()
    }
}