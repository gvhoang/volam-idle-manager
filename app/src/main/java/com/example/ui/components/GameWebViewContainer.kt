package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.ViewLayoutMode
import com.example.webview.GameTabManager

class TabCardViewHolder(
    val root: LinearLayout,
    val titleText: TextView,
    val statusDot: View,
    val btnOpen: Button,
    val btnPc: Button,
    val btnReload: Button,
    val btnStop: Button,
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
    onSelectTab: (Int) -> Unit,
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
        // Thanh Tab điều hướng khi ở chế độ xem 1 Tab
        if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("single_tab_navigation_bar"),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0..3).forEach { i ->
                        val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }
                        val isSelected = (selectedTabIndex == i)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelectTab(i) }
                                .testTag("single_tab_selector_chip_$i"),
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            color = if (stats.state == TabLifecycleState.IN_GAME) Color(0xFF10B981)
                                            else if (stats.state == TabLifecycleState.LOGGING_IN) Color(0xFFF59E0B)
                                            else Color(0xFF64748B),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tab ${i + 1}" + if (stats.characterName.isNotBlank() && stats.characterName != "Chưa mở tab") ": ${stats.characterName}" else "",
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

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
                for (i in 0..3) {
                    val holder = tabCardHolders[i]
                    val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }
                    val isSelected = (selectedTabIndex == i)

                    val newTitle = "Tab ${i + 1}: ${stats.characterName}"
                    if (holder.titleText.text != newTitle) {
                        holder.titleText.text = newTitle
                    }
                    holder.titleText.setTextColor(
                        if (isSelected) AndroidColor.parseColor("#38BDF8") else AndroidColor.parseColor("#E2E8F0")
                    )

                    // Điểm báo trạng thái online/offline
                    val dotColor = if (stats.state == TabLifecycleState.IN_GAME) AndroidColor.parseColor("#10B981")
                        else if (stats.state == TabLifecycleState.LOGGING_IN) AndroidColor.parseColor("#F59E0B")
                        else AndroidColor.parseColor("#64748B")
                    holder.statusDot.setBackgroundColor(dotColor)

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

    // Mini Header của Tab (Hỗ trợ vuốt ngang khi cột hẹp trên màn hình dọc)
    val density = context.resources.displayMetrics.density
    val headerScrollView = android.widget.HorizontalScrollView(context).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (28 * density).toInt()
        )
        setBackgroundColor(AndroidColor.parseColor("#111827"))
    }

    val headerLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(AndroidColor.parseColor("#111827"))
        val padH = (6 * density).toInt()
        setPadding(padH, 0, padH, 0)
        setOnClickListener { onSelectTab(tabIndex) }
    }

    val statusDot = View(context).apply {
        val size = (8 * density).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            rightMargin = (5 * density).toInt()
        }
        setBackgroundColor(AndroidColor.parseColor("#64748B"))
    }
    headerLayout.addView(statusDot)

    val titleText = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (4 * density).toInt()
        }
        minWidth = (56 * density).toInt()
        text = "Tab ${tabIndex + 1}"
        setTextColor(AndroidColor.parseColor("#E2E8F0"))
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
    }
    headerLayout.addView(titleText)

    val btnOpen = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            (42 * density).toInt(),
            (22 * density).toInt()
        ).apply {
            rightMargin = (4 * density).toInt()
        }
        text = "▶ Mở"
        textSize = 8.5f
        isSingleLine = true
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#16A34A"))
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            onSelectTab(tabIndex)
            tabManager.openSingleTab(tabIndex)
        }
    }
    headerLayout.addView(btnOpen)

    val btnPc = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            (32 * density).toInt(),
            (22 * density).toInt()
        ).apply {
            rightMargin = (4 * density).toInt()
        }
        text = "PC"
        textSize = 9.5f
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#4B5563")) // Mặc định Mobile (Xám)
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            val isPC = tabManager.toggleDesktopMode(tabIndex)
            setBackgroundColor(if (isPC) AndroidColor.parseColor("#9333EA") else AndroidColor.parseColor("#4B5563"))
        }
    }
    headerLayout.addView(btnPc)

    val btnReload = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            (32 * density).toInt(),
            (22 * density).toInt()
        ).apply {
            rightMargin = (4 * density).toInt()
        }
        text = "↻"
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        isSingleLine = true
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#3B82F6"))
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            val wv = tabManager.getOrCreateWebView(tabIndex)
            wv.reload()
        }
    }
    headerLayout.addView(btnReload)

    val btnStop = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            (34 * density).toInt(),
            (22 * density).toInt()
        )
        text = "⏹"
        textSize = 8.5f
        isSingleLine = true
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#B91C1C"))
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            tabManager.stopSingleTab(tabIndex)
        }
    }
    headerLayout.addView(btnStop)

    headerScrollView.addView(headerLayout)
    card.addView(headerScrollView)

    // Khung chứa WebView + Idle Overlay
    val contentContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        setBackgroundColor(AndroidColor.parseColor("#090D14"))
    }

    // THUỐC ĐẶC TRỊ CHỐNG ĐÓNG POPUP <SELECT>: 
    // Wrapper chặn triệt để các luồng đo đạc (measure) rác từ Jetpack Compose.
    // Nguyên nhân sâu xa: Mỗi khi Compose vẽ lại màn hình (ví dụ khi cập nhật ping mạng hoặc stats),
    // nó gọi measure() trên toàn bộ cây View. Khi WebView nhận lệnh measure() (dù kích thước không đổi),
    // bộ máy Chromium mặc định sẽ "cẩn tắc vô áy náy" đóng sập ngay lập tức mọi menu Dropdown/Select đang mở.
    val webViewWrapper = object : FrameLayout(context) {
        private var lastWidthSpec = -1
        private var lastHeightSpec = -1

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (widthMeasureSpec == lastWidthSpec && heightMeasureSpec == lastHeightSpec) {
                // Kích thước không đổi -> chặn đứng không cho truyền xuống WebView
                setMeasuredDimension(measuredWidth, measuredHeight)
                return
            }
            lastWidthSpec = widthMeasureSpec
            lastHeightSpec = heightMeasureSpec
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // Gắn WebView độc lập của Tab
    val webView = tabManager.getOrCreateWebView(tabIndex)
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    webViewWrapper.addView(webView)
    contentContainer.addView(webViewWrapper)

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
        titleText = titleText,
        statusDot = statusDot,
        btnOpen = btnOpen,
        btnPc = btnPc,
        btnReload = btnReload,
        btnStop = btnStop,
        idleOverlay = idleOverlay,
        idleOpenBtn = idleOpenBtn,
        idleTitle = idleTitle
    )
}