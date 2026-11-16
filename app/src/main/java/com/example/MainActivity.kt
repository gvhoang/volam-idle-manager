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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
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
    // Vị trí nút khôi phục (phân số của vùng vẽ được). null = chưa từng kéo ⇒ nút tự dùng
    // vị trí mặc định, đúng chỗ bản trước khi kéo được đứng.
    val fullScreenButtonPos by viewModel.fullScreenButtonPos.collectAsState()
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
            // Nút KÉO ĐƯỢC tới bất kỳ đâu trong vùng dùng được và nhớ chỗ đứng qua các lần
            // mở app: PK mode có thêm cụm kỹ năng/nút đánh ở đáy phải, nên một vị trí cố định
            // 88dp nào đó cũng sẽ đè lên HUD của một chế độ nào đó.
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
                    positionFraction = fullScreenButtonPos,
                    onTap = {
                        // Chạm chrome: đẩy lastTouchTimeMs như mọi ô trên dải, để watchdog
                        // không bấm tổng hợp ngay sau cú chạm này.
                        viewModel.tabManager.markUiInteraction()
                        viewModel.setFullScreen(false)
                    },
                    onPositionCommitted = { x, y -> viewModel.setFullScreenButtonPos(x, y) },
                    // fillMaxSize: composable cần kích thước vùng vẽ để quy đổi phân số ↔ pixel.
                    // Nó KHÔNG gắn pointerInput ở lớp ngoài nên vẫn trong suốt với chạm; chỉ
                    // ô 36dp bên trong nhận con trỏ.
                    modifier = Modifier.fillMaxSize()
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
// NÚT KHÔI PHỤC THANH ĐIỀU KHIỂN (chế độ toàn màn hình) — KÉO ĐƯỢC
//
// Vì sao đúng 36dp và KHÔNG có vùng chạm nới rộng: đây là lớp phủ DUY NHẤT nằm trên
// WebView, nên mỗi dp thừa là một dp cướp chạm của game. `pointerInput` gắn SAU `size(36.dp)`
// nên vùng nhận con trỏ đúng bằng hình vẽ; chạm ngoài nó vẫn rơi xuống WebView.
// `BoxWithConstraints` bọc ngoài chỉ để đọc kích thước vùng vẽ — nó không có modifier chạm
// nào nên vẫn trong suốt với con trỏ, không phải một "bẫy chạm" toàn màn hình.
//
// MỘT cử chỉ, MỘT quyết định: vượt slop ⇒ kéo (và chiếm con trỏ), chưa vượt slop mà nhả
// tay ⇒ tap. Không dùng detectTapGestures + detectDragGestures song song: hai bộ dò cùng
// nhận một sự kiện thì cú kéo có thể kết thúc bằng một onTap. Ở đây nhánh tap và nhánh kéo
// loại trừ nhau trong CÙNG một vòng lặp nên không có đường nào phát hai lần.
//
// Vị trí sống dưới dạng PHÂN SỐ 0f..1f của vùng dùng được (không phải pixel) để xoay màn
// hình / đổi mật độ / đổi cỡ chữ / đổi cỡ cửa sổ vẫn giữ đúng chỗ tương đối. Nguồn sự thật
// là ViewModel (đọc từ prefs); nút chỉ giữ một vị trí pixel TẠM trong lúc kéo.
//
// Alpha 0.55 lúc nghỉ, 0.9 khi đang kéo: nút phải nhìn thấy được (nếu không, người dùng
// kẹt trong chế độ toàn màn hình mà không biết đường ra) nhưng không được tranh sáng với
// HUD của game. Đây là thứ duy nhất khiến "không có chrome" vẫn thoát ra được.
// =============================================================================

/**
 * Vùng nút được phép đứng, tính bằng pixel cửa sổ.
 *
 * [maxX]/[maxY] là độ dời LỚN NHẤT của góc trên-trái (đã trừ kích thước nút), nên vùng nhận
 * được luôn đúng 0f..1f. Nhờ vậy phân số ghi vào prefs không cần biết kích thước màn hình,
 * mật độ hay cỡ cửa sổ.
 */
