package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.ViewLayoutMode
import com.example.webview.GameTabManager

class TabCardViewHolder(
    val root: LinearLayout,
    val idleOverlay: LinearLayout,
    val idleOpenBtn: Button,
    val idleTitle: TextView
)

@Composable
fun GameWebViewContainer(
    tabManager: GameTabManager,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    layoutMode: ViewLayoutMode,
    isDownloading: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenControlSheet: (Int) -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // THUỐC ĐẶC TRỊ SỐ 2: Cấp Window Token thực tế (Activity) cho WebView 
    // để Android OS cho phép tạo cửa sổ Popup <select> thay vì bị crash/đóng lập tức.
    tabManager.updateActivityContext(context)

    // Giữ nguyên thực thể (Instance) của 4 TabContainerView
    val tabCardHolders = remember {
        Array(4) { i ->
            createTabCardViewHolder(context, i, tabManager, onSelectTab)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .testTag("webview_game_container")
    ) {
        AndroidView(
            factory = { ctx ->
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#090D14"))
                }
            },
            update = { rootLayout ->
                // =========================================================================
                // QUY CHUẨN 2: CHUYỂN ĐỔI CHẾ ĐỘ 4 CỘT DỌC (1x4), LƯỚI 2x2, VÀ 1 TAB TOÀN MÀN
                // Chỉ tháo và gắn lại view KHI CHẾ ĐỘ HIỂN THỊ HOẶC TAB ĐƯỢC CHỌN THAY ĐỔI.
                // Tránh gọi removeAllViews() liên tục gây giật / nháy màn hình!
                // =========================================================================
                val currentLayoutKey = "${layoutMode.name}_${if (layoutMode == ViewLayoutMode.SINGLE_TAB) selectedTabIndex else -1}"
                val lastLayoutKey = rootLayout.tag as? String

                if (lastLayoutKey != currentLayoutKey || rootLayout.childCount == 0) {
                    rootLayout.tag = currentLayoutKey

                    for (holder in tabCardHolders) {
                        (holder.root.parent as? ViewGroup)?.removeView(holder.root)
                    }
                    rootLayout.removeAllViews()

                    when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> {
                            // Chế độ 1: 4 Cột Dọc (1 Hàng x 4 Cột song song)
                            rootLayout.orientation = LinearLayout.HORIZONTAL
                            rootLayout.weightSum = 4f

                            for (i in 0..3) {
                                val holder = tabCardHolders[i]
                                val params = LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                ).apply {
                                    if (i > 0) leftMargin = 3
                                }
                                holder.root.layoutParams = params
                                rootLayout.addView(holder.root)
                            }
                        }

                        ViewLayoutMode.TWO_BY_TWO -> {
                            // Chế độ 2: Lưới 2x2 (2 Hàng ngang x 2 Cột)
                            rootLayout.orientation = LinearLayout.VERTICAL
                            rootLayout.weightSum = 2f

                            val rowTop = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                weightSum = 2f
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    0,
                                    1f
                                )
                            }

                            val rowBottom = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                weightSum = 2f
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    0,
                                    1f
                                ).apply {
                                    topMargin = 3
                                }
                            }

                            // Hàng trên: Tab 0 & Tab 1
                            for (i in 0..1) {
                                val holder = tabCardHolders[i]
                                val params = LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                ).apply {
                                    if (i == 1) leftMargin = 3
                                }
                                holder.root.layoutParams = params
                                rowTop.addView(holder.root)
                            }

                            // Hàng dưới: Tab 2 & Tab 3
                            for (i in 2..3) {
                                val holder = tabCardHolders[i]
                                val params = LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                ).apply {
                                    if (i == 3) leftMargin = 3
                                }
                                holder.root.layoutParams = params
                                rowBottom.addView(holder.root)
                            }

                            rootLayout.addView(rowTop)
                            rootLayout.addView(rowBottom)
                        }

                        ViewLayoutMode.SINGLE_TAB -> {
                            // Chế độ 3: 1 Tab (Toàn màn hình) - Hiển thị duy nhất tab được chọn
                            rootLayout.orientation = LinearLayout.VERTICAL
                            rootLayout.weightSum = 1f

                            val activeIndex = selectedTabIndex.coerceIn(0, 3)
                            val holder = tabCardHolders[activeIndex]
                            val params = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                1f
                            )
                            holder.root.layoutParams = params
                            rootLayout.addView(holder.root)
                        }
                    }

                    // Căn chỉnh lại Viewport mà không reload trang (chỉ gọi khi đổi chế độ)
                    tabManager.adjustAllViewports()
                }

                // Cập nhật trạng thái hiển thị của từng Tab và Idle Overlay trực tiếp tại chỗ (không remove/add View)
                // (Mini-header 28dp đã bỏ: trạng thái tab do dải 32dp ở đáy báo.
                //  Khối idle overlay bên dưới giữ nguyên — nút [▶ MỞ TAB N] vẫn tại chỗ.)
                for (i in 0..3) {
                    val holder = tabCardHolders[i]
                    val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }

                    // Hiển thị màn hình chờ (Idle) hay WebView
                    if (stats.state == TabLifecycleState.IDLE || stats.state == TabLifecycleState.OFFLINE) {
                        if (holder.idleOverlay.visibility != View.VISIBLE) {
                            holder.idleOverlay.visibility = View.VISIBLE
                        }
                        val desiredIdleText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "Tab ${i + 1}\nChờ mở"
                        } else {
                            "Tab ${i + 1}: Chờ mở tab (Idle)"
                        }
                        if (holder.idleTitle.text != desiredIdleText) {
                            holder.idleTitle.text = desiredIdleText
                        }
                        val desiredBtnText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "▶ Mở"
                        } else {
                            "▶ MỞ TAB ${i + 1}"
                        }
                        if (holder.idleOpenBtn.text != desiredBtnText) {
                            holder.idleOpenBtn.text = desiredBtnText
                        }
                    } else {
                        if (holder.idleOverlay.visibility != View.GONE) {
                            holder.idleOverlay.visibility = View.GONE
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Dải trạng thái 32dp: sibling của AndroidView, KHÔNG phải overlay.
        // Là view Compose riêng nằm dưới WebView trong Column nên không bao giờ đè lên
        // vùng chạm của game; WebView giữ đúng bề rộng và nhận trọn phần còn lại.
        GameStatusStrip(
            tabManager = tabManager,
            characterStatsList = characterStatsList,
            selectedTabIndex = selectedTabIndex,
            isDownloading = isDownloading,
            onSelectTab = onSelectTab,
            onOpenSidebar = onOpenSidebar,
            onOpenControlSheet = onOpenControlSheet
        )
    }
}

