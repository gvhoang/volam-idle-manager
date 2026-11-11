package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.model.ViewLayoutMode
import com.example.ui.MainViewModel
import com.example.ui.components.AccountDialog
import com.example.ui.components.AutoConfigDialog
import com.example.ui.components.ControlSheet
import com.example.ui.components.hasDownloadTraffic
import com.example.ui.components.GameWebViewContainer
import com.example.ui.components.OledBlackScreen
import com.example.ui.components.SidebarView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while farming 24/7
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainScreen(
                    viewModel = viewModel,
                    onSetWindowBrightnessLow = { low ->
                        val layoutParams = window.attributes
                        layoutParams.screenBrightness = if (low) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = layoutParams
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.tabManager.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        viewModel.tabManager.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.tabManager.onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSetWindowBrightnessLow: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isMasterAutoRunning by viewModel.isMasterAutoRunning.collectAsState()
    val characterStatsList by viewModel.characterStatsList.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val networkData by viewModel.networkData.collectAsState()
    // Ô ⋯ đổi màu theo lưu lượng tải về (§8). Truyền Boolean thay vì cả NetworkDataInfo để
    // container không phải recompose mỗi 2 giây khi số byte đổi mà tốc độ vẫn bằng 0.
    val isDownloading = hasDownloadTraffic(networkData.downloadSpeedFormatted)
    val fps by viewModel.fps.collectAsState()
    val isSleepMode by viewModel.isSleepMode.collectAsState()
    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val editingAccount by viewModel.editingAccount.collectAsState()
    val isBlackScreenSynced by viewModel.isBlackScreenSynced.collectAsState()
    val autoConfig by viewModel.autoConfig.collectAsState()
    val isAutoDialogVisible by viewModel.isAutoDialogVisible.collectAsState()

    // Bảng điều khiển nhanh: mọi control từng nằm ở header 44dp + mini-header 28dp đều ở đây.
    val isControlSheetOpen by viewModel.isControlSheetOpen.collectAsState()
    val controlSheetTabIndex by viewModel.controlSheetTabIndex.collectAsState()
    val desktopModeStates by viewModel.desktopModeStates.collectAsState()
    val sheetTabStats = characterStatsList.getOrElse(controlSheetTabIndex) {
        com.example.model.CharacterStats(tabIndex = controlSheetTabIndex)
    }

    // Handle brightness adjustments when Sleep Mode toggles
    LaunchedEffect(isSleepMode) {
        onSetWindowBrightnessLow(isSleepMode)
    }

    // Back button handling
    BackHandler(enabled = isSleepMode || editingAccount != null || isAutoDialogVisible || isControlSheetOpen || (isPortrait && isSidebarVisible)) {
        when {
            isSleepMode -> viewModel.setSleepMode(false)
            isControlSheetOpen -> viewModel.dismissControlSheet()
            editingAccount != null -> viewModel.dismissAccountDialog()
            isAutoDialogVisible -> viewModel.dismissAutoConfigDialog()
            isPortrait && isSidebarVisible -> viewModel.toggleSidebar()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14)),
        // Không để Scaffold tự chừa inset: dải 32dp ở đáy phải là thứ duy nhất báo
        // chiều cao chrome, và nó tự lo phần nav inset của chính nó.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
            ) {
                // Main Workspace: Tối ưu cho cả màn hình dọc (RedMagic 8 Pro, Vivo X100 Ultra) và màn ngang PC
                if (isPortrait) {
                    // Màn hình dọc: GameWebView chiếm 100% toàn màn hình; Bảng điều khiển là Drawer trượt đè lên
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        GameWebViewContainer(
                            tabManager = viewModel.tabManager,
                            characterStatsList = characterStatsList,
                            selectedTabIndex = selectedTabIndex,
                            layoutMode = layoutMode,
                            isDownloading = isDownloading,
                            onSelectTab = { viewModel.selectTab(it) },
                            onOpenControlSheet = { viewModel.openControlSheet(it) },
                            onOpenSidebar = {
                                viewModel.tabManager.markUiInteraction()
                                viewModel.toggleSidebar()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isSidebarVisible) {
                            // Lớp phủ mờ (Scrim) - chạm vào đóng bảng
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { viewModel.toggleSidebar() }
                            )

                            // Bảng điều khiển (Sidebar) hiển thị đè lên bên trái với bề rộng 320dp
                            SidebarView(
                                isMasterAutoRunning = isMasterAutoRunning,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onSyncAutoMode = { viewModel.syncAutoMode(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
                                isAutoPilotOn = autoConfig.on,
                                onOpenAutoConfig = { viewModel.openAutoConfigDialog() },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(320.dp)
                                    .align(Alignment.CenterStart)
                            )
                        }
                    }
                } else {
                    // Màn hình ngang (Tablet / PC): Bảng điều khiển và WebViews nằm cạnh nhau (Row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isSidebarVisible) {
                            SidebarView(
                                isMasterAutoRunning = isMasterAutoRunning,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onSyncAutoMode = { viewModel.syncAutoMode(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
                                isAutoPilotOn = autoConfig.on,
                                onOpenAutoConfig = { viewModel.openAutoConfigDialog() },
                                modifier = Modifier
                                    .width(265.dp)
                                    .windowInsetsPadding(
                                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                    )
                            )
                        }

                        GameWebViewContainer(
                            tabManager = viewModel.tabManager,
                            characterStatsList = characterStatsList,
                            selectedTabIndex = selectedTabIndex,
                            layoutMode = layoutMode,
                            isDownloading = isDownloading,
                            onSelectTab = { viewModel.selectTab(it) },
                            onOpenControlSheet = { viewModel.openControlSheet(it) },
                            onOpenSidebar = {
                                viewModel.tabManager.markUiInteraction()
                                viewModel.toggleSidebar()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Bảng điều khiển nhanh (overlay — KHÔNG đẩy WebView xuống).
            // Đặt TRƯỚC OledBlackScreen để chế độ treo máy che lên trên; mở khoá quay lại sheet.
            ControlSheet(
                isOpen = isControlSheetOpen,
                tabIndex = controlSheetTabIndex,
                tabLabel = sheetTabStats.characterName,
                layoutMode = layoutMode,
                fps = fps,
                isDesktopMode = desktopModeStates.getOrElse(controlSheetTabIndex) { false },
                networkData = networkData,
                onOpenTab = {
                    viewModel.selectTab(controlSheetTabIndex)
                    viewModel.tabManager.openSingleTab(controlSheetTabIndex)
                },
                onToggleDesktopMode = { viewModel.toggleDesktopMode(controlSheetTabIndex) },
                onReloadTab = { viewModel.reloadTab(controlSheetTabIndex) },
                onStopTab = { viewModel.stopSingleTab(controlSheetTabIndex) },
                onChangeLayoutMode = { viewModel.setLayoutMode(it) },
                onToggleFps = { viewModel.toggleFps() },
                onActivateSleepMode = { viewModel.setSleepMode(true) },
                onOpenSidebar = {
                    // Drawer nằm dưới sheet trong cây view: đóng sheet trước rồi mới mở drawer.
                    viewModel.dismissControlSheet()
                    viewModel.toggleSidebar()
                },
                onResetNetworkStats = { viewModel.resetNetworkCounters() },
                onDismiss = { viewModel.dismissControlSheet() },
                onInteract = { viewModel.tabManager.markUiInteraction() }
            )

            // MODULE 4: OLED Black Screen Overlay (Double tap to unlock)
            if (isSleepMode) {
                OledBlackScreen(
                    onUnlock = { viewModel.setSleepMode(false) }
                )
            }

            // Edit Account Dialog
            editingAccount?.let { account ->
                AccountDialog(
                    accountConfig = account,
                    onSave = { updated -> viewModel.saveEditingAccount(updated) },
                    onDismiss = { viewModel.dismissAccountDialog() }
                )
            }

            // Bảng 🤖 Tự động (cấu hình dùng chung cho cả 4 tab)
            if (isAutoDialogVisible) {
                AutoConfigDialog(
                    config = autoConfig,
                    onSave = { updated -> viewModel.saveAutoConfig(updated) },
                    onResetCounters = { viewModel.resetAutoDailyCounters() },
                    onDismiss = { viewModel.dismissAutoConfigDialog() }
                )
            }
        }
    }
}