private data class RestoreButtonBounds(
    val left: Float,
    val top: Float,
    val maxX: Float,
    val maxY: Float
) {
    /** Giữ nút nằm TRỌN trong vùng — cả lúc kéo lẫn khi kích thước vùng vừa đổi. */
    fun clamp(position: Offset): Offset = Offset(
        position.x.coerceIn(left, left + maxX),
        position.y.coerceIn(top, top + maxY)
    )

    /** Pixel → phân số. `maxX`/`maxY` = 0 (cửa sổ hẹp hơn nút) thì trả 0, không chia 0. */
    fun toFraction(position: Offset): Pair<Float, Float> = Pair(
        if (maxX > 0f) ((position.x - left) / maxX).coerceIn(0f, 1f) else 0f,
        if (maxY > 0f) ((position.y - top) / maxY).coerceIn(0f, 1f) else 0f
    )
}

@Composable
private fun FullScreenRestoreButton(
    positionFraction: Pair<Float, Float>?,
    onTap: () -> Unit,
    onPositionCommitted: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.cd_fullscreen_restore)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var isDragging by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    BoxWithConstraints(modifier = modifier) {
        // Vùng dùng được = đúng vùng lưới game chiếm: Column của màn hình đã trừ safeDrawing
        // ở trên/cạnh, container game giữ inset nav bar ở đáy. Ở đây lấy systemBars ∪
        // displayCutout chứ KHÔNG lấy safeDrawing: safeDrawing gồm cả IME, mà bàn phím
        // thuộc cửa sổ hộp thoại tài khoản — nút không được nhảy chỗ khi đang gõ.
        val usableInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val buttonSizePx = with(density) { 36.dp.toPx() }
        val areaLeft = usableInsets.getLeft(density, layoutDirection).toFloat()
        val areaTop = usableInsets.getTop(density).toFloat()
        val areaRight =
            constraints.maxWidth.toFloat() - usableInsets.getRight(density, layoutDirection)
        val areaBottom = constraints.maxHeight.toFloat() - usableInsets.getBottom(density)
        val maxX = (areaRight - areaLeft - buttonSizePx).coerceAtLeast(0f)
        val maxY = (areaBottom - areaTop - buttonSizePx).coerceAtLeast(0f)

        // Chưa từng kéo: quy đổi ĐÚNG vị trí của bản trước khi kéo được (mép phải, cách đáy
        // 88dp) thành phân số, nên lần vẽ đầu tiên giống hệt bản cũ chứ không nhảy vào góc.
        val marginEndPx = with(density) { 8.dp.toPx() }
        val marginBottomPx = with(density) { 88.dp.toPx() }
        val defaultFractionX = if (maxX > 0f) ((maxX - marginEndPx) / maxX) else 0f
        val defaultFractionY = if (maxY > 0f) ((maxY - marginBottomPx) / maxY) else 0f
        val fractionX = (positionFraction?.first ?: defaultFractionX).coerceIn(0f, 1f)
        val fractionY = (positionFraction?.second ?: defaultFractionY).coerceIn(0f, 1f)
        val derivedPos = Offset(areaLeft + fractionX * maxX, areaTop + fractionY * maxY)

        // Vị trí pixel chỉ tồn tại TRONG lúc kéo. Giữ trong State (không phải biến của
        // composition) để mỗi khung hình kéo chỉ chạy lại layout; State cũng không bị "đóng
        // băng" giá trị của lần composition đầu như một biến thường nằm trong pointerInput.
        val dragPos = remember { mutableStateOf<Offset?>(null) }
        val boundsState = rememberUpdatedState(RestoreButtonBounds(areaLeft, areaTop, maxX, maxY))
        val derivedState = rememberUpdatedState(derivedPos)
        val commitState = rememberUpdatedState(onPositionCommitted)
        // pointerInput(Unit) chỉ chạy khối lambda ở lần composition đầu, nên callback phải
        // đọc qua State: đó là lý do có `tapState` chứ không gọi thẳng `onTap`.
        val tapState = rememberUpdatedState(onTap)

        // Phân số (nguồn sự thật) hoặc kích thước vùng vừa đổi — thả tay, xoay màn hình,
        // đổi cỡ chữ, bật/tắt cửa sổ chia đôi: bỏ vị trí tạm để vị trí được suy lại từ phân
        // số trên vùng mới. Nhờ vậy nút không bao giờ giữ pixel của một cấu hình cũ.
        LaunchedEffect(fractionX, fractionY, maxX, maxY) {
            dragPos.value = null
        }

        val alpha by animateFloatAsState(
            targetValue = if (isDragging) 0.9f else 0.55f,
            animationSpec = tween(durationMillis = 120),
            label = "fullscreen_restore_alpha"
        )
        val buttonScale by animateFloatAsState(
            targetValue = if (isDragging) 1.06f else 1f,
            animationSpec = tween(durationMillis = 120),
            label = "fullscreen_restore_scale"
        )

        Box(
            modifier = Modifier
                // Đọc State ngay trong lambda layout: kéo chỉ re-layout, không recompose cả
                // nút. Clamp ở đây là lưới an toàn cho cấu hình đổi giữa hai khung hình.
                .offset {
                    val clamped = boundsState.value.clamp(dragPos.value ?: derivedState.value)
                    IntOffset(clamped.x.roundToInt(), clamped.y.roundToInt())
                }
                // Vùng nhận chạm = ĐÚNG 36dp: `size` đứng trước `pointerInput`, và lớp vẽ
                // (alpha/scale) nằm ở Box con bên trong. Gắn scale lên chính node này thì
                // hit-test đi theo phép biến đổi và vùng chạm nới ra ~1dp khi đang kéo.
                .size(36.dp)
                .pointerInput(Unit) {
                    // ~8dp của hệ thống (ViewConfiguration.touchSlop), không tự đặt số khác:
                    // ngưỡng phải khớp cảm giác của mọi cử chỉ khác trên máy.
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        // requireUnconsumed = false: nút là lớp trên cùng nên `down` luôn tới
                        // đây, nhưng không được từ chối nếu một lớp khác đã chạm vào nó.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            // Con trỏ của cử chỉ này biến mất (hệ thống thu hồi) ⇒ dừng.
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragging) {
                                // Lớp khác (vd. gesture cạnh màn hình) đã nhận sự kiện.
                                if (event.changes.any { it.isConsumed }) break
                                travelled += change.positionChange()
                                if (!change.pressed) {
                                    // Nhả tay TRƯỚC khi vượt slop ⇒ tap, không phải kéo.
                                    if (travelled.getDistance() <= touchSlop) tapState.value()
                                    break
                                }
                                if (travelled.getDistance() > touchSlop) {
                                    dragging = true
                                    isDragging = true
                                    dragPos.value = boundsState.value.clamp(
                                        (dragPos.value ?: derivedState.value) + travelled
                                    )
                                    // Từ đây con trỏ thuộc về nút: game không nhận phần kéo.
                                    change.consume()
                                    travelled = Offset.Zero
                                }
                            } else {
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    dragPos.value = boundsState.value.clamp(
                                        (dragPos.value ?: derivedState.value) + delta
                                    )
                                    change.consume()
                                }
                                if (!change.pressed) break
                            }
                        }
                        if (dragging) {
                            isDragging = false
                            // Ghi MỘT lần lúc thả tay, dưới dạng phân số (không ghi pixel).
                            dragPos.value?.let { finalPos ->
                                val (fractionXFinal, fractionYFinal) =
                                    boundsState.value.toFraction(finalPos)
                                commitState.value(fractionXFinal, fractionYFinal)
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    // Giữ action "bấm" cho TalkBack và UI test: pointerInput không tự tạo
                    // action ngữ nghĩa như `clickable` từng làm.
                    onClick(label = description) {
                        onTap()
                        true
                    }
                }
                .testTag("fullscreen_restore_button")
        ) {
            // Chỉ lớp vẽ bị phóng to khi kéo; vùng nhận chạm ở trên vẫn đúng 36dp.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .clip(shape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF38BDF8), shape),
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
    }
}