private fun createTabCardViewHolder(
    context: Context,
    tabIndex: Int,
    tabManager: GameTabManager,
    onSelectTab: (Int) -> Unit
): TabCardViewHolder {
    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AndroidColor.parseColor("#0F172A"))
    }

    val density = context.resources.displayMetrics.density

    // Khung chứa WebView + Idle Overlay
    val contentContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        setBackgroundColor(AndroidColor.parseColor("#090D14"))
    }

    // Gắn WebView độc lập của Tab trực tiếp vào khung chứa.
    // (Đã bỏ lớp wrapper chặn onMeasure: View.measure() vốn đã bỏ qua khi spec không đổi,
    //  còn wrapper thì có thể đóng băng một kích thước sai sau khi đổi layout.)
    val webView = tabManager.getOrCreateWebView(tabIndex)
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    contentContainer.addView(webView)

    // Ô hiển thị này chỉ được tạo 1 lần (remember), nên khi GameTabManager phải tạo lại WebView
    // (bị thu hồi RAM / đổi Cụm) UI phải tự gắn WebView mới vào đúng ô.
    // Chèn ở index 0 để không đè lên idleOverlay đang nằm phía trên.
    var attachedWebView: WebView = webView
    tabManager.registerSlot(tabIndex) { newWebView ->
        if (newWebView !== attachedWebView) {
            (attachedWebView.parent as? ViewGroup)?.removeView(attachedWebView)
            attachedWebView = newWebView
        }
        (newWebView.parent as? ViewGroup)?.removeView(newWebView)
        contentContainer.addView(
            newWebView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    // GIAI ĐOẠN 1: Màn hình chờ (Idle Overlay) có nút [▶ Mở Tab X]
    val idleOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val pad = (4 * density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(AndroidColor.parseColor("#0B0F19"))
    }

    val idleTitle = TextView(context).apply {
        gravity = Gravity.CENTER
        text = "Tab ${tabIndex + 1}: Chờ mở tab (Idle)"
        setTextColor(AndroidColor.parseColor("#94A3B8"))
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    idleOverlay.addView(idleTitle)

    val idleOpenBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (32 * density).toInt()
        ).apply {
            topMargin = (8 * density).toInt()
        }
        val btnPadH = (8 * density).toInt()
        setPadding(btnPadH, 0, btnPadH, 0)
        minWidth = (48 * density).toInt()
        minimumWidth = (48 * density).toInt()
        text = "▶ MỞ TAB ${tabIndex + 1}"
        textSize = 9.5f
        isSingleLine = true
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#15803D"))
        setOnClickListener {
            onSelectTab(tabIndex)
            tabManager.openSingleTab(tabIndex)
        }
    }
    idleOverlay.addView(idleOpenBtn)

    contentContainer.addView(idleOverlay)
    card.addView(contentContainer)

    return TabCardViewHolder(
        root = card,
        idleOverlay = idleOverlay,
        idleOpenBtn = idleOpenBtn,
        idleTitle = idleTitle
    )
}