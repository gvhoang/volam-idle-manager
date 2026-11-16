package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.update.AppVisibility
import com.example.update.UpdateUiState
import kotlinx.coroutines.delay
import androidx.compose.foundation.border

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
        // AppOp "Cài đặt ứng dụng không rõ nguồn gốc" không có callback kết quả: kiểm lại
        // ở đây. KHÔNG tự cài — chỉ đưa UI về trạng thái bấm được "Cài đặt".
        viewModel.onActivityResumed()
        viewModel.tabManager.onAppForegrounded()
    }

    override fun onStart() {
        super.onStart()
        // PackageInstaller chỉ được commit từ Activity đang STARTED (Android 10+ chặn BAL),
        // và STATUS_PENDING_USER_ACTION chỉ được startActivity khi app visible.
        AppVisibility.onActivityStarted()
    }

    override fun onPause() {
        super.onPause()
        viewModel.tabManager.onAppBackgrounded()
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.onActivityStopped()
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
    val isFullScreen by viewModel.isFullScreen.collectAsState()
    val isSleepMode by viewModel.isSleepMode.collectAsState()
    val isSidebarVisible by viewModel.isSidebarVisible.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val editingAccount by viewModel.editingAccount.collectAsState()
    val isBlackScreenSynced by viewModel.isBlackScreenSynced.collectAsState()
    val autoConfig by viewModel.autoConfig.collectAsState()
    val isAutoDialogVisible by viewModel.isAutoDialogVisible.collectAsState()
    val tabCount by viewModel.tabCount.collectAsState()
    val tabCountWarnShown by viewModel.tabCountWarnShown.collectAsState()

    // Bảng điều khiển nhanh: mọi control từng nằm ở header 44dp + mini-header 28dp đều ở đây.
    val isControlSheetOpen by viewModel.isControlSheetOpen.collectAsState()
    val controlSheetTabIndex by viewModel.controlSheetTabIndex.collectAsState()
    val desktopModeStates by viewModel.desktopModeStates.collectAsState()
    val sheetTabStats = characterStatsList.getOrElse(controlSheetTabIndex) {
        com.example.model.CharacterStats(tabIndex = controlSheetTabIndex)
    }

    // ===== Tự động cập nhật: banner nhắc không chặn (32dp) =====
    // Chỉ nhắc khi CÓ bản mới và tối đa 1 lần / versionCode. Ẩn khi Treo máy (màn hình cố ý
    // đen 0.01), không dialog, không cướp focus, tự ẩn sau 8s.
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    var updateBanner by remember { mutableStateOf<String?>(null) }

    // Hộp thoại nhắc cập nhật khi MỞ APP (không cần mở bảng điều khiển). Khác banner 32dp:
    // chỉ hiện một lần / versionCode, không hiện khi Treo máy, và chỉ có hai lựa chọn.
    val foregroundTick by viewModel.foregroundTick.collectAsState()
    var updateNoticeVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(updateState, isSleepMode, updateNoticeVersion) {
        // Hộp thoại nhắc đang mở thì không vẽ thêm banner 32dp phía sau nó. Đóng hộp thoại
        // ("Để sau") sẽ chạy lại hiệu ứng này và banner có thể hiện — hộp thoại thì không lặp lại.
        if (isSleepMode || updateNoticeVersion != null) {
            updateBanner = null
            return@LaunchedEffect
        }
        val state = updateState
        if (state is UpdateUiState.Available && viewModel.shouldShowUpdateBanner()) {
            viewModel.markUpdateBannerShown()
            updateBanner = context.getString(R.string.update_banner_available, state.versionName)
        }
    }

    // Thông điệp "đã cập nhật lên X" của process trước (kết quả cài tới muộn).
    LaunchedEffect(Unit) {
        viewModel.consumeLaunchMessage()?.let { message -> updateBanner = message }
    }

    // Xét nhắc cập nhật ở BA thời điểm: composition đầu (cold start đọc release từ cache),
    // mỗi lần Activity resume (foregroundTick), và khi trạng thái chuyển sang Available do
    // lần kiểm tra 3 giây sau khởi động. `takeUpdateNotice()` tự đánh dấu đã nhắc nên ba
    // nguồn này không thể hiện hai lần cho cùng một versionCode.
    // Treo máy thì bỏ qua và KHÔNG đánh dấu: đèn hình đang cố ý tắt.
    LaunchedEffect(foregroundTick, updateState is UpdateUiState.Available, isSleepMode) {
        if (isSleepMode || updateNoticeVersion != null) return@LaunchedEffect
        viewModel.takeUpdateNotice()?.let { version -> updateNoticeVersion = version }
    }

    // Hẹn giờ tách riêng, key theo CHÍNH nội dung banner: nếu một hiệu ứng khác đổi banner
    // thì đồng hồ cũ bị huỷ và đặt lại — không có đường nào để banner kẹt vĩnh viễn.
    LaunchedEffect(updateBanner) {
        if (updateBanner != null) {
            delay(8_000)
            updateBanner = null
        }
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
                            tabCount = tabCount,
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
                            isFullScreen = isFullScreen,
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
                                tabCount = tabCount,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
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
                                tabCount = tabCount,
                                characterStatsList = characterStatsList,
                                selectedTabIndex = selectedTabIndex,
                                onSelectTab = { viewModel.selectTab(it) },
                                onLoginAll = { viewModel.onMasterLoginAllClicked() },
                                onStopAll = { viewModel.onMasterStopAllClicked() },
                                onEditAccount = { viewModel.startEditingAccount(it) },
                                onToggleSidebar = { viewModel.toggleSidebar() },
                                getAccount = { viewModel.prefs.getAccount(it) },
                                isBlackScreenSynced = isBlackScreenSynced,
                                onSyncBlackScreen = { viewModel.syncBlackScreen(it) },
                                modifier = Modifier
                                    .width(265.dp)
                                    .windowInsetsPadding(
                                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                    )
                            )
                        }

                        GameWebViewContainer(
                            tabManager = viewModel.tabManager,
                            tabCount = tabCount,
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
                            isFullScreen = isFullScreen,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Toàn màn hình: dải 32dp KHÔNG được dựng nên đây là đường duy nhất lấy lại
            // thanh điều khiển. Đặt ngay sau Column game và TRƯỚC banner/sheet/OLED: nó là
            // lớp phủ duy nhất trên game, và mọi overlay khác phải vẽ đè lên nó.
            //
            // Ba trạng thái ẩn, mỗi cái vì nút không được nổi trên một bề mặt khác:
            //  • Treo máy — màn hình đó cố ý tắt mọi thứ hiển thị.
            //  • Sheet đang mở — sheet đã có chính toggle này; nút nằm trong vùng scrim sẽ
            //    thành một nút lơ lửng trên nền mờ (và sheet có thể thấp hơn 0.46 màn hình).
            //  • Drawer đang mở — drawer đè 320dp từ trái; ở màn 360dp nút 36dp ở mép phải
            //    rơi ĐÚNG lên panel drawer (drawer được vẽ trước nút trong cây view).
            // Ẩn nút không làm kẹt ai: cả hai bề mặt đó đều có nút đóng riêng, đóng xong
            // nút hiện lại ngay. Trạng thái toàn màn hình vẫn chỉ do ViewModel quyết định.
            if (isFullScreen && !isSleepMode && !isControlSheetOpen && !isSidebarVisible) {
                FullScreenRestoreButton(
                    onClick = {
                        // Chạm chrome: đẩy lastTouchTimeMs như mọi ô trên dải, để watchdog
                        // không bấm tổng hợp ngay sau cú chạm này.
                        viewModel.tabManager.markUiInteraction()
                        viewModel.setFullScreen(false)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Đặt trong vùng game nhưng TRÊN nav bar: inset do hệ thống, không phải
                        // chrome quản lý, nên lưới game kết thúc đúng chỗ cũ (chỉ cao thêm 32dp).
                        .navigationBarsPadding()
                        // 88dp: nhảy qua hàng HUD đáy của game (cần điều khiển + thanh kỹ năng
                        // + nút ⚙ Hệ thống nằm ở đáy màn hình game — xem GameScriptInjector),
                        // nên không đè lên pad. 8dp mép phải để góc dưới vẫn chạm được.
                        .padding(end = 8.dp, bottom = 88.dp)
                )
            }

            // Banner cập nhật: overlay 32dp ở ĐỈNH, không đẩy WebView xuống (không reflow HUD).
            updateBanner?.takeIf { !isSleepMode }?.let { text ->
                val bannerShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp)
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(bannerShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF38BDF8), bannerShape)
                        .clickable {
                            viewModel.tabManager.markUiInteraction()
                            updateBanner = null
                            viewModel.openControlSheet(selectedTabIndex)
                        }
                        .padding(horizontal = 10.dp)
                        .testTag("update_banner"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = text,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                isFullScreen = isFullScreen,
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
                onToggleFullScreen = { viewModel.toggleFullScreen() },
                onActivateSleepMode = { viewModel.setSleepMode(true) },
                onOpenSidebar = {
                    // Drawer nằm dưới sheet trong cây view: đóng sheet trước rồi mới mở drawer.
                    viewModel.dismissControlSheet()
                    viewModel.toggleSidebar()
                },
                onResetNetworkStats = { viewModel.resetNetworkCounters() },
                onSyncAutoMode = { viewModel.syncAutoMode(it) },
                isAutoPilotOn = autoConfig.on,
                onOpenAutoConfig = {
                    // Drawer nằm dưới sheet: đóng sheet trước để hộp thoại không bị che.
                    viewModel.dismissControlSheet()
                    viewModel.openAutoConfigDialog()
                },
                tabCount = tabCount,
                maxTabCount = viewModel.maxTabCount,
                isLowRam = viewModel.isLowRam,
                tabCountWarnShown = tabCountWarnShown,
                hasBusyTabsInRange = { from, toExclusive ->
                    viewModel.hasBusyTabsInRange(from, toExclusive)
                },
                onSetTabCount = { viewModel.setTabCount(it) },
                onMarkTabCountWarned = { viewModel.markTabCountWarned() },
                appVersionName = viewModel.appVersionName,
                updateState = updateState,
                onCheckUpdate = { viewModel.checkForUpdate() },
                onStartDownload = { viewModel.startUpdateDownload() },
                onInstallUpdate = { viewModel.installUpdate() },
                onOpenReleases = {
                    val open = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(viewModel.releasesPageUrl())
                    )
                    runCatching { context.startActivity(open) }
                },
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
                    onDelete = { deleted -> viewModel.deleteAccount(deleted.tabIndex) },
                    onDismiss = { viewModel.dismissAccountDialog() }
                )
            }

            // Bảng 🤖 Tự động (cấu hình dùng chung cho cả 4 tab)
            if (isAutoDialogVisible) {
                AutoConfigDialog(
                    config = autoConfig,
                    tabCount = tabCount,
                    onSave = { updated -> viewModel.saveAutoConfig(updated) },
                    onResetCounters = { viewModel.resetAutoDailyCounters() },
                    onDismiss = { viewModel.dismissAutoConfigDialog() }
                )
            }

            // Nhắc cập nhật khi mở app: đúng hai lựa chọn. "Cập nhật" đi vào luồng tải/cài
            // hiện có trong bảng điều khiển; "Để sau" chỉ đóng hộp thoại — banner 32dp có thể
            // nhắc lại, nhưng hộp thoại này thì không (cờ update_dialog_shown_for theo versionCode).
            updateNoticeVersion?.let { version ->
                AlertDialog(
                    onDismissRequest = { updateNoticeVersion = null },
                    containerColor = Color(0xFF1E293B),
                    title = {
                        Text(
                            text = stringResource(R.string.update_notice_title, version),
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(
                                R.string.update_notice_body,
                                viewModel.appVersionName
                            ),
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                updateNoticeVersion = null
                                viewModel.onUpdateNoticeAccepted()
                            },
                            modifier = Modifier.testTag("update_notice_update")
                        ) {
                            Text(
                                text = stringResource(R.string.update_notice_update),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { updateNoticeVersion = null },
                            modifier = Modifier.testTag("update_notice_later")
                        ) {
                            Text(
                                text = stringResource(R.string.update_notice_later),
                                fontSize = 11.sp
                            )
                        }
                    }
                )
            }
        }
    }
}

// =============================================================================
// NÚT KHÔI PHỤC THANH ĐIỀU KHIỂN (chế độ toàn màn hình)
//
// Vì sao đúng 36dp và KHÔNG có vùng chạm nới rộng: đây là lớp phủ DUY NHẤT nằm trên
// WebView, nên mỗi dp thừa là một dp cướp chạm của game. `clickable` gắn sau `size(36.dp)`
// nên vùng nhận chạm đúng bằng hình vẽ; chạm ngoài nó vẫn rơi xuống WebView.
//
// Alpha 0.55 lúc nghỉ, 0.9 khi đang giữ: nút phải nhìn thấy được (nếu không, người dùng
// kẹt trong chế độ toàn màn hình mà không biết đường ra) nhưng không được tranh sáng với
// HUD của game. Đây là thứ duy nhất khiến "không có chrome" vẫn thoát ra được.
// =============================================================================
@Composable
private fun FullScreenRestoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.cd_fullscreen_restore)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 0.55f,
        animationSpec = tween(durationMillis = 120),
        label = "fullscreen_restore_alpha"
    )
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF38BDF8), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .testTag("fullscreen_restore_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(18.dp)
        )
    }